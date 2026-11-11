package com.t3tools.peer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.t3tools.peer.model.PeerAppearance

/**
 * The palette of the official T3 Code app, taken from its theme tokens
 * (`apps/mobile/generated-uniwind-default-theme-variables.json`), so both
 * clients look like the same product.
 */
@Immutable
data class PeerPalette(
  val screen: Color,
  val card: Color,
  val grouped: Color,
  val foreground: Color,
  val muted: Color,
  val border: Color,
  val primary: Color,
  val onPrimary: Color,
  val userBubble: Color,
  val onUserBubble: Color,
  val warning: Color,
  val danger: Color,
  val composer: Color,
  val composerBorder: Color,
)

private val LightPalette =
  PeerPalette(
    screen = Color(0xFFFCFCFC),
    card = Color(0xFFFFFFFF),
    grouped = Color(0xFFF4F4F5),
    foreground = Color(0xFF27272A),
    muted = Color(0xFF6F6F79),
    border = Color(0xFFE4E4E7),
    primary = Color(0xFF1B4ED8),
    onPrimary = Color(0xFFFFFFFF),
    userBubble = Color(0xFFEFEFF1),
    onUserBubble = Color(0xFF27272A),
    warning = Color(0xFFBB4D00),
    danger = Color(0xFFC10007),
    composer = Color(0xFFFCFCFC),
    composerBorder = Color(0xFFE4E4E7),
  )

private val DarkPalette =
  PeerPalette(
    screen = Color(0xFF0A0A0A),
    card = Color(0xFF111111),
    grouped = Color(0xFF1A1B1B),
    foreground = Color(0xFFF5F5F5),
    muted = Color(0xFF838383),
    border = Color(0xFF191919),
    primary = Color(0xFF346BF1),
    onPrimary = Color(0xFFFFFFFF),
    userBubble = Color(0xFF161616),
    onUserBubble = Color(0xFFF5F5F5),
    warning = Color(0xFFFFB900),
    danger = Color(0xFFFF6467),
    composer = Color(0xFF111111),
    composerBorder = Color(0xFF191919),
  )

/** The palette for an appearance that has already been resolved. */
fun peerPalette(dark: Boolean): PeerPalette = if (dark) DarkPalette else LightPalette

/** The colour the window starts in, so the app never flashes the other tone. */
fun peerScreenColor(dark: Boolean): Int = peerPalette(dark).screen.toArgb()

val LocalPeerPalette = staticCompositionLocalOf { LightPalette }

/** Whether the app is painting itself dark, for surfaces that ask. */
val LocalPeerDark = staticCompositionLocalOf { false }

@Composable
fun T3PeerTheme(appearance: PeerAppearance = PeerAppearance.SYSTEM, content: @Composable () -> Unit) {
  val dark = appearance.resolveDark(isSystemInDarkTheme())
  val palette = peerPalette(dark)
  val colors =
    if (dark) {
      darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        background = palette.screen,
        onBackground = palette.foreground,
        surface = palette.card,
        onSurface = palette.foreground,
        surfaceVariant = palette.grouped,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border,
        error = palette.danger,
        onError = Color.White,
      )
    } else {
      lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        background = palette.screen,
        onBackground = palette.foreground,
        surface = palette.card,
        onSurface = palette.foreground,
        surfaceVariant = palette.grouped,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        outlineVariant = palette.border,
        error = palette.danger,
        onError = Color.White,
      )
    }
  PeerSystemBars(dark)
  androidx.compose.runtime.CompositionLocalProvider(
    LocalPeerPalette provides palette,
    LocalPeerDark provides dark,
  ) {
    MaterialTheme(colorScheme = colors, content = content)
  }
}

/**
 * The status and navigation bars follow the app, not the system: a user who
 * picks dark on a light desktop still needs light icons on the bars.
 *
 * A sheet is a window of its own, so this has to run again inside it.
 */
@Composable
fun PeerSystemBars(dark: Boolean) {
  val view = LocalView.current
  LaunchedEffect(dark) {
    val window =
      (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivity()?.window
        ?: return@LaunchedEffect
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = !dark
      isAppearanceLightNavigationBars = !dark
    }
  }
}
