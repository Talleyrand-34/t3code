package com.t3tools.peer.ui

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.t3tools.peer.model.SubagentSummary

/** Short labels for the states a thread can be in, as the official app shows them. */
fun statusLabel(status: String): String? =
  when (status) {
    "running", "starting", "preparing", "queued" -> "Working"
    "waiting" -> "Approval"
    "failed" -> "Failed"
    "interrupted" -> "Interrupted"
    "cancelled" -> "Cancelled"
    else -> null
  }

/** How a subagent of this thread is doing. */
fun subagentStatusLabel(subagent: SubagentSummary): String =
  when (subagent.status) {
    "pending" -> "Waiting"
    "running" -> "Working"
    "waiting" -> "Waiting"
    "completed" -> "Finished"
    "failed" -> "Failed"
    "cancelled" -> "Cancelled"
    "interrupted" -> "Interrupted"
    else -> ""
  }

/**
 * A run that finished a moment ago. The list points it out, so a turn that
 * ended while the phone was in a pocket does not pass unnoticed.
 */
fun justFinished(completedAt: String, now: Instant = Instant.now()): Boolean {
  if (completedAt.isEmpty()) return false
  val moment =
    try {
      Instant.parse(completedAt)
    } catch (failure: DateTimeParseException) {
      return false
    }
  val elapsed = Duration.between(moment, now)
  return !elapsed.isNegative && elapsed.toMinutes() < JUST_FINISHED_MINUTES
}

/** How long a finished run stays worth pointing out. */
private const val JUST_FINISHED_MINUTES = 10L

/** A subagent that has run out of road, one way or another. */
fun subagentIsFinished(subagent: SubagentSummary): Boolean =
  subagent.status == "completed" ||
    subagent.status == "failed" ||
    subagent.status == "cancelled" ||
    subagent.status == "interrupted"

/** "10m", "2h", "3d" — how long ago something happened. */
fun relativeTime(isoTimestamp: String, now: Instant = Instant.now()): String {
  val moment =
    try {
      Instant.parse(isoTimestamp)
    } catch (failure: DateTimeParseException) {
      return ""
    }
  val elapsed = Duration.between(moment, now)
  if (elapsed.isNegative) return "now"
  val minutes = elapsed.toMinutes()
  return when {
    minutes < 1 -> "now"
    minutes < 60 -> "${minutes}m"
    elapsed.toHours() < 24 -> "${elapsed.toHours()}h"
    elapsed.toDays() < 7 -> "${elapsed.toDays()}d"
    else -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(moment)
  }
}

/** "11:28 PM" — the clock under a message. */
fun clockTime(isoTimestamp: String): String {
  val moment =
    try {
      Instant.parse(isoTimestamp)
    } catch (failure: DateTimeParseException) {
      return ""
    }
  return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    .format(moment.atZone(java.time.ZoneId.systemDefault()))
}
