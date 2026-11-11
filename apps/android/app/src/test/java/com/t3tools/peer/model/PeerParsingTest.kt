package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerParsingTest {
  @Test
  fun `a queued run shows the message it will run`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "queued", messageId = "message-1")),
        )
        .put(
          "messages",
          JSONArray().put(message(id = "message-1", role = "user", text = "Ship it")),
        )

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertEquals(1, pending.size)
    assertEquals("Ship it", pending.single().text)
    assertTrue(pending.single().pending)
    assertEquals(ChatRole.USER, pending.single().role)
  }

  @Test
  fun `a run that started is not pending`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "running", messageId = "message-1")),
        )
        .put("messages", JSONArray().put(message(id = "message-1", role = "user", text = "Ship it")))

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `the server's own messages are never pending`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray()
            .put(run(id = "run-1", status = "queued", messageId = "message-1"))
            .put(run(id = "run-2", status = "queued", messageId = "message-2")),
        )
        .put(
          "messages",
          JSONArray()
            .put(
              message(id = "message-1", role = "user", text = "A notification")
                .put("notification", JSONObject()),
            )
            .put(
              message(id = "message-2", role = "user", text = "A completion")
                .put("delegatedCompletion", JSONObject()),
            ),
        )

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `an assistant message is never pending`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "queued", messageId = "message-1")),
        )
        .put(
          "messages",
          JSONArray().put(message(id = "message-1", role = "assistant", text = "Hello")),
        )

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertTrue(pending.isEmpty())
  }

  @Test
  fun `the queue holds the order the server gave`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray()
            .put(run(id = "run-1", status = "queued", messageId = "message-1", ordinal = 1, queuePosition = 2))
            .put(run(id = "run-2", status = "queued", messageId = "message-2", ordinal = 2, queuePosition = 1))
            .put(run(id = "run-3", status = "queued", messageId = "message-3", ordinal = 3)),
        )
        .put(
          "messages",
          JSONArray()
            .put(message(id = "message-1", role = "user", text = "Second in the queue"))
            .put(message(id = "message-2", role = "user", text = "First in the queue"))
            .put(message(id = "message-3", role = "user", text = "Without a place")),
        )

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertEquals(
      listOf("First in the queue", "Second in the queue", "Without a place"),
      pending.map { it.text },
    )
  }

  @Test
  fun `a guest's words stand in for the message`() {
    val projection =
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "queued", messageId = "message-1")),
        )
        .put(
          "messages",
          JSONArray().put(
            message(id = "message-1", role = "user", text = "Attributed to someone")
              .put("context", JSONObject().put("guest", JSONObject().put("text", "From the guest"))),
          ),
        )

    val pending = PeerParsing.pendingLines(PeerParsing.runs(projection), PeerParsing.messages(projection))

    assertEquals("From the guest", pending.single().text)
  }

  @Test
  fun `a subagent is not a thread of the list`() {
    val subagent = thread(id = "thread-1", lineage = lineage(relationship = "subagent"))

    assertFalse(PeerParsing.thread(subagent, emptyMap()).isListed)
  }

  @Test
  fun `a side chat is not a thread of the list either`() {
    val sideChat = thread(id = "thread-1", lineage = lineage(relationship = "fork", sideChat = true))

    assertFalse(PeerParsing.thread(sideChat, emptyMap()).isListed)
  }

  @Test
  fun `an ordinary thread is a thread of the list`() {
    val ordinary = thread(id = "thread-1", lineage = lineage(relationship = "fork"))

    assertTrue(PeerParsing.thread(ordinary, emptyMap()).isListed)
  }

  @Test
  fun `a thread without lineage is a thread of the list`() {
    val ordinary = thread(id = "thread-1").put("lineage", JSONObject().put("parentThreadId", JSONObject.NULL))

    assertTrue(PeerParsing.thread(ordinary, emptyMap()).isListed)
  }

  @Test
  fun `the subagents of a thread come with their state`() {
    val projection =
      JSONObject().put(
        "subagents",
        JSONArray().put(
          JSONObject()
            .put("id", "node-1")
            .put("parentNodeId", "node-0")
            .put("childThreadId", "thread-2")
            .put("title", "Map the push path")
            .put("status", "completed")
            .put("result", "All done")
            .put("startedAt", "2026-11-11T09:00:00.000Z")
            .put("completedAt", "2026-11-11T09:05:00.000Z"),
        ),
      )

    val subagents = PeerParsing.subagents(projection)

    assertEquals(1, subagents.size)
    assertEquals("Map the push path", subagents.single().title)
    assertEquals("completed", subagents.single().status)
    assertEquals("thread-2", subagents.single().childThreadId)
    assertEquals("All done", subagents.single().result)
  }

  @Test
  fun `a subagent without a title falls back to its prompt`() {
    val projection =
      JSONObject().put(
        "subagents",
        JSONArray().put(
          JSONObject().put("id", "node-1").put("prompt", "Look at the logs").put("status", "running"),
        ),
      )

    assertEquals("Look at the logs", PeerParsing.subagents(projection).single().title)
  }

  @Test
  fun `a thread keeps the branch and the worktree it runs in`() {
    val shell =
      thread(id = "thread-1", lineage = lineage(relationship = "fork"))
        .put("branch", "feat/thing")
        .put("worktreePath", "/tmp/worktrees/thing")

    val summary = PeerParsing.thread(shell, emptyMap())

    assertEquals("feat/thing", summary.branch)
    assertEquals("/tmp/worktrees/thing", summary.worktreePath)
  }

  @Test
  fun `a thread without a branch says nothing about one`() {
    val shell = thread(id = "thread-1", lineage = lineage(relationship = "fork")).put("branch", JSONObject.NULL)

    assertEquals("", PeerParsing.thread(shell, emptyMap()).branch)
  }

  @Test
  fun `a run with no queue position keeps its ordinal`() {
    val run = run(id = "run-1", status = "queued", messageId = "message-1", ordinal = 4)

    assertNull(PeerParsing.run(run)?.queuePosition)
    assertEquals(4, PeerParsing.run(run)?.ordinal)
  }

  private fun run(
    id: String,
    status: String,
    messageId: String,
    ordinal: Int = 1,
    queuePosition: Int? = null,
  ): JSONObject {
    val run =
      JSONObject()
        .put("id", id)
        .put("status", status)
        .put("userMessageId", messageId)
        .put("ordinal", ordinal)
    if (queuePosition != null) run.put("queuePosition", queuePosition)
    return run
  }

  private fun message(id: String, role: String, text: String): JSONObject =
    JSONObject().put("id", id).put("role", role).put("text", text)

  private fun thread(id: String, lineage: JSONObject? = null): JSONObject {
    val thread = JSONObject().put("id", id).put("title", "A thread").put("projectId", "project-1")
    if (lineage != null) thread.put("lineage", lineage)
    return thread
  }

  private fun lineage(relationship: String, sideChat: Boolean = false): JSONObject =
    JSONObject()
      .put("parentThreadId", "thread-0")
      .put("relationshipToParent", relationship)
      .put("rootThreadId", "thread-0")
      .put("sideChat", sideChat)
}
