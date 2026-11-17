package com.t3tools.peer.model

/**
 * Where a thread stands, as the desktop app groups them.
 *
 * A thread is working while a run is in it, finished once that run settled, and
 * idle when it has nothing running and nothing settled.
 */
enum class ThreadState {
  WORKING,
  FINISHED,
  IDLE;

  companion object {
    /**
     * The group a thread belongs to.
     *
     * The run's own state decides first: a thread that is running, preparing or
     * waiting for the user is working, and a failed or cancelled run is not. A
     * thread whose run settled is finished, and the rest are idle.
     */
    fun of(thread: ThreadSummary): ThreadState =
      when {
        thread.status in WORKING_STATUSES -> WORKING
        thread.status in BROKEN_STATUSES -> IDLE
        thread.settledAt.isNotEmpty() -> FINISHED
        thread.latestRunCompletedAt.isNotEmpty() -> FINISHED
        else -> IDLE
      }

    /** The groups, in the order the desktop app shows them. */
    val ORDER: List<ThreadState> = listOf(WORKING, FINISHED, IDLE)

    private val WORKING_STATUSES = setOf("running", "starting", "preparing", "queued", "waiting")
    private val BROKEN_STATUSES = setOf("failed", "interrupted", "cancelled")
  }
}

/**
 * The threads in the order the desktop app shows them: the ones that are working,
 * then the ones that finished, then the idle ones. Inside a group the newest comes
 * first, because the server hands the list over oldest first.
 */
fun orderThreads(threads: List<ThreadSummary>): List<ThreadSummary> =
  ThreadState.ORDER.flatMap { state ->
    threads.filter { ThreadState.of(it) == state }.sortedWith(ThreadOrdering.newestFirst)
  }
