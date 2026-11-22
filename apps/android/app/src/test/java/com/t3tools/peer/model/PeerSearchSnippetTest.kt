package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSearchSnippetTest {
  @Test
  fun `a snippet shows words, not the markdown the agent wrote`() {
    val result =
      JSONObject()
        .put(
          "matches",
          JSONArray().put(
            JSONObject()
              .put("threadId", "thread-1")
              .put("projectId", "project-1")
              .put("snippet", "## Done\nI added `clamp` next to **sum**."),
          ),
        )

    val matches = PeerSearch.messageMatches(result)

    assertEquals("Done I added clamp next to sum.", matches.single().snippet)
  }

  @Test
  fun `a snippet loses the bullets and the table pipes`() {
    val result =
      JSONObject()
        .put(
          "matches",
          JSONArray().put(
            JSONObject()
              .put("threadId", "thread-1")
              .put("projectId", "project-1")
              .put("snippet", "| Step | Tool |\n| --- | --- |\n| Read | read |"),
          ),
        )

    val matches = PeerSearch.messageMatches(result)

    assertEquals("Step Tool --- --- Read read", matches.single().snippet)
  }

  @Test
  fun `a long snippet is cut`() {
    val result =
      JSONObject()
        .put(
          "matches",
          JSONArray().put(
            JSONObject()
              .put("threadId", "thread-1")
              .put("projectId", "project-1")
              .put("snippet", "word ".repeat(80)),
          ),
        )

    val matches = PeerSearch.messageMatches(result)

    assertTrue(matches.single().snippet.length <= 141)
    assertTrue(matches.single().snippet.endsWith("…"))
  }

  @Test
  fun `a thread that arrives twice is one row`() {
    val threads = listOf(thread(id = "thread-1", title = "One"), thread(id = "thread-1", title = "One"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold(""), emptyList())

    assertEquals(1, rows.size)
  }

  @Test
  fun `a query longer than the server takes is cut before it is sent`() {
    assertEquals(200, PeerSearch.forServer("word ".repeat(100)).length)
    assertEquals("two words", PeerSearch.forServer("  two words  "))
  }

  private fun thread(id: String, title: String): ThreadSummary =
    ThreadSummary(
      id = id,
      title = title,
      projectId = "project-1",
      projectTitle = "A project",
      branch = "",
      status = "idle",
      updatedAt = "2026-11-22T09:00:00.000Z",
    )
}
