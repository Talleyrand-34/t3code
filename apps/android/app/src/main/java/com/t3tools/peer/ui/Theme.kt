package com.t3tools.peer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
  lightColorScheme(
    primary = Color(0xFF2F6FE4),
    onPrimary = Color.White,
    surfaceVariant = Color(0xFFEFF2F7),
  )

private val DarkColors =
  darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0B1B33),
    surfaceVariant = Color(0xFF232733),
  )

@Composable
fun T3PeerTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
    content = content,
  )
}
