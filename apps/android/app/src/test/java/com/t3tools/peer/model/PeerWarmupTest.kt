package com.t3tools.peer.model

import com.t3tools.peer.net.PeerProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerWarmupTest {
  @Test
  fun `a thread subscription asks for the bounded window`() {
    val payload = PeerProtocol.threadSubscription("thread-1")

    assertEquals("thread-1", payload.optString("threadId"))
    assertTrue(payload.optBoolean("acceptBoundedSnapshot"))
    assertTrue(payload.optBoolean("acceptDeferredWork"))
    assertTrue(payload.optBoolean("acceptCompactTurnItems"))
  }

  @Test
  fun `the threads an agent works on come first`() {
    val threads =
      listOf(
        thread(id = "idle-old", status = "completed", updatedAt = "2026-11-10T10:00:00.000Z"),
        thread(id = "working-old", status = "running", updatedAt = "2026-11-10T10:00:00.000Z"),
        thread(id = "idle-new", status = "completed", updatedAt = "2026-11-17T10:00:00.000Z"),
        thread(id = "working-new", status = "waiting", updatedAt = "2026-11-16T10:00:00.000Z"),
      )

    val warm = selectWarmThreads(threads, limit = 2)

    assertEquals(listOf("working-new", "working-old"), warm.map { it.id })
  }

  @Test
  fun `the threads touched last fill the rest`() {
    val threads =
      listOf(
        thread(id = "idle-old", status = "completed", updatedAt = "2026-11-10T10:00:00.000Z"),
        thread(id = "idle-new", status = "completed", updatedAt = "2026-11-17T10:00:00.000Z"),
        thread(id = "idle-middle", status = "completed", updatedAt = "2026-11-15T10:00:00.000Z"),
      )

    val warm = selectWarmThreads(threads, limit = 2)

    assertEquals(listOf("idle-new", "idle-middle"), warm.map { it.id })
  }

  @Test
  fun `nothing is kept ready when there is no room`() {
    val threads = listOf(thread(id = "idle", status = "completed", updatedAt = "2026-11-17T10:00:00.000Z"))

    assertEquals(emptyList<ThreadSummary>(), selectWarmThreads(threads, limit = 0))
  }

  private fun thread(id: String, status: String, updatedAt: String): ThreadSummary =
    ThreadSummary(
      id = id,
      title = id,
      projectId = "project-1",
      projectTitle = "project",
      branch = "",
      status = status,
      updatedAt = updatedAt,
    )
}
