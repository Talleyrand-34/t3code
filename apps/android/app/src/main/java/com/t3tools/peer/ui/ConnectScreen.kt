package com.t3tools.peer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ConnectScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  var codeVisible by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("T3 Peer", style = MaterialTheme.typography.headlineMedium)
    Text(
      "Talk to a T3 Code server straight from this phone. The connection goes " +
        "over your network, and no cloud service takes part in it.",
      style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedTextField(
      value = viewModel.serverInput,
      onValueChange = { viewModel.serverInput = it },
      label = { Text("Server address") },
      placeholder = { Text("192.168.1.20:3923") },
      supportingText = { Text("A pairing link works here too: http://192.168.1.20:3923/#token=…") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = viewModel.codeInput,
      onValueChange = { viewModel.codeInput = it },
      label = { Text("Pairing code") },
      supportingText = { Text("Run `t3 pair` on the host to get one. It works once.") },
      singleLine = true,
      visualTransformation = if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { codeVisible = !codeVisible }) {
          Icon(
            imageVector = if (codeVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (codeVisible) "Hide the code" else "Show the code",
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )

    Button(
      onClick = viewModel::connect,
      enabled = !viewModel.connecting,
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (viewModel.connecting) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
      }
      Text(if (viewModel.connecting) "Connecting" else "Connect")
    }
  }
}
