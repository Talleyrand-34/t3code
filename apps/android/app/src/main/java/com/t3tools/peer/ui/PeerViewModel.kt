package com.t3tools.peer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.t3tools.peer.data.PeerStore
import com.t3tools.peer.model.AgentApproval
import com.t3tools.peer.model.AgentQuestion
import com.t3tools.peer.model.AgentQuestionOption
import com.t3tools.peer.model.BranchRef
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import com.t3tools.peer.model.ComposerDispatch
import com.t3tools.peer.model.FollowUp
import com.t3tools.peer.model.FollowUpBehavior
import com.t3tools.peer.model.PeerAppearance
import com.t3tools.peer.model.PeerFile
import com.t3tools.peer.model.PeerFiles
import com.t3tools.peer.model.PeerParsing
import com.t3tools.peer.model.PeerSearch
import com.t3tools.peer.model.PeerThreadState
import com.t3tools.peer.model.PhotoAttachment
import com.t3tools.peer.model.Photos
import com.t3tools.peer.model.ProjectChoice
import com.t3tools.peer.model.QueuedRun
import com.t3tools.peer.model.StoredAttachment
import com.t3tools.peer.model.SubagentSummary
import com.t3tools.peer.model.ThreadListRow
import com.t3tools.peer.model.ThreadMessageMatch
import com.t3tools.peer.model.ThreadState
import com.t3tools.peer.model.ThreadSummary
import com.t3tools.peer.model.orderThreads
import com.t3tools.peer.net.PeerEndpoint
import com.t3tools.peer.net.PeerEndpoints
import com.t3tools.peer.net.PeerException
import com.t3tools.peer.net.PeerSession
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class PeerScreen { CONNECT, THREADS, THREAD }

/** How long the app waits for the user to stop typing before it asks the server. */
private const val SEARCH_DEBOUNCE_MS = 250L

