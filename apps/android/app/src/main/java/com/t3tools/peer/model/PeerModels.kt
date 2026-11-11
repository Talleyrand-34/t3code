package com.t3tools.peer.model

import org.json.JSONObject

data class ThreadSummary(
  val id: String,
  val title: String,
  val projectId: String,
  val projectTitle: String,
  val branch: String,
  val status: String,
  val updatedAt: String,
  /** Subagents and side chats live inside their parent, never in the list. */
  val isListed: Boolean = true,
  val parentThreadId: String = "",
  /** Where the thread runs, when it has a worktree of its own. */
  val worktreePath: String = "",
  /** When the last run of this thread finished, so a finished run can be pointed out. */
  val latestRunCompletedAt: String = "",
)

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatLine(
  val id: String,
  val role: ChatRole,
  val text: String,
  val streaming: Boolean,
  /** The server has the message, but no run has started it yet. */
  val pending: Boolean = false,
)

/** A run the server took but has not started. */
data class QueuedRun(
  val id: String,
  val userMessageId: String,
  val status: String,
  val ordinal: Int,
  val queuePosition: Int?,
)

/** A message the user sent that the server keeps for a later run. */
data class PendingMessage(
  val id: String,
  val text: String,
  val createdAt: String,
)

/** Another agent working inside this thread. */
data class SubagentSummary(
  val id: String,
  val parentNodeId: String,
  val childThreadId: String,
  val title: String,
  val status: String,
  val progress: String,
  val result: String,
  val startedAt: String,
  val completedAt: String,
)

/** A branch or tag the host knows about in a checkout. */
data class BranchRef(
  val name: String,
  val isRemote: Boolean,
  val current: Boolean,
  val worktreePath: String,
)

object PeerParsing {
  fun thread(shell: JSONObject, projectTitles: Map<String, String>): ThreadSummary {
    val projectId = shell.optString("projectId")
    val lineage = shell.optJSONObject("lineage")
    val relationshipToParent = lineage?.optString("relationshipToParent").orEmpty()
    val sideChat = lineage?.optBoolean("sideChat") ?: false
    return ThreadSummary(
      id = shell.optString("id"),
      title = shell.optString("title").ifEmpty { "Untitled" },
      projectId = projectId,
      projectTitle = projectTitles[projectId] ?: projectId,
      branch = if (shell.isNull("branch")) "" else shell.optString("branch"),
      status = shell.optString("status"),
      updatedAt = shell.optString("updatedAt"),
      isListed = relationshipToParent != "subagent" && !sideChat,
      parentThreadId = lineage?.optString("parentThreadId").orEmpty(),
      worktreePath = if (shell.isNull("worktreePath")) "" else shell.optString("worktreePath"),
      latestRunCompletedAt =
        if (shell.isNull("latestRunCompletedAt")) "" else shell.optString("latestRunCompletedAt"),
    )
  }

  /** Reads the messages of a thread out of a thread projection. */
  fun lines(projection: JSONObject): List<ChatLine> {
    val items = projection.optJSONArray("visibleTurnItems")
      ?: projection.optJSONArray("turnItems")
      ?: return emptyList()
    val lines = mutableListOf<ChatLine>()
    for (index in 0 until items.length()) {
      val entry = items.optJSONObject(index) ?: continue
      val item = entry.optJSONObject("item") ?: entry
      val line = line(item) ?: continue
      lines += line
    }
    return lines
  }

  /** Turns one turn item into something worth showing in a chat. */
  fun line(item: JSONObject): ChatLine? {
    val id = item.optString("id")
    if (id.isEmpty()) return null
    val type = item.optString("type")
    val streaming = item.optBoolean("streaming")
    return when (type) {
      "user_message" ->
        ChatLine(id, ChatRole.USER, item.optString("text"), streaming)

      "assistant_message" ->
        ChatLine(id, ChatRole.ASSISTANT, item.optString("text"), streaming)

      "reasoning" ->
        ChatLine(id, ChatRole.SYSTEM, item.optString("text"), streaming)

      "notification" ->
        ChatLine(id, ChatRole.SYSTEM, item.optString("text"), streaming)

      "proposed_plan" ->
        ChatLine(id, ChatRole.ASSISTANT, item.optString("markdown"), streaming)

      "user_input_request" ->
        ChatLine(id, ChatRole.SYSTEM, "The agent asked a question.", false)

      "approval_request" ->
        ChatLine(id, ChatRole.SYSTEM, "The agent asked for approval.", false)

      "command_execution" ->
        ChatLine(id, ChatRole.SYSTEM, "Ran a command.", false)

      "file_change" ->
        ChatLine(id, ChatRole.SYSTEM, "Changed ${item.optString("fileName")}", false)

      "todo_list" ->
        ChatLine(id, ChatRole.SYSTEM, "Updated the plan.", false)

      "checkpoint" ->
        ChatLine(id, ChatRole.SYSTEM, "Saved a checkpoint.", false)

      "web_search" ->
        ChatLine(id, ChatRole.SYSTEM, "Searched the web.", false)

      "file_search" ->
        ChatLine(id, ChatRole.SYSTEM, "Searched files.", false)

      else -> null
    }
  }

