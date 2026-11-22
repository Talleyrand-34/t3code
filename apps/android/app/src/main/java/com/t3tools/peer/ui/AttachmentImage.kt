package com.t3tools.peer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.t3tools.peer.model.StoredAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The photos the chat has already fetched, so a scroll does not fetch them again.
 *
 * The cache is bounded, so a long chat cannot hold on to every photo it showed.
 */
private val loadedPhotos =
  object : LruCache<String, Bitmap>(PHOTO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
  }

private val photoClient = OkHttpClient()

/** How much the photo cache may hold. */
private const val PHOTO_CACHE_BYTES = 32 * 1024 * 1024

/**
 * A photo the user attached to a message.
 *
 * The address is short-lived, so it is minted when the photo comes into view, and the
 * picture itself is kept for as long as the app runs.
 */
@Composable
fun AttachmentImage(attachment: StoredAttachment, viewModel: PeerViewModel, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  var bitmap by remember(attachment.id) {
    mutableStateOf(loadedPhotos.get(attachment.id))
  }
  var failed by remember(attachment.id) { mutableStateOf(false) }

  LaunchedEffect(attachment.id) {
    if (bitmap != null) return@LaunchedEffect
    val url = viewModel.attachmentUrl(attachment) ?: return@LaunchedEffect
    val image =
      withContext(Dispatchers.IO) {
        runCatching {
          photoClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bytes = response.body?.bytes() ?: return@runCatching null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          }
        }.getOrNull()
      }
    if (image == null) {
      failed = true
    } else {
      loadedPhotos.put(attachment.id, image)
      bitmap = image
    }
  }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(palette.grouped),
    contentAlignment = Alignment.Center,
  ) {
    when {
      bitmap != null ->
        Image(
          bitmap = bitmap!!.asImageBitmap(),
          contentDescription = attachment.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().height(180.dp),
        )

      failed ->
        Text(
          "The photo could not be loaded.",
          style = MaterialTheme.typography.labelMedium,
          color = palette.muted,
          modifier = Modifier.padding(12.dp),
        )

      else ->
        Text(
          "Loading the photo…",
          style = MaterialTheme.typography.labelMedium,
          color = palette.muted,
          modifier = Modifier.padding(12.dp),
        )
    }
  }
}

/** A photo the user picked but has not sent yet. */
@Composable
fun PendingPhoto(name: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
  val palette = LocalPeerPalette.current
  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(palette.grouped)
        .clickable(onClick = onRemove)
        .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(
      "Photo: $name  ×",
      style = MaterialTheme.typography.labelMedium,
      color = palette.foreground,
    )
  }
}