/** How long a notice stays on the screen before it goes away. */
private const val NOTICE_MS = 5_000L

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
    private set
  var appearance by mutableStateOf(store.appearance)
  var settingsVisible by mutableStateOf(false)
  /** The photo the user picked, waiting to go with the next message. */
  var pendingPhoto by mutableStateOf<PhotoAttachment?>(null)
    private set
  /** The file the user picked, waiting to go with the next message. */
  var pendingFile by mutableStateOf<PeerFile?>(null)
    private set
  /** What the user has written for the question the agent asked. */
  var questionDraft by mutableStateOf("")
    private set
  /** What the user is searching for. */
  var searchQuery by mutableStateOf("")
    private set
  /** The project the list is narrowed to, or null for every project. */
  var selectedProjectId by mutableStateOf<String?>(null)
    private set
  /** What happens to a message sent while the agent is working. */
  var followUpBehavior by mutableStateOf(store.followUpBehavior)
    private set
  /** Whether the sheet with the messages that wait is open. */
  var queueSheetVisible by mutableStateOf(false)
    private set
  /** Whether the sheet that starts a new thread is open. */
  var newThreadVisible by mutableStateOf(false)
    private set
  /** What the user writes for the new thread. */
  var newThreadText by mutableStateOf("")
    private set
  /** The project the new thread belongs to. */
  var newThreadProjectId by mutableStateOf<String?>(null)
    private set
  /** Whether the app posts a notification when the agent needs the user. */
  var notificationsEnabled by mutableStateOf(store.notificationsEnabled)
    private set
  /** A short notice about what just happened. */
  var notice by mutableStateOf<String?>(null)
    private set

  private var session: PeerSession? = null
  private var threadStream: Int? = null
  private var reconnectJob: Job? = null
  private var reconnectAttempts = 0
  private var connectionGeneration = 0
  private var appliedLink: String? = null
  private var threadState = PeerThreadState()
  private val pendingSends = mutableListOf<PendingSend>()
  private var projectTitles = emptyMap<String, String>()
  private var projectRoots = emptyMap<String, String>()
  /** The messages the server found for the search. */
  private var messageMatches by mutableStateOf(emptyList<ThreadMessageMatch>())
  private var searchJob: Job? = null
  private var noticeJob: Job? = null
  private var undoAction: (() -> Unit)? = null
  /** The thread the app should open once the list arrives. */
  private var wantedThreadId: String? = null

  /** A message the phone sent, waiting for the server to take it. */
  private class PendingSend(val messageId: String, val line: ChatLine)

  init {
    // A saved session lets the app come back on its own.
    if (store.serverInput.isNotBlank() && !store.bearerToken.isNullOrBlank()) {
      connect()
    }
  }

  /** The projects the list can be narrowed to, by name. */
  val projects: List<ProjectChoice>
    get() =
      threads
        .map { ProjectChoice(it.projectId, it.projectTitle) }
        .distinctBy { it.id }
        .sortedBy { it.title.lowercase() }

  /**
   * The threads the list shows. A search puts the threads whose own text matches
   * first, and the ones a message matched after them.
   */
  val threadRows: List<ThreadListRow>
    get() {
      val scoped = threads.filter { selectedProjectId == null || it.projectId == selectedProjectId }
      return PeerSearch.rows(scoped, PeerSearch.fold(searchQuery), messageMatches)
    }

  /** The threads that are waiting for the user to answer something. */
  val attentionCount: Int
    get() = threads.count { it.needsAttention }

  /** The run that is working right now, if there is one. */
  val activeRun: QueuedRun?
    get() = threadState.activeRun

  /** Whether the thread has a run that is still working. */
  val isBusy: Boolean
    get() = threadState.isBusy

  /** The messages that wait for a run, in the order the queue holds them. */
  val queuedRuns: List<QueuedRun>
    get() = threadState.queuedRuns

  /** Whether the queue is held, so nothing starts it on its own. */
  val queueIsHeld: Boolean
    get() = threadState.queueIsHeld

  /** The question the agent is waiting on, when it has not been answered yet. */
  val pendingQuestion: AgentQuestion?
    get() = threadState.pendingQuestion

  /** The approval the agent is waiting on, when it has not been answered yet. */
  val pendingApproval: AgentApproval?
    get() = threadState.pendingApproval

  /** The requests the app has answered, so a question is not asked twice. */
  val answeredRequestIds: Set<String>
    get() = threadState.answeredRequestIds

  /** The words a queued message will run. */
  fun queuedText(run: QueuedRun): String = threadState.queuedText(run)

  /**
   * The mode the send button uses.
   *
   * A permission that is waiting changes the mode: a plain send would decline the
   * permission and send the agent somewhere else, so the message steers instead.
   */
  val sendMode: ComposerDispatch
    get() = modeFor(alternate = false)

  /**
   * The mode a send uses.
   *
   * A permission that is waiting steers, the way the official app steers, so the
   * message reaches the waiting turn instead of declining what it asked for.
   */
  private fun modeFor(alternate: Boolean): ComposerDispatch {
    val behavior = if (pendingApproval != null) FollowUpBehavior.STEER else followUpBehavior
    return FollowUp.resolve(
      running = threadState.isBusy,
      canSteer = !threadState.activeRunIsCompaction,
      behavior = behavior,
      alternate = alternate,
    )
  }

  /** What the user has written for the question. */
  fun updateQuestionDraft(value: String) {
    questionDraft = value
  }

  /** Fills the answer box with one of the options the agent offered. */
  fun chooseOption(question: AgentQuestion, option: AgentQuestionOption) {
    questionDraft = option.value
  }

  /** Sends the answer the agent is waiting for. */
  fun answerQuestion() {
    val thread = openThread ?: return
    val question = pendingQuestion ?: return
    val answer = questionDraft.trim()
    if (answer.isEmpty()) return
    questionDraft = ""
    threadState.markAnswered(question.requestId)
    viewModelScope.launch {
      try {
        session?.respondToRequest(thread.id, question.requestId, mapOf(question.id to answer))
      } catch (failure: Exception) {
        error = failure.message ?: "Could not answer the agent."
      }
    }
    publishLines()
    clearNotification()
  }

  /** Answers a permission the agent asked for. */
  fun respondToApproval(decision: String) {
    val thread = openThread ?: return
    val approval = pendingApproval ?: return
    threadState.markAnswered(approval.requestId)
    viewModelScope.launch {
      try {
        session?.respondToApproval(thread.id, approval.requestId, decision)
      } catch (failure: Exception) {
        error = failure.message ?: "Could not answer the agent."
      }
    }
    publishLines()
    clearNotification()
  }

  /** Narrows the list to one project, or back to every project. */
  fun selectProject(projectId: String?) {
    selectedProjectId = projectId
  }

  /**
   * Sets what the user is searching for. The thread's own text is matched here,
   * over the threads the app already lists; the server is asked for the messages
   * that match, because the server owns them.
   */
  fun updateSearchQuery(value: String) {
    searchQuery = value
    searchJob?.cancel()
    if (!PeerSearch.isSearchable(value)) {
      messageMatches = emptyList()
      return
    }
    val query = value.trim()
    searchJob = viewModelScope.launch {
      delay(SEARCH_DEBOUNCE_MS)
      val session = session ?: return@launch
      try {
        val matches = session.searchMessages(query)
        if (searchQuery.trim() == query) messageMatches = matches
      } catch (_: Exception) {
        // A search that fails leaves the titles, which the app already knows.
      }
    }
  }

  fun dismissError() {
    error = null
  }

  fun chooseAppearance(value: PeerAppearance) {
    appearance = value
    store.appearance = value
  }

  /** Sets what the user is writing, and keeps the words for the thread. */
  fun updateComposer(value: String) {
    composer = value
    openThread?.let { store.setDraftFor(it.id, value) }
  }

  /** Sets what happens to a message sent while the agent is working. */
  fun chooseFollowUpBehavior(value: FollowUpBehavior) {
    followUpBehavior = value
    store.followUpBehavior = value
  }

  /** Turns the notifications on or off. */
  fun chooseNotifications(enabled: Boolean) {
    notificationsEnabled = enabled
    store.notificationsEnabled = enabled
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
    clearThread()
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
    threadState.forgetAnswers()
    questionDraft = ""
    openThread = thread
    clearThread()
    composer = store.draftFor(thread.id)
    screen = PeerScreen.THREAD
    closeThreadStream()
    threadStream = session?.subscribeThread(thread.id, ::onThreadItem)
    viewModelScope.launch {
      try {
        session?.visitThread(thread.id)
      } catch (_: Exception) {
        // Reading a thread does not need the server to accept the visit.
      }
    }
  }

  /** Opens a thread the app knows the id of, once the list has it. */
  fun openThreadById(threadId: String) {
    wantedThreadId = threadId
    val thread = threads.firstOrNull { it.id == threadId }
    if (thread != null) {
      wantedThreadId = null
      openThread(thread)
    }
  }

  /** Leaves the thread and shows the list again, the way the back button does. */
  fun backToThreads() {
    openThread = null
    clearThread()
    screen = PeerScreen.THREADS
    closeThreadStream()
  }

  /** Forgets the thread the app was showing. */
  private fun clearThread() {
    threadState = PeerThreadState()
    pendingSends.clear()
    lines = emptyList()
    subagents = emptyList()
    composer = ""
    pendingPhoto = null
    pendingFile = null
    questionDraft = ""
  }

  /** The old thread must stop streaming, or its items land in the new one. */
  private fun closeThreadStream() {
    threadStream?.let { session?.unsubscribe(it) }
    threadStream = null
  }

  /**
   * Sends what the user wrote.
   *
   * @param alternate whether the user asked for the other mode for this message.
   */
  fun send(alternate: Boolean = false) {
    val text = composer.trim()
    val thread = openThread ?: return
    val photo = pendingPhoto
    val file = pendingFile
    if (text.isEmpty() && photo == null && file == null) return
    val mode = modeFor(alternate)
    val messageId = UUID.randomUUID().toString()
    val line =
      ChatLine(
        id = "local:$messageId",
        role = ChatRole.USER,
        text = text,
        streaming = false,
        sending = true,
        attachments =
          listOfNotNull(
            photo?.let { StoredAttachment(id = "", name = it.name, mimeType = it.mimeType, sizeBytes = it.sizeBytes) },
            file?.let { StoredAttachment(id = "", name = it.name, mimeType = it.mimeType, sizeBytes = it.sizeBytes) },
          ),
      )
    pendingSends += PendingSend(messageId, line)
    updateComposer("")
    pendingPhoto = null
    pendingFile = null
    publishLines()
    viewModelScope.launch {
      try {
        val session = session ?: throw PeerException("The connection to the server is gone.")
        val stored = storeAttachments(thread.id, messageId, photo, file)
        session.sendMessage(
          threadId = thread.id,
          text = text,
          attachments = stored,
          messageId = messageId,
          dispatchMode = FollowUp.dispatchMode(mode),
          deliveryIntent = FollowUp.deliveryIntent(mode),
        )
      } catch (failure: Exception) {
        pendingSends.removeAll { it.messageId == messageId }
        publishLines()
        updateComposer(text)
        error = failure.message ?: "Could not send the message."
      }
    }
  }

  /**
   * Stores what the user attached, so the message carries the attachment the
   * server gave back rather than the photo or the file itself.
   */
  private suspend fun storeAttachments(
    threadId: String,
    messageId: String,
    photo: PhotoAttachment?,
    file: PeerFile?,
  ): List<StoredAttachment> {
    val session = session ?: return emptyList()
    val stored = mutableListOf<StoredAttachment>()
    if (photo != null) {
      session.persistAttachment(threadId, messageId, photo)?.let { stored += it }
    }
    if (file != null) {
      session.uploadAttachment(threadId, messageId, file)?.let { stored += it }
    }
    return stored
  }

  /** Stops the run that is working, and holds the queue so nothing restarts it. */
  fun stopRun() {
    val thread = openThread ?: return
    val run = threadState.activeRun ?: return
    viewModelScope.launch {
      try {
        session?.interruptRun(thread.id, run.id)
        showNotice("Stopping. The messages that wait stay waiting.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not stop the agent."
      }
    }
  }

  /** Releases the queue, so the messages that wait start again. */
  fun resumeQueue() {
    val thread = openThread ?: return
    viewModelScope.launch {
      try {
        session?.resumeQueue(thread.id)
        showNotice("The queue is running again.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not resume the queue."
      }
    }
  }

  /** Opens the sheet with the messages that wait. */
  fun showQueue() {
    queueSheetVisible = true
  }

  fun hideQueue() {
    queueSheetVisible = false
  }

  /** Takes a queued message out of the queue. */
  fun cancelQueuedRun(run: QueuedRun) {
    val thread = openThread ?: return
    viewModelScope.launch {
      try {
        session?.cancelQueuedRun(thread.id, run.id)
        showNotice("The message was taken out of the queue.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not remove the message."
      }
    }
  }

  /** Changes what a queued message says. */
  fun editQueuedRun(run: QueuedRun, text: String) {
    val thread = openThread ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      try {
        session?.editQueuedRun(thread.id, run.id, trimmed)
        showNotice("The queued message was changed.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not change the message."
      }
    }
  }

  /**
   * Sends a queued message now: on its own when nothing is working, and into the
   * turn that is working otherwise.
   */
  fun sendQueuedNow(run: QueuedRun) {
    val thread = openThread ?: return
    viewModelScope.launch {
      try {
        val session = session ?: return@launch
        val active = threadState.activeRun
        if (active == null) {
          session.startQueuedRun(thread.id, run.id)
        } else {
          session.promoteQueuedRun(thread.id, run.id, active.id)
        }
        showNotice("The message is on its way.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not send the message."
      }
    }
  }

  /** Opens the sheet that starts a new thread. */
  fun showNewThread() {
    newThreadVisible = true
    newThreadText = ""
    newThreadProjectId = selectedProjectId ?: threads.firstOrNull()?.projectId
  }

  fun hideNewThread() {
    newThreadVisible = false
  }

  /** Sets what the new thread says. */
  fun updateNewThreadText(value: String) {
    newThreadText = value
  }

  /** Sets the project the new thread belongs to. */
  fun selectNewThreadProject(projectId: String) {
    newThreadProjectId = projectId
  }

  /** Starts a new thread with what the user wrote. */
  fun startNewThread() {
    val text = newThreadText.trim()
    val projectId = newThreadProjectId ?: return
    if (text.isEmpty()) return
    val template = threads.firstOrNull { it.projectId == projectId }
    if (template?.modelSelection == null) {
      error = "This project has no thread to take the model from yet. Open one first."
      return
    }
    viewModelScope.launch {
      try {
        val session = session ?: throw PeerException("The connection to the server is gone.")
        val threadId =
          session.launchThread(
            projectId = projectId,
            title = text.take(60),
            text = text,
            modelSelection = template?.modelSelection,
            runtimeMode = template?.runtimeMode.orEmpty(),
            interactionMode = template?.interactionMode.orEmpty(),
            branch = template?.branch?.ifEmpty { null },
          )
        newThreadVisible = false
        if (threadId != null) {
          showNotice("The thread was started.")
          openThreadById(threadId)
        }
      } catch (failure: Exception) {
        error = failure.message ?: "Could not start the thread."
      }
    }
  }

  /** Puts a thread away, and takes it back out again. */
  fun setThreadArchived(thread: ThreadSummary, archived: Boolean) {
    viewModelScope.launch {
      try {
        session?.setThreadArchived(thread.id, archived)
        showNotice(if (archived) "The thread was put away." else "The thread is back.")
        if (archived) {
          threads = threads.filterNot { it.id == thread.id }
        }
      } catch (failure: Exception) {
        error = failure.message ?: "Could not change the thread."
      }
    }
  }

  /** Gives a thread a new name. */
  fun renameThread(thread: ThreadSummary, title: String) {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      try {
        session?.renameThread(thread.id, trimmed)
        threads = threads.map { if (it.id == thread.id) it.copy(title = trimmed) else it }
        if (openThread?.id == thread.id) openThread = openThread?.copy(title = trimmed)
        showNotice("The thread was renamed.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not rename the thread."
      }
    }
  }

  /** Removes a thread, and says so in a way the user can take back. */
  fun deleteThread(thread: ThreadSummary) {
    val removed = thread
    threads = threads.filterNot { it.id == thread.id }
    viewModelScope.launch {
      try {
        session?.deleteThread(thread.id)
        showNotice("The thread was deleted.", undo = {
          viewModelScope.launch {
            runCatching { session?.setThreadArchived(removed.id, false) }
          }
        })
      } catch (failure: Exception) {
        error = failure.message ?: "Could not delete the thread."
      }
    }
  }

  /** Wakes a snoozed thread, or puts it back to sleep. */
  fun setThreadSnoozed(thread: ThreadSummary, snoozed: Boolean) {
    viewModelScope.launch {
      try {
        session?.setThreadSnoozed(thread.id, snoozed)
        showNotice(if (snoozed) "The thread is asleep." else "The thread is awake.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not change the thread."
      }
    }
  }

  /** Marks a thread as not read, so the list points at it again. */
  fun markThreadUnread(thread: ThreadSummary) {
    viewModelScope.launch {
      try {
        session?.markThreadUnread(thread.id)
        showNotice("The thread was marked unread.")
      } catch (failure: Exception) {
        error = failure.message ?: "Could not mark the thread."
      }
    }
  }

  /** Takes back what the notice offered. */
  fun undoNotice() {
    val action = undoAction
    undoAction = null
    notice = null
    noticeJob?.cancel()
    action?.invoke()
  }

  /** Says what just happened, and offers to take it back. */
  private fun showNotice(message: String, undo: (() -> Unit)? = null) {
    undoAction = undo
    notice = message
    noticeJob?.cancel()
    noticeJob = viewModelScope.launch {
      delay(NOTICE_MS)
      notice = null
      undoAction = null
    }
  }

  /** Picks a photo to send with the next message. */
  fun attachPhoto(uri: android.net.Uri) {
    val context = getApplication<Application>()
    val photo = Photos.prepare(uri, context.contentResolver)
    if (photo == null) {
      error = "That photo could not be read."
      return
    }
    pendingPhoto = photo
  }

  /** Picks a file to send with the next message. */
  fun attachFile(uri: android.net.Uri) {
    val context = getApplication<Application>()
    val file = PeerFiles.prepare(uri, context.contentResolver)
    if (file == null) {
      error = "That file could not be read, or it is too big."
      return
    }
    pendingFile = file
  }

  /** Drops the photo the user picked. */
  fun clearPendingPhoto() {
    pendingPhoto = null
  }

  /** Drops the file the user picked. */
  fun clearPendingFile() {
    pendingFile = null
  }

  /**
   * The address a stored photo is served from. The link is short-lived, so it is minted
   * when the photo is about to be shown.
   */
  suspend fun attachmentUrl(attachment: StoredAttachment): String? {
    val session = session ?: return null
    return runCatching { session.attachmentUrl(attachment) }.getOrNull()
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
        threads = orderThreads(listed(collected))
        wantedThreadId?.let { openThreadById(it) }
      }

      "thread.updated" -> {
        val shell = item.optJSONObject("thread") ?: return
        val summary = PeerParsing.thread(shell, projectTitles)
        if (!summary.isListed) {
          // A subagent belongs inside its parent, not in the list.
          threads = threads.filterNot { it.id == summary.id }
          return
        }
        threads = orderThreads(threads.filterNot { it.id == summary.id } + summary)
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
        threadState.readSnapshot(projection)
        publishLines()
        publishSubagents()
      }

      "event" -> {
        val event = item.optJSONObject("event") ?: return
        if (event.optString("type") == "thread.updated") {
          val title = event.optJSONObject("payload")?.optString("title").orEmpty()
          if (title.isNotEmpty()) {
            openThread = openThread?.copy(title = title)
          }
          return
        }
        if (!threadState.readEvent(event)) return
        publishLines()
        publishSubagents()
        notifyWhenNeeded()
      }
    }
  }

  /**
   * The chat, with the messages the phone sent but the server has not taken yet.
   * They stay apart from the turn items: a queued message turns into a turn item
   * under its own id, so keeping them together would show it twice.
   */
  private fun publishLines() {
    val local =
      pendingSends
        .filter { !threadState.hasMessage(it.messageId) }
        .map { it.line }
    lines = threadState.lines + local
  }

  private fun publishSubagents() {
    subagents = threadState.subagents
  }

  /** Takes the notification away, for work the user has seen. */
  private fun clearNotification() {
    PeerNotifications.clear(getApplication<Application>())
  }

  /** Tells the user about the agent that needs them. */
  private fun notifyWhenNeeded() {
    if (!notificationsEnabled) return
    val thread = openThread ?: return
    val approval = threadState.pendingApproval
    val question = threadState.pendingQuestion
    val context = getApplication<Application>()
    when {
      approval != null ->
        PeerNotifications.postNeedsAttention(context, thread.id, thread.title, "Approve")
      question != null ->
        PeerNotifications.postNeedsAttention(context, thread.id, thread.title, "Answer")
      else -> return
    }
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
    searchJob?.cancel()
    noticeJob?.cancel()
    // The app is gone, so the socket has nothing to talk to.
    session?.close()
    session = null
  }
}
