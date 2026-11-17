package com.t3tools.peer.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/**
 * A photo the user attached to a message.
 *
 * The photo travels as a data URL, which the server stores and hands back with an id.
 */
data class PhotoAttachment(
  val name: String,
  val mimeType: String,
  val sizeBytes: Int,
  /** The encoded photo, until the server has stored it. */
  val dataUrl: String,
) {
  /** The attachment the server stored, which carries the id the chat reads it by. */
  fun toStoredAttachment(): JSONObject = JSONObject().apply {
    put("type", "image")
    put("name", name)
    put("mimeType", mimeType)
    put("sizeBytes", sizeBytes)
    put("dataUrl", dataUrl)
  }
}

/** A photo that the server already stored. */
data class StoredAttachment(
  val id: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Int = 0,
)

object Photos {
  /** The server takes images up to this size. */
  const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

  /** The longest edge a photo is scaled to before it is sent. */
  const val MAX_IMAGE_EDGE = 1600

  /**
   * Reads a photo, scales it down and encodes it as a data URL the server accepts.
   *
   * The server takes an image up to 10 MiB, and a phone photo is usually bigger than that,
   * so the photo is scaled before it is encoded.
   */
  fun prepare(uri: Uri, resolver: android.content.ContentResolver): PhotoAttachment? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Decoding with bounds returns no bitmap, so only the stream itself decides failure.
    val stream = resolver.openInputStream(uri) ?: return null
    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val scaled = scale(bitmap, bounds.outWidth, bounds.outHeight)
    if (scaled != bitmap) bitmap.recycle()
    val encoded = encode(scaled) ?: return null
    scaled.recycle()
    val name = displayName(uri)
    return PhotoAttachment(
      name = name,
      mimeType = "image/jpeg",
      sizeBytes = encoded.size,
      dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(encoded, Base64.NO_WRAP),
    )
  }

  private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    var longEdge = maxOf(width, height)
    while (longEdge / 2 >= MAX_IMAGE_EDGE) {
      longEdge /= 2
      sample *= 2
    }
    return sample
  }

  private fun scale(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    val longEdge = maxOf(width, height)
    if (longEdge <= MAX_IMAGE_EDGE) return bitmap
    val ratio = MAX_IMAGE_EDGE.toFloat() / longEdge
    return Bitmap.createScaledBitmap(
      bitmap,
      (width * ratio).toInt().coerceAtLeast(1),
      (height * ratio).toInt().coerceAtLeast(1),
      true,
    )
  }

  private fun encode(bitmap: Bitmap): ByteArray? {
    var quality = 90
    var bytes = ByteArrayOutputStream()
    while (true) {
      bytes = ByteArrayOutputStream()
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)) return null
      if (bytes.size() <= MAX_IMAGE_BYTES || quality <= 40) break
      quality -= 15
    }
    return bytes.toByteArray()
  }

  private fun displayName(uri: Uri): String {
    val name = uri.lastPathSegment?.substringAfterLast('/')?.trim()
    return if (name.isNullOrEmpty() || !name.contains('.')) "photo.jpg" else name.take(120)
  }
}
