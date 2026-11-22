package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerApprovalsTest {
  @Test
  fun `a permission reads the request it belongs to`() {
    val item =
      JSONObject()
        .put("id", "item-1")
        .put("type", "approval_request")
        .put("requestId", "request-1")
        .put("prompt", "Run the migration?")
        .put("reason", "It writes to the database.")
        .put("appName", "Bash")

    val approval = PeerApprovals.from(item)

    assertEquals("request-1", approval?.requestId)
    assertEquals("Run the migration?", approval?.prompt)
    assertEquals("It writes to the database.", approval?.reason)
    assertEquals("Bash", approval?.appName)
  }

  @Test
  fun `a permission with no answers still can be allowed or declined`() {
    val item =
      JSONObject().put("id", "item-1").put("type", "approval_request").put("requestId", "request-1")

    val approval = PeerApprovals.from(item)

    assertEquals(2, approval?.options?.size)
    assertEquals(PeerApprovals.ACCEPT, approval?.options?.first()?.decision)
    assertEquals(PeerApprovals.DECLINE, approval?.options?.last()?.decision)
  }

  @Test
  fun `the answers the provider offers are kept`() {
    val item =
      JSONObject()
        .put("id", "item-1")
        .put("type", "approval_request")
        .put("requestId", "request-1")
        .put(
          "options",
          JSONArray()
            .put(JSONObject().put("decision", "accept").put("label", "Allow once"))
            .put(
              JSONObject()
                .put("decision", "acceptForSession")
                .put("label", "Allow session")
                .put("warning", "It will not ask again"),
            )
            .put(JSONObject().put("decision", "decline").put("label", "Decline")),
        )

    val approval = PeerApprovals.from(item)

    assertEquals(3, approval?.options?.size)
    assertEquals("Allow once", approval?.options?.first()?.label)
    assertEquals("It will not ask again", approval?.options?.get(1)?.warning)
  }

  @Test
  fun `a request with no id is not a permission`() {
    assertNull(PeerApprovals.from(JSONObject().put("type", "approval_request")))
  }

  @Test
  fun `a decision wears a name the user understands`() {
    assertEquals("Allow once", PeerApprovals.decisionLabel(PeerApprovals.ACCEPT))
    assertEquals("Allow session", PeerApprovals.decisionLabel(PeerApprovals.ACCEPT_FOR_SESSION))
    assertEquals("Decline", PeerApprovals.decisionLabel(PeerApprovals.DECLINE))
  }

  @Test
  fun `a thread that waits for the user says so`() {
    val shell =
      JSONObject()
        .put("id", "thread-1")
        .put("title", "A thread")
        .put("projectId", "project-1")
        .put(
          "pendingRuntimeRequest",
          JSONObject().put("id", "request-1").put("kind", "dynamic_tool_call"),
        )

    val summary = PeerParsing.thread(shell, emptyMap())

    assertTrue(summary.needsAttention)
    assertEquals("dynamic_tool_call", summary.pendingRequestKind)
  }

  @Test
  fun `a thread that waits for nothing says nothing`() {
    val shell =
      JSONObject().put("id", "thread-1").put("title", "A thread").put("projectId", "project-1")

    assertTrue(!PeerParsing.thread(shell, emptyMap()).needsAttention)
  }
}
