package expo.modules.t3voicetranscription

import java.util.Locale

internal class VoiceTranscriptionException(
  val code: String,
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)

// ML Kit reports every mode UNAVAILABLE for tags with Unicode extensions, which Android adds
// for regional preferences such as the temperature unit (en-US-u-mu-celsius).
internal fun speechLocale(systemLocale: Locale): Locale = systemLocale.stripExtensions()

/**
 * On-device transcription is not part of this build.
 *
 * The speech model is delivered by Google Play Services, so a build without Play Services has
 * no local engine. Recording still works, and the environment and cloud destinations transcribe
 * the recording on a server instead.
 */
internal fun isLocalVoiceTranscriptionSupported(): Boolean = false

internal fun prepareLocalVoiceTranscription(): Int =
  throw VoiceTranscriptionException(
    "unavailable",
    "On-device voice transcription is not available in this build. Use an environment or cloud destination."
  )

internal fun localVoiceTranscriptionUnavailable(): Nothing =
  throw VoiceTranscriptionException(
    "unavailable",
    "On-device voice transcription is not available in this build. Use an environment or cloud destination."
  )
