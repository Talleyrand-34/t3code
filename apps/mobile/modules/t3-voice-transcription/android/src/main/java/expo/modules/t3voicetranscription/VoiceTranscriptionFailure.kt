package expo.modules.t3voicetranscription

import kotlinx.coroutines.CancellationException

internal fun voiceTranscriptionErrorCode(
  error: Throwable,
  fallback: String
): String = when (error) {
  is CancellationException -> "cancelled"
  is VoiceTranscriptionException -> error.code
  else -> fallback
}
