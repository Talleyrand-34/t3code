package com.t3tools.peer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.t3tools.peer.ui.PeerApp
import com.t3tools.peer.ui.T3PeerTheme

class MainActivity : ComponentActivity() {
  /** A pairing link the app was opened with. */
  private val pairingLink = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pairingLink.value = intent?.dataString
    enableEdgeToEdge()
    setContent {
      T3PeerTheme {
        PeerApp(pairingLink = pairingLink.value)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pairingLink.value = intent.dataString
  }
}
