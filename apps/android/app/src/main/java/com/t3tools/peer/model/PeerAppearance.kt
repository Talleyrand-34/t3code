package com.t3tools.peer.model

/**
 * How the app paints itself: follow the system, or stay light or dark
 * whatever the system does.
 */
enum class PeerAppearance(val storageValue: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  /** Whether the app paints itself dark, given what the system asks for. */
  fun resolveDark(systemDark: Boolean): Boolean =
    when (this) {
      SYSTEM -> systemDark
      LIGHT -> false
      DARK -> true
    }

  companion object {
    /** A value we do not know falls back to following the system. */
    fun fromStorage(value: String?): PeerAppearance =
      entries.firstOrNull { it.storageValue == value } ?: SYSTEM
  }
}
