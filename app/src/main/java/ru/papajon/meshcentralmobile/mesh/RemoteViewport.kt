package ru.papajon.meshcentralmobile.mesh

import kotlin.math.min

/**
 * Maps between screen coordinates inside the remote desktop canvas and remote framebuffer pixels.
 *
 * Kept free of Android types so the mapping can be unit tested: wrong maths here means the remote
 * mouse lands somewhere other than where the user tapped.
 */
data class RemoteViewport(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val remoteWidth: Int,
    val remoteHeight: Int,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val fitWidth: Boolean = false,
    /**
     * Part of the canvas covered by local chrome. Zero by default, so callers that do not measure
     * panels keep the full-canvas geometry they had before.
     */
    val insets: ViewportInsets = ViewportInsets(),
) {
    val remoteValid: Boolean get() = remoteWidth > 0 && remoteHeight > 0
    val canvasValid: Boolean get() = canvasWidth > 0 && canvasHeight > 0

    private val safeInsets = insets.clampedTo(canvasWidth, canvasHeight)

    /** The uncovered rectangle inside the canvas that can show and accept the remote screen. */
    val availableLeft: Float = safeInsets.left
    val availableTop: Float = safeInsets.top
    val availableWidth: Float =
        (canvasWidth.toFloat() - safeInsets.left - safeInsets.right).coerceAtLeast(0f)
    val availableHeight: Float =
        (canvasHeight.toFloat() - safeInsets.top - safeInsets.bottom).coerceAtLeast(0f)
    val availableRight: Float get() = availableLeft + availableWidth
    val availableBottom: Float get() = availableTop + availableHeight

    private val availableValid: Boolean get() = canvasValid && availableWidth > 0f && availableHeight > 0f

    // A panel can consume the whole canvas for a moment (animation, IME resize). Keep the maths
    // finite and fall back to the full canvas instead of dividing by zero.
    private val contentLeft: Float get() = if (availableValid) availableLeft else 0f
    private val contentTop: Float get() = if (availableValid) availableTop else 0f
    private val contentWidth: Float get() = if (availableValid) availableWidth else canvasWidth.toFloat()
    private val contentHeight: Float get() = if (availableValid) availableHeight else canvasHeight.toFloat()

    /** Scale that fits the whole remote screen into the uncovered canvas area. */
    val fitScale: Float
        get() {
            if (!remoteValid || !availableValid) return 1f
            if (fitWidth && remoteHeight > remoteWidth) return availableWidth / remoteWidth
            return min(availableWidth / remoteWidth, availableHeight / remoteHeight)
        }

    val scale: Float get() = fitScale * zoom

    val drawnWidth: Float get() = remoteWidth * scale
    val drawnHeight: Float get() = remoteHeight * scale

    val left: Float get() = contentLeft + (contentWidth - drawnWidth) / 2f + panX
    val top: Float get() = contentTop + (contentHeight - drawnHeight) / 2f + panY

    /** Converts a touch position inside the canvas to absolute remote framebuffer pixels. */
    fun toRemote(x: Float, y: Float): Pair<Int, Int> {
        if (!remoteValid || !canvasValid) return 0 to 0
        val scaleValue = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val remoteX = ((x - left) / scaleValue).toInt().coerceIn(0, remoteWidth - 1)
        val remoteY = ((y - top) / scaleValue).toInt().coerceIn(0, remoteHeight - 1)
        return remoteX to remoteY
    }

    /** Converts a remote framebuffer point back into canvas coordinates. */
    fun toCanvas(remoteX: Int, remoteY: Int): Pair<Float, Float> {
        if (!remoteValid || !canvasValid) return 0f to 0f
        val scaleValue = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (left + remoteX * scaleValue) to (top + remoteY * scaleValue)
    }

    /** Relative pointer motion keeps subpixel precision and reveals the pointer near the edges. */
    fun moveTrackpad(x: Float, y: Float, dx: Float, dy: Float): TrackpadMotion {
        if (!remoteValid || !availableValid || !scale.isFinite() || scale <= 0f) {
            return TrackpadMotion(x, y, panX, panY)
        }
        val nextX = (x + dx / scale).coerceIn(0f, (remoteWidth - 1).toFloat())
        val nextY = (y + dy / scale).coerceIn(0f, (remoteHeight - 1).toFloat())
        return TrackpadMotion(
            nextX,
            nextY,
            followPoint(x = left + nextX * scale, start = contentLeft, extent = contentWidth,
                drawn = drawnWidth, pan = panX),
            followPoint(x = top + nextY * scale, start = contentTop, extent = contentHeight,
                drawn = drawnHeight, pan = panY),
        )
    }

    /**
     * Pans just enough to bring an already known remote pointer back into the uncovered area.
     * Used after a keyboard or panel change: the working point and zoom stay, only the view follows
     * the trackpad cursor out from under the new chrome.
     */
    fun panToRevealCursor(remoteX: Float, remoteY: Float): Pair<Float, Float> {
        if (!remoteValid || !availableValid || !scale.isFinite() || scale <= 0f) return panX to panY
        val x = remoteX.coerceIn(0f, (remoteWidth - 1).toFloat())
        val y = remoteY.coerceIn(0f, (remoteHeight - 1).toFloat())
        return followPoint(left + x * scale, contentLeft, contentWidth, drawnWidth, panX) to
            followPoint(top + y * scale, contentTop, contentHeight, drawnHeight, panY)
    }

    /** Keep the same remote pixel at the visible centre when only the canvas geometry changes. */
    fun panKeepingVisibleCentre(previous: RemoteViewport): Pair<Float, Float> {
        if (!availableValid || !previous.availableValid || !remoteValid ||
            previous.remoteWidth != remoteWidth || previous.remoteHeight != remoteHeight) return panX to panY
        val ratio = fitScale / previous.fitScale
        if (!ratio.isFinite() || ratio <= 0f) return panX to panY
        return clampPan(panX * ratio, panY * ratio)
    }

    private fun followPoint(x: Float, start: Float, extent: Float, drawn: Float, pan: Float): Float {
        if (extent <= 0f) return pan
        val margin = extent * 0.1f
        val correction = x.coerceIn(start + margin, start + extent - margin) - x
        // Preserve manual free pan; following may reduce existing empty space but never enlarge it.
        val limit = ((drawn - extent) / 2f).coerceAtLeast(0f)
        return (pan + correction).coerceIn(minOf(-limit, pan), maxOf(limit, pan))
    }

    /**
     * Keeps the visible area inside the canvas when zoomed, and still allows a bounded free pan when
     * the whole framebuffer already fits.
     *
     * Without that slack, dragging does nothing at fit zoom (the picture is not larger than the
     * screen), which reads as "I cannot move around the screen". The desktop can be parked anywhere
     * inside [FREE_PAN_FRACTION] of the canvas and "Fit" recentres it.
     */
    fun clampPan(newPanX: Float, newPanY: Float): Pair<Float, Float> {
        val maxX = ((drawnWidth - contentWidth) / 2f).coerceAtLeast(contentWidth * FREE_PAN_FRACTION)
        val maxY = ((drawnHeight - contentHeight) / 2f).coerceAtLeast(contentHeight * FREE_PAN_FRACTION)
        return newPanX.coerceIn(-maxX, maxX) to newPanY.coerceIn(-maxY, maxY)
    }

    /**
     * Zooms while keeping the point under the fingers still, which is what makes pinch-zoom feel
     * attached to the content instead of jumping.
     *
     * Returns the new zoom and the pan that keeps [focusX],[focusY] (canvas coordinates) over the
     * same remote pixel.
     */
    fun zoomAround(focusX: Float, focusY: Float, requestedZoom: Float): ZoomResult {
        if (!remoteValid || !canvasValid) return ZoomResult(zoom, panX, panY)
        val newZoom = requestedZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return ZoomResult(zoom, panX, panY)
        val newScale = fitScale * newZoom
        if (!newScale.isFinite() || newScale <= 0f) return ZoomResult(zoom, panX, panY)

        // Remote pixel currently under the focus point.
        val currentScale = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val remoteX = (focusX - left) / currentScale
        val remoteY = (focusY - top) / currentScale

        // Centre formula of `left`, solved for the pan that keeps the same remote pixel in place.
        val baseLeft = contentLeft + (contentWidth - remoteWidth * newScale) / 2f
        val baseTop = contentTop + (contentHeight - remoteHeight * newScale) / 2f
        val newPanX = focusX - remoteX * newScale - baseLeft
        val newPanY = focusY - remoteY * newScale - baseTop
        val clamped = copy(zoom = newZoom).clampPan(newPanX, newPanY)
        val clampedX = clamped.first
        val clampedY = clamped.second
        return ZoomResult(newZoom, clampedX, clampedY)
    }

    /** Zooms the session buttons around the visible centre, keeping the current pan meaningful. */
    fun zoomAtVisibleCentre(requestedZoom: Float): ZoomResult =
        zoomAround(contentLeft + contentWidth / 2f, contentTop + contentHeight / 2f, requestedZoom)

    /** Percentage of the remote width that is currently visible, for the zoom indicator. */
    val visibleRemoteFraction: Float
        get() {
            if (!remoteValid || !canvasValid) return 1f
            val scaleValue = scale.takeIf { it.isFinite() && it > 0f } ?: 1f
            return (contentWidth / (remoteWidth * scaleValue)).coerceIn(0f, 1f)
        }

    /** True when a canvas point lies in the part not covered by local panels. */
    fun hitsAvailable(x: Float, y: Float): Boolean {
        if (!availableValid) return false
        return x >= availableLeft && x <= availableRight && y >= availableTop && y <= availableBottom
    }

    /**
     * True when a canvas point actually covers the framebuffer image and is not under local chrome.
     *
     * Clicks are only sent for such points: without this check a tap in the black letterbox area
     * would clamp to the nearest framebuffer edge and click whatever sits there (the taskbar, for
     * example), and a tap on a panel would click the covered desktop.
     */
    fun hitsImage(x: Float, y: Float): Boolean {
        if (!remoteValid || !hitsAvailable(x, y)) return false
        return x >= left && x <= left + drawnWidth && y >= top && y <= top + drawnHeight
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 16f

        /** How far the desktop may be parked inside the canvas when it already fits. */
        const val FREE_PAN_FRACTION = 0.35f
    }
}

data class ZoomResult(val zoom: Float, val panX: Float, val panY: Float)

data class TrackpadMotion(val x: Float, val y: Float, val panX: Float, val panY: Float)
