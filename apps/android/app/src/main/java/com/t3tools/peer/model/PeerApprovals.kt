package com.t3tools.peer.model

import org.json.JSONObject

/**
 * A permission the agent asked for, and the answers it offered.
 */
data class AgentApproval(
  val requestId: String,
  /** What the agent wants to do, in the provider's words. */
  val prompt: String,
  /** Why the agent needs it, when it says. */
  val reason: String,
  /** The tool or app that wants the permission, when it names one. */
  val appName: String,
  val options: List<AgentApprovalOption>,
)

/** One of the answers the agent offered. */
data class AgentApprovalOption(
  /** The decision the server takes when this answer is picked. */
  val decision: String,
  val label: String,
  /** What picking it costs, when the provider says. */
  val warning: String = "",
)

object PeerApprovals {
  /** Let it happen once. */
  const val ACCEPT = "accept"

  /** Let it happen for the rest of this session. */
  const val ACCEPT_FOR_SESSION = "acceptForSession"

  /** Do not let it happen. */
  const val DECLINE = "decline"

  /**
   * Reads the approval out of a turn item.
   *
   * A provider that offers no answers of its own still gets the two answers
   * every provider understands, so the phone can always say yes or no.
   */
  fun from(item: JSONObject): AgentApproval? {
    val requestId = item.optString("requestId")
    if (requestId.isEmpty()) return null
    val options = mutableListOf<AgentApprovalOption>()
    val raw = item.optJSONArray("options")
    if (raw != null) {
      for (index in 0 until raw.length()) {
        val option = raw.optJSONObject(index) ?: continue
        val decision = option.optString("decision")
        if (decision.isEmpty()) continue
        options +=
          AgentApprovalOption(
            decision = decision,
            label = option.optString("label").ifEmpty { decisionLabel(decision) },
            warning = option.optString("warning"),
          )
      }
    }
    if (options.isEmpty()) {
      options += AgentApprovalOption(ACCEPT, decisionLabel(ACCEPT))
      options += AgentApprovalOption(DECLINE, decisionLabel(DECLINE))
    }
    return AgentApproval(
      requestId = requestId,
      prompt = item.optString("prompt"),
      reason = item.optString("reason"),
      appName = item.optString("appName"),
      options = options,
    )
  }

  /** The name a decision wears when the provider offers none of its own. */
  fun decisionLabel(decision: String): String =
    when (decision) {
      ACCEPT -> "Allow once"
      ACCEPT_FOR_SESSION -> "Allow session"
      "acceptAlways" -> "Always allow"
      DECLINE -> "Decline"
      "cancel" -> "Cancel"
      else -> decision
    }
}
