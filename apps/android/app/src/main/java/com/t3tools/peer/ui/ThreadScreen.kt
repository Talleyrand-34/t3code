package com.t3tools.peer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t3tools.peer.model.AgentApproval
import com.t3tools.peer.model.AgentQuestion
import com.t3tools.peer.model.AgentQuestionOption
import com.t3tools.peer.model.ChatLine
import com.t3tools.peer.model.ChatRole
import com.t3tools.peer.model.ComposerDispatch
import com.t3tools.peer.model.FollowUp
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadScreen(viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  val listState = rememberLazyListState()
  val lines = viewModel.lines
  val imeVisible = WindowInsets.isImeVisible

  // The system photo picker hands back a photo the app may read.
  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let { viewModel.attachPhoto(it) }
    }

  // The system file picker hands back a file the app may read.
  val filePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let { viewModel.attachFile(it) }
    }

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
      ThreadStatus(
        viewModel = viewModel,
      )
      SubagentList(viewModel.subagents)
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(lines, key = { it.id }) { line ->
          LineRow(line)
          line.attachments.forEach { attachment ->
            if (!line.sending && attachment.mimeType.startsWith("image/")) {
              AttachmentImage(attachment = attachment, viewModel = viewModel)
            } else {
              AttachmentName(attachment.name)
            }
          }
          line.question?.let { question ->
            QuestionCard(
              question = question,
              answered = question.requestId in viewModel.answeredRequestIds,
              onChoose = { option -> viewModel.chooseOption(question, option) },
            )
          }
          line.approval?.let { approval ->
            ApprovalCard(
              approval = approval,
              answered = approval.requestId in viewModel.answeredRequestIds,
              onDecide = { decision -> viewModel.respondToApproval(decision) },
            )
          }
        }
      }
      val pendingPhoto = viewModel.pendingPhoto
      val pendingFile = viewModel.pendingFile
      val question = viewModel.pendingQuestion
      val approval = viewModel.pendingApproval
      if (question != null) {
        Composer(
          value = viewModel.questionDraft,
          onValueChange = { viewModel.updateQuestionDraft(it) },
          onSend = { viewModel.answerQuestion() },
          placeholder = "Answer the agent",
          sendEnabled = viewModel.questionDraft.isNotBlank(),
        )
      } else {
        Composer(
          value = viewModel.composer,
          onValueChange = { viewModel.updateComposer(it) },
          onSend = { alternate -> viewModel.send(alternate) },
          mode = viewModel.sendMode,
          onStop = viewModel::stopRun,
          onPickPhoto = { photoPicker.launch(PickVisualMediaRequest()) },
          onPickFile = { filePicker.launch(arrayOf("*/*")) },
          pendingPhotoName = pendingPhoto?.name,
          onClearPhoto = viewModel::clearPendingPhoto,
          pendingFileName = pendingFile?.name,
          onClearFile = viewModel::clearPendingFile,
        )
      }
    }
  }
  if (viewModel.branchSheetVisible) {
    BranchSheet(viewModel)
  }
  if (viewModel.queueSheetVisible) {
    QueuedMessagesSheet(viewModel)
  }
}

/**
 * What the thread is doing, above the chat.
 *
 * The strip says what the agent is doing, how many messages wait, and gives the
 * two controls that change it: stopping the run, and letting the queue go.
 */
@Composable
private fun ThreadStatus(viewModel: PeerViewModel) {
  val palette = LocalPeerPalette.current
  val queued = viewModel.queuedRuns.size
  val held = viewModel.queueIsHeld
  val working = viewModel.isBusy
  if (!working && queued == 0 && !held) return
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (working) {
      StatusChip(text = "Working", color = palette.primary) { viewModel.stopRun() }
    }
    if (held) {
      StatusChip(text = "Queue paused", color = palette.warning) { viewModel.resumeQueue() }
    } else if (queued > 0) {
      StatusChip(text = "$queued queued", color = palette.muted) { viewModel.showQueue() }
    }
  }
}

/** A small button that says what the thread is doing. */
@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
  val palette = LocalPeerPalette.current
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = color,
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(palette.grouped)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

/** The name of a file that is going with a message. */
@Composable
private fun AttachmentName(name: String) {
  val palette = LocalPeerPalette.current
  Text(
    name,
    style = MaterialTheme.typography.labelMedium,
    color = palette.muted,
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(palette.grouped)
        .padding(horizontal = 10.dp, vertical = 6.dp),
  )
}

/**
 * The question the agent asked, with the options it offered. The answer goes back
 * on the request the question belongs to.
 */
@Composable
private fun QuestionCard(
  question: AgentQuestion,
  answered: Boolean,
  onChoose: (AgentQuestionOption) -> Unit,
) {
  val palette = LocalPeerPalette.current
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(palette.card)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (question.header.isNotEmpty()) {
      Text(
        question.header,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = palette.primary,
      )
    }
    Text(question.question, fontSize = 15.sp, color = palette.foreground)
    question.options.forEach { option ->
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.grouped)
            .clickable(enabled = !answered) { onChoose(option) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(option.label, fontSize = 14.sp, color = palette.foreground)
          if (option.description.isNotEmpty()) {
            Text(
              option.description,
              style = MaterialTheme.typography.labelMedium,
              color = palette.muted,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
    if (answered) {
      Text(
        "Answered",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
    }
  }
}

/**
 * The permission the agent asked for. The answer goes back on the request it
 * belongs to, so the agent reads it where it asked.
 */
@Composable
private fun ApprovalCard(
  approval: AgentApproval,
  answered: Boolean,
  onDecide: (String) -> Unit,
) {
  val palette = LocalPeerPalette.current
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(palette.card)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      if (approval.appName.isEmpty()) "The agent asked for approval." else "${approval.appName} asked for approval.",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = palette.primary,
    )
    if (approval.prompt.isNotEmpty()) {
      Text(approval.prompt, fontSize = 15.sp, color = palette.foreground)
    }
    if (approval.reason.isNotEmpty()) {
      Text(
        approval.reason,
        style = MaterialTheme.typography.bodySmall,
        color = palette.muted,
      )
    }
    approval.options.forEach { option ->
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.grouped)
            .clickable(enabled = !answered) { onDecide(option.decision) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(option.label, fontSize = 14.sp, color = palette.foreground)
          if (option.warning.isNotEmpty()) {
            Text(
              option.warning,
              style = MaterialTheme.typography.labelMedium,
              color = palette.warning,
            )
          }
        }
      }
    }
    if (answered) {
      Text(
        "Answered",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
      )
    }
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
        if (line.pending || line.sending) {
          // The phone has the message, or the server has it but no run started it.
          Text(
            if (line.sending) "Sending" else "Queued",
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(end = 4.dp, bottom = 2.dp),
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (line.pending || line.sending) palette.grouped else palette.userBubble)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          MarkdownText(
            text = line.text,
            color = if (line.pending || line.sending) palette.muted else palette.onUserBubble,
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

/**
 * The composer.
 *
 * While the agent is working, the send button says what the message will do: it
 * waits for the turn to finish, or it reaches the agent right away. Holding the
 * button picks the other one for this message alone.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun Composer(
  value: String,
  onValueChange: (String) -> Unit,
  onSend: (alternate: Boolean) -> Unit,
  mode: ComposerDispatch = ComposerDispatch.AUTO,
  onStop: (() -> Unit)? = null,
  placeholder: String = "Ask the agent, or run a command",
  sendEnabled: Boolean = value.isNotBlank(),
  onPickPhoto: (() -> Unit)? = null,
  onPickFile: (() -> Unit)? = null,
  pendingPhotoName: String? = null,
  onClearPhoto: (() -> Unit)? = null,
  pendingFileName: String? = null,
  onClearFile: (() -> Unit)? = null,
) {
  val palette = LocalPeerPalette.current
  val haptics = LocalHapticFeedback.current
  var menuOpen by remember { mutableStateOf(false) }
  val busy = mode != ComposerDispatch.AUTO
  // An empty composer with a run working offers the stop, the way the official app does.
  val showStop = onStop != null && value.isBlank() && busy
  Surface(color = palette.screen) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          // The keyboard takes the bottom of the screen, so the composer sits
          // on top of it and the chat above that.
          .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
          .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      if (pendingPhotoName != null && onClearPhoto != null) {
        PendingPhoto(
          name = pendingPhotoName,
          onRemove = onClearPhoto,
          modifier = Modifier.padding(bottom = 6.dp),
        )
      }
      if (pendingFileName != null && onClearFile != null) {
        PendingPhoto(
          name = pendingFileName,
          onRemove = onClearFile,
          modifier = Modifier.padding(bottom = 6.dp),
        )
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
        if (onPickPhoto != null) {
          IconButton(onClick = onPickPhoto) {
            Icon(
              Icons.Filled.AddAPhoto,
              contentDescription = "Attach a photo",
              tint = palette.muted,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        if (onPickFile != null) {
          IconButton(onClick = onPickFile) {
            Icon(
              Icons.Filled.AttachFile,
              contentDescription = "Attach a file",
              tint = palette.muted,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
          if (value.isEmpty()) {
            Text(placeholder, fontSize = 15.sp, color = palette.muted)
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
        if (busy && !showStop) {
          Text(
            FollowUp.label(mode),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(end = 6.dp, bottom = 10.dp),
          )
        }
        if (showStop) {
          Box(
            modifier =
              Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(palette.danger)
                .clickable(onClick = onStop),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Filled.Stop,
              contentDescription = "Stop the agent",
              tint = androidx.compose.ui.graphics.Color.White,
              modifier = Modifier.size(18.dp),
            )
          }
        } else {
          Box(
            modifier =
              Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (sendEnabled) palette.primary else palette.grouped)
                .combinedClickable(
                  enabled = sendEnabled,
                  onClick = { onSend(false) },
                  onLongClick = {
                    if (busy) {
                      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                      menuOpen = true
                    }
                  },
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = if (sendEnabled) palette.onPrimary else palette.muted,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      if (menuOpen) {
        val other = if (mode == ComposerDispatch.QUEUE) ComposerDispatch.STEER else ComposerDispatch.QUEUE
        DropdownMenu(expanded = true, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = {
              Column {
                Text(FollowUp.label(other), style = MaterialTheme.typography.bodyMedium)
                Text(
                  FollowUp.description(other),
                  style = MaterialTheme.typography.labelSmall,
                  color = palette.muted,
                )
              }
            },
            onClick = {
              menuOpen = false
              onSend(true)
            },
          )
        }
      }
    }
  }
}
