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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.ProjectChoice

/**
 * Starts a new thread.
 *
 * The thread runs the model and mode the project already uses, so the phone does
 * not need the whole provider list to start one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewThreadSheet(viewModel: PeerViewModel) {
  val palette = LocalPeerPalette.current
  ModalBottomSheet(
    onDismissRequest = viewModel::hideNewThread,
    containerColor = palette.screen,
    contentColor = palette.foreground,
  ) {
    PeerSystemBars(LocalPeerDark.current)
    Column(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        "New thread",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
      )
      if (viewModel.projects.size > 1) {
        ProjectChoiceRow(
          projects = viewModel.projects,
          selectedProjectId = viewModel.newThreadProjectId,
          onSelect = { viewModel.selectNewThreadProject(it) },
        )
      }
      BasicTextField(
        value = viewModel.newThreadText,
        onValueChange = { viewModel.updateNewThreadText(it) },
        textStyle =
          androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.foreground),
        cursorBrush = SolidColor(palette.primary),
        maxLines = 6,
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.grouped)
            .padding(horizontal = 12.dp, vertical = 10.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "Start",
          style = MaterialTheme.typography.labelLarge,
          color = if (viewModel.newThreadText.isBlank()) palette.muted else palette.primary,
          modifier =
            Modifier.clickable(enabled = viewModel.newThreadText.isNotBlank()) {
              viewModel.startNewThread()
            },
        )
        Text(
          "Cancel",
          style = MaterialTheme.typography.labelLarge,
          color = palette.muted,
          modifier = Modifier.clickable { viewModel.hideNewThread() },
        )
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

/** The project the new thread belongs to. */
@Composable
private fun ProjectChoiceRow(
  projects: List<ProjectChoice>,
  selectedProjectId: String?,
  onSelect: (String) -> Unit,
) {
  val palette = LocalPeerPalette.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    projects.forEach { project ->
      val selected = project.id == selectedProjectId
      Text(
        project.title,
        fontSize = 13.sp,
        color = if (selected) palette.onPrimary else palette.foreground,
        maxLines = 1,
        modifier =
          Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) palette.primary else palette.grouped)
            .clickable { onSelect(project.id) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}
