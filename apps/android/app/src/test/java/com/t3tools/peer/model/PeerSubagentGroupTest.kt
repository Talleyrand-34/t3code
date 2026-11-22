package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSubagentGroupTest {
  @Test
  fun `the subagents show until the group is folded away`() {
    val state = PeerThreadState()
    state.readSnapshot(projectionWithSubagent("Subagent one"))

    assertEquals(1, state.visibleSubagents.size)
    assertFalse(state.subagentsCollapsed)
  }

  @Test
  fun `folding the group away hides the subagents`() {
    val state = PeerThreadState()
    state.readSnapshot(projectionWithSubagent("Subagent one"))

    state.toggleSubagents()

    assertTrue(state.subagentsCollapsed)
    assertTrue(state.visibleSubagents.isEmpty())
    // The subagents are still known, so opening the group brings them back.
    assertEquals(1, state.subagents.size)
  }

  @Test
  fun `opening the group again shows them`() {
    val state = PeerThreadState()
    state.readSnapshot(projectionWithSubagent("Subagent one"))

    state.toggleSubagents()
    state.toggleSubagents()

    assertFalse(state.subagentsCollapsed)
    assertEquals("Subagent one", state.visibleSubagents.single().title)
  }

  @Test
  fun `a subagent that arrives later joins the group`() {
    val state = PeerThreadState()
    state.readSnapshot(projectionWithSubagent("Subagent one"))
    state.toggleSubagents()

    state.readEvent(
      JSONObject()
        .put("type", "subagent.updated")
        .put(
          "payload",
          JSONObject()
            .put("id", "node-2")
            .put("title", "Subagent two")
            .put("status", "running"),
        ),
    )

    assertEquals(2, state.subagents.size)
    assertTrue(state.visibleSubagents.isEmpty())
  }

  private fun projectionWithSubagent(title: String): JSONObject =
    JSONObject().put(
      "subagents",
      JSONArray().put(
        JSONObject()
          .put("id", "node-1")
          .put("title", title)
          .put("status", "running"),
      ),
    )
}
