package com.xick.app.ui.channel

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Xtra-style gesture on the player:
 *  - Drag DOWN past 1/5 of the screen height -> minimize into a corner.
 *  - When minimized, drag SIDEWAYS. Released past the fling threshold -> dismiss;
 *    otherwise it snaps back to the corner.
 *  - Tap while minimized -> restore to full size.
 *
 * The gesture never moves the view itself while minimized; it reports offsets so
 * the fragment can keep the corner position and add the drag on top of it.
 */
class MiniPlayerGesture(
    private val view: View,
    private val onProgress: (Float) -> Unit,
    private val onMinimize: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onHorizontalDrag: (Float) -> Unit,
    private val onSnapBack: () -> Unit,
    private val onTap: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val flingPxPerSec = 1500f
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var verticalDrag = false
    private var horizontalDrag = false
    private var moved = false
    private var downTime = 0L
    var minimized = false

    private val minimizeDistance = view.resources.displayMetrics.heightPixels / 5f

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                tracking = true
                verticalDrag = false
                horizontalDrag = false
                moved = false
                return minimized
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                if (!verticalDrag && !horizontalDrag) {
                    if (!minimized && dy > touchSlop && abs(dy) > abs(dx)) verticalDrag = true
                    else if (minimized && abs(dx) > touchSlop && abs(dx) > abs(dy)) horizontalDrag = true
                }
                if (verticalDrag) {
                    onProgress((dy / minimizeDistance).coerceIn(0f, 1f))
                    return true
                }
                if (horizontalDrag) {
                    onHorizontalDrag(dx)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasVertical = verticalDrag
                val wasHorizontal = horizontalDrag
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (wasVertical) {
                    if (dy >= minimizeDistance) {
                        minimized = true
                        onMinimize()
                    } else {
                        onProgress(0f)
                    }
                } else if (wasHorizontal) {
                    val elapsed = (event.eventTime - downTime).coerceAtLeast(1L)
                    val velocity = dx * 1000f / elapsed
                    if (abs(dx) >= view.width / 3f || abs(velocity) > flingPxPerSec) {
                        onDismiss()
                    } else {
                        onSnapBack()
                    }
                } else if (!moved && minimized && event.actionMasked == MotionEvent.ACTION_UP) {
                    onTap()
                }
                tracking = false
                verticalDrag = false
                horizontalDrag = false
                return wasVertical || wasHorizontal
            }
        }
        return false
    }

    /** Called when the mini-player is restored to full size. */
    fun reset() {
        minimized = false
    }
}
