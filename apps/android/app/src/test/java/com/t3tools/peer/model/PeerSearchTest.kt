package com.t3tools.peer.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSearchTest {
  @Test
  fun `a thread whose own text matches comes before one a message matched`() {
    val threads = listOf(thread(id = "thread-1", title = "Push path"), thread(id = "thread-2", title = "Other"))
    val matches = listOf(match(threadId = "thread-2"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), matches)

    assertEquals(listOf("thread-1", "thread-2"), rows.map { it.thread.id })
    assertEquals(SearchSource.TITLE, rows.first().source)
    assertEquals(SearchSource.MESSAGE, rows.last().source)
  }

  @Test
  fun `a message hit shows the text it matched`() {
    val threads = listOf(thread(id = "thread-1", title = "Other"))
    val matches = listOf(match(threadId = "thread-1", snippet = "the push path"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), matches)

    assertEquals("the push path", rows.single().snippet)
  }

  @Test
  fun `a thread that already matched its own text is not repeated`() {
    val threads = listOf(thread(id = "thread-1", title = "Push path"))
    val matches = listOf(match(threadId = "thread-1", snippet = "the push path"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), matches)

    assertEquals(1, rows.size)
    assertEquals(SearchSource.TITLE, rows.single().source)
  }

  @Test
  fun `a message hit on a thread the list does not have is left out`() {
    val threads = listOf(thread(id = "thread-1", title = "Other"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), listOf(match(threadId = "thread-9")))

    assertTrue(rows.isEmpty())
  }

  @Test
  fun `without a search every thread is a row of its own`() {
    val threads = listOf(thread(id = "thread-1", title = "One"), thread(id = "thread-2", title = "Two"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold(""), listOf(match(threadId = "thread-2")))

    assertEquals(listOf("thread-1", "thread-2"), rows.map { it.thread.id })
    assertTrue(rows.all { it.source == null })
  }

  @Test
  fun `a search keeps the groups in order`() {
    val threads =
      listOf(
        thread(id = "finished", title = "Push path", status = "idle", settledAt = "2026-11-11T09:00:00.000Z"),
        thread(id = "working", title = "Push path", status = "running"),
        thread(id = "idle", title = "Push path"),
      )

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), listOf())

    assertEquals(listOf("working", "finished", "idle"), rows.map { it.thread.id })
  }

  @Test
  fun `a search shows the newest thread of a group first`() {
    val threads =
      listOf(
        thread(id = "older", title = "Push path", updatedAt = "2026-11-11T08:00:00.000Z"),
        thread(id = "newer", title = "Push path", updatedAt = "2026-11-11T09:00:00.000Z"),
      )

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), listOf())

    assertEquals(listOf("newer", "older"), rows.map { it.thread.id })
  }

  @Test
  fun `inside a group a thread whose own text matched comes first`() {
    val threads =
      listOf(
        thread(id = "message-hit", title = "Other", updatedAt = "2026-11-11T10:00:00.000Z"),
        thread(id = "title-hit", title = "Push path", updatedAt = "2026-11-11T08:00:00.000Z"),
      )
    val matches = listOf(match(threadId = "message-hit"))

    val rows = PeerSearch.rows(threads, PeerSearch.fold("push"), matches)

    assertEquals(listOf("title-hit", "message-hit"), rows.map { it.thread.id })
  }

  @Test
  fun `every word of the query has to be in the thread's text`() {
    val threads = listOf(thread(id = "thread-1", title = "Push path"))

    assertTrue(PeerSearch.matchesThreadText(threads.single(), PeerSearch.fold("push path")))
    assertFalse(PeerSearch.matchesThreadText(threads.single(), PeerSearch.fold("push branch")))
  }

  @Test
  fun `the branch counts as the thread's own text`() {
    val threads = listOf(thread(id = "thread-1", title = "Unrelated", branch = "feat/push"))

    assertTrue(PeerSearch.matchesThreadText(threads.single(), PeerSearch.fold("push")))
  }

  @Test
  fun `the query is folded the way the server folds it`() {
    assertEquals("push path", PeerSearch.fold("  \"Push   PATH\"  "))
    assertEquals("push", PeerSearch.fold("Push"))
  }

  @Test
  fun `a query the server would refuse is not sent`() {
    assertFalse(PeerSearch.isSearchable("p"))
    assertTrue(PeerSearch.isSearchable("pu"))
  }

  @Test
  fun `the server's answer is read into message hits`() {
    val result =
      JSONObject().put(
        "matches",
        JSONArray()
          .put(
            JSONObject()
              .put("threadId", "thread-1")
              .put("projectId", "project-1")
              .put("source", "user")
              .put("snippet", "the push path")
              .put("messageCreatedAt", "2026-11-11T09:00:00.000Z"),
          )
          .put(JSONObject().put("threadId", "")),
      )

    val matches = PeerSearch.messageMatches(result)

    assertEquals(1, matches.size)
    assertEquals("thread-1", matches.single().threadId)
    assertEquals("project-1", matches.single().projectId)
    assertEquals("the push path", matches.single().snippet)
    assertEquals("2026-11-11T09:00:00.000Z", matches.single().messageCreatedAt)
  }

  private fun thread(
    id: String,
    title: String,
    branch: String = "",
    status: String = "idle",
    settledAt: String = "",
    updatedAt: String = "2026-11-11T09:00:00.000Z",
  ): ThreadSummary =
    ThreadSummary(
      id = id,
      title = title,
      projectId = "project-1",
      projectTitle = "A project",
      branch = branch,
      status = status,
      updatedAt = updatedAt,
      settledAt = settledAt,
    )

  private fun match(threadId: String, snippet: String = ""): ThreadMessageMatch =
    ThreadMessageMatch(
      threadId = threadId,
      projectId = "project-1",
      snippet = snippet,
      messageCreatedAt = "2026-11-11T09:00:00.000Z",
    )
}
