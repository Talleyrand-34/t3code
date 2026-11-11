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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The branches the host knows about. Picking one moves the checkout the
 * thread runs in; the host refuses that unless the session was granted the
 * source control scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchSheet(viewModel: PeerViewModel) {
  val palette = LocalPeerPalette.current
  ModalBottomSheet(
    onDismissRequest = viewModel::hideBranches,
    containerColor = palette.screen,
    contentColor = palette.foreground,
  ) {
    // The sheet is a window of its own, so its bars need telling as well.
    PeerSystemBars(LocalPeerDark.current)
    Column(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        "Branch",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        modifier = Modifier.padding(top = 4.dp),
      )
      if (viewModel.branches.isEmpty()) {
        Text(
          "No branches to show.",
          fontSize = 16.sp,
          color = palette.foreground,
          modifier = Modifier.padding(vertical = 8.dp),
        )
      }
      viewModel.branches.forEach { branch ->
        Row(
          modifier =
            Modifier.fillMaxWidth().clickable { viewModel.switchBranch(branch) }
              .padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            branch.name,
            fontSize = 16.sp,
            color = palette.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          if (branch.isRemote) {
            Spacer(Modifier.width(8.dp))
            Text(
              "remote",
              style = MaterialTheme.typography.labelSmall,
              color = palette.muted,
            )
          }
          if (branch.current) {
            Spacer(Modifier.width(8.dp))
            Box(
              modifier = Modifier.size(20.dp).clip(CircleShape).background(palette.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = palette.onPrimary,
                modifier = Modifier.size(13.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      HorizontalDivider(color = palette.border)
      Spacer(Modifier.height(6.dp))
      Text(
        "The branch is the checkout the thread runs in. Switching it needs a session " +
          "with the source control scope.",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        modifier = Modifier.padding(bottom = 12.dp),
      )
    }
  }
}
