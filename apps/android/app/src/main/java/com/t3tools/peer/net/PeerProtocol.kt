package com.t3tools.peer.net

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire details of the T3 Code RPC protocol: Effect's RPC carried over a
 * WebSocket as JSON. One socket message is one frame.
 */
object PeerProtocol {
  const val ORCHESTRATION_PROTOCOL_VERSION = 2

  const val METHOD_SERVER_GET_CONFIG = "server.getConfig"
  const val METHOD_SUBSCRIBE_SHELL = "orchestration.subscribeShell"
  const val METHOD_SUBSCRIBE_THREAD = "orchestration.subscribeThread"
  const val METHOD_DISPATCH_COMMAND = "orchestration.dispatchCommand"
  const val METHOD_PERSIST_ATTACHMENTS = "assets.persistChatAttachments"
  const val METHOD_VCS_LIST_REFS = "vcs.listRefs"
  const val METHOD_VCS_SWITCH_REF = "vcs.switchRef"

  fun request(id: Int, tag: String, payload: JSONObject): JSONObject =
    JSONObject()
      .put("_tag", "Request")
      .put("id", id)
      .put("tag", tag)
      .put("payload", payload)
      .put("headers", JSONArray())

  fun ack(requestId: Int): JSONObject =
    JSONObject().put("_tag", "Ack").put("requestId", requestId)

  /**
   * What a thread subscription starts with. A thread that has been worked on
   * for days carries megabytes of history, and a phone cannot carry that over
   * the air and read it, so the app asks for the bounded window the host keeps
   * for a fresh client: the last turns, the running work and the messages the
   * queue holds. The web app asks for the same window.
   */
  fun threadSubscription(threadId: String): JSONObject =
    JSONObject()
      .put("threadId", threadId)
      .put("acceptBoundedSnapshot", true)
      .put("acceptDeferredWork", true)
      .put("acceptCompactTurnItems", true)

  fun pong(): JSONObject = JSONObject().put("_tag", "Pong")

  fun ping(): JSONObject = JSONObject().put("_tag", "Ping")

  fun interrupt(requestId: Int): JSONObject =
    JSONObject().put("_tag", "Interrupt").put("requestId", requestId)
}
