package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.FollowUpBehavior
import com.t3tools.peer.model.PeerAppearance

/** Appearance, follow-ups and connection settings, in the shape the official app uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: PeerViewModel) {
  val palette = LocalPeerPalette.current
  ModalBottomSheet(
    onDismissRequest = viewModel::hideSettings,
    containerColor = palette.screen,
    contentColor = palette.foreground,
  ) {
    // The sheet is a window of its own, so its bars need telling as well.
    PeerSystemBars(LocalPeerDark.current)
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        "Appearance",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        modifier = Modifier.padding(top = 4.dp),
      )
      AppearanceOption("System", viewModel.appearance == PeerAppearance.SYSTEM) {
        viewModel.chooseAppearance(PeerAppearance.SYSTEM)
      }
      AppearanceOption("Light", viewModel.appearance == PeerAppearance.LIGHT) {
        viewModel.chooseAppearance(PeerAppearance.LIGHT)
      }
      AppearanceOption("Dark", viewModel.appearance == PeerAppearance.DARK) {
        viewModel.chooseAppearance(PeerAppearance.DARK)
      }

      Spacer(Modifier.height(6.dp))
      HorizontalDivider(color = palette.border)
      Spacer(Modifier.height(6.dp))

      Text(
        "While the agent is running",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
      FollowUpOption(
        label = "Queue",
        description = "Your message waits and runs after the current turn finishes.",
        selected = viewModel.followUpBehavior == FollowUpBehavior.QUEUE,
      ) {
        viewModel.chooseFollowUpBehavior(FollowUpBehavior.QUEUE)
      }
      FollowUpOption(
        label = "Steer",
        description = "Your message reaches the agent right away, changing what it is working on.",
        selected = viewModel.followUpBehavior == FollowUpBehavior.STEER,
      ) {
        viewModel.chooseFollowUpBehavior(FollowUpBehavior.STEER)
      }
      Text(
        "Hold the send button to use the other option for a single message.",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        modifier = Modifier.padding(bottom = 4.dp),
      )

      Spacer(Modifier.height(6.dp))
      HorizontalDivider(color = palette.border)
      Spacer(Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Notifications", style = MaterialTheme.typography.bodyLarge, color = palette.foreground)
          Text(
            "Says when the agent needs you.",
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted,
          )
        }
        Switch(
          checked = viewModel.notificationsEnabled,
          onCheckedChange = { viewModel.chooseNotifications(it) },
        )
      }

      Spacer(Modifier.height(6.dp))
      HorizontalDivider(color = palette.border)
      Spacer(Modifier.height(6.dp))

      Text(
        "Connected to ${viewModel.environmentLabel.ifEmpty { "no server" }}",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.foreground,
      )
      Text(
        viewModel.serverInput,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
      Text(
        "Disconnect",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.danger,
        modifier =
          Modifier.clickable {
            viewModel.hideSettings()
            viewModel.disconnect()
          }.padding(vertical = 6.dp),
      )
      Text(
        "Forget this server",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.danger,
        modifier =
          Modifier.clickable {
            viewModel.hideSettings()
            viewModel.forgetServer()
          }.padding(vertical = 6.dp),
      )
      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, onSelect: () -> Unit) {
  val palette = LocalPeerPalette.current
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      fontSize = 16.sp,
      color = palette.foreground,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Box(
        modifier = Modifier.size(20.dp).clip(CircleShape).background(palette.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = palette.onPrimary,
          modifier = Modifier.size(13.dp),
        )
      }
    }
  }
}

/** One of the ways a message can reach the agent while it is working. */
@Composable
private fun FollowUpOption(
  label: String,
  description: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  val palette = LocalPeerPalette.current
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(label, fontSize = 16.sp, color = palette.foreground)
      Text(
        description,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
    }
    if (selected) {
      Box(
        modifier = Modifier.size(20.dp).clip(CircleShape).background(palette.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = palette.onPrimary,
          modifier = Modifier.size(13.dp),
        )
      }
    }
  }
}

/** A small rounded mark, used where the official app shows a project icon. */
@Composable
fun MarkBadge(text: String, color: androidx.compose.ui.graphics.Color) {
  val palette = LocalPeerPalette.current
  Box(
    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(color),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text.take(1).uppercase(),
      fontSize = 11.sp,
      fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
      color = palette.screen,
    )
  }
}
