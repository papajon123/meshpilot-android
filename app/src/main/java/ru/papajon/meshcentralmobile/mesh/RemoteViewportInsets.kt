package ru.papajon.meshcentralmobile.mesh

/**
 * A rectangle in canvas-local pixels.
 *
 * Local session panels (top bar, dock, collapsed controls) are measured in the same coordinate
 * space as the canvas so the viewport can keep the remote screen in the part that is not covered.
 * Kept free of Android types so the geometry is unit tested.
 */
data class ViewportRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun translated(dx: Float, dy: Float): ViewportRect =
        ViewportRect(left + dx, top + dy, right + dx, bottom + dy)

    fun intersection(other: ViewportRect): ViewportRect? {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (r > l && b > t) ViewportRect(l, t, r, b) else null
    }
}

/**
 * How much of the canvas is covered by local chrome. Applied to [RemoteViewport] as an inset from
 * each canvas edge; all values are zero by default, which keeps the old full-canvas behaviour.
 */
data class ViewportInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    /** Clamps insets to the canvas so a zero or oversized panel cannot create a negative area. */
    fun clampedTo(canvasWidth: Int, canvasHeight: Int): ViewportInsets {
        val width = canvasWidth.coerceAtLeast(0).toFloat()
        val height = canvasHeight.coerceAtLeast(0).toFloat()
        val clampedLeft = left.coerceIn(0f, width)
        val clampedTop = top.coerceIn(0f, height)
        return ViewportInsets(
            left = clampedLeft,
            top = clampedTop,
            right = right.coerceIn(0f, width - clampedLeft),
            bottom = bottom.coerceIn(0f, height - clampedTop),
        )
    }
}

/**
 * Builds canvas insets from measured panel rectangles.
 *
 * [top] and [bottom] are canvas-local. [bottom] and [imeBottom] describe overlapping occluders
 * (a dock can sit above or below the keyboard), so they are combined with `max`, never summed.
 */
fun viewportInsets(
    canvasWidth: Int,
    canvasHeight: Int,
    top: ViewportRect? = null,
    bottom: ViewportRect? = null,
    imeBottom: Float = 0f,
): ViewportInsets {
    val width = canvasWidth.coerceAtLeast(0).toFloat()
    val height = canvasHeight.coerceAtLeast(0).toFloat()
    if (width <= 0f || height <= 0f) return ViewportInsets()
    val canvas = ViewportRect(0f, 0f, width, height)
    // A panel measured after system-bar padding can start below the canvas top. Reserve the whole strip
    // through its bottom edge, including the padding above it, just as we do for the bottom dock.
    val topInset = top?.intersection(canvas)?.bottom ?: 0f
    val panelBottom = bottom?.intersection(canvas)?.let { canvas.bottom - it.top } ?: 0f
    val keyboardBottom = imeBottom.coerceIn(0f, height)
    return ViewportInsets(
        top = topInset,
        bottom = maxOf(panelBottom, keyboardBottom),
    ).clampedTo(canvasWidth, canvasHeight)
}
