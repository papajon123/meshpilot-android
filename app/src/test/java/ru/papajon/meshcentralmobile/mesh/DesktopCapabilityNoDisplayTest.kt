package ru.papajon.meshcentralmobile.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCapabilityNoDisplayTest {
    private val linux = MeshDevice(
        "node//test", "mesh//test", "Server", conn = 1, mtype = 2, agentId = 6,
        osDescription = "Ubuntu 22.04 Linux", rights = MeshRights.FULL,
    )

    @Test fun headlessAgentWithoutDesktopBitHasNoDesktop() {
        val headless = linux.copy(agentCaps = 14) // terminal + files + console, no desktop
        assertEquals(DesktopCapability.UnsupportedNoDisplay, headless.desktopCapability)
        assertFalse(headless.canOpenNativeDesktop)
        assertFalse(headless.inputAllowed)
    }

    @Test fun agentWithDesktopBitKeepsDesktop() {
        val graphical = linux.copy(agentCaps = 15)
        assertEquals(DesktopCapability.MeshAgentDesktop, graphical.desktopCapability)
        assertTrue(graphical.canOpenNativeDesktop)
    }

    @Test fun unknownCapsStillAllowAnAttempt() {
        assertEquals(DesktopCapability.MeshAgentDesktop, linux.copy(agentCaps = null).desktopCapability)
    }

    @Test fun androidAgentIsNotHiddenByCaps() {
        val phone = linux.copy(agentId = 14, osDescription = "Android", agentCaps = 12)
        assertEquals(DesktopCapability.MeshAgentDesktop, phone.desktopCapability)
    }

    @Test fun offlineAndPermissionReasonsWinOverNoDisplay() {
        assertEquals(DesktopCapability.UnsupportedOffline, linux.copy(conn = 0, agentCaps = 14).desktopCapability)
        assertEquals(
            DesktopCapability.UnsupportedNoPermission,
            linux.copy(rights = MeshRights.NO_DESKTOP, agentCaps = 14).desktopCapability,
        )
    }
}
