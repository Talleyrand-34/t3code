package com.t3tools.peer.net

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PeerException(message: String) : IOException(message)

/**
 * The two calls a direct connection needs before its socket opens: trade the
 * pairing code for an access token, then trade that for a WebSocket ticket.
 * Neither goes through a cloud service.
 */
class PeerAuth(private val http: OkHttpClient) {
  suspend fun accessToken(endpoint: PeerEndpoint): String = withContext(Dispatchers.IO) {
    if (endpoint.code.isBlank()) {
      throw PeerException("Enter the pairing code from `t3 pair` on the host.")
    }
    val body = FormBody.Builder()
      .add("grant_type", GRANT_TYPE)
      .add("subject_token", endpoint.code)
      .add("subject_token_type", BOOTSTRAP_TOKEN_TYPE)
      .add("requested_token_type", ACCESS_TOKEN_TYPE)
      .add("client_device_type", "mobile")
      .add("client_label", "T3 Peer")
      .build()
    val request = Request.Builder()
      .url(endpoint.httpBaseUrl + "/oauth/token")
      .post(body)
      .build()
    http.newCall(request).execute().use { response ->
      val text = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw PeerException(failureMessage(response.code, text))
      }
      val token = JSONObject(text).optString("access_token")
      if (token.isBlank()) {
        throw PeerException("The server did not return an access token.")
      }
      token
    }
  }

  /** Asks for a socket ticket. Returns null when the session is no longer valid. */
  suspend fun websocketTicket(endpoint: PeerEndpoint, accessToken: String): String? =
    withContext(Dispatchers.IO) {
      ticket(endpoint, accessToken, "/api/auth/websocket-ticket/rpc")
        ?: ticket(endpoint, accessToken, "/api/auth/websocket-ticket")
    }

  private fun ticket(endpoint: PeerEndpoint, accessToken: String, path: String): String? {
    val request = Request.Builder()
      .url(endpoint.httpBaseUrl + path)
      .header("authorization", "Bearer $accessToken")
      .post(ByteArray(0).toRequestBody())
      .build()
    http.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val text = response.body?.string().orEmpty()
      if (text.isBlank()) return null
      return JSONObject(text).optString("ticket").takeIf { it.isNotBlank() }
    }
  }

  private fun failureMessage(status: Int, body: String): String {
    val fromBody = runCatching {
      val json = JSONObject(body)
      sequenceOf("message", "error_description", "error")
        .map { json.optString(it) }
        .firstOrNull { it.isNotBlank() }
    }.getOrNull()
    return fromBody ?: "The server refused the pairing code (HTTP $status)."
  }

  private companion object {
    const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange"
    const val BOOTSTRAP_TOKEN_TYPE = "urn:t3:params:oauth:token-type:environment-bootstrap"
    const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
  }
}
