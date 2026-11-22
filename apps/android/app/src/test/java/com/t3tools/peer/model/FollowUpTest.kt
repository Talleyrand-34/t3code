package com.t3tools.peer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowUpTest {
  @Test
  fun `a message sent while nothing runs starts a turn`() {
    assertEquals(ComposerDispatch.AUTO, FollowUp.resolve(running = false))
  }

  @Test
  fun `the user's choice decides what a message does while the agent works`() {
    assertEquals(
      ComposerDispatch.QUEUE,
      FollowUp.resolve(running = true, behavior = FollowUpBehavior.QUEUE),
    )
    assertEquals(
      ComposerDispatch.STEER,
      FollowUp.resolve(running = true, behavior = FollowUpBehavior.STEER),
    )
  }

  @Test
  fun `the other mode is the one the user did not choose`() {
    assertEquals(
      ComposerDispatch.STEER,
      FollowUp.resolve(running = true, behavior = FollowUpBehavior.QUEUE, alternate = true),
    )
    assertEquals(
      ComposerDispatch.QUEUE,
      FollowUp.resolve(running = true, behavior = FollowUpBehavior.STEER, alternate = true),
    )
  }

  @Test
  fun `a turn that cannot be steered takes a queue`() {
    assertEquals(
      ComposerDispatch.QUEUE,
      FollowUp.resolve(running = true, canSteer = false, behavior = FollowUpBehavior.STEER),
    )
  }

  @Test
  fun `a queued message waits for the turn that is working`() {
    assertEquals("queue_after_active", FollowUp.dispatchMode(ComposerDispatch.QUEUE))
    assertNull(FollowUp.deliveryIntent(ComposerDispatch.QUEUE))
  }

  @Test
  fun `a steer is sent as an intent, so the server can queue it instead`() {
    assertEquals("auto", FollowUp.deliveryIntent(ComposerDispatch.STEER))
    assertEquals("start_immediately", FollowUp.dispatchMode(ComposerDispatch.STEER))
  }

  @Test
  fun `a message that starts a turn carries the intent the server reads`() {
    assertEquals("auto", FollowUp.deliveryIntent(ComposerDispatch.AUTO))
    assertEquals("start_immediately", FollowUp.dispatchMode(ComposerDispatch.AUTO))
  }

  @Test
  fun `the send button says what the message will do`() {
    assertEquals("Queue message", FollowUp.label(ComposerDispatch.QUEUE))
    assertEquals("Steer message", FollowUp.label(ComposerDispatch.STEER))
    assertEquals("Send", FollowUp.label(ComposerDispatch.AUTO))
  }

  @Test
  fun `a stored choice that is not known falls back to the default`() {
    assertEquals(FollowUpBehavior.STEER, FollowUpBehavior.fromStorage(null))
    assertEquals(FollowUpBehavior.STEER, FollowUpBehavior.fromStorage("nonsense"))
    assertEquals(FollowUpBehavior.QUEUE, FollowUpBehavior.fromStorage("queue"))
  }
}
