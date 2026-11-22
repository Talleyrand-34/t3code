package com.t3tools.peer.net

import com.t3tools.peer.model.BranchRef
import com.t3tools.peer.model.PeerFile
import com.t3tools.peer.model.PhotoAttachment
import com.t3tools.peer.model.StoredAttachment
import com.t3tools.peer.model.PeerSearch
import com.t3tools.peer.model.ThreadMessageMatch
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
  private val http: OkHttpClient,
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
   * Stores a file with the server and returns the attachment the chat reads it by.
   *
   * The server takes a file in two steps: it signs a place to put the bytes, and
   * the bytes go there. The attachment is only known once the server has them.
   */
  suspend fun uploadAttachment(threadId: String, messageId: String, file: PeerFile): StoredAttachment? {
    val ticket =
      socket.call(
        PeerProtocol.METHOD_CREATE_UPLOAD_URL,
        JSONObject()
          .put("type", "file")
          .put("name", file.name)
          .put("mimeType", file.mimeType)
          .put("sizeBytes", file.sizeBytes),
      )
    val attachmentId = ticket.optString("attachmentId")
    val relativeUrl = ticket.optString("relativeUrl")
    if (attachmentId.isEmpty() || relativeUrl.isEmpty()) return null
    uploadBytes(relativeUrl, file.bytes)
    return StoredAttachment(
      id = attachmentId,
      name = file.name,
      mimeType = file.mimeType,
      sizeBytes = file.sizeBytes,
    )
  }

  /** Puts the bytes where the server said they go. */
  private suspend fun uploadBytes(relativeUrl: String, bytes: ByteArray) {
    val request =
      Request.Builder()
        .url(endpoint.httpBaseUrl.trimEnd('/') + "/" + relativeUrl.trimStart('/'))
        .header("authorization", "Bearer $bearerToken")
        .post(bytes.toRequestBody(null))
        .build()
    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw PeerException("The file could not be stored (HTTP ${response.code}).")
      }
    }
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

  /**
   * Sends a message, and says how it should reach the agent.
   *
   * A queued message waits for the running turn to finish. A steered one reaches
   * the agent right away, and the server takes it as a queue when the provider
   * cannot steer.
   */
  suspend fun sendMessage(
    threadId: String,
    text: String,
    attachments: List<StoredAttachment> = emptyList(),
    messageId: String = UUID.randomUUID().toString(),
    dispatchMode: String = "start_immediately",
    deliveryIntent: String? = null,
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
                .put("type", if (attachment.mimeType.startsWith("image/")) "image" else "file")
                .put("id", attachment.id)
                .put("name", attachment.name)
                .put("mimeType", attachment.mimeType)
                .put("sizeBytes", attachment.sizeBytes)
            )
          }
        })
        .put("dispatchMode", JSONObject().put("type", dispatchMode))
    if (deliveryIntent != null) command.put("deliveryIntent", deliveryIntent)
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  /**
   * Stops the run that is working, and holds the queue so nothing restarts it.
   *
   * The hold is what the official app's Stop does too: the messages that wait
   * stay waiting until the user says otherwise.
   */
  suspend fun interruptRun(threadId: String, runId: String) {
    dispatch(
      JSONObject()
        .put("type", "run.interrupt")
        .put("threadId", threadId)
        .put("runId", runId)
        .put("holdQueue", true),
    )
  }

  /** Releases the queue, so the messages that wait start again. */
  suspend fun resumeQueue(threadId: String) {
    dispatch(JSONObject().put("type", "queue.resume").put("threadId", threadId))
  }

  /** Starts one queued message now, which the server allows when nothing is working. */
  suspend fun startQueuedRun(threadId: String, runId: String) {
    dispatch(
      JSONObject().put("type", "queued-run.start").put("threadId", threadId).put("runId", runId),
    )
  }

  /** Sends a queued message into the turn that is working. */
  suspend fun promoteQueuedRun(threadId: String, queuedRunId: String, targetRunId: String) {
    dispatch(
      JSONObject()
        .put("type", "queued-message.promote-to-steer")
        .put("threadId", threadId)
        .put("queuedRunId", queuedRunId)
        .put("targetRunId", targetRunId),
    )
  }

  /** Takes a queued message out of the queue. */
  suspend fun cancelQueuedRun(threadId: String, runId: String) {
    dispatch(
      JSONObject().put("type", "queued-run.cancel").put("threadId", threadId).put("runId", runId),
    )
  }

  /** Changes what a queued message says. */
  suspend fun editQueuedRun(threadId: String, runId: String, text: String) {
    dispatch(
      JSONObject()
        .put("type", "queued-run.edit")
        .put("threadId", threadId)
        .put("runId", runId)
        .put("text", text),
    )
  }

  /**
   * Answers a question the agent asked. The answer goes back on the request it
   * belongs to, so the agent reads it where it asked.
   */
  suspend fun respondToRequest(threadId: String, requestId: String, answers: Map<String, String>) {
    dispatch(
      JSONObject()
        .put("type", "runtime-request.respond")
        .put("threadId", threadId)
        .put("requestId", requestId)
        .put("answers", JSONObject(answers)),
    )
  }

  /** Answers a permission the agent asked for. */
  suspend fun respondToApproval(threadId: String, requestId: String, decision: String) {
    dispatch(
      JSONObject()
        .put("type", "runtime-request.respond")
        .put("threadId", threadId)
        .put("requestId", requestId)
        .put("decision", decision),
    )
  }

  /** Stops one of the other agents working inside the thread. */
  suspend fun stopSubagent(threadId: String, runId: String, subagentId: String) {
    dispatch(
      JSONObject()
        .put("type", "subagent.stop")
        .put("threadId", threadId)
        .put("runId", runId)
        .put("subagentId", subagentId),
    )
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
        .put("threadId", threadId)
        .put("branch", branch)
    if (worktreePath != null) command.put("worktreePath", worktreePath)
    dispatch(command)
  }

  /**
   * Starts a new thread with a message.
   *
   * The server needs to know which model and mode the thread runs in, so the
   * phone sends the ones the project already uses.
   */
  suspend fun launchThread(
    projectId: String,
    title: String,
    text: String,
    modelSelection: JSONObject?,
    runtimeMode: String,
    interactionMode: String,
    branch: String?,
  ): String? {
    val input =
      JSONObject()
        .put("commandId", UUID.randomUUID().toString())
        .put("creationSource", "mobile")
        .put("projectId", projectId)
        .put("title", title)
        .put("generateTitle", true)
        .put("runtimeMode", runtimeMode.ifEmpty { "full-access" })
        .put("interactionMode", interactionMode.ifEmpty { "default" })
        .put(
          "workspaceStrategy",
          if (branch.isNullOrBlank()) {
            JSONObject().put("type", "root")
          } else {
            JSONObject().put("type", "root").put("branch", branch)
          },
        )
        .put(
          "initialMessage",
          JSONObject()
            .put("messageId", UUID.randomUUID().toString())
            .put("text", text)
            .put("attachments", org.json.JSONArray()),
        )
    if (modelSelection != null) input.put("modelSelection", modelSelection)
    val result = socket.call(PeerProtocol.METHOD_LAUNCH_THREAD, input)
    return result.optString("threadId").ifEmpty { null }
  }

  /** Renames a thread. */
  suspend fun renameThread(threadId: String, title: String) {
    dispatch(
      JSONObject().put("type", "thread.metadata.update").put("threadId", threadId).put("title", title),
    )
  }

  /** Puts a thread away, and takes it back out again. */
  suspend fun setThreadArchived(threadId: String, archived: Boolean) {
    dispatch(
      JSONObject()
        .put("type", if (archived) "thread.archive" else "thread.unarchive")
        .put("threadId", threadId),
    )
  }

  /** Removes a thread. */
  suspend fun deleteThread(threadId: String) {
    dispatch(JSONObject().put("type", "thread.delete").put("threadId", threadId))
  }

  /** Wakes a snoozed thread, or puts it back to sleep for a while. */
  suspend fun setThreadSnoozed(threadId: String, snoozed: Boolean, snoozedUntil: String? = null) {
    if (snoozed) {
      dispatch(
        JSONObject()
          .put("type", "thread.snooze")
          .put("threadId", threadId)
          .put("snoozedUntil", snoozedUntil ?: defaultSnoozeUntil()),
      )
      return
    }
    dispatch(
      JSONObject().put("type", "thread.unsnooze").put("threadId", threadId).put("reason", "user"),
    )
  }

  /** Marks a thread as not read, so the list points at it again. */
  suspend fun markThreadUnread(threadId: String) {
    dispatch(JSONObject().put("type", "thread.mark-unread").put("threadId", threadId))
  }

  /** Marks a thread as read. */
  suspend fun visitThread(threadId: String) {
    dispatch(
      JSONObject()
        .put("type", "thread.visit")
        .put("threadId", threadId)
        .put("visitedAt", java.time.Instant.now().toString()),
    )
  }

  /** Sends one command the server owns. */
  private suspend fun dispatch(command: JSONObject) {
    command.put("commandId", UUID.randomUUID().toString())
    socket.call(PeerProtocol.METHOD_DISPATCH_COMMAND, command)
  }

  /** When a snoozed thread wakes up on its own. */
  private fun defaultSnoozeUntil(): String =
    java.time.Instant.now().plus(java.time.Duration.ofHours(SNOOZE_HOURS)).toString()

  fun close() {
    socket.close()
  }

  companion object {
    /** How many branches the picker asks for at once. */
    private const val LIST_REFS_LIMIT = 50

    /** How many message hits a search asks for at once. */
    private const val SEARCH_LIMIT = 50

    /** How long a snoozed thread waits before it wakes on its own. */
    private const val SNOOZE_HOURS = 1L

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
        http = http,
        endpoint = endpoint,
        environmentLabel = environment?.optString("label").orEmpty().ifEmpty { "T3 Code" },
        environmentId = environment?.optString("environmentId").orEmpty(),
        serverVersion = environment?.optString("serverVersion").orEmpty(),
        bearerToken = bearerToken,
      )
    }
  }
}
