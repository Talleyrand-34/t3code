package com.t3tools.peer

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import com.t3tools.peer.data.PeerStore
import com.t3tools.peer.model.PeerAppearance
import com.t3tools.peer.ui.PeerApp
import com.t3tools.peer.ui.PeerNotifications
import com.t3tools.peer.ui.PeerViewModel
import com.t3tools.peer.ui.T3PeerTheme
import com.t3tools.peer.ui.peerScreenColor

class MainActivity : ComponentActivity() {
  /** A pairing link the app was opened with. */
  private val pairingLink = mutableStateOf<String?>(null)

  /** The thread a notification asked the user to open. */
  private val threadToOpen = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pairingLink.value = intent?.dataString
    threadToOpen.value = intent?.getStringExtra(EXTRA_THREAD_ID)
    val dark = PeerStore(applicationContext).appearance.resolveDark(systemPrefersDark())
    // The window starts in the app's own tone, so a dark app never flashes white.
    window.setBackgroundDrawable(ColorDrawable(peerScreenColor(dark)))
    enableEdgeToEdge(
      statusBarStyle = systemBarStyle(dark),
      navigationBarStyle = systemBarStyle(dark),
    )
    PeerNotifications.ensureChannel(this)
    askForNotifications()
    setContent {
      val viewModel: PeerViewModel = viewModel()
      LaunchedEffect(threadToOpen.value) {
        threadToOpen.value?.let { viewModel.openThreadById(it) }
      }
      T3PeerTheme(appearance = viewModel.appearance) {
        PeerApp(viewModel = viewModel, pairingLink = pairingLink.value)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pairingLink.value = intent.dataString
    threadToOpen.value = intent.getStringExtra(EXTRA_THREAD_ID)
  }

  /** The app posts a notification when the agent needs the user. */
  private fun askForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (PeerNotifications.allowed(this)) return
    ActivityCompat.requestPermissions(
      this,
      arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
      NOTIFICATION_PERMISSION_REQUEST,
    )
  }

  private fun systemPrefersDark(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES

  private fun systemBarStyle(dark: Boolean): SystemBarStyle =
    if (dark) {
      SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
      SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }

  companion object {
    /** The thread a notification points at. */
    const val EXTRA_THREAD_ID = "threadId"

    private const val NOTIFICATION_PERMISSION_REQUEST = 4101
  }
}
