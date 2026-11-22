package com.t3tools.peer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerAppearanceTest {
  @Test
  fun `system follows the system`() {
    assertTrue(PeerAppearance.SYSTEM.resolveDark(systemDark = true))
    assertFalse(PeerAppearance.SYSTEM.resolveDark(systemDark = false))
  }

  @Test
  fun `light and dark ignore the system`() {
    assertFalse(PeerAppearance.LIGHT.resolveDark(systemDark = true))
    assertTrue(PeerAppearance.DARK.resolveDark(systemDark = false))
  }

  @Test
  fun `a stored value we do not know follows the system`() {
    assertEquals(PeerAppearance.SYSTEM, PeerAppearance.fromStorage("sepia"))
    assertEquals(PeerAppearance.SYSTEM, PeerAppearance.fromStorage(null))
    assertEquals(PeerAppearance.LIGHT, PeerAppearance.fromStorage("light"))
    assertEquals(PeerAppearance.DARK, PeerAppearance.fromStorage("dark"))
  }
}
