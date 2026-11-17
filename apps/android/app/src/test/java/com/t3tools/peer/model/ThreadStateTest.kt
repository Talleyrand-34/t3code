package com.t3tools.peer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThreadStateTest {
  @Test
  fun `a thread with a run in it is working`() {
    assertEquals(ThreadState.WORKING, ThreadState.of(thread(status = "running")))
    assertEquals(ThreadState.WORKING, ThreadState.of(thread(status = "waiting")))
    assertEquals(ThreadState.WORKING, ThreadState.of(thread(status = "preparing")))
  }

  @Test
  fun `a thread whose run settled is finished`() {
    assertEquals(ThreadState.FINISHED, ThreadState.of(thread(status = "idle", settledAt = "2026-11-11T09:00:00.000Z")))
  }

  @Test
  fun `a thread that never ran is idle`() {
    assertEquals(ThreadState.IDLE, ThreadState.of(thread(status = "idle")))
  }

  @Test
  fun `a thread whose run failed is not working`() {
    assertEquals(ThreadState.IDLE, ThreadState.of(thread(status = "failed")))
  }

  @Test
  fun `the working threads come first, then the finished ones, then the idle ones`() {
    val idle = thread(id = "idle", status = "idle")
    val finished = thread(id = "finished", status = "idle", settledAt = "2026-11-11T09:00:00.000Z")
    val working = thread(id = "working", status = "running")

    val ordered = orderThreads(listOf(idle, finished, working))

    assertEquals(listOf("working", "finished", "idle"), ordered.map { it.id })
  }

  @Test
  fun `the newest thread of a group comes first`() {
    val older = thread(id = "older", status = "running", updatedAt = "2026-11-11T08:00:00.000Z")
    val newer = thread(id = "newer", status = "running", updatedAt = "2026-11-11T09:00:00.000Z")

    val ordered = orderThreads(listOf(older, newer))

    assertEquals(listOf("newer", "older"), ordered.map { it.id })
  }

  @Test
  fun `a finished thread is sorted by the moment it settled`() {
    val settled =
      thread(
        id = "settled",
        status = "idle",
        settledAt = "2026-11-11T09:00:00.000Z",
        updatedAt = "2026-11-11T09:00:00.000Z",
      )
    val touched =
      thread(
        id = "touched",
        status = "idle",
        settledAt = "2026-11-11T08:00:00.000Z",
        updatedAt = "2026-11-11T10:00:00.000Z",
      )

    val ordered = orderThreads(listOf(touched, settled))

    assertEquals(listOf("settled", "touched"), ordered.map { it.id })
  }

  @Test
  fun `a thread whose run completed is sorted by that moment`() {
    val completed =
      thread(
        id = "completed",
        status = "idle",
        latestRunCompletedAt = "2026-11-11T09:00:00.000Z",
        updatedAt = "2026-11-11T09:00:00.000Z",
      )
    val older = thread(id = "older", status = "idle", updatedAt = "2026-11-11T08:00:00.000Z")

    val ordered = orderThreads(listOf(older, completed))

    assertEquals(listOf("completed", "older"), ordered.map { it.id })
  }

  private fun thread(
    id: String = "thread-1",
    status: String = "idle",
    settledAt: String = "",
    latestRunCompletedAt: String = "",
    updatedAt: String = "2026-11-11T09:00:00.000Z",
  ): ThreadSummary =
    ThreadSummary(
      id = id,
      title = "A thread",
      projectId = "project-1",
      projectTitle = "A project",
      branch = "",
      status = status,
      updatedAt = updatedAt,
      settledAt = settledAt,
      latestRunCompletedAt = latestRunCompletedAt,
    )
}

class ProjectIdentityTest {
  @Test
  fun `a project wears a monogram of its name`() {
    assertEquals("T3", ProjectIdentity.of("t3code").monogram)
    assertEquals("PR", ProjectIdentity.of("   ").monogram)
  }

  @Test
  fun `a project with several words takes the first and the first of the last`() {
    assertEquals("MP", ProjectIdentity.of("my project").monogram)
  }

  @Test
  fun `a project always wears the same colour`() {
    assertEquals(ProjectIdentity.of("t3code").colorIndex, ProjectIdentity.of("t3code").colorIndex)
  }

  @Test
  fun `two projects do not share a colour`() {
    assertNotEquals(ProjectIdentity.of("alpha").colorIndex, ProjectIdentity.of("beta").colorIndex)
  }

  @Test
  fun `a colour is one of the colours a project can wear`() {
    val color = ProjectIdentity.of("t3code").colorIndex
    assert(color in 0 until ProjectIdentity.COLOR_COUNT)
  }
}
