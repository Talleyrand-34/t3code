package com.t3tools.peer.data

import android.content.Context

/**
 * Remembers the server and the session it granted. The pairing code is never
 * stored: it works once, and the session it mints lasts longer.
 */
class PeerStore(context: Context) {
  private val preferences = context.getSharedPreferences("t3-peer", Context.MODE_PRIVATE)

  var serverInput: String
    get() = preferences.getString(SERVER_KEY, "").orEmpty()
    set(value) = preferences.edit().putString(SERVER_KEY, value.trim()).apply()

  /** The bearer session for [serverInput], or null when there is none. */
  var bearerToken: String?
    get() = preferences.getString(TOKEN_KEY, null)
    set(value) {
      preferences.edit().apply {
        if (value.isNullOrBlank()) remove(TOKEN_KEY) else putString(TOKEN_KEY, value)
      }.apply()
    }

  fun forgetSession() {
    bearerToken = null
  }

  private companion object {
    const val SERVER_KEY = "server"
    const val TOKEN_KEY = "bearerToken"
  }
}
