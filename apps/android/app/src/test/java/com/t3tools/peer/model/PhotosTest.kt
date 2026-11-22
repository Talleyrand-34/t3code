package com.t3tools.peer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotosTest {
  @Test
  fun `a photo travels in the shape the server stores`() {
    val photo =
      PhotoAttachment(
        name = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048,
        dataUrl = "data:image/jpeg;base64,AAAA",
      )

    val attachment = photo.toStoredAttachment()

    assertEquals("image", attachment.getString("type"))
    assertEquals("photo.jpg", attachment.getString("name"))
    assertEquals("image/jpeg", attachment.getString("mimeType"))
    assertEquals(2048, attachment.getInt("sizeBytes"))
    assertEquals("data:image/jpeg;base64,AAAA", attachment.getString("dataUrl"))
  }

  @Test
  fun `the data url carries the mime type the server checks against`() {
    val photo =
      PhotoAttachment(
        name = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 4,
        dataUrl = "data:image/jpeg;base64,AAAA",
      )

    val declared = photo.toStoredAttachment().getString("mimeType")
    val inDataUrl = photo.dataUrl.removePrefix("data:").substringBefore(';')

    // The server refuses a photo whose data url and declared type disagree.
    assertEquals(inDataUrl, declared)
    assertTrue(declared.startsWith("image/"))
  }
}
