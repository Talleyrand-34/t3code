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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.QueuedRun

/**
 * The messages that wait for a turn.
 *
 * Each one can be sent on its own, changed, or taken out. The queue stays held
 * after a stop, so the sheet says so and offers to let it go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueuedMessagesSheet(viewModel: PeerViewModel) {
  val palette = LocalPeerPalette.current
  val runs = viewModel.queuedRuns
  ModalBottomSheet(
    onDismissRequest = viewModel::hideQueue,
    containerColor = palette.screen,
    contentColor = palette.foreground,
  ) {
    PeerSystemBars(LocalPeerDark.current)
    Column(
      modifier =
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        "Queued",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
      )
      if (viewModel.queueIsHeld) {
        Text(
          "The queue is paused, so nothing starts on its own.",
          style = MaterialTheme.typography.bodyMedium,
          color = palette.muted,
        )
        Text(
          "Resume queue",
          style = MaterialTheme.typography.bodyMedium,
          color = palette.primary,
          modifier = Modifier.clickable { viewModel.resumeQueue() }.padding(vertical = 4.dp),
        )
        HorizontalDivider(color = palette.border)
      }
      if (runs.isEmpty()) {
        Text(
          "No messages are waiting.",
          style = MaterialTheme.typography.bodyMedium,
          color = palette.muted,
          modifier = Modifier.padding(vertical = 12.dp),
        )
      }
      runs.forEach { run ->
        QueuedMessageRow(
          run = run,
          text = viewModel.queuedText(run),
          canSendNow = viewModel.activeRun == null,
          onSendNow = { viewModel.sendQueuedNow(run) },
          onRemove = { viewModel.cancelQueuedRun(run) },
          onEdit = { text -> viewModel.editQueuedRun(run, text) },
        )
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

/** One message that waits, with the things the user can do to it. */
@Composable
private fun QueuedMessageRow(
  run: QueuedRun,
  text: String,
  canSendNow: Boolean,
  onSendNow: () -> Unit,
  onRemove: () -> Unit,
  onEdit: (String) -> Unit,
) {
  val palette = LocalPeerPalette.current
  var editing by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf(text) }
  Column(
    modifier =
      Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (editing) {
      BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.foreground),
        cursorBrush = SolidColor(palette.primary),
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
          "Save",
          style = MaterialTheme.typography.labelLarge,
          color = palette.primary,
          modifier = Modifier.clickable {
            editing = false
            onEdit(draft)
          },
        )
        Text(
          "Cancel",
          style = MaterialTheme.typography.labelLarge,
          color = palette.muted,
          modifier = Modifier.clickable { editing = false },
        )
      }
    } else {
      Text(
        text.ifEmpty { "Queued message" },
        fontSize = 15.sp,
        color = palette.foreground,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
          if (canSendNow) "Send now" else "Send into the turn",
          style = MaterialTheme.typography.labelLarge,
          color = palette.primary,
          modifier = Modifier.clickable(onClick = onSendNow).padding(vertical = 2.dp),
        )
        Text(
          "Edit",
          style = MaterialTheme.typography.labelLarge,
          color = palette.foreground,
          modifier = Modifier.clickable { editing = true }.padding(vertical = 2.dp),
        )
        Text(
          "Remove",
          style = MaterialTheme.typography.labelLarge,
          color = palette.danger,
          modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 2.dp),
        )
      }
    }
  }
}
