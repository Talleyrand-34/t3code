package expo.modules.t3voicetranscription

internal interface PcmOutput : AutoCloseable {
  suspend fun write(buffer: ByteArray, count: Int)
  fun finish()
  fun verifyConsumed()
}

internal inline fun requireCompleteAudio(condition: Boolean, message: () -> String) {
  if (!condition) throw VoiceTranscriptionException("incomplete-audio", message())
}
