package ru.papajon.meshcentralmobile.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentInfoTest {
    private fun node(agent: String) = (MeshControlParser.parse(
        """{"action":"nodes","nodes":{"mesh//a":[{"_id":"node//n","name":"srv","conn":1,"mtype":2,"agent":$agent}]}}""",
    ) as MeshControlEvent.Nodes).devices.single()

    @Test fun currentAgentShowsItsCoreBuildInsteadOfVersionZero() {
        val device = node("""{"id":6,"ver":0,"caps":14,"core":"Mar 10 2025, 4027413137"}""")
        assertEquals("Mar 10 2025, 4027413137", device.agentCore)
        assertEquals("Mar 10 2025, 4027413137", device.agentVersionLabel)
    }

    @Test fun explicitAgentVersionWins() {
        assertEquals("1.2.3", node("""{"id":7,"ver":"1.2.3","core":"Mar 10 2025"}""").agentVersionLabel)
    }

    @Test fun missingVersionAndCoreStayEmpty() {
        val device = node("""{"id":6,"ver":0}""")
        assertEquals("", device.agentVersionLabel)
        assertEquals(null, device.agentCore)
        assertEquals("", node("""{"id":6,"core":"   "}""").agentVersionLabel)
    }

    @Test fun headlessAgentReportsNoDisplayEvenOffline() {
        assertTrue(node("""{"id":6,"caps":14}""").reportsNoDisplay)
        assertTrue(node("""{"id":6,"caps":14}""").copy(conn = 0).reportsNoDisplay)
        assertFalse(node("""{"id":6,"caps":15}""").reportsNoDisplay)
        assertFalse(node("""{"id":6}""").reportsNoDisplay)
        assertFalse(node("""{"id":14,"caps":12}""").reportsNoDisplay)
    }
}
