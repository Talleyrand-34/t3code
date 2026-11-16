package com.t3tools.peer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import com.t3tools.peer.model.LocalAttachment
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  val listState = rememberLazyListState()
  val lines = viewModel.lines
  val pending = viewModel.pendingLines
  val imeVisible = WindowInsets.isImeVisible
  val context = LocalContext.current
  // The picker gives back a stream the app can read; the image itself goes up
  // with the message the user sends.
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      viewModel.readAttachment(context, uri)
    }

  // The system back button leaves the thread for the list, like the arrow does.
  BackHandler { viewModel.backToThreads() }

  LaunchedEffect(lines.size, pending.size, imeVisible) {
    // The waiting messages sit after the chat, so the newest thing is either
    // the last answer or the first message that waits.
    val rows = lines.size + if (pending.isEmpty()) 0 else pending.size + 1
    if (rows == 0) return@LaunchedEffect
    // The list measures the new rows after this effect starts, so wait for
    // them before asking for the last one.
    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= rows }
    // The keyboard takes half the screen, so keep the newest line in view.
    listState.scrollToItem((rows - 1).coerceAtLeast(0))
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
        showSubagents = viewModel.showSubagents,
        onToggleSubagents = viewModel::toggleSubagents,
      )
      if (viewModel.showSubagents) {
        SubagentList(viewModel.subagents)
      }
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(lines, key = { it.id }) { line -> LineRow(line) }
        if (pending.isNotEmpty()) {
          item(key = "pending-header") { PendingHeader(pending.size) }
          items(pending, key = { it.id }) { line -> PendingRow(line) }
        }
      }
      Composer(
        value = viewModel.composer,
        onValueChange = viewModel::onComposerChange,
        onSend = viewModel::send,
        attachments = viewModel.attachments,
        onRemoveAttachment = viewModel::removeAttachment,
        onAttach = { picker.launch("image/*") },
        onStop = viewModel::stop,
        canStop = viewModel.activeRunId != null,
        canContinue = viewModel.resumableRunId != null || viewModel.holdsQueuedRuns,
        onContinue = viewModel::continueThread,
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
  showSubagents: Boolean,
  onToggleSubagents: () -> Unit,
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
    // The agents inside the thread are a detail of it, so they can be put away.
    IconButton(onClick = onToggleSubagents) {
      Icon(
        if (showSubagents) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
        contentDescription = if (showSubagents) "Hide subagents" else "Show subagents",
        tint = if (showSubagents) palette.muted else palette.foreground,
      )
    }
  }
}

/**
 * The messages the server holds but has not run yet. They wait at the end of
 * the chat, apart from the turn items, so it is clear that nothing has
 * answered them yet.
 */
@Composable
private fun PendingHeader(count: Int) {
  val palette = LocalPeerPalette.current
  Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Pending",
        style = MaterialTheme.typography.labelMedium,
        color = palette.warning,
      )
      Spacer(Modifier.width(6.dp))
      Text(
        "$count",
        style = MaterialTheme.typography.labelSmall,
        color = palette.muted,
      )
    }
    Text(
      "The server has these. No run has started them yet.",
      style = MaterialTheme.typography.labelSmall,
      color = palette.muted,
    )
  }
}

/** One waiting message, with the place it holds in the queue. */
@Composable
private fun PendingRow(line: ChatLine) {
  val palette = LocalPeerPalette.current
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Filled.Schedule,
        contentDescription = null,
        tint = palette.muted,
        modifier = Modifier.size(11.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        if (line.pendingPosition == 1) "Next" else "${line.pendingPosition} of ${line.pendingTotal}",
        style = MaterialTheme.typography.labelSmall,
        color = palette.muted,
      )
    }
    Spacer(Modifier.height(3.dp))
    Box(
      modifier =
        Modifier
          .clip(RoundedCornerShape(16.dp))
          .background(palette.grouped)
          .border(1.dp, palette.border, RoundedCornerShape(16.dp))
          .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      MarkdownText(
        text = line.text,
        color = palette.muted,
        fontSizeSp = 15f,
        lineHeightSp = 21f,
        codeBackground = palette.screen,
      )
    }
  }
}

@Composable
private fun LineRow(line: ChatLine) {
  val palette = LocalPeerPalette.current
  when (line.role) {
    ChatRole.USER ->
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.userBubble)
            .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        MarkdownText(
          text = line.text,
          color = palette.onUserBubble,
          fontSizeSp = 15f,
          lineHeightSp = 21f,
          codeBackground = palette.grouped,
        )
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
private fun Composer(
  value: String,
  onValueChange: (String) -> Unit,
  onSend: () -> Unit,
  attachments: List<LocalAttachment>,
  onRemoveAttachment: (LocalAttachment) -> Unit,
  onAttach: () -> Unit,
  onStop: () -> Unit,
  canStop: Boolean,
  canContinue: Boolean,
  onContinue: () -> Unit,
) {
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
      if (attachments.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          attachments.forEach { attachment ->
            Row(
              modifier =
                Modifier.clip(RoundedCornerShape(10.dp))
                  .background(palette.grouped)
                  .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                attachment.name,
                style = MaterialTheme.typography.labelSmall,
                color = palette.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              IconButton(onClick = { onRemoveAttachment(attachment) }) {
                Icon(
                  Icons.Filled.Close,
                  contentDescription = "Remove ${attachment.name}",
                  tint = palette.muted,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.composer)
            .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        IconButton(onClick = onAttach) {
          Icon(
            Icons.Filled.AttachFile,
            contentDescription = "Attach files",
            tint = palette.muted,
            modifier = Modifier.size(18.dp),
          )
        }
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
        when {
          canStop ->
            Box(
              modifier =
                Modifier.size(38.dp).clip(CircleShape).background(palette.danger).clickable(onClick = onStop),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Filled.Stop,
                contentDescription = "Stop",
                tint = palette.screen,
                modifier = Modifier.size(18.dp),
              )
            }

          canContinue ->
            Box(
              modifier =
                Modifier.size(38.dp).clip(CircleShape).background(palette.primary).clickable(onClick = onContinue),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Continue",
                tint = palette.onPrimary,
                modifier = Modifier.size(18.dp),
              )
            }

          else ->
            Box(
              modifier =
                Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(if (value.isBlank() && attachments.isEmpty()) palette.grouped else palette.primary)
                  .clickable(enabled = value.isNotBlank() || attachments.isNotEmpty(), onClick = onSend),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint =
                  if (value.isBlank() && attachments.isEmpty()) palette.muted else palette.onPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
        }
      }
    }
  }
}