package com.t3tools.peer.model

import org.json.JSONObject

/** Where a hit came from: the thread's own text, or a message inside it. */
enum class SearchSource { TITLE, MESSAGE }

/** A message the server found for a query. */
data class ThreadMessageMatch(
  val threadId: String,
  val projectId: String,
  val snippet: String,
  val messageCreatedAt: String,
)

/** One row of the thread list. */
data class ThreadListRow(
  val thread: ThreadSummary,
  /** The message that matched, for a content hit. */
  val snippet: String = "",
  /** Null for a row that is not a hit, which is every row outside a search. */
  val source: SearchSource? = null,
  /** The group the row belongs to: needs you, working, finished or idle. */
  val state: ThreadState = ThreadState.IDLE,
)

/**
 * The thread search.
 *
 * A thread's own text is matched here, over the threads the app already lists, so
 * a title match is instant. A message inside a thread is matched by the server,
 * which owns the messages. The two kinds of hit are shown apart: a thread whose
 * own text matches comes first, and the ones a message matched follow.
 */
object PeerSearch {
  /** The shortest query the server accepts. */
  const val MIN_QUERY_LENGTH = 2

  /** The longest query the server accepts. */
  const val MAX_QUERY_LENGTH = 200

  /** The query as the server folds it: lower case, no wrapping quotes, single spaces. */
  fun fold(query: String): String =
    query
      .trim()
      .trim('"', '\'')
      .lowercase()
      .split(WHITESPACE)
      .filter { it.isNotEmpty() }
      .joinToString(" ")

  /** Whether the query is long enough for the server to answer it. */
  fun isSearchable(query: String): Boolean = fold(query).length >= MIN_QUERY_LENGTH

  /** The query as the server takes it: trimmed and no longer than it accepts. */
  fun forServer(query: String): String = query.trim().take(MAX_QUERY_LENGTH)

  /** Whether the thread's own text carries every word of the query. */
  fun matchesThreadText(thread: ThreadSummary, foldedQuery: String): Boolean {
    if (foldedQuery.isEmpty()) return false
    val haystack = listOf(thread.title, thread.branch).joinToString(" ").lowercase()
    return foldedQuery.split(' ').all { haystack.contains(it) }
  }

  /**
   * The rows a search shows: the threads whose own text matches first, then the
   * ones a message inside them matched.
   *
   * The groups stay as they are: a search narrows the list, it does not reshuffle
   * it. Inside a group a thread whose own text matches comes before one a message
   * inside it matched.
   */
  fun rows(
    threads: List<ThreadSummary>,
    foldedQuery: String,
    messageMatches: List<ThreadMessageMatch>,
  ): List<ThreadListRow> {
    val searching = foldedQuery.isNotEmpty()
    val matchByThread = messageMatches.distinctBy { it.threadId }.associateBy { it.threadId }
    // The list gives every row a key, so a thread that arrives twice would take
    // the list down with it.
    val rows =
      threads.distinctBy { it.id }.mapNotNull { thread ->
        val titleHit = searching && matchesThreadText(thread, foldedQuery)
        val match = if (searching && !titleHit) matchByThread[thread.id] else null
        if (searching && !titleHit && match == null) return@mapNotNull null
        ThreadListRow(
          thread,
          snippet = match?.snippet ?: "",
          source =
            when {
              titleHit -> SearchSource.TITLE
              match != null -> SearchSource.MESSAGE
              else -> null
            },
          state = ThreadState.of(thread),
        )
      }
    return ThreadState.ORDER.flatMap { state ->
      rows.filter { it.state == state }.sortedWith(rowOrder)
    }
  }

  /** Inside a group: a thread whose own text matched first, then the newest first. */
  private val rowOrder: Comparator<ThreadListRow> =
    Comparator { left, right ->
      val bySource = sourceRank(left).compareTo(sourceRank(right))
      if (bySource != 0) {
        bySource
      } else {
        val byTime =
          ThreadOrdering.sortMillis(right.thread).compareTo(ThreadOrdering.sortMillis(left.thread))
        if (byTime != 0) byTime else left.thread.id.compareTo(right.thread.id)
      }
    }

  private fun sourceRank(row: ThreadListRow): Int =
    when (row.source) {
      SearchSource.TITLE -> 0
      SearchSource.MESSAGE -> 1
      null -> 0
    }

  /** Reads the server's answer. */
  fun messageMatches(result: JSONObject): List<ThreadMessageMatch> {
    val matches = result.optJSONArray("matches") ?: return emptyList()
    val parsed = mutableListOf<ThreadMessageMatch>()
    for (index in 0 until matches.length()) {
      val match = matches.optJSONObject(index) ?: continue
      val threadId = match.optString("threadId")
      if (threadId.isEmpty()) continue
      parsed +=
        ThreadMessageMatch(
          threadId = threadId,
          projectId = match.optString("projectId"),
          snippet = plainSnippet(match.optString("snippet")),
          messageCreatedAt = match.optString("messageCreatedAt"),
        )
    }
    return parsed
  }

  /**
   * The snippet as plain text.
   *
   * The server hands back the message as it was written, so it carries the
   * markdown the agent wrote. A list row shows words, not markup.
   */
  fun plainSnippet(snippet: String): String {
    val plain =
      snippet
        .replace("```", " ")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s{0,3}[-*+]\\s+"), "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace("|", " ")
        .replace(Regex("[*_]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (plain.length <= MAX_SNIPPET_LENGTH) return plain
    return plain.take(MAX_SNIPPET_LENGTH).trimEnd() + "…"
  }

  /** How long a snippet shows before it is cut. */
  private const val MAX_SNIPPET_LENGTH = 140

  private val WHITESPACE = Regex("\\s+")
}
