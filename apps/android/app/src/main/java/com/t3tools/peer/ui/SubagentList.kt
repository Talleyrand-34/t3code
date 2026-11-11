package com.t3tools.peer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.t3tools.peer.model.SubagentSummary

/**
 * The other agents working inside this thread. A subagent is not a thread of
 * its own here: it belongs to the thread that started it.
 */
@Composable
fun SubagentList(subagents: List<SubagentSummary>) {
  if (subagents.isEmpty()) return
  val palette = LocalPeerPalette.current
  Column(
    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      "Subagents",
      style = MaterialTheme.typography.labelMedium,
      color = palette.muted,
    )
    subagents.forEach { subagent ->
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier.size(7.dp).clip(CircleShape).background(subagentStatusColor(subagent, palette)),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            subagent.title.ifEmpty { "Subagent" },
            style = MaterialTheme.typography.bodyMedium,
            color = palette.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            subagentStatusLabel(subagent),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
          )
        }
        val result = subagent.result
        if (result.isNotEmpty() && subagentIsFinished(subagent)) {
          Text(
            result,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 15.dp, top = 2.dp),
          )
        }
      }
    }
  }
}

/** The colour a subagent's dot takes, so a finished one reads as done. */
private fun subagentStatusColor(subagent: SubagentSummary, palette: PeerPalette): Color =
  when (subagent.status) {
    "running", "starting", "preparing" -> palette.primary
    "waiting", "pending" -> palette.warning
    "failed" -> palette.danger
    else -> palette.muted
  }
