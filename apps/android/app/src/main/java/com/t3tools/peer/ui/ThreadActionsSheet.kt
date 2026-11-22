package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.ThreadSummary

/**
 * What the user can do with a thread from the list.
 *
 * Holding a row opens this, the way a long press opens a menu on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadActionsSheet(
  thread: ThreadSummary,
  onDismiss: () -> Unit,
  onArchive: () -> Unit,
  onRename: (String) -> Unit,
  onSnooze: () -> Unit,
  onMarkUnread: () -> Unit,
  onDelete: () -> Unit,
) {
  val palette = LocalPeerPalette.current
  var renaming by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf(thread.title) }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = palette.screen,
    contentColor = palette.foreground,
  ) {
    PeerSystemBars(LocalPeerDark.current)
    Column(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        thread.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
      )
      if (renaming) {
        BasicTextField(
          value = title,
          onValueChange = { title = it },
          singleLine = true,
          textStyle =
            androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.foreground),
          cursorBrush = SolidColor(palette.primary),
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(palette.grouped)
              .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          Text(
            "Save",
            style = MaterialTheme.typography.labelLarge,
            color = palette.primary,
            modifier = Modifier.clickable { onRename(title) },
          )
          Text(
            "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = palette.muted,
            modifier = Modifier.clickable { renaming = false },
          )
        }
      } else {
        ActionRow("Rename") { renaming = true }
        ActionRow("Put away") { onArchive() }
        ActionRow("Sleep for an hour") { onSnooze() }
        ActionRow("Mark unread") { onMarkUnread() }
        ActionRow("Delete", danger = true) { onDelete() }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

/** One thing the user can do with the thread. */
@Composable
private fun ActionRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
  val palette = LocalPeerPalette.current
  Text(
    label,
    style = MaterialTheme.typography.bodyLarge,
    color = if (danger) palette.danger else palette.foreground,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
  )
}
