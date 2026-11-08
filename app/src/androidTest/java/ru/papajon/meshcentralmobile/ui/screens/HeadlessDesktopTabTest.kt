package ru.papajon.meshcentralmobile.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import ru.papajon.meshcentralmobile.R
import ru.papajon.meshcentralmobile.mesh.MeshDevice
import ru.papajon.meshcentralmobile.mesh.MeshRights
import ru.papajon.meshcentralmobile.mesh.MeshServerCapabilities
import ru.papajon.meshcentralmobile.ui.DeviceTab
import ru.papajon.meshcentralmobile.ui.theme.MeshCentralMobileTheme

/** Ubuntu Server without X: the agent reports caps without the Desktop bit (issue from a user, 0.12.5). */
class HeadlessDesktopTabTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(caps: Int?, core: String? = null) {
        compose.setContent {
            MeshCentralMobileTheme {
                DeviceScreen(
                    MeshDevice("node//headless", "mesh//fixture", "Server", conn = 1, agentId = 6, mtype = 2,
                        osDescription = "Ubuntu 22.04.5 LTS", agentVersion = "0", agentCore = core, agentCaps = caps,
                        connectivityKnown = true, rights = MeshRights.FULL),
                    MeshServerCapabilities(), true, DeviceTab.Overview,
                    onSelect = {}, onBack = {}, onWebPanel = {}, sessionContent = {},
                )
            }
        }
    }

    @Test fun headlessAgentHasNoDesktopTabButExplainsWhyInTheOverview() {
        show(caps = 14)
        compose.onNodeWithTag("device-tab-Desktop").assertDoesNotExist()
        compose.onNodeWithTag("device-tab-Terminal").assertIsEnabled()
        compose.onNodeWithTag("device-tab-Files").assertIsEnabled()
        compose.onNodeWithTag("device-tool-Desktop").performScrollTo().assertIsNotEnabled()
            .assertTextContains(compose.activity.getString(R.string.devices_capability_reason_no_display))
    }

    @Test fun graphicalAgentKeepsTheDesktopTab() {
        show(caps = 15)
        compose.onNodeWithTag("device-tab-Desktop").assertIsEnabled()
    }

    @Test fun unknownCapsKeepTheDesktopTab() {
        show(caps = null)
        compose.onNodeWithTag("device-tab-Desktop").assertIsEnabled()
    }

    @Test fun agentVersionShowsTheCoreBuildWhenVersionIsZero() {
        show(caps = 14, core = "Mar 10 2025, 4027413137")
        compose.onNodeWithText("Mar 10 2025, 4027413137").performScrollTo()
    }
}
