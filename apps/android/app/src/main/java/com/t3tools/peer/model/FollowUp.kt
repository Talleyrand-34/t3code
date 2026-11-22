package com.t3tools.peer.model

/**
 * What happens to a message the user sends while the agent is still working.
 *
 * A queued message waits for the turn to finish; a steered message reaches the
 * agent right away. The server decides what a steer means, so a provider that
 * cannot steer takes the message as a queue instead of refusing it.
 */
enum class FollowUpBehavior(val storageValue: String) {
  QUEUE("queue"),
  STEER("steer");

  companion object {
    /** A message that reaches the agent right away is what most people want. */
    val DEFAULT = STEER

    /** A value we do not know falls back to the default. */
    fun fromStorage(value: String?): FollowUpBehavior =
      entries.firstOrNull { it.storageValue == value } ?: DEFAULT
  }
}

/** The mode one send uses. */
enum class ComposerDispatch {
  /** The server decides: start the turn, steer it, or queue the message. */
  AUTO,
  QUEUE,
  STEER,
}

/**
 * The decision the composer makes, and the wire fields that carry it.
 *
 * The official app and the web app share one rule: while nothing is running a
 * message starts a turn, a turn that cannot be steered takes a queue, and the
 * user's own choice decides the rest. The alternate action is the other one.
 */
object FollowUp {
  /**
   * The mode a send should use.
   *
   * @param running whether the thread has a run that is still working.
   * @param canSteer whether the running turn can take steering input. A
   *   compaction cannot be steered into, so it always takes a queue.
   * @param behavior the mode the user chose in the settings.
   * @param alternate whether the user asked for the other mode for this message.
   */
  fun resolve(
    running: Boolean,
    canSteer: Boolean = true,
    behavior: FollowUpBehavior = FollowUpBehavior.DEFAULT,
    alternate: Boolean = false,
  ): ComposerDispatch {
    if (!running) return ComposerDispatch.AUTO
    if (!canSteer) return ComposerDispatch.QUEUE
    val chosen =
      if (behavior == FollowUpBehavior.QUEUE) ComposerDispatch.QUEUE else ComposerDispatch.STEER
    if (!alternate) return chosen
    return if (chosen == ComposerDispatch.QUEUE) ComposerDispatch.STEER else ComposerDispatch.QUEUE
  }

  /**
   * The intent the server reads to decide how the message reaches the agent.
   *
   * A steer is sent as `auto`, the way the official app sends it, so a provider
   * that cannot steer takes the message as a queue instead of refusing it.
   */
  fun deliveryIntent(mode: ComposerDispatch): String? =
    when (mode) {
      ComposerDispatch.AUTO -> "auto"
      ComposerDispatch.QUEUE -> null
      ComposerDispatch.STEER -> "auto"
    }

  /** The mode the server starts the message with. */
  fun dispatchMode(mode: ComposerDispatch): String =
    when (mode) {
      ComposerDispatch.QUEUE -> "queue_after_active"
      else -> "start_immediately"
    }

  /** The label the send button wears while the agent is working. */
  fun label(mode: ComposerDispatch): String =
    when (mode) {
      ComposerDispatch.QUEUE -> "Queue message"
      ComposerDispatch.STEER -> "Steer message"
      ComposerDispatch.AUTO -> "Send"
    }

  /** What the label says the mode does, for the menu that picks the other one. */
  fun description(mode: ComposerDispatch): String =
    when (mode) {
      ComposerDispatch.QUEUE -> "Runs after the current turn"
      ComposerDispatch.STEER -> "Reaches the agent right away"
      ComposerDispatch.AUTO -> "Starts a new turn"
    }
}
