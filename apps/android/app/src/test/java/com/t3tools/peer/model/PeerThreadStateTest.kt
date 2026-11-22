package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerThreadStateTest {
  @Test
  fun `a run that waits in the queue shows the message it will run`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "running", messageId = "message-1")),
        )
        .put("messages", JSONArray().put(message(id = "message-1", text = "First")))
    )

    // The server creates the queued run; it only updates it once it starts.
    state.readEvent(event("run.created", run(id = "run-2", status = "queued", messageId = "message-2")))
    state.readEvent(event("message.updated", message(id = "message-2", text = "Second")))

    val pending = state.lines.filter { it.pending }
    assertEquals(1, pending.size)
    assertEquals("Second", pending.single().text)
  }

  @Test
  fun `a run that started is not pending`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "running", messageId = "message-1")),
        )
        .put("messages", JSONArray().put(message(id = "message-1", text = "First")))
    )

    state.readEvent(event("run.created", run(id = "run-2", status = "queued", messageId = "message-2")))
    state.readEvent(event("message.updated", message(id = "message-2", text = "Second")))

    assertTrue(state.queuedRuns.size == 1)
    assertEquals("Second", state.queuedText(state.queuedRuns.single()))
  }

  @Test
  fun `the run that is working is the one that says so`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject().put(
        "runs",
        JSONArray()
          .put(run(id = "run-1", status = "completed", messageId = "message-1", ordinal = 1))
          .put(run(id = "run-2", status = "running", messageId = "message-2", ordinal = 2)),
      )
    )

    assertEquals("run-2", state.activeRun?.id)
    assertTrue(state.isBusy)
  }

  @Test
  fun `a queue that is held says so`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject().put(
        "runs",
        JSONArray().put(
          run(id = "run-1", status = "queued", messageId = "message-1").put("queueHeld", true),
        ),
      )
    )

    assertTrue(state.queueIsHeld)
  }

  @Test
  fun `the phone learns that the server took its message`() {
    val state = PeerThreadState()

    assertFalse(state.hasMessage("message-1"))
    state.readEvent(event("message.updated", message(id = "message-1", text = "Ship it")))
    assertTrue(state.hasMessage("message-1"))
  }

  @Test
  fun `a user message that became a turn item says the server took it`() {
    val state = PeerThreadState()

    state.readEvent(
      event(
        "turn-item.updated",
        JSONObject()
          .put("id", "item-1")
          .put("type", "user_message")
          .put("messageId", "message-1")
          .put("text", "Ship it"),
      )
    )

    assertTrue(state.hasMessage("message-1"))
  }

  @Test
  fun `a question that was answered is not asked again`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject().put(
        "visibleTurnItems",
        JSONArray().put(
          JSONObject()
            .put("id", "item-1")
            .put("type", "user_input_request")
            .put("requestId", "request-1")
            .put(
              "questions",
              JSONArray().put(
                JSONObject().put("id", "question-1").put("question", "Which one?"),
              ),
            ),
        ),
      )
    )

    assertEquals("request-1", state.pendingQuestion?.requestId)
    state.markAnswered("request-1")
    assertTrue(state.pendingQuestion == null)
  }

  @Test
  fun `a permission that was answered is not asked again`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject().put(
        "visibleTurnItems",
        JSONArray().put(
          JSONObject()
            .put("id", "item-1")
            .put("type", "approval_request")
            .put("requestId", "request-1")
            .put("prompt", "Run the command?"),
        ),
      )
    )

    assertEquals("request-1", state.pendingApproval?.requestId)
    state.markAnswered("request-1")
    assertTrue(state.pendingApproval == null)
  }

  @Test
  fun `a compaction cannot be steered into`() {
    val state = PeerThreadState()
    state.readSnapshot(
      JSONObject()
        .put(
          "runs",
          JSONArray().put(run(id = "run-1", status = "running", messageId = "message-1")),
        )
        .put("messages", JSONArray().put(message(id = "message-1", text = "/compact")))
    )

    assertTrue(state.activeRunIsCompaction)
  }

  private fun event(type: String, payload: JSONObject): JSONObject =
    JSONObject().put("type", type).put("payload", payload)

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

  private fun message(id: String, text: String): JSONObject =
    JSONObject().put("id", id).put("role", "user").put("text", text)
}
