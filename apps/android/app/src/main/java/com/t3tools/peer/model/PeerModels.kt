package com.t3tools.peer.model

import org.json.JSONObject

data class ThreadSummary(
  val id: String,
  val title: String,
  val projectId: String,
  val projectTitle: String,
  val status: String,
  val updatedAt: String,
)

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatLine(
  val id: String,
  val role: ChatRole,
  val text: String,
  val streaming: Boolean,
)

object PeerParsing {
  fun thread(shell: JSONObject, projectTitles: Map<String, String>): ThreadSummary {
    val projectId = shell.optString("projectId")
    return ThreadSummary(
      id = shell.optString("id"),
      title = shell.optString("title").ifEmpty { "Untitled" },
      projectId = projectId,
      projectTitle = projectTitles[projectId] ?: projectId,
      status = shell.optString("status"),
      updatedAt = shell.optString("updatedAt"),
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
}
