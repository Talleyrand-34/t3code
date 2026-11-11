package com.t3tools.peer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.t3tools.peer.model.PeerAppearance

/**
 * Remembers the server and the session it granted. The pairing code is never
 * stored: it works once, and the session it mints lasts longer.
 *
 * The session is a bearer credential, so it lives in encrypted storage rather than
 * in plain preferences.
 */
class PeerStore(context: Context) {
  private val preferences =
    EncryptedSharedPreferences.create(
      context,
      "t3-peer",
      MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

  var serverInput: String
    get() = preferences.getString(SERVER_KEY, "").orEmpty()
    set(value) = commit { putString(SERVER_KEY, value.trim()) }

  /** The bearer session for [serverInput], or null when there is none. */
  var bearerToken: String?
    get() = preferences.getString(TOKEN_KEY, null)
    set(value) {
      commit {
        if (value.isNullOrBlank()) remove(TOKEN_KEY) else putString(TOKEN_KEY, value)
      }
    }

  fun forgetSession() {
    bearerToken = null
  }

  /** How the app paints itself. */
  var appearance: PeerAppearance
    get() = PeerAppearance.fromStorage(preferences.getString(APPEARANCE_KEY, null))
    set(value) = commit { putString(APPEARANCE_KEY, value.storageValue) }

  /**
   * Writes straight to disk. An update force-stops the app, and a write still
   * waiting in the queue would go with it, taking the pairing with it.
   */
  private inline fun commit(write: SharedPreferences.Editor.() -> Unit) {
    preferences.edit().apply(write).commit()
  }

  private companion object {
    const val SERVER_KEY = "server"
    const val TOKEN_KEY = "bearerToken"
    const val APPEARANCE_KEY = "appearance"
  }
}
