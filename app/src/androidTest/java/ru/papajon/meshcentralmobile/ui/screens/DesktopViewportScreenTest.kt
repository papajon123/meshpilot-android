package ru.papajon.meshcentralmobile.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.papajon.meshcentralmobile.R
import ru.papajon.meshcentralmobile.mesh.*
import ru.papajon.meshcentralmobile.model.ConnectionState
import ru.papajon.meshcentralmobile.security.SecureVault
import ru.papajon.meshcentralmobile.ui.theme.MeshCentralMobileTheme

/** Full screen with production panels and a synthetic framebuffer/recording transport. */
class DesktopViewportScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: MeshCentralClient
    private val sink = DesktopRecordingSink()
    private val colors = listOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xffffff00.toInt())
    // Keep a pure source-colour interior after scaling and the production 1dp framebuffer outline.
    private val markerSize = 64
    private var fixtureWidth = 400
    private var fixtureHeight = 1600

    @Suppress("UNCHECKED_CAST")
    private fun screen(landscape: Boolean = false, frameWidth: Int = 400, frameHeight: Int = 1600) {
        fixtureWidth = frameWidth
        fixtureHeight = frameHeight
        val orientation = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE
            else android.content.res.Configuration.ORIENTATION_PORTRAIT
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == orientation }
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            // Match MainActivity's manifest contract. With the unspecified default, API 26 pans
            // this bare ComponentActivity to the hidden editor and moves the entire top bar offscreen.
            compose.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        client = MeshCentralClient(SecureVault(compose.activity, "viewport-screen-fixture"), scope)
        client.javaClass.getDeclaredField("remoteInputSink").apply { isAccessible = true }.set(client, sink)
        val relay = client.javaClass.getDeclaredField("relay").apply { isAccessible = true }.get(client)
        val state = relay.javaClass.getDeclaredField("_desktopState").apply { isAccessible = true }
            .get(relay) as MutableStateFlow<RemoteDesktopState>
        val frame = Bitmap.createBitmap(fixtureWidth, fixtureHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint()
        listOf(0 to 0, (fixtureWidth - markerSize) to 0, 0 to (fixtureHeight - markerSize),
            (fixtureWidth - markerSize) to (fixtureHeight - markerSize)).forEachIndexed { i, (x, y) ->
            paint.color = colors[i]
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + markerSize).toFloat(), (y + markerSize).toFloat(), paint)
        }
        state.value = RemoteDesktopState(
            device = MeshDevice("node//viewport", "mesh//fixture", "Viewport fixture", conn = 1, agentId = 3, rights = 8),
            connectionState = ConnectionState.Connected, handshakeComplete = true,
            framebuffer = frame, remoteWidth = fixtureWidth, remoteHeight = fixtureHeight,
        )
        compose.setContent { MeshCentralMobileTheme {
            // The session is deliberately not at root (as in a host with consumed system insets).
            Box(Modifier.fillMaxSize().padding(top = 36.dp, bottom = 20.dp)) {
                RemoteDesktopScreen(client, {}, {}, shortcutProfileId = "viewport-fixture")
            }
        } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    @After fun cleanup() {
        if (::client.isInitialized) client.close()
        scope.cancel()
    }

    private fun control(id: Int) = compose.onNodeWithContentDescription(compose.activity.getString(id))

    private fun assertCornersReachable() {
        val root = compose.onNodeWithTag("remote-desktop")
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val top = compose.onNodeWithTag("session-top-bar").fetchSemanticsNode().boundsInRoot.bottom - rootBounds.top
        val bottom = compose.onNodeWithTag("session-dock").fetchSemanticsNode().boundsInRoot.top - rootBounds.top
        val image = root.captureToImage()
        val orientation = if (compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            "landscape" else "portrait"
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/meshpilot-viewport-$orientation.png"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() } }
        val pixels = image.toPixelMap()
        val positions = colors.map { color ->
            val target = androidx.compose.ui.graphics.Color(color)
            var left = pixels.width; var right = -1; var first = pixels.height; var last = -1
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val p = pixels[x, y]
                if (kotlin.math.abs(p.red - target.red) < 0.02f && kotlin.math.abs(p.green - target.green) < 0.02f &&
                    kotlin.math.abs(p.blue - target.blue) < 0.02f) {
                    left = minOf(left, x); right = maxOf(right, x); first = minOf(first, y); last = maxOf(last, y)
                }
            }
            assertTrue("Framebuffer corner $color is obscured by chrome; canvas=${pixels.width}x${pixels.height}, " +
                "root=$rootBounds, topPanelBottom=$top, dockTop=$bottom, " +
                "frame=${fixtureWidth}x${fixtureHeight}, marker=$markerSize", right >= left && last >= first)
            assertTrue("corner y=$first is under top bar bottom=$top", first >= top)
            assertTrue("corner y=$last is under dock top=$bottom", last < bottom)
            Offset((left + right) / 2f, (first + last) / 2f)
        }
        positions.forEachIndexed { i, point ->
            // On a tiny landscape fit the two top corners are within double-tap slop. Each corner
            // is an independent click, so let the production wall-clock double-tap interval expire.
            android.os.SystemClock.sleep(400)
            compose.runOnIdle { sink.events.clear(); sink.clickPositions.clear() }
            root.performTouchInput { click(point) }
            compose.runOnIdle {
                assertEquals("corner $i did not reach remote input", 1, sink.clickPositions.size)
                val (x, y) = sink.clickPositions.single()
                assertTrue("corner $i mapped to $x,$y", x in (if (i % 2 == 0) 0..markerSize
                    else (fixtureWidth - 1 - markerSize) until fixtureWidth) &&
                    y in (if (i < 2) 0..markerSize else (fixtureHeight - 1 - markerSize) until fixtureHeight))
            }
        }
    }

    @Test fun fitKeepsEveryCornerReachableWithMeasuredPanelsAndOffsetHost() {
        screen()
        assertCornersReachable()
    }

    @Test fun landscapeFitKeepsAllFourCornersReachable() {
        // A desktop-shaped framebuffer leaves resolvable corner patches on a small landscape
        // viewport. The other tests retain the tall framebuffer that exposes panel occlusion.
        screen(landscape = true, frameWidth = 800, frameHeight = 450)
        assertCornersReachable()
    }

    @Test fun localControlsAndModeSelectionDoNotClickCoveredDesktop() {
        screen()
        compose.runOnIdle { sink.events.clear() }
        control(R.string.session_mode_direct_touch).performTouchInput { click() }
        compose.onNodeWithText(compose.activity.getString(R.string.session_mode_pan_zoom)).performClick()
        compose.onNodeWithTag("remote-desktop").performTouchInput {
            swipe(Offset(width / 2f, height / 2f), Offset(width / 2f + 50f, height / 2f + 60f), 200)
        }
        control(R.string.session_mode_zoom_short).performTouchInput { click() }
        compose.onAllNodesWithText(compose.activity.getString(R.string.session_mode_direct_touch)).onLast().performClick()
        control(R.string.session_dock_hide).performTouchInput { click() }
        control(R.string.session_dock_show).performTouchInput { click() }
        control(R.string.session_dock_more).performTouchInput { click() }
        // Free pan at 100% must still have a way back to Fit.
        control(R.string.common_action_fit_to_screen).performScrollTo().assertIsEnabled()
        control(R.string.session_zoom_in).performScrollTo().performTouchInput { click() }
        control(R.string.session_zoom_out).performTouchInput { click() }
        control(R.string.common_action_fit_to_screen).assertIsEnabled()
        control(R.string.common_action_fit_to_screen).performTouchInput { click() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertTrue("local controls sent remote input: ${sink.events}", sink.events.isEmpty()) }
        assertCornersReachable()
    }

    @Test fun softKeyboardStillLeavesAllFitCornersAboveIt() {
        screen()
        control(R.string.session_dock_typing).performTouchInput { click() }
        compose.waitUntil(8000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithTag("session-top-bar").assertIsDisplayed()
        assertCornersReachable()
    }
}
