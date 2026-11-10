package com.t3tools.peer.net

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * A live connection to one T3 Code server, reached directly over the network.
 * Everything here stays between the phone and the server: no cloud service is
 * involved at any point.
 */
class PeerSession private constructor(
  private val socket: PeerSocket,
  val endpoint: PeerEndpoint,
  val environmentLabel: String,
  val environmentId: String,
  val serverVersion: String,
  /** The session token, so the app can come back without pairing again. */
  val bearerToken: String,
) {
  fun subscribeShell(onItem: (JSONObject) -> Unit) {
    socket.stream(PeerProtocol.METHOD_SUBSCRIBE_SHELL, JSONObject(), onItem)
  }

  /** Starts the live view of one thread and returns the handle to stop it. */
  fun subscribeThread(threadId: String, onItem: (JSONObject) -> Unit): Int =
    socket.stream(
      PeerProtocol.METHOD_SUBSCRIBE_THREAD,
      JSONObject().put("threadId", threadId),
      onItem,
    )

  /** Stops a thread's live view, so its items stop arriving. */
  fun unsubscribe(requestId: Int) {
    socket.cancel(requestId)
  }

  suspend fun sendMessage(threadId: String, text: String) {
    val command =
      JSONObject()
        .put("type", "message.dispatch")
        .put("commandId", UUID.randomUUID().toString())
        .put("createdBy", "user")
        .put("creationSource", "mobile")
        .put("threadId", threadId)
        .put("messageId", UUID.randomUUID().toString())
        .put("text", text)
        .put("attachments", org.json.JSONArray())
        .put("dispatchMode", JSONObject().put("type", "start_immediately"))
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  fun close() {
    socket.close()
  }

  companion object {
    /** A socket that stays quiet when idle and notices a dead one. */
    private fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    /** The session token to use and the socket ticket that goes with it. */
    private suspend fun resolveSession(
      auth: PeerAuth,
      endpoint: PeerEndpoint,
      savedToken: String?,
    ): Pair<String, String> {
      if (!savedToken.isNullOrBlank()) {
        val ticket = runCatching { auth.websocketTicket(endpoint, savedToken) }.getOrNull()
        if (ticket != null) return savedToken to ticket
      }
      val token = auth.accessToken(endpoint)
      val ticket =
        auth.websocketTicket(endpoint, token)
          ?: throw PeerException("The server did not return a WebSocket ticket.")
      return token to ticket
    }

    suspend fun connect(
      endpoint: PeerEndpoint,
      callbackScope: CoroutineScope,
      savedToken: String? = null,
      onClosed: (reason: String) -> Unit = {},
      http: OkHttpClient = defaultHttpClient(),
    ): PeerSession {
      val auth = PeerAuth(http)
      // A saved session outlives the app, so the phone comes back without a
      // new pairing code. It only falls back to the code when the server
      // refuses the session.
      val (bearerToken, ticket) = resolveSession(auth, endpoint, savedToken)
      val socketUrl =
        endpoint.wsBaseUrl +
          "/ws?wsTicket=" +
          java.net.URLEncoder.encode(ticket, "UTF-8") +
          "&orchestrationProtocol=" +
          PeerProtocol.ORCHESTRATION_PROTOCOL_VERSION
      val socket = PeerSocket(http, callbackScope, onClosed)
      socket.open(socketUrl)
      val config = socket.call(PeerProtocol.METHOD_SERVER_GET_CONFIG, JSONObject())
      val environment = config.optJSONObject("environment")
      return PeerSession(
        socket = socket,
        endpoint = endpoint,
        environmentLabel = environment?.optString("label").orEmpty().ifEmpty { "T3 Code" },
        environmentId = environment?.optString("environmentId").orEmpty(),
        serverVersion = environment?.optString("serverVersion").orEmpty(),
        bearerToken = bearerToken,
      )
    }
  }
}
