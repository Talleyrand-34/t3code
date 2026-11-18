package com.t3tools.peer.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.t3tools.peer.data.PeerStore
import com.t3tools.peer.model.BranchRef
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import com.t3tools.peer.model.LocalAttachment
import com.t3tools.peer.model.PeerAppearance
import com.t3tools.peer.model.PeerParsing
import com.t3tools.peer.model.PendingMessage
import com.t3tools.peer.model.QueuedRun
import com.t3tools.peer.model.SubagentSummary
import com.t3tools.peer.model.ThreadSummary
import com.t3tools.peer.model.selectWarmThreads
import com.t3tools.peer.net.PeerEndpoint
import com.t3tools.peer.net.PeerEndpoints
import com.t3tools.peer.net.PeerSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

enum class PeerScreen { CONNECT, THREADS, THREAD }

class PeerViewModel(application: Application) : AndroidViewModel(application) {
  private val store = PeerStore(application)

  var screen by mutableStateOf(PeerScreen.CONNECT)
    private set
  var serverInput by mutableStateOf(store.serverInput)
  var codeInput by mutableStateOf("")
  var connecting by mutableStateOf(false)
    private set
  var error by mutableStateOf<String?>(null)
    private set
  var environmentLabel by mutableStateOf("")
    private set
  var threads by mutableStateOf(emptyList<ThreadSummary>())
    private set
  var openThread by mutableStateOf<ThreadSummary?>(null)
    private set
  var lines by mutableStateOf(emptyList<ChatLine>())
    private set
  /** The messages the server holds but has not run yet. */
  var pendingLines by mutableStateOf(emptyList<ChatLine>())
    private set
  /** The images the user attached to the message they are writing. */
  var attachments by mutableStateOf(emptyList<LocalAttachment>())
    private set
  /** The run the thread is working on, while one is. */
  var activeRunId by mutableStateOf<String?>(null)
    private set
  /** The run that was interrupted and can be continued. */
  var resumableRunId by mutableStateOf<String?>(null)
    private set
  /** Whether the thread holds turns it has not started. */
  var holdsQueuedRuns by mutableStateOf(false)
    private set
  /** The other agents working inside the open thread. */
  var subagents by mutableStateOf(emptyList<SubagentSummary>())
    private set
  /** Whether the agents inside a thread are shown. */
  var showSubagents by mutableStateOf(store.showSubagents)
    private set
  /** The branch of the open thread, and the branches it can move to. */
  var branches by mutableStateOf(emptyList<BranchRef>())
    private set
  var branchSheetVisible by mutableStateOf(false)
    private set
  var composer by mutableStateOf("")
  var appearance by mutableStateOf(store.appearance)
  var settingsVisible by mutableStateOf(false)
  /** True while the app reads everything again from the host. */
  var resyncing by mutableStateOf(false)
    private set

  private var session: PeerSession? = null
  private var shellStream: Int? = null
  /** The streams of the threads the app keeps ready, by thread id. */
  private val threadStreams = LinkedHashMap<String, Int>()
  /** What the app knows about each thread it keeps ready. */
  private val threadStates = LinkedHashMap<String, ThreadState>()
  private var draftSaveJob: Job? = null
  private var reconnectJob: Job? = null
  private var reconnectAttempts = 0
  private var connectionGeneration = 0
  private var appliedLink: String? = null
  private var projectTitles = emptyMap<String, String>()
  private var projectRoots = emptyMap<String, String>()

  init {
    // A saved session lets the app come back on its own.
    if (store.serverInput.isNotBlank() && !store.bearerToken.isNullOrBlank()) {
      connect()
    }
  }

  fun dismissError() {
    error = null
  }

  /**
   * Reads everything again from the host. A subscription starts with a full
   * snapshot, so a fresh one rebuilds the list from the server's own view of
   * it. It is the way out of a session that missed something.
   */
  fun resync() {
    val session = session ?: return
    if (resyncing) return
    resyncing = true
    try {
      closeShellStream()
      threads = emptyList()
      shellStream = session.subscribeShell(::onShellItem)
      // Everything the app held came from the host, so it goes and comes again.
      releaseAllThreads()
      updateWarmThreads()
      publishOpenThread()
    } catch (failure: Exception) {
      error = failure.message ?: "Could not resync."
    } finally {
      resyncing = false
    }
  }

