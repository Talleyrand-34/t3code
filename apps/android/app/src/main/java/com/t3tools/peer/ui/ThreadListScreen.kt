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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.ThreadSummary

@Composable
fun ThreadListScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  Surface(modifier = modifier.fillMaxSize(), color = palette.screen) {
    Column(modifier = Modifier.fillMaxSize()) {
      Header(
        environmentLabel = viewModel.environmentLabel,
        onDisconnect = viewModel::disconnect,
        onSettings = viewModel::showSettings,
      )
      if (viewModel.threads.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("No threads yet", style = MaterialTheme.typography.titleMedium)
          Text(
            "Threads on this server will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomPadding()) {
          items(viewModel.threads, key = { it.id }) { thread ->
            ThreadRow(thread) { viewModel.openThread(thread) }
          }
        }
      }
    }
  }
}

@Composable
private fun Header(environmentLabel: String, onDisconnect: () -> Unit, onSettings: () -> Unit) {
  val palette = LocalPeerPalette.current
  Row(
    modifier =
      Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
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
    Spacer(Modifier.weight(1f))
    if (environmentLabel.isNotEmpty()) {
      Text(
        environmentLabel,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.width(4.dp))
    }
    IconButton(onClick = onDisconnect) {
      Icon(
        Icons.AutoMirrored.Filled.Logout,
        contentDescription = "Disconnect",
        tint = palette.muted,
      )
    }
    IconButton(onClick = onSettings) {
      Icon(
        Icons.Filled.Settings,
        contentDescription = "Settings",
        tint = palette.muted,
      )
    }
  }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onOpen: () -> Unit) {
  val palette = LocalPeerPalette.current
  val status = statusLabel(thread.status)
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 9.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      ProjectBadge(thread.projectTitle)
      Spacer(Modifier.width(7.dp))
      Text(
        thread.projectTitle,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.weight(1f))
      Text(
        relativeTime(thread.updatedAt),
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
    }
    Row(verticalAlignment = Alignment.Top) {
      Text(
        thread.title,
        fontSize = 16.sp,
        color = palette.foreground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (status != null) {
        Spacer(Modifier.width(8.dp))
        Text(
          status,
          style = MaterialTheme.typography.labelMedium,
          color = statusColor(thread.status, palette),
          modifier = Modifier.padding(top = 3.dp),
        )
      }
    }
    if (thread.branch.isNotEmpty()) {
      Text(
        thread.branch,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun statusColor(status: String, palette: PeerPalette) =
  when (status) {
    "waiting", "failed" -> palette.danger
    "interrupted", "cancelled" -> palette.muted
    else -> palette.primary
  }

/** The project's square mark, as the official app shows beside a thread. */@Composable
private fun ProjectBadge(projectTitle: String) {
  val palette = LocalPeerPalette.current
  val initial = projectTitle.firstOrNull()?.uppercase() ?: "·"
  Box(
    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(palette.foreground),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      initial,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = palette.screen,
    )
  }
}

private fun bottomPadding() = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
