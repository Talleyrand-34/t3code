package com.t3tools.peer.net

import com.t3tools.peer.model.BranchRef
import com.t3tools.peer.model.PhotoAttachment
import com.t3tools.peer.model.StoredAttachment
import com.t3tools.peer.model.PeerSearch
import com.t3tools.peer.model.ThreadMessageMatch
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

  /**
   * The messages that match a query, in the server's order.
   *
   * The server owns the messages, so it is the one that finds them. A thread's own
   * text is matched by the app, over the threads it already lists.
   */
  suspend fun searchMessages(query: String, limit: Int = SEARCH_LIMIT): List<ThreadMessageMatch> {
    val result =
      socket.call(
        PeerProtocol.METHOD_SEARCH_THREADS,
        JSONObject().put("query", query).put("limit", limit),
      )
    return PeerSearch.messageMatches(result)
  }

  /**
   * Stores a photo with the server and returns the attachment the chat reads it by.
   */
  suspend fun persistAttachment(
    threadId: String,
    messageId: String,
    photo: PhotoAttachment,
  ): StoredAttachment? {
    val result =
      socket.call(
        PeerProtocol.METHOD_PERSIST_ATTACHMENTS,
        JSONObject()
          .put("threadId", threadId)
          .put("messageId", messageId)
          .put("attachments", org.json.JSONArray().put(photo.toStoredAttachment())),
      )
    val attachment = result.optJSONArray("attachments")?.optJSONObject(0) ?: return null
    val id = attachment.optString("id")
    if (id.isEmpty()) return null
    return StoredAttachment(
      id = id,
      name = attachment.optString("name"),
      mimeType = attachment.optString("mimeType"),
      sizeBytes = attachment.optInt("sizeBytes"),
    )
  }

  /**
   * The address a stored photo is served from. The link is short-lived, so it is minted
   * when the photo is about to be shown.
   */
  suspend fun attachmentUrl(attachment: StoredAttachment): String? {
    val result =
      socket.call(
        PeerProtocol.METHOD_CREATE_ASSET_URL,
        JSONObject().put(
          "resource",
          JSONObject()
            .put("_tag", "attachment")
            .put("attachmentId", attachment.id)
            .put("fileName", attachment.name)
            .put("mimeType", attachment.mimeType),
        ),
      )
    val relative = result.optString("relativeUrl")
    if (relative.isEmpty()) return null
    return endpoint.httpBaseUrl.trimEnd('/') + "/" + relative.trimStart('/')
  }

  suspend fun sendMessage(
    threadId: String,
    text: String,
    attachments: List<StoredAttachment> = emptyList(),
    messageId: String = UUID.randomUUID().toString(),
  ) {
    val command =
      JSONObject()
        .put("type", "message.dispatch")
        .put("commandId", UUID.randomUUID().toString())
        .put("createdBy", "user")
        .put("creationSource", "mobile")
        .put("threadId", threadId)
        .put("messageId", messageId)
        .put("text", text)
        .put("attachments", org.json.JSONArray().apply {
          attachments.forEach { attachment ->
            put(
              org.json.JSONObject()
                .put("type", "image")
                .put("id", attachment.id)
                .put("name", attachment.name)
                .put("mimeType", attachment.mimeType)
                .put("sizeBytes", attachment.sizeBytes)
            )
          }
        })
        .put("dispatchMode", JSONObject().put("type", "start_immediately"))
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  /**
   * Answers a question the agent asked. The answer goes back on the request it
   * belongs to, so the agent reads it where it asked.
   */
  suspend fun respondToRequest(threadId: String, requestId: String, answers: Map<String, String>) {
    val command =
      JSONObject()
        .put("type", "runtime-request.respond")
        .put("commandId", UUID.randomUUID().toString())
        .put("threadId", threadId)
        .put("requestId", requestId)
        .put("answers", JSONObject(answers))
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  /** The branches and tags the host knows about in a checkout. */
  suspend fun listRefs(cwd: String): List<BranchRef> {
    val result =
      socket.call(
        PeerProtocol.METHOD_VCS_LIST_REFS,
        JSONObject().put("cwd", cwd).put("refKind", "all").put("limit", LIST_REFS_LIMIT),
      )
    val refs = result.optJSONArray("refs") ?: return emptyList()
    val parsed = mutableListOf<BranchRef>()
    for (index in 0 until refs.length()) {
      val ref = refs.optJSONObject(index) ?: continue
      val name = ref.optString("name")
      if (name.isEmpty()) continue
      parsed +=
        BranchRef(
          name = name,
          isRemote = ref.optBoolean("isRemote"),
          current = ref.optBoolean("current"),
          worktreePath = ref.optString("worktreePath"),
        )
    }
    return parsed
  }

  /**
   * Moves the checkout to another branch. The host refuses this unless the
   * session was granted the source control scope.
   */
  suspend fun switchRef(cwd: String, refName: String): String {
    val result =
      socket.call(
        PeerProtocol.METHOD_VCS_SWITCH_REF,
        JSONObject().put("cwd", cwd).put("refName", refName),
      )
    return result.optString("refName").ifEmpty { refName }
  }

  /** Keeps the thread's own branch in step with the checkout it runs in. */
  suspend fun updateThreadBranch(threadId: String, branch: String, worktreePath: String?) {
    val command =
      JSONObject()
        .put("type", "thread.metadata.update")
        .put("commandId", UUID.randomUUID().toString())
        .put("threadId", threadId)
        .put("branch", branch)
    if (worktreePath != null) command.put("worktreePath", worktreePath)
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  fun close() {
    socket.close()
  }

  companion object {
    /** How many branches the picker asks for at once. */
    private const val LIST_REFS_LIMIT = 50

    /** How many message hits a search asks for at once. */
    private const val SEARCH_LIMIT = 50

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
