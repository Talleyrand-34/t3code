package com.t3tools.peer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.t3tools.peer.model.ThreadSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(viewModel.environmentLabel.ifEmpty { "T3 Code" })
            Text(
              "${viewModel.threads.size} threads",
              style = MaterialTheme.typography.labelSmall,
            )
          }
        },
        actions = {
          IconButton(onClick = viewModel::disconnect) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect")
          }
        },
      )
    },
  ) { padding ->
    if (viewModel.threads.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("No threads yet", style = MaterialTheme.typography.titleMedium)
        Text(
          "Threads appear here once the server has some.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      return@Scaffold
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
      items(viewModel.threads, key = { it.id }) { thread ->
        ThreadRow(thread) { viewModel.openThread(thread) }
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onOpen: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      thread.title,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row2(thread)
  }
}

@Composable
private fun Row2(thread: ThreadSummary) {
  androidx.compose.foundation.layout.Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      thread.projectTitle,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (thread.status.isNotEmpty() && thread.status != "idle") {
      Text(
        thread.status,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
