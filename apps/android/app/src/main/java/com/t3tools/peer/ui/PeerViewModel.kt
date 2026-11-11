package com.t3tools.peer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.t3tools.peer.data.PeerStore
import com.t3tools.peer.model.BranchRef
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import com.t3tools.peer.model.PeerAppearance
import com.t3tools.peer.model.PeerParsing
import com.t3tools.peer.model.PendingMessage
import com.t3tools.peer.model.QueuedRun
import com.t3tools.peer.model.SubagentSummary
import com.t3tools.peer.model.ThreadSummary
import com.t3tools.peer.net.PeerEndpoint
import com.t3tools.peer.net.PeerEndpoints
import com.t3tools.peer.net.PeerSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

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
  /** The other agents working inside the open thread. */
  var subagents by mutableStateOf(emptyList<SubagentSummary>())
    private set
  /** The branch of the open thread, and the branches it can move to. */
  var branches by mutableStateOf(emptyList<BranchRef>())
    private set
  var branchSheetVisible by mutableStateOf(false)
    private set
  var composer by mutableStateOf("")
  var appearance by mutableStateOf(store.appearance)
  var settingsVisible by mutableStateOf(false)

  private var session: PeerSession? = null
  private var threadStream: Int? = null
  private var reconnectJob: Job? = null
  private var reconnectAttempts = 0
  private var connectionGeneration = 0
  private var appliedLink: String? = null
  private val lineStore = LinkedHashMap<String, ChatLine>()
  private val runStore = LinkedHashMap<String, QueuedRun>()
  private val messageStore = LinkedHashMap<String, PendingMessage>()
  private val subagentStore = LinkedHashMap<String, SubagentSummary>()
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
    closeThreadStream()
    session?.close()
    session = null
    threads = emptyList()
    openThread = null
    lines = emptyList()
    lineStore.clear()
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
    lineStore.clear()
    lines = emptyList()
    screen = PeerScreen.THREAD
    closeThreadStream()
    threadStream = session?.subscribeThread(thread.id, ::onThreadItem)
  }

  /** The old thread must stop streaming, or its items land in the new one. */
  private fun closeThreadStream() {
    threadStream?.let { session?.unsubscribe(it) }
    threadStream = null
  }

  fun send() {
    val text = composer.trim()
    val thread = openThread ?: return
    if (text.isEmpty()) return
    composer = ""
    viewModelScope.launch {
      try {
        session?.sendMessage(thread.id, text)
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
      threadStream = null
      store.bearerToken = opened.bearerToken
      store.serverInput = serverInput
      codeInput = ""
      reconnectAttempts = 0
      error = null
      environmentLabel = opened.environmentLabel
      threads = emptyList()
      screen = PeerScreen.THREADS
      opened.subscribeShell(::onShellItem)
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
      }

      "thread.removed" -> {
        val id = item.optString("threadId")
        threads = threads.filterNot { it.id == id }
      }
    }
  }

  private fun onThreadItem(item: JSONObject) {
    when (item.optString("kind")) {
      "snapshot" -> {
        val projection = item.optJSONObject("projection") ?: return
        lineStore.clear()
        PeerParsing.lines(projection).forEach { lineStore[it.id] = it }
        runStore.clear()
        PeerParsing.runs(projection).forEach { runStore[it.id] = it }
        messageStore.clear()
        PeerParsing.messages(projection).forEach { messageStore[it.id] = it }
        subagentStore.clear()
        PeerParsing.subagents(projection).forEach { subagentStore[it.id] = it }
        publishLines()
        publishSubagents()
      }

      "event" -> {
        val event = item.optJSONObject("event") ?: return
        when (event.optString("type")) {
          "turn-item.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val line = PeerParsing.line(payload) ?: return
            lineStore[line.id] = line
            publishLines()
          }

          "run.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val run = PeerParsing.run(payload) ?: return
            runStore[run.id] = run
            publishLines()
          }

          "message.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val message = PeerParsing.message(payload) ?: return
            messageStore[message.id] = message
            publishLines()
          }

          "subagent.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val subagent = PeerParsing.subagent(payload) ?: return
            subagentStore[subagent.id] = subagent
            publishSubagents()
          }

          "thread.updated" -> {
            val payload = event.optJSONObject("payload") ?: return
            val title = payload.optString("title")
            if (title.isNotEmpty()) {
              openThread = openThread?.copy(title = title)
            }
          }
        }
      }
    }
  }

  /**
   * The chat, with the messages no run has started yet at the end. They stay
   * apart from the turn items: a queued message turns into a turn item under
   * its own id, so keeping them together would show it twice.
   */
  private fun publishLines() {
    lines = lineStore.values.toList() + pendingLines()
  }

  private fun pendingLines(): List<ChatLine> =
    PeerParsing.pendingLines(runStore.values.toList(), messageStore.values.toList())

  private fun publishSubagents() {
    subagents = subagentStore.values.sortedBy { it.startedAt }
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
    session?.close()
    session = null
  }
}