  /** The runs of a thread projection, newest last. */
  fun runs(projection: JSONObject): List<QueuedRun> {
    val runs = projection.optJSONArray("runs") ?: return emptyList()
    val parsed = mutableListOf<QueuedRun>()
    for (index in 0 until runs.length()) {
      val run = runs.optJSONObject(index) ?: continue
      parsed += run(run) ?: continue
    }
    return parsed
  }

  fun run(run: JSONObject): QueuedRun? {
    val id = run.optString("id")
    if (id.isEmpty()) return null
    return QueuedRun(
      id = id,
      userMessageId = run.optString("userMessageId"),
      status = run.optString("status"),
      ordinal = run.optInt("ordinal", 0),
      queuePosition = if (run.isNull("queuePosition")) null else run.optInt("queuePosition"),
    )
  }

  /** The user messages of a thread projection that no run has taken yet. */
  fun messages(projection: JSONObject): List<PendingMessage> {
    val messages = projection.optJSONArray("messages") ?: return emptyList()
    val parsed = mutableListOf<PendingMessage>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      parsed += message(message) ?: continue
    }
    return parsed
  }

  fun message(message: JSONObject): PendingMessage? {
    val id = message.optString("id")
    if (id.isEmpty()) return null
    if (message.optString("role") != "user") return null
    // The server's own messages are not the user's, so they are never pending.
    if (message.has("notification") || message.has("delegatedCompletion")) return null
    val text = guestText(message) ?: message.optString("text")
    if (text.isEmpty()) return null
    return PendingMessage(id = id, text = text, createdAt = message.optString("createdAt"))
  }

  /** The words a guest wrote, when the message came in through a shared link. */
  private fun guestText(message: JSONObject): String? {
    val guest = message.optJSONObject("context")?.optJSONObject("guest") ?: return null
    return guest.optString("text").ifEmpty { null }
  }

  /**
   * The messages no run has started yet, in the order the queue holds them.
   *
   * They stay apart from the turn items: a queued message turns into a turn
   * item under its own id, so keeping them together would show it twice.
   */
  fun pendingLines(runs: List<QueuedRun>, messages: List<PendingMessage>): List<ChatLine> {
    val byId = messages.associateBy { it.id }
    return runs
      .filter { it.status == "queued" }
      .sortedWith(compareBy({ it.queuePosition ?: it.ordinal }, { it.ordinal }))
      .mapNotNull { run ->
        val message = byId[run.userMessageId] ?: return@mapNotNull null
        ChatLine(
          id = "pending:${run.id}",
          role = ChatRole.USER,
          text = message.text,
          streaming = false,
          pending = true,
        )
      }
  }

  /** The other agents working inside a thread. */
  fun subagents(projection: JSONObject): List<SubagentSummary> {    val subagents = projection.optJSONArray("subagents") ?: return emptyList()
    val parsed = mutableListOf<SubagentSummary>()
    for (index in 0 until subagents.length()) {
      val subagent = subagents.optJSONObject(index) ?: continue
      parsed += subagent(subagent) ?: continue
    }
    return parsed
  }

  fun subagent(subagent: JSONObject): SubagentSummary? {
    val id = subagent.optString("id")
    if (id.isEmpty()) return null
    val title = subagent.optString("title")
    return SubagentSummary(
      id = id,
      parentNodeId = subagent.optString("parentNodeId"),
      childThreadId = subagent.optString("childThreadId"),
      title = title.ifEmpty { subagent.optString("prompt").take(60) },
      status = subagent.optString("status"),
      progress = subagent.optString("progress"),
      result = subagent.optString("result"),
      startedAt = subagent.optString("startedAt"),
      completedAt = subagent.optString("completedAt"),
    )
  }
}
