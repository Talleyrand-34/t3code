package com.t3tools.peer.net

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Speaks T3 Code's RPC over a WebSocket. A request carries a tag and a JSON
 * payload; the answer arrives as an exit frame, and a stream arrives as chunk
 * frames that the client acknowledges.
 */
class PeerSocket(
  private val http: OkHttpClient,
  private val callbackScope: CoroutineScope,
  private val onClosed: (reason: String) -> Unit,
) {
  private val nextRequestId = AtomicInteger(1)
  private val calls = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
  private val streams = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
  private val lastFrameAt = AtomicLong(System.currentTimeMillis())
  private var socket: WebSocket? = null

  suspend fun open(socketUrl: String) {
    val opened = CompletableDeferred<Unit>()
    val request = Request.Builder().url(socketUrl).build()
    val webSocket = http.newWebSocket(
      request,
      object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
          opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
          lastFrameAt.set(System.currentTimeMillis())
          handle(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
          opened.completeExceptionally(PeerException(t.message ?: "The connection failed."))
          failAll(t.message ?: "The connection failed.")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
          opened.completeExceptionally(PeerException("The server closed the connection."))
          failAll("The server closed the connection.")
        }
      },
    )
    socket = webSocket
    opened.await()
    watchForSilence()
  }

  /**
   * A socket can stay open after the server is gone, so the client pings and
   * treats a quiet connection as a dead one.
   */
  private fun watchForSilence() {
    callbackScope.launch {
      while (true) {
        delay(WATCHDOG_INTERVAL_MS)
        if (System.currentTimeMillis() - lastFrameAt.get() > QUIET_LIMIT_MS) {
          failAll("The connection went quiet.")
          return@launch
        }
        send(PeerProtocol.ping())
      }
    }
  }

  /** Runs one call and returns the value of its exit frame. */
  suspend fun call(tag: String, payload: JSONObject, timeoutMillis: Long = 30_000): JSONObject {
    val id = nextRequestId.getAndIncrement()
    val deferred = CompletableDeferred<JSONObject>()
    calls[id] = deferred
    send(PeerProtocol.request(id, tag, payload))
    return withContext(Dispatchers.IO) {
      try {
        withTimeout(timeoutMillis) { deferred.await() }
      } finally {
        calls.remove(id)
      }
    }
  }

  /** Starts a stream. Every value it produces is handed to [onValue]. */
  fun stream(tag: String, payload: JSONObject, onValue: (JSONObject) -> Unit): Int {
    val id = nextRequestId.getAndIncrement()
    streams[id] = onValue
    send(PeerProtocol.request(id, tag, payload))
    return id
  }

  /** Stops a stream so the server stops sending it. */
  fun cancel(requestId: Int) {
    streams.remove(requestId)
    send(PeerProtocol.interrupt(requestId))
  }

  fun close() {
    streams.clear()
    calls.values.forEach { it.completeExceptionally(PeerException("The connection closed.")) }
    calls.clear()
    socket?.close(1000, "closed by client")
    socket = null
  }

  private fun send(frame: JSONObject) {
    socket?.send(frame.toString())
  }

  private fun handle(text: String) {
    val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
    when (frame.optString("_tag")) {
      "Chunk" -> {
        val requestId = frame.optInt("requestId")
        val values = frame.optJSONArray("values")
        if (values != null) {
          for (index in 0 until values.length()) {
            val value = values.optJSONObject(index) ?: continue
            streams[requestId]?.let { onValue ->
              callbackScope.launch { onValue(value) }
            }
          }
        }
        // The server waits for this before it writes the next chunk.
        send(PeerProtocol.ack(requestId))
      }

      "Exit" -> {
        val requestId = frame.optInt("requestId")
        val exit = frame.optJSONObject("exit")
        streams.remove(requestId)
        val deferred = calls.remove(requestId)
        if (deferred != null) {
          if (exit != null && exit.optString("_tag") == "Success") {
            deferred.complete(exit.optJSONObject("value") ?: JSONObject())
          } else {
            deferred.completeExceptionally(PeerException(failureMessage(exit)))
          }
        }
      }

      "Ping" -> send(PeerProtocol.pong())

      "Defect" -> failAll("The server reported a defect.")
    }
  }

  private fun failAll(reason: String) {
    calls.values.forEach { it.completeExceptionally(PeerException(reason)) }
    calls.clear()
    streams.clear()
    callbackScope.launch { onClosed(reason) }
  }

  private fun failureMessage(exit: JSONObject?): String {
    val cause = exit?.optJSONArray("cause") ?: return "The server refused the request."
    for (index in 0 until cause.length()) {
      val entry = cause.optJSONObject(index) ?: continue
      when (entry.optString("_tag")) {
        "Fail" -> {
          val error = entry.optJSONObject("error")
          val message = error?.optString("message").orEmpty()
          if (message.isNotBlank()) return message
        }

        "Die" -> {
          val defect = entry.opt("defect")
          return "The server failed: $defect"
        }
      }
    }
    return "The server refused the request."
  }

  private companion object {
    const val WATCHDOG_INTERVAL_MS = 10_000L
    const val QUIET_LIMIT_MS = 30_000L
  }
}
