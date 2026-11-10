package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun PeerApp(viewModel: PeerViewModel = viewModel(), pairingLink: String? = null) {
  val palette = LocalPeerPalette.current

  LaunchedEffect(pairingLink) {
    pairingLink?.let { viewModel.applyPairingLink(it) }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = palette.screen,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      viewModel.error?.let { message ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(palette.grouped)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.foreground,
            modifier = Modifier.weight(1f),
          )
          Text(
            "Dismiss",
            style = MaterialTheme.typography.labelMedium,
            color = palette.primary,
            modifier = Modifier.clickable { viewModel.dismissError() }.padding(start = 12.dp),
          )
        }
      }

      when (viewModel.screen) {
        PeerScreen.CONNECT -> ConnectScreen(viewModel)
        PeerScreen.THREADS -> ThreadListScreen(viewModel)
        PeerScreen.THREAD -> ThreadScreen(viewModel)
      }

      if (viewModel.settingsVisible) {
        SettingsSheet(viewModel)
      }
    }
  }
}
