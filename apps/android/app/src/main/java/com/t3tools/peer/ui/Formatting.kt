package com.t3tools.peer.ui

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

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
