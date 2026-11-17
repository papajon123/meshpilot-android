package ru.papajon.meshcentralmobile.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Touch-to-remote coordinate mapping. A wrong mapping makes the remote cursor land somewhere the
 * user did not tap, which is the kind of defect that only shows up on a real device.
 */
class RemoteViewportTest {
    @Test
    fun fitsRemoteScreenIntoCanvasKeepingAspectRatio() {
        val viewport = RemoteViewport(canvasWidth = 1000, canvasHeight = 1000, remoteWidth = 1920, remoteHeight = 1080)

        assertEquals(1000f / 1920f, viewport.fitScale, 0.0001f)
        assertEquals(1000f, viewport.drawnWidth, 0.01f)
        assertEquals(562.5f, viewport.drawnHeight, 0.01f)
        assertEquals(0f, viewport.left, 0.01f)
        assertEquals(218.75f, viewport.top, 0.01f)
    }

    @Test
    fun mapsCanvasCornersToRemotePixels() {
        val viewport = RemoteViewport(canvasWidth = 1000, canvasHeight = 1000, remoteWidth = 1920, remoteHeight = 1080)

        assertEquals(0 to 0, viewport.toRemote(0f, viewport.top))
        assertEquals(0 to 0, viewport.toRemote(0.4f, viewport.top + 0.4f))
        assertEquals(
            1919 to 1079,
            viewport.toRemote(viewport.left + viewport.drawnWidth - 0.5f, viewport.top + viewport.drawnHeight - 0.5f),
        )
    }

    @Test
    fun mapsRemoteCentreBackToCanvas() {
        val viewport = RemoteViewport(canvasWidth = 800, canvasHeight = 600, remoteWidth = 1600, remoteHeight = 900)
        val (x, y) = viewport.toCanvas(800, 450)

        assertEquals(400f, x, 0.01f)
        assertEquals(300f, y, 0.01f)
        assertEquals(800 to 450, viewport.toRemote(x, y))
    }

    @Test
    fun zoomKeepsCentreAndClampsPanToImageBounds() {
        val viewport = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1920,
            remoteHeight = 1080,
            zoom = 2f,
        )

        assertEquals(2000f, viewport.drawnWidth, 0.01f)
        assertEquals(-500f, viewport.left, 0.01f)