  /** Hides the agents inside a thread, or shows them again. */
  fun toggleSubagents() {
    showSubagents = !showSubagents
    store.showSubagents = showSubagents
  }

  /** Keeps the words the user typed, so they are still there when they come back. */
  fun onComposerChange(text: String) {
    composer = text
    val thread = openThread ?: return
    // The store writes to disk, so it waits for the typing to settle.
    draftSaveJob?.cancel()
    draftSaveJob = viewModelScope.launch {
      delay(DRAFT_SAVE_DELAY_MS)
      store.setDraft(thread.id, text)
    }
  }

  /** Adds an image the user picked to the message they are writing. */
  fun attachImage(attachment: LocalAttachment) {
    if (attachments.size >= MAX_ATTACHMENTS) {
      error = "You can attach up to $MAX_ATTACHMENTS images to a message."
      return
    }
    attachments = attachments + attachment
  }

  /**
   * Reads the image the user picked. The host keeps the picture with the
   * message, so it travels as a data URL.
   */
  fun readAttachment(context: Context, uri: Uri) {
    viewModelScope.launch {
      try {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/png"
        val stream = resolver.openInputStream(uri)
          ?: throw IllegalStateException("Could not read the image.")
        val bytes = stream.use { it.readBytes() }
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
          error = "That image is too large to send."
          return@launch
        }
        val name = queryDisplayName(resolver, uri) ?: "image"
        attachImage(
          LocalAttachment(
            name = name,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            dataUrl = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
          ),
        )
      } catch (failure: Exception) {
        error = failure.message ?: "Could not attach the image."
      }
    }
  }

  /** The name the picker gave the file, so the user recognizes it. */
  private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, null, null, null, null)?.use { cursor ->
      val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return null
  }

  /** Takes an attached image back off the message. */
  fun removeAttachment(attachment: LocalAttachment) {
    attachments = attachments.filterNot { it === attachment }
  }

  /** The id a message carries, so the host can keep the images with it. */
  private fun messageId(): String = UUID.randomUUID().toString()

  /** Stops the turn the thread is working on. */
  fun stop() {
    val thread = openThread ?: return
    val runId = activeRunId ?: return
    viewModelScope.launch {
      try {
        session?.interruptRun(thread.id, runId)
      } catch (failure: Exception) {
        error = failure.message ?: "Could not stop the thread."
      }
    }
  }

  /**
   * Goes on with the turn the thread interrupted, without the user writing
   * anything. It also releases the turns the host is holding.
   */
  fun continueThread() {
    val thread = openThread ?: return
    val resumable = resumableRunId
    viewModelScope.launch {
      try {
        val session = session ?: return@launch
        if (resumable == null) {
          session.resumeQueue(thread.id)
          return@launch
        }
        session.sendMessage(thread.id, messageId(), CONTINUE_TEXT, emptyList(), resumable)
        if (holdsQueuedRuns) session.resumeQueue(thread.id)
      } catch (failure: Exception) {
        error = failure.message ?: "Could not continue the thread."
      }
    }
  }

  fun chooseAppearance(value: PeerAppearance) {
    appearance = value
    store.appearance = value
  }

  fun showSettings() {
    settingsVisible = true
  }

  fun hideSettings() {
    settingsVisible = false
  }

  /** Opens the branch picker and asks the host what the project has. */
  fun showBranches() {
    branchSheetVisible = true
    loadBranches()
  }

  fun hideBranches() {
    branchSheetVisible = false
  }

  /** Moves the checkout of the open thread to another branch. */
  fun switchBranch(ref: BranchRef) {
    val thread = openThread ?: return
    val checkout = checkoutOf(thread, ref) ?: return
    viewModelScope.launch {
      try {
        val session = session ?: return@launch
        val switched = session.switchRef(checkout, ref.name)
        // The thread follows the checkout it runs in.
        session.updateThreadBranch(thread.id, switched, worktreeFor(thread, ref))
        openThread = openThread?.copy(branch = switched)
        branches = branches.map { it.copy(current = it.name == switched) }
        branchSheetVisible = false
      } catch (failure: Exception) {
        error = failure.message ?: "Could not change the branch."
      }
    }
  }

  private fun loadBranches() {
    val thread = openThread ?: return
    val checkout = checkoutOf(thread, null) ?: return
    viewModelScope.launch {
      try {
        branches = session?.listRefs(checkout) ?: return@launch
      } catch (failure: Exception) {
        error = failure.message ?: "Could not read the branches."
      }
    }
  }

  /**
   * Where the switch happens: a branch that lives in a worktree of its own
   * is moved there, so the project's checkout is left alone.
   */
  private fun checkoutOf(thread: ThreadSummary, ref: BranchRef?): String? {
    val projectRoot = projectRoots[thread.projectId].orEmpty()
    val refWorktree = ref?.worktreePath.orEmpty()
    if (refWorktree.isNotEmpty() && refWorktree != projectRoot) return refWorktree
    return thread.worktreePath.ifEmpty { projectRoot }.ifEmpty { null }
  }

  /** The worktree the thread should point at after the switch, if it changes. */
  private fun worktreeFor(thread: ThreadSummary, ref: BranchRef): String? {
    val projectRoot = projectRoots[thread.projectId].orEmpty()
    val refWorktree = ref.worktreePath
    if (refWorktree.isEmpty() || refWorktree == projectRoot) return null
    return refWorktree
  }

  /**
   * Takes a pairing link from a deep link or the clipboard. The link carries
   * the server address and the one-time code.
   */
  fun applyPairingLink(link: String) {
    if (link == appliedLink) return
    appliedLink = link
    val pairingUrl = runCatching { android.net.Uri.parse(link).getQueryParameter("pairingUrl") }
      .getOrNull()
    val target = pairingUrl?.takeIf { it.isNotBlank() } ?: link
    val endpoint = PeerEndpoints.parse(target, "") ?: return
    if (endpoint.code.isBlank()) return
    serverInput = endpoint.httpBaseUrl
      .removePrefix("https://")
      .removePrefix("http://")
    codeInput = endpoint.code
    connect()
  }

  fun connect() {
    reconnectJob?.cancel()
    reconnectJob = null
    val endpoint = PeerEndpoints.parse(serverInput, codeInput)
    if (endpoint == null) {
      error = "Enter the server address, for example 192.168.1.20:3923."
      return
    }
    if (endpoint.code.isBlank() && store.bearerToken.isNullOrBlank()) {
      error = "Enter the pairing code from `t3 pair` on the host."
      return
    }
    connecting = true
    error = null
    viewModelScope.launch { openSession(endpoint) }
  }

  fun disconnect() {
    reconnectJob?.cancel()
    reconnectJob = null
    connectionGeneration += 1
    closeShellStream()
    releaseAllThreads()
    session?.close()
    session = null
    threads = emptyList()
    openThread = null
    lines = emptyList()
    pendingLines = emptyList()
    subagents = emptyList()
    environmentLabel = ""
    screen = PeerScreen.CONNECT
  }

  /**
   * Drops the session the host granted, so the app stops reconnecting on its own and
   * needs a new pairing code.
   */
  fun forgetServer() {
    disconnect()
    store.forgetSession()
  }

  fun openThread(thread: ThreadSummary) {
    openThread = thread
    // The words typed for this thread are still there, even after the app was
    // closed or the thread was left.
    composer = store.draft(thread.id)
    screen = PeerScreen.THREAD
    // A thread that is kept ready shows its chat at once, without a wait.
    publishOpenThread()
    keepThreadReady(thread.id)
  }

  /** Leaves the thread and shows the list again, the way the back button does. */
  fun backToThreads() {
    openThread = null
    lines = emptyList()
    pendingLines = emptyList()
    subagents = emptyList()
    activeRunId = null
    resumableRunId = null
    holdsQueuedRuns = false
    screen = PeerScreen.THREADS
  }

  /**
   * Keeps a thread ready, so opening it shows its chat at once. The host sends
   * the same bounded window it sends the web app, which is a fraction of a
   * thread that has been worked on for days.
   */
  private fun keepThreadReady(threadId: String) {
    val session = session ?: return
    if (threadStreams.containsKey(threadId)) return
    threadStreams[threadId] =
      session.subscribeThread(threadId) { item -> onThreadItem(threadId, item) }
    threadStates.getOrPut(threadId) { ThreadState() }
  }

  /** Lets a thread go, so the host stops sending it and the phone forgets it. */
  private fun releaseThread(threadId: String) {
    threadStreams.remove(threadId)?.let { session?.unsubscribe(it) }
    threadStates.remove(threadId)
  }

  /** Lets every thread go, for when the host is read again from the start. */
  private fun releaseAllThreads() {
    for (requestId in threadStreams.values.toList()) {
      session?.unsubscribe(requestId)
    }
    threadStreams.clear()
    threadStates.clear()
  }

  /**
   * Keeps the threads worth opening ready: the ones an agent works on, then
   * the ones touched last. The phone holds those, so opening one of them shows
   * its chat at once instead of waiting for the host.
   */
  private fun updateWarmThreads() {
    if (session == null) return
    val wanted = selectWarmThreads(threads, PRELOAD_LIMIT)
    for (thread in wanted) keepThreadReady(thread.id)
    val keep = wanted.mapTo(mutableSetOf()) { it.id }
    openThread?.id?.let { keep.add(it) }
    for (threadId in threadStreams.keys.toList()) {
      if (threadId !in keep) releaseThread(threadId)
    }
  }

  /** The same for the thread list: a second stream would double every update. */
  private fun closeShellStream() {
    shellStream?.let { session?.unsubscribe(it) }
    shellStream = null
  }

  fun send() {
    val text = composer.trim()
    val thread = openThread ?: return
    if (text.isEmpty() && attachments.isEmpty()) return
    val pending = attachments
    composer = ""
    attachments = emptyList()
    store.setDraft(thread.id, "")
    viewModelScope.launch {
      try {
        val session = session ?: return@launch
        val messageId = messageId()
        // The host keeps the images with the message, so they go up first.
        val stored = pending.map { session.uploadAttachment(thread.id, messageId, it) }
        session.sendMessage(thread.id, messageId, text, stored)
      } catch (failure: Exception) {
        error = failure.message ?: "Could not send the message."
      }
    }
  }

  private suspend fun openSession(endpoint: PeerEndpoint) {
    val generation = ++connectionGeneration
    try {
      val opened =
        PeerSession.connect(
          endpoint = endpoint,
          callbackScope = viewModelScope,
          savedToken = store.bearerToken,
          onClosed = { reason -> if (generation == connectionGeneration) onSessionClosed(reason) },
        )
      session?.close()
      session = opened
      releaseAllThreads()
      shellStream = null
      store.bearerToken = opened.bearerToken
      store.serverInput = serverInput
      codeInput = ""
      reconnectAttempts = 0
      error = null
      environmentLabel = opened.environmentLabel
      threads = emptyList()
      screen = PeerScreen.THREADS
      shellStream = opened.subscribeShell(::onShellItem)
    } catch (failure: Exception) {
      // The host may still be down, so keep trying.
      scheduleReconnect(failure.message ?: "Could not connect to the server.")
    } finally {
      connecting = false
    }
  }

  /** A dropped socket is not the end: the session outlives the connection. */
  private fun onSessionClosed(reason: String) {
    if (session == null) return
    session = null
    scheduleReconnect(reason)
  }

  private fun scheduleReconnect(reason: String) {
    val endpoint = PeerEndpoints.parse(serverInput, codeInput)
    if (endpoint == null || (endpoint.code.isBlank() && store.bearerToken.isNullOrBlank())) {
      error = "$reason Pair again to reconnect."
      screen = PeerScreen.CONNECT
      return
    }
    if (reconnectJob?.isActive == true) return
    val waitSeconds = minOf(60L, 1L shl minOf(reconnectAttempts, 5))
    reconnectAttempts += 1
    error = "$reason Reconnecting in ${waitSeconds}s."
    reconnectJob = viewModelScope.launch {
      delay(waitSeconds * 1_000)
      reconnectJob = null
      if (session != null) return@launch
      openSession(endpoint)
    }
  }

  private fun onShellItem(item: JSONObject) {
    when (item.optString("kind")) {
      "snapshot" -> {
        val snapshot = item.optJSONObject("snapshot") ?: return
        val titles = readProjectTitles(snapshot)
        // A snapshot that names resolved repository roots only refreshes the
        // project metadata. The thread list stays as it was.
        if (item.has("resolvedRepositoryIdentityRoots")) {
          if (titles.isNotEmpty() && titles != projectTitles) {
            projectTitles = titles
            threads = threads.map { it.copy(projectTitle = titles[it.projectId] ?: it.projectTitle) }
          }
          return
        }
        projectTitles = titles
        projectRoots = readProjectRoots(snapshot)
        val collected = mutableListOf<ThreadSummary>()
        readThreads(snapshot.optJSONArray("threads"), collected)
        readThreads(snapshot.optJSONArray("archivedThreads"), collected)
        threads = listed(collected).sortedByDescending { it.updatedAt }
        updateWarmThreads()
      }

      "thread.updated" -> {
        val shell = item.optJSONObject("thread") ?: return
        val summary = PeerParsing.thread(shell, projectTitles)
        if (!summary.isListed) {
          // A subagent belongs inside its parent, not in the list.
          threads = threads.filterNot { it.id == summary.id }
          return
        }
        threads = (threads.filterNot { it.id == summary.id } + summary)
          .sortedByDescending { it.updatedAt }
        updateWarmThreads()
      }

      "thread.removed" -> {
        val id = item.optString("threadId")
        threads = threads.filterNot { it.id == id }
        releaseThread(id)
      }
    }
  }

  private fun onThreadItem(threadId: String, item: JSONObject) {
    val state = threadStates.getOrPut(threadId) { ThreadState() }
    when (item.optString("kind")) {
      "snapshot" -> {
        val projection = item.optJSONObject("projection") ?: return
        state.lines.clear()
        PeerParsing.lines(projection).forEach { state.lines[it.id] = it }
        state.runs.clear()
        PeerParsing.runs(projection).forEach { state.runs[it.id] = it }
        state.messages.clear()
        PeerParsing.messages(projection).forEach { state.messages[it.id] = it }
        state.subagents.clear()
        PeerParsing.subagents(projection).forEach { state.subagents[it.id] = it }
        publishThread(threadId)
      }

      "event" -> {
        val event = item.optJSONObject("event") ?: return
        when (event.optString("type")) {
          "turn-item.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val line = PeerParsing.line(payload) ?: return
            state.lines[line.id] = line
            publishThread(threadId)
          }

          "run.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val run = PeerParsing.run(payload) ?: return
            state.runs[run.id] = run
            publishThread(threadId)
          }

          "message.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val message = PeerParsing.message(payload) ?: return
            state.messages[message.id] = message
            publishThread(threadId)
          }

          "subagent.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val subagent = PeerParsing.subagent(payload) ?: return
            state.subagents[subagent.id] = subagent
            publishThread(threadId)
          }

          "thread.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val title = payload.optString("title")
            if (title.isNotEmpty() && openThread?.id == threadId) {
              openThread = openThread?.copy(title = title)
            }
          }
        }
      }
    }
  }

  /** Shows what changed in a thread, but only when it is the one on screen. */
  private fun publishThread(threadId: String) {
    if (openThread?.id != threadId) return
    publishOpenThread()
  }

  /**
   * Shows what the app knows about the open thread. A thread that is kept
   * ready has its chat already, so it appears at once.
   */
  private fun publishOpenThread() {
    val thread = openThread ?: return
    val state = threadStates[thread.id]
    lines = state?.lines?.values?.toList() ?: emptyList()
    pendingLines =
      state?.let {
        PeerParsing.pendingLines(it.runs.values.toList(), it.messages.values.toList())
      } ?: emptyList()
    subagents = state?.subagents?.values?.sortedBy { it.startedAt } ?: emptyList()
    publishRuns(state)
  }

  /**
   * What the thread can be asked to do right now: stop the turn it is working
   * on, or go on with the one it interrupted.
   */
  private fun publishRuns(state: ThreadState?) {
    val runs = state?.runs?.values?.sortedBy { it.ordinal } ?: emptyList()
    activeRunId = runs.lastOrNull { it.status in LIVE_RUN_STATUSES }?.id
    resumableRunId = runs.lastOrNull { it.status == "interrupted" }?.id
    holdsQueuedRuns = runs.any { it.status == "queued" && it.queueHeld }
  }

  /** Subagents and side chats live inside their parent, never in the list. */
  private fun listed(threads: List<ThreadSummary>): List<ThreadSummary> =
    threads.filter { it.isListed }

  private fun readProjectTitles(snapshot: JSONObject): Map<String, String> {
    val titles = mutableMapOf<String, String>()
    val projects = snapshot.optJSONArray("projects") ?: return titles
    for (index in 0 until projects.length()) {
      val project = projects.optJSONObject(index) ?: continue
      val id = project.optString("id")
      if (id.isNotEmpty()) titles[id] = project.optString("title").ifEmpty { id }
    }
    return titles
  }

  /** Where each project lives, so the app can ask the host about its branches. */
  private fun readProjectRoots(snapshot: JSONObject): Map<String, String> {
    val roots = mutableMapOf<String, String>()
    val projects = snapshot.optJSONArray("projects") ?: return roots
    for (index in 0 until projects.length()) {
      val project = projects.optJSONObject(index) ?: continue
      val id = project.optString("id")
      val root = project.optString("workspaceRoot")
      if (id.isNotEmpty() && root.isNotEmpty()) roots[id] = root
    }
    return roots
  }

  private fun readThreads(array: org.json.JSONArray?, into: MutableList<ThreadSummary>) {
    if (array == null) return
    for (index in 0 until array.length()) {
      val shell = array.optJSONObject(index) ?: continue
      into += PeerParsing.thread(shell, projectTitles)
    }
  }

  override fun onCleared() {
    super.onCleared()
    // The app is gone, so the socket has nothing to talk to.
    draftSaveJob?.cancel()
    session?.close()
    session = null
  }

  private companion object {
    /**
     * The runs the thread is working on. A run waiting for approval still holds
     * the turn, so it can be stopped too.
     */
    val LIVE_RUN_STATUSES = setOf("running", "waiting", "starting", "preparing")

    /**
     * How many threads the app keeps ready. The host sends each one a bounded
     * window, so this is a few hundred kilobytes of history the phone holds to
     * make opening a thread instant.
     */
    const val PRELOAD_LIMIT = 10

    /** What the app writes when the user goes on with a turn themselves. */
    const val CONTINUE_TEXT = "Continue where you left off."

    /** How many images one message may carry. */
    const val MAX_ATTACHMENTS = 8

    /** The largest image the host takes. */
    const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

    /** How long the typing has to settle before the draft goes to disk. */
    const val DRAFT_SAVE_DELAY_MS = 400L
  }
}

/** What the app knows about one thread, so it can be opened without a wait. */
private class ThreadState {
  val lines = LinkedHashMap<String, ChatLine>()
  val runs = LinkedHashMap<String, QueuedRun>()
  val messages = LinkedHashMap<String, PendingMessage>()
  val subagents = LinkedHashMap<String, SubagentSummary>()
}
