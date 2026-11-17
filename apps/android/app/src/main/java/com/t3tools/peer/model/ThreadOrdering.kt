package com.t3tools.peer.model

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * The order the desktop app shows threads in.
 *
 * The server hands the shell snapshot over oldest first, so the app sorts the
 * threads itself: the working ones first, then the ones that finished, then the
 * idle ones, and inside a group the newest first.
 */
object ThreadOrdering {
  /**
   * The moment a thread is sorted by.
   *
   * A finished thread is sorted by the moment its run settled, the way the desktop
   * app sorts it. Every other thread is sorted by the moment it was last touched.
   */
  fun sortMillis(thread: ThreadSummary): Long {
    if (ThreadState.of(thread) == ThreadState.FINISHED) {
      val settled = millis(thread.settledAt)
      if (settled != 0L) return settled
      val completed = millis(thread.latestRunCompletedAt)
      if (completed != 0L) return completed
    }
    return millis(thread.updatedAt)
  }

  /** The newest first, with the thread id to break a tie, as the desktop app does. */
  val newestFirst: Comparator<ThreadSummary> =
    Comparator { left, right ->
      val byTime = sortMillis(right).compareTo(sortMillis(left))
      if (byTime != 0) byTime else left.id.compareTo(right.id)
    }

  private fun millis(isoTimestamp: String): Long =
    try {
      if (isoTimestamp.isEmpty()) 0L else Instant.parse(isoTimestamp).toEpochMilli()
    } catch (failure: DateTimeParseException) {
      0L
    }
}
