package com.t3tools.peer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val listState = rememberLazyListState()
  val lines = viewModel.lines

  LaunchedEffect(lines.size) {
    if (lines.isEmpty()) return@LaunchedEffect
    // The list measures the new rows after this effect starts, so wait for
    // them before asking for the last one.
    snapshotFlow { listState.layoutInfo.totalItemsCount }
      .first { it >= lines.size }
    listState.scrollToItem(lines.lastIndex)
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            viewModel.openThread?.title ?: "Thread",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(onClick = viewModel::backToThreads) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
    bottomBar = {
      Composer(
        value = viewModel.composer,
        onValueChange = { viewModel.composer = it },
        onSend = viewModel::send,
      )
    },
  ) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(lines) { line -> LineRow(line) }
    }
  }
}

@Composable
private fun LineRow(line: ChatLine) {
  val alignment = if (line.role == ChatRole.USER) Alignment.End else Alignment.Start
  val container =
    when (line.role) {
      ChatRole.USER -> MaterialTheme.colorScheme.primaryContainer
      ChatRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
      ChatRole.SYSTEM -> MaterialTheme.colorScheme.surface
    }
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
    Surface(color = container, shape = MaterialTheme.shapes.medium) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text(
          line.text,
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (line.role == ChatRole.SYSTEM) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
        )
        if (line.streaming) {
          Text(
            "…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
  Surface(tonalElevation = 3.dp) {
    androidx.compose.foundation.layout.Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Message") },
        maxLines = 4,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onSend, enabled = value.isNotBlank()) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
      }
    }
  }
}
