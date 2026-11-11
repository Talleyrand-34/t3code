package com.t3tools.peer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  val listState = rememberLazyListState()
  val lines = viewModel.lines
  val imeVisible = WindowInsets.isImeVisible

  // The system back button leaves the thread for the list, like the arrow does.
  BackHandler { viewModel.backToThreads() }

  LaunchedEffect(lines.size, imeVisible) {
    if (lines.isEmpty()) return@LaunchedEffect
    // The list measures the new rows after this effect starts, so wait for
    // them before asking for the last one.
    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= lines.size }
    // The keyboard takes half the screen, so keep the newest line in view.
    listState.scrollToItem(lines.lastIndex)
  }

  Surface(modifier = modifier.fillMaxSize(), color = palette.screen) {
    Column(modifier = Modifier.fillMaxSize()) {
      ThreadHeader(
        title = viewModel.openThread?.title ?: "Thread",
        subtitle = viewModel.openThread?.projectTitle.orEmpty(),
        branch = viewModel.openThread?.branch.orEmpty(),
        // Back leaves the thread for the list, the way it always did.
        onBack = viewModel::backToThreads,
        onBranch = viewModel::showBranches,
      )
      SubagentList(viewModel.subagents)
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(lines, key = { it.id }) { line -> LineRow(line) }
      }
      Composer(
        value = viewModel.composer,
        onValueChange = { viewModel.composer = it },
        onSend = viewModel::send,
      )
    }
  }
  if (viewModel.branchSheetVisible) {
    BranchSheet(viewModel)
  }
}

@Composable
private fun ThreadHeader(
  title: String,
  subtitle: String,
  branch: String,
  onBack: () -> Unit,
  onBranch: () -> Unit,
) {
  val palette = LocalPeerPalette.current
  Row(
    modifier =
      Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = palette.foreground,
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = palette.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle.isNotEmpty()) {
        Text(
          subtitle,
          style = MaterialTheme.typography.labelMedium,
          color = palette.muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (branch.isNotEmpty()) {
      Text(
        branch,
        style = MaterialTheme.typography.labelMedium,
        color = palette.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onBranch).padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun LineRow(line: ChatLine) {
  val palette = LocalPeerPalette.current
  when (line.role) {
    ChatRole.USER ->
      Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (line.pending) {
          // The server has it, but no run has started it yet.
          Text(
            "Queued",
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(end = 4.dp, bottom = 2.dp),
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (line.pending) palette.grouped else palette.userBubble)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          MarkdownText(
            text = line.text,
            color = if (line.pending) palette.muted else palette.onUserBubble,
            fontSizeSp = 15f,
            lineHeightSp = 21f,
            codeBackground = palette.grouped,
          )
        }
      }

    ChatRole.ASSISTANT ->
      MarkdownText(
        text = line.text,
        color = palette.foreground,
        codeBackground = palette.grouped,
      )

    ChatRole.SYSTEM ->
      Text(
        line.text,
        fontSize = 13.sp,
        color = palette.muted,
        modifier = Modifier.fillMaxWidth(),
      )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
  val palette = LocalPeerPalette.current
  Surface(color = palette.screen) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          // The keyboard takes the bottom of the screen, so the composer sits
          // on top of it and the chat above that.
          .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
          .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .background(palette.composer)
          .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
          if (value.isEmpty()) {
            Text(
              "Ask the agent, or run a command",
              fontSize = 15.sp,
              color = palette.muted,
            )
          }
          BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = palette.foreground),
            cursorBrush = SolidColor(palette.primary),
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (value.isBlank()) palette.grouped else palette.primary)
            .clickable(enabled = value.isNotBlank(), onClick = onSend),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            tint = if (value.isBlank()) palette.muted else palette.onPrimary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
