package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  var codeVisible by remember { mutableStateOf(false) }

  Surface(modifier = modifier.fillMaxSize(), color = palette.screen) {
    Column(
      modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "T3 Peer",
          fontSize = 21.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.foreground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          "DIRECT",
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.sp,
          color = palette.muted,
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.grouped)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }

      Text(
        "Talk to a T3 Code server over your own network. No cloud service " +
          "takes part in it.",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.muted,
      )

      Spacer(Modifier.height(4.dp))

      PeerField(
        value = viewModel.serverInput,
        onValueChange = { viewModel.serverInput = it },
        label = "Server address",
        placeholder = "192.168.1.20:3923",
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
      )

      PeerField(
        value = viewModel.codeInput,
        onValueChange = { viewModel.codeInput = it },
        label = "Pairing code",
        placeholder = "from `t3 pair`",
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation =
          if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
          IconButton(onClick = { codeVisible = !codeVisible }) {
            Icon(
              imageVector = if (codeVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = if (codeVisible) "Hide the code" else "Show the code",
              tint = palette.muted,
            )
          }
        },
      )

      Text(
        "A pairing link works too: open it on this phone and the app fills " +
          "this in. The code works once; the session it grants lasts longer.",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )

      Spacer(Modifier.height(2.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(if (viewModel.connecting) palette.muted else palette.foreground)
          .clickable(enabled = !viewModel.connecting) { viewModel.connect() }
          .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (viewModel.connecting) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = palette.screen,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Text(
            "Connect",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = palette.screen,
          )
        }
      }
    }
  }
}

/** A field with the official app's flat, bordered look. */
@Composable
private fun PeerField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  trailing: (@Composable () -> Unit)? = null,
) {
  val palette = LocalPeerPalette.current
  Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = palette.muted)
    androidx.compose.material3.OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = palette.muted) },
      singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
      keyboardOptions = keyboardOptions,
      visualTransformation = visualTransformation,
      trailingIcon = trailing,
      shape = RoundedCornerShape(12.dp),
      colors =
        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
          focusedBorderColor = palette.primary,
          unfocusedBorderColor = palette.border,
          focusedContainerColor = palette.card,
          unfocusedContainerColor = palette.card,
          focusedTextColor = palette.foreground,
          unfocusedTextColor = palette.foreground,
          cursorColor = palette.primary,
        ),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
