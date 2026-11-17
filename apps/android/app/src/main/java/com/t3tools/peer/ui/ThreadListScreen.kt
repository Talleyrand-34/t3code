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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.t3tools.peer.model.ProjectChoice
import com.t3tools.peer.model.ProjectIdentity
import com.t3tools.peer.model.SearchSource
import com.t3tools.peer.model.ThreadListRow
import com.t3tools.peer.model.ThreadState

@Composable
fun ThreadListScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  val rows = viewModel.threadRows
  Surface(modifier = modifier.fillMaxSize(), color = palette.screen) {
    Column(modifier = Modifier.fillMaxSize()) {
      Header(
        environmentLabel = viewModel.environmentLabel,
        onDisconnect = viewModel::disconnect,
        onSettings = viewModel::showSettings,
      )
      SearchField(
        query = viewModel.searchQuery,
        onQueryChange = viewModel::updateSearchQuery,
      )
      ProjectFilter(
        projects = viewModel.projects,
        selectedProjectId = viewModel.selectedProjectId,
        onSelect = viewModel::selectProject,
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
      } else if (rows.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text("No threads match", style = MaterialTheme.typography.titleMedium)
          Text(
            "Nothing here answers to that search.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
          )
        }
      } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomPadding()) {
          itemsIndexed(rows, key = { _, row -> row.thread.id }) { index, row ->
            if (rows.getOrNull(index - 1)?.state != row.state) {
              GroupHeader(row.state)
            }
            ThreadRow(row) { viewModel.openThread(row.thread) }
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
      modifier =
        Modifier
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

/** Where the user types what to look for. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  val palette = LocalPeerPalette.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(palette.grouped)
        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Filled.Search,
      contentDescription = null,
      tint = palette.muted,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(8.dp))
    Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
      if (query.isEmpty()) {
        Text(
          "Search threads and messages",
          fontSize = 15.sp,
          color = palette.muted,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
          androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.foreground),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.primary),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (query.isNotEmpty()) {
      IconButton(onClick = { onQueryChange("") }) {
        Icon(
          Icons.Filled.Close,
          contentDescription = "Clear the search",
          tint = palette.muted,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

/** The projects the list can be narrowed to. */
@Composable
private fun ProjectFilter(
  projects: List<ProjectChoice>,
  selectedProjectId: String?,
  onSelect: (String?) -> Unit,
) {
  if (projects.size < 2) return
  val palette = LocalPeerPalette.current
  LazyRow(
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      ProjectChip(
        label = "All",
        selected = selectedProjectId == null,
        onClick = { onSelect(null) },
      )
    }
    items(projects, key = { it.id }) { project ->
      ProjectChip(
        label = project.title,
        selected = selectedProjectId == project.id,
        onClick = { onSelect(project.id) },
      )
    }
  }
}

@Composable
private fun ProjectChip(label: String, selected: Boolean, onClick: () -> Unit) {
  val palette = LocalPeerPalette.current
  Text(
    label,
    fontSize = 13.sp,
    color = if (selected) palette.onPrimary else palette.foreground,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier =
      Modifier
        .clip(CircleShape)
        .background(if (selected) palette.primary else palette.grouped)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
  )
}

@Composable
private fun ThreadRow(row: ThreadListRow, onOpen: () -> Unit) {
  val palette = LocalPeerPalette.current
  val thread = row.thread
  val status = threadStateLabel(thread) ?: if (justFinished(thread.latestRunCompletedAt)) "Finished" else null
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
    if (row.source == SearchSource.MESSAGE && row.snippet.isNotEmpty()) {
      Text(
        row.snippet,
        style = MaterialTheme.typography.bodySmall,
        color = palette.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
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
    "interrupted", "cancelled", "idle" -> palette.muted
    else -> palette.primary
  }

/** The group's name, as the desktop app heads its sections. */
@Composable
private fun GroupHeader(state: ThreadState) {
  val palette = LocalPeerPalette.current
  Text(
    when (state) {
      ThreadState.WORKING -> "Working"
      ThreadState.FINISHED -> "Finished"
      ThreadState.IDLE -> "Idle"
    },
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = palette.muted,
    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
  )
}

/** The project's square mark, as the official app shows beside a thread. */
@Composable
private fun ProjectBadge(projectTitle: String) {
  val palette = LocalPeerPalette.current
  val identity = ProjectIdentity.of(projectTitle)
  val color = palette.projectColors.getOrElse(identity.colorIndex) { palette.foreground }
  Box(
    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(color),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      identity.monogram,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = palette.screen,
    )
  }
}

private fun bottomPadding() = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
