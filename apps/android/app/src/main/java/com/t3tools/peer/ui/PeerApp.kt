package com.t3tools.peer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PeerApp(viewModel: PeerViewModel = viewModel(), pairingLink: String? = null) {
  LaunchedEffect(pairingLink) {
    pairingLink?.let { viewModel.applyPairingLink(it) }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      viewModel.error?.let { message ->
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
          }
        }
      }

      when (viewModel.screen) {
        PeerScreen.CONNECT -> ConnectScreen(viewModel)
        PeerScreen.THREADS -> ThreadListScreen(viewModel)
        PeerScreen.THREAD -> ThreadScreen(viewModel)
      }
    }
  }
}