        val (clampedX, clampedY) = viewport.clampPan(10_000f, -10_000f)
        // Horizontal overflow is larger than the free-pan slack, vertical is smaller, so the
        // vertical limit is the slack rather than half the image.
        assertEquals(500f, clampedX, 0.01f)
        assertEquals(-1000f * RemoteViewport.FREE_PAN_FRACTION, clampedY, 0.01f)
    }

    @Test
    fun tapOutsideImageIsClampedToFramebuffer() {
        val viewport = RemoteViewport(canvasWidth = 1000, canvasHeight = 1000, remoteWidth = 640, remoteHeight = 480)

        val (x, y) = viewport.toRemote(-50f, -50f)

        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun missingFrameSizeIsHandledWithoutCrash() {
        val viewport = RemoteViewport(canvasWidth = 0, canvasHeight = 0, remoteWidth = 0, remoteHeight = 0)

        assertTrue(!viewport.remoteValid)
        assertEquals(0 to 0, viewport.toRemote(10f, 10f))
        assertEquals(1f, viewport.fitScale, 0f)
    }

    @Test
    fun zoomAroundKeepsTheTouchedRemotePixelUnderTheFinger() {
        val base = RemoteViewport(canvasWidth = 1000, canvasHeight = 800, remoteWidth = 5120, remoteHeight = 1440)
        val focusX = 700f
        val focusY = 420f
        val remoteBefore = base.toRemote(focusX, focusY)

        for (requestedZoom in listOf(2.5f, 12f, 16f)) {
            val zoomed = base.zoomAround(focusX, focusY, requestedZoom)
            val after = base.copy(zoom = zoomed.zoom, panX = zoomed.panX, panY = zoomed.panY)
            val remoteAfter = after.toRemote(focusX, focusY)

            assertEquals(requestedZoom, zoomed.zoom, 0.001f)
            assertTrue("remote x drifted: ${remoteBefore.first} -> ${remoteAfter.first}", kotlin.math.abs(remoteBefore.first - remoteAfter.first) <= 2)
            assertTrue("remote y drifted: ${remoteBefore.second} -> ${remoteAfter.second}", kotlin.math.abs(remoteBefore.second - remoteAfter.second) <= 2)
        }
    }

    @Test
    fun zoomIsClampedToTheAllowedRange() {
        val base = RemoteViewport(canvasWidth = 1000, canvasHeight = 800, remoteWidth = 1920, remoteHeight = 1080)

        assertEquals(0f, base.zoomAround(500f, 400f, 0.1f).zoom - 1f, 0.001f)
        assertEquals(16f, base.zoomAround(500f, 400f, 99f).zoom, 0.001f)
    }

    @Test
    fun panIsClampedToTheLargerOfOverflowAndFreePanSlack() {
        val base = RemoteViewport(canvasWidth = 1000, canvasHeight = 800, remoteWidth = 1920, remoteHeight = 1080, zoom = 2f)

        val (x, y) = base.clampPan(5_000f, 5_000f)
        val expectedX = maxOf((base.drawnWidth - 1000f) / 2f, 1000f * RemoteViewport.FREE_PAN_FRACTION)
        val expectedY = maxOf((base.drawnHeight - 800f) / 2f, 800f * RemoteViewport.FREE_PAN_FRACTION)

        assertEquals(expectedX, x, 0.01f)
        assertEquals(expectedY, y, 0.01f)
    }

    @Test
    fun aFittingDesktopCanStillBeMovedButStaysReachable() {
        // 5120x1440 on a tall phone: fit zoom shows the whole desktop, and dragging must still do
        // something, otherwise the user cannot move around at all.
        val base = RemoteViewport(canvasWidth = 1080, canvasHeight = 2337, remoteWidth = 5120, remoteHeight = 1440)

        val (x, y) = base.clampPan(10_000f, -10_000f)

        assertEquals(1080f * RemoteViewport.FREE_PAN_FRACTION, x, 0.01f)
        assertEquals(-2337f * RemoteViewport.FREE_PAN_FRACTION, y, 0.01f)
        assertTrue(kotlin.math.abs(x) < base.drawnWidth)
    }

    @Test
    fun mappingFollowsTheViewportWhenZoomedAndPanned() {
        val viewport = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 800,
            remoteWidth = 1920,
            remoteHeight = 1080,
            zoom = 2f,
            panX = 100f,
            panY = -50f,
        )

        val (canvasX, canvasY) = viewport.toCanvas(960, 540)
        assertEquals(960 to 540, viewport.toRemote(canvasX, canvasY))
    }

    @Test
    fun tapsInTheLetterboxAreRejected() {
        // 5120x1440 on a tall phone leaves large black bands; a tap there must not click the
        // nearest framebuffer edge.
        val viewport = RemoteViewport(canvasWidth = 1080, canvasHeight = 2337, remoteWidth = 5120, remoteHeight = 1440)

        assertTrue(viewport.hitsImage(540f, viewport.top + 10f))
        assertTrue(viewport.hitsImage(540f, viewport.top + viewport.drawnHeight - 10f))
        assertFalse(viewport.hitsImage(540f, viewport.top - 40f))
        assertFalse(viewport.hitsImage(540f, viewport.top + viewport.drawnHeight + 40f))
    }

    // Issue #19 regression: the top bar and dock cover the framebuffer. Fit must use the
    // uncovered rectangle, otherwise the bottom edge of a fitted desktop is behind the dock.
    @Test
    fun fitKeepsTheWholeFramebufferBetweenTheTopBarAndDock() {
        val viewport = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1000,
            remoteHeight = 1000,
            insets = ViewportInsets(top = 180f, bottom = 240f),
        )

        assertEquals(580f / 1000f, viewport.fitScale, 0.0001f)
        assertEquals(180f, viewport.availableTop, 0.01f)
        assertEquals(760f, viewport.availableBottom, 0.01f)
        assertEquals(180f, viewport.top, 0.01f)

        val (_, topEdge) = viewport.toCanvas(0, 0)
        val (_, bottomEdge) = viewport.toCanvas(0, 999)
        assertTrue("top edge $topEdge is under the top bar", topEdge >= viewport.availableTop - 0.01f)
        assertTrue("bottom edge $bottomEdge is under the dock", bottomEdge <= viewport.availableBottom + 0.01f)

        // The old full-canvas fit really did park the bottom edge behind the dock.
        val fullCanvas = RemoteViewport(1000, 1000, 1000, 1000)
        assertTrue(fullCanvas.toCanvas(0, 999).second > viewport.availableBottom)
    }

    @Test
    fun tapsOnPanelsAreRejectedEvenWhenTheImageIsUnderneath() {
        val viewport = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1000,
            remoteHeight = 1000,
            zoom = 2f,
            panY = 400f,
            insets = ViewportInsets(top = 200f, bottom = 200f),
        )

        assertFalse("top bar must not click the covered desktop", viewport.hitsImage(500f, 100f))
        assertFalse("dock must not click the covered desktop", viewport.hitsImage(500f, 900f))
        assertFalse(viewport.hitsAvailable(500f, 900f))
        assertTrue(viewport.hitsImage(500f, 500f))
    }

    @Test
    fun anyCornerCanBeBroughtIntoTheUncoveredArea() {
        val insets = ViewportInsets(top = 120f, bottom = 160f)
        val canvases = listOf(1000 to 700, 700 to 1000) // landscape and portrait
        for ((canvasWidth, canvasHeight) in canvases) {
            for (zoom in listOf(1f, 16f)) {
                val base = RemoteViewport(
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    remoteWidth = 1920,
                    remoteHeight = 1080,
                    zoom = zoom,
                    insets = insets,
                )
                val corners = listOf(
                    intArrayOf(0, 0, 10_000, 10_000),
                    intArrayOf(1919, 0, -10_000, 10_000),
                    intArrayOf(0, 1079, 10_000, -10_000),
                    intArrayOf(1919, 1079, -10_000, -10_000),
                )
                for (corner in corners) {
                    val clamped = base.clampPan(corner[2].toFloat(), corner[3].toFloat())
                    val view = base.copy(panX = clamped.first, panY = clamped.second)
                    val (x, y) = view.toCanvas(corner[0], corner[1])
                    val label = "canvas ${canvasWidth}x$canvasHeight zoom $zoom corner ${corner[0]},${corner[1]}"
                    assertTrue("$label x=$x outside ${view.availableLeft}..${view.availableRight}",
                        x >= view.availableLeft - 0.5f && x <= view.availableRight + 0.5f)
                    assertTrue("$label y=$y outside ${view.availableTop}..${view.availableBottom}",
                        y >= view.availableTop - 0.5f && y <= view.availableBottom + 0.5f)
                    assertEquals(corner[0] to corner[1], view.toRemote(x, y))
                }
            }
        }
    }

    @Test
    fun changingTheAvailableAreaKeepsTheWorkingPointAndZoom() {
        val base = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1920,
            remoteHeight = 1080,
            zoom = 2.5f,
            panX = 120f,
            panY = -80f,
        )
        val before = base.toRemote(
            base.availableLeft + base.availableWidth / 2f,
            base.availableTop + base.availableHeight / 2f,
        )

        val withKeyboard = base.copy(insets = ViewportInsets(top = 180f, bottom = 260f))
        val after = withKeyboard.toRemote(
            withKeyboard.availableLeft + withKeyboard.availableWidth / 2f,
            withKeyboard.availableTop + withKeyboard.availableHeight / 2f,
        )

        assertEquals(base.zoom, withKeyboard.zoom, 0f)
        assertEquals(before.first.toFloat(), after.first.toFloat(), 1f)
        assertEquals(before.second.toFloat(), after.second.toFloat(), 1f)
    }

    @Test
    fun trackpadFollowsTheCursorOutFromUnderTheKeyboard() {
        val base = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1920,
            remoteHeight = 1080,
            zoom = 4f,
        )
        val cursorX = 1919f
        val cursorY = 1079f
        val keyboard = base.copy(insets = ViewportInsets(bottom = 400f))

        val (panX, panY) = keyboard.panToRevealCursor(cursorX, cursorY)
        val revealed = keyboard.copy(panX = panX, panY = panY)
        val (x, y) = revealed.toCanvas(cursorX.toInt(), cursorY.toInt())

        assertTrue("cursor x=$x is under the keyboard", revealed.hitsImage(x, y))
        assertTrue("cursor y=$y is under the keyboard", y <= revealed.availableBottom)
        assertNotEquals(base.panY, panY, 0f)
    }

    @Test
    fun zeroAndOversizedInsetsStayFiniteAndClickableAreaIsEmpty() {
        val missing = RemoteViewport(0, 0, 0, 0, insets = ViewportInsets(top = 100f, bottom = 100f))
        assertTrue(missing.fitScale.isFinite())
        assertFalse(missing.hitsImage(0f, 0f))

        val covered = RemoteViewport(
            canvasWidth = 1000,
            canvasHeight = 1000,
            remoteWidth = 1920,
            remoteHeight = 1080,
            insets = ViewportInsets(left = 2000f, top = 2000f, right = -5f, bottom = 5000f),
        )
        assertEquals(0f, covered.availableWidth, 0f)
        assertEquals(0f, covered.availableHeight, 0f)
        assertFalse(covered.hitsAvailable(500f, 500f))
        assertFalse(covered.hitsImage(500f, 500f))
        assertTrue(covered.fitScale.isFinite())
        assertTrue(covered.scale.isFinite())
    }

    @Test
    fun measuredInsetsUsePanelBoundsAndDoNotDoubleCountTheKeyboard() {
        val insets = viewportInsets(
            canvasWidth = 1000,
            canvasHeight = 800,
            top = ViewportRect(-10f, -20f, 1010f, 120f),
            bottom = ViewportRect(0f, 600f, 1000f, 800f),
            imeBottom = 350f,
        )

        assertEquals(120f, insets.top, 0.01f)
        // Dock occlusion is 200 and the keyboard is 350; they overlap, so the answer is not 550.
        assertEquals(350f, insets.bottom, 0.01f)

        val oversized = viewportInsets(
            canvasWidth = 100,
            canvasHeight = 100,
            bottom = ViewportRect(0f, 40f, 100f, 100f),
            imeBottom = 500f,
        )
        assertEquals(100f, oversized.bottom, 0.01f)

        val empty = viewportInsets(0, 0, top = ViewportRect(0f, 0f, 10f, 10f), imeBottom = 10f)
        assertEquals(ViewportInsets(), empty)
    }

    @Test
    fun topPanelMeasuredInsideSystemPaddingReservesThroughItsBottom() {
        val insets = viewportInsets(400, 800, top = ViewportRect(12f, 30f, 388f, 150f))
        assertEquals(150f, insets.top, 0f)
    }

    @Test
    fun heightLimitedFitKeepsWorkingPixelWhenKeyboardChangesScale() {
        val before = RemoteViewport(800, 1000, 400, 1600, zoom = 3f, panX = 75f, panY = -150f)
        val keyboard = before.copy(insets = ViewportInsets(top = 150f, bottom = 400f))
        assertNotEquals(before.fitScale, keyboard.fitScale, 0f)
        val pan = keyboard.panKeepingVisibleCentre(before)
        val after = keyboard.copy(panX = pan.first, panY = pan.second)
        assertEquals(before.zoom, after.zoom, 0f)
        assertEquals(before.toRemote(400f, 500f),
            after.toRemote(after.availableLeft + after.availableWidth / 2f, after.availableTop + after.availableHeight / 2f))
    }
}
