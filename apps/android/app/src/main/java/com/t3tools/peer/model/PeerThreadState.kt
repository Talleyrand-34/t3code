package com.t3tools.peer.model

import org.json.JSONObject

/**
 * The live view of one thread: the lines it shows, the runs the server holds,
 * the messages waiting for a run, and the other agents inside it.
 *
 * The server sends a snapshot when the subscription opens and an event for
 * everything that happens after it. Both are read here, so the event that
 * creates a queued run cannot be lost: the server only updates a run once it
 * starts, so a message that waits in the queue is never seen otherwise.
 */
class PeerThreadState {
  private val lineStore = LinkedHashMap<String, ChatLine>()
  private val runStore = LinkedHashMap<String, QueuedRun>()
  private val messageStore = LinkedHashMap<String, PendingMessage>()
  private val subagentStore = LinkedHashMap<String, SubagentSummary>()
  private val answeredRequests = mutableSetOf<String>()
  private val turnMessageIds = mutableSetOf<String>()

  /** The chat, with the messages no run has started yet at the end. */
  val lines: List<ChatLine>
    get() = lineStore.values.toList() + pendingLines()

  /** The other agents working inside the thread. */
  val subagents: List<SubagentSummary>
    get() = subagentStore.values.sortedBy { it.startedAt }

  /** Whether the list of subagents is folded away. */
  var subagentsCollapsed: Boolean = false
    private set

  /** Folds the list of subagents away, or opens it again. */
  fun toggleSubagents() {
    subagentsCollapsed = !subagentsCollapsed
  }

  /** The subagents the thread shows: none of them while the group is folded away. */
  val visibleSubagents: List<SubagentSummary>
    get() = if (subagentsCollapsed) emptyList() else subagents

  /** The run that is working right now, if there is one. */
  val activeRun: QueuedRun?
    get() = runStore.values.filter { it.status in ACTIVE_STATUSES }.maxByOrNull { it.ordinal }

  /** Whether the thread has a run that is still working. */
  val isBusy: Boolean
    get() = activeRun != null

  /** The messages waiting for a run, in the order the queue holds them. */
  val queuedRuns: List<QueuedRun>
    get() =
      runStore.values
        .filter { it.status == "queued" }
        .sortedWith(compareBy({ it.queuePosition ?: it.ordinal }, { it.ordinal }))

  /** Whether the queue is held, so nothing starts it on its own. */
  val queueIsHeld: Boolean
    get() = runStore.values.any { it.status == "queued" && it.held }

  /** The question the agent is waiting on, when it has not been answered yet. */
  val pendingQuestion: AgentQuestion?
    get() {
      val asked = lineStore.values.lastOrNull { it.question != null }?.question ?: return null
      return if (asked.requestId in answeredRequests) null else asked
    }

  /** The approval the agent is waiting on, when it has not been answered yet. */
  val pendingApproval: AgentApproval?
    get() {
      val asked = lineStore.values.lastOrNull { it.approval != null }?.approval ?: return null
      return if (asked.requestId in answeredRequests) null else asked
    }

  /** The requests the app has answered, so one is not asked twice. */
  val answeredRequestIds: Set<String>
    get() = answeredRequests

  /** The words a queued message will run. */
  fun queuedText(run: QueuedRun): String = messageStore[run.userMessageId]?.text ?: ""

  /** Whether the server already has the message the phone sent. */  fun hasMessage(messageId: String): Boolean =
    messageStore.containsKey(messageId) || turnMessageIds.contains(messageId)

  /**
   * Whether the run that is working is a compaction.
   *
   * A compaction cannot be steered into, so a message sent while one runs has to
   * wait for it.
   */
  val activeRunIsCompaction: Boolean
    get() {
      val run = activeRun ?: return false
      val text = messageStore[run.userMessageId]?.text ?: return false
      return text.trim().equals("/compact", ignoreCase = true)
    }

  /** Remembers that the user answered a request, so it is not asked twice. */
  fun markAnswered(requestId: String) {
    answeredRequests += requestId
  }

  /** Forgets what was answered, for a thread the app opens again. */
  fun forgetAnswers() {
    answeredRequests.clear()
  }

  /** Reads the snapshot the server sent when the subscription opened. */
  fun readSnapshot(projection: JSONObject) {
    lineStore.clear()
    turnMessageIds.clear()
    PeerParsing.lines(projection).forEach { line ->
      lineStore[line.id] = line
      if (line.role == ChatRole.USER && line.messageId.isNotEmpty()) {
        turnMessageIds += line.messageId
      }
    }
    runStore.clear()
    PeerParsing.runs(projection).forEach { runStore[it.id] = it }
    messageStore.clear()
    PeerParsing.messages(projection).forEach { messageStore[it.id] = it }
    subagentStore.clear()
    PeerParsing.subagents(projection).forEach { subagentStore[it.id] = it }
  }

  /**
   * Reads one event the server sent while the subscription is open.
   *
   * Returns whether the event changed the thread.
   */
  fun readEvent(event: JSONObject): Boolean {
    val payload = event.optJSONObject("payload")
    return when (event.optString("type")) {
      "turn-item.updated" -> {
        val line = payload?.let { PeerParsing.line(it) } ?: return false
        lineStore[line.id] = line
        // A user message becomes a turn item under its own id, so the phone can
        // tell that the server took the message it sent.
        if (line.role == ChatRole.USER && line.messageId.isNotEmpty()) {
          turnMessageIds += line.messageId
        }
        true
      }
      // A run that waits in the queue is created, not updated: the server only
      // updates it once it starts, so the queued message is lost without this.
      "run.created", "run.updated" -> {
        val run = payload?.let { PeerParsing.run(it) } ?: return false
        runStore[run.id] = run
        true
      }
      "message.updated" -> {
        val message = payload?.let { PeerParsing.message(it) } ?: return false
        messageStore[message.id] = message
        true
      }
      "subagent.updated" -> {
        val subagent = payload?.let { PeerParsing.subagent(it) } ?: return false
        subagentStore[subagent.id] = subagent
        true
      }
      else -> false
    }
  }

  /** The messages that wait for a run, which the chat shows at its end. */
  private fun pendingLines(): List<ChatLine> =
    PeerParsing.pendingLines(runStore.values.toList(), messageStore.values.toList())

  private companion object {
    val ACTIVE_STATUSES = setOf("preparing", "starting", "running", "waiting")
  }
}
