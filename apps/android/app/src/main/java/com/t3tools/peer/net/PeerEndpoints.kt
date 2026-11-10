package com.t3tools.peer.net

/** Where a T3 Code server lives and the one-time code that pairs with it. */
data class PeerEndpoint(
  val httpBaseUrl: String,
  val wsBaseUrl: String,
  val code: String,
)

/**
 * Reads what the user typed. A pairing link carries the code in its fragment,
 * so `http://host:3923/#token=abc` is enough on its own.
 */
object PeerEndpoints {
  fun parse(serverInput: String, codeInput: String): PeerEndpoint? {
    val raw = serverInput.trim()
    if (raw.isEmpty()) return null

    val hashIndex = raw.indexOf('#')
    val base = if (hashIndex >= 0) raw.substring(0, hashIndex) else raw
    val fragment = if (hashIndex >= 0) raw.substring(hashIndex + 1) else ""

    val codeFromFragment = fragment
      .split('&')
      .firstOrNull { it.startsWith("token=") }
      ?.removePrefix("token=")
      ?.trim()
      .orEmpty()
    val code = codeInput.trim().ifEmpty { codeFromFragment }

    val httpBaseUrl = normalizeBase(base) ?: return null
    return PeerEndpoint(
      httpBaseUrl = httpBaseUrl,
      wsBaseUrl = httpBaseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://"),
      code = code,
    )
  }

  private fun normalizeBase(base: String): String? {
    val trimmed = base.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val authority = withScheme.substringAfter("://").substringBefore('/')
    if (authority.isEmpty()) return null
    return "$scheme://$authority"
  }
}
