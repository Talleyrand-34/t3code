package com.t3tools.peer.model

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/**
 * A file the user attached to a message.
 *
 * The bytes travel to the server in one piece, so the phone reads them once and
 * keeps them until the server has them.
 */
data class PeerFile(
  val name: String,
  val mimeType: String,
  val sizeBytes: Int,
  val bytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is PeerFile &&
        name == other.name &&
        mimeType == other.mimeType &&
        sizeBytes == other.sizeBytes &&
        bytes.contentEquals(other.bytes))

  override fun hashCode(): Int =
    (name.hashCode() * 31 + mimeType.hashCode()) * 31 + sizeBytes
}

object PeerFiles {
  /** The server takes a file up to this size. */
  const val MAX_FILE_BYTES = 32 * 1024 * 1024

  /**
   * Reads a file the user picked.
   *
   * A file bigger than the server takes is refused here, so the phone does not
   * spend its data on a message the server will not accept.
   */
  fun prepare(uri: Uri, resolver: ContentResolver): PeerFile? {
    val name = displayName(uri, resolver) ?: return null
    val mimeType = resolver.getType(uri)?.ifEmpty { null } ?: guessMimeType(name)
    val bytes =
      resolver.openInputStream(uri)?.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
          val read = stream.read(buffer)
          if (read <= 0) break
          total += read
          if (total > MAX_FILE_BYTES) return null
          out.write(buffer, 0, read)
        }
        out.toByteArray()
      } ?: return null
    if (bytes.isEmpty()) return null
    return PeerFile(
      name = name,
      mimeType = mimeType,
      sizeBytes = bytes.size,
      bytes = bytes,
    )
  }

  /** The name the file carries, as the picker showed it. */
  private fun displayName(uri: Uri, resolver: ContentResolver): String? {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (index >= 0 && cursor.moveToFirst()) {
        val name = cursor.getString(index)?.trim()
        if (!name.isNullOrEmpty()) return name.take(120)
      }
    }
    val last = uri.lastPathSegment?.substringAfterLast('/')?.trim()
    return last?.takeIf { it.isNotEmpty() && it.contains('.') }?.take(120)
  }

  /** The kind of file, when the picker did not say. */
  private fun guessMimeType(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
      "txt", "log" -> "text/plain"
      "md" -> "text/markdown"
      "json" -> "application/json"
      "pdf" -> "application/pdf"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "zip" -> "application/zip"
      else -> "application/octet-stream"
    }
}
