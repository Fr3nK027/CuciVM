package com.cuvm.ui.display

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import kotlin.math.abs

/** RFB button mask bits. */
private const val LEFT = 1
private const val MIDDLE = 2
private const val RIGHT = 4
private const val WHEEL_UP = 8
private const val WHEEL_DOWN = 16

/**
 * Touch and mouse -> RFB pointer events.
 * Mouse/touchpad: moves, real buttons and the wheel pass straight through (laptop-style use).
 * Touch: tap = left click, long press = right click, drag = left-button drag, two fingers = wheel.
 */
suspend fun PointerInputScope.guestPointer(fb: (Offset) -> Pair<Int, Int>, send: (Int, Int, Int) -> Unit) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitPointerEventScope {
        var downAt = 0L
        var downPos = Offset.Zero
        var dragging = false
        var longFired = false
        var twoFinger = false
        var scrollAcc = 0f
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull() ?: continue
            val (x, y) = fb(c.position)

            if (c.type == PointerType.Mouse) {
                if (e.type == PointerEventType.Scroll) {
                    val dy = c.scrollDelta.y
                    if (dy != 0f) { val w = if (dy < 0) WHEEL_UP else WHEEL_DOWN; send(x, y, w); send(x, y, 0) }
                } else {
                    var mask = 0
                    if (e.buttons.isPrimaryPressed) mask = mask or LEFT
                    if (e.buttons.isTertiaryPressed) mask = mask or MIDDLE
                    if (e.buttons.isSecondaryPressed) mask = mask or RIGHT
                    send(x, y, mask)
                }
                e.changes.forEach { it.consume() }
                continue
            }

            val pressed = e.changes.count { it.pressed }
            when {
                pressed >= 2 -> {
                    // Two-finger vertical swipe scrolls the guest.
                    twoFinger = true
                    scrollAcc += e.changes.map { it.position.y - it.previousPosition.y }.average().toFloat()
                    if (abs(scrollAcc) > 24f) { val w = if (scrollAcc > 0) WHEEL_UP else WHEEL_DOWN; send(x, y, w); send(x, y, 0); scrollAcc = 0f }
                }
                e.type == PointerEventType.Press -> {
                    downAt = c.uptimeMillis; downPos = c.position; dragging = false; longFired = false; twoFinger = false; scrollAcc = 0f
                    send(x, y, 0)
                }
                e.type == PointerEventType.Move && c.pressed && !twoFinger -> {
                    if (!dragging && !longFired && (c.position - downPos).getDistance() > slop) {
                        dragging = true
                        val (dx, dy) = fb(downPos); send(dx, dy, LEFT)
                    }
                    if (dragging) send(x, y, LEFT)
                    else if (!longFired && c.uptimeMillis - downAt > longPressMs) { longFired = true; send(x, y, RIGHT); send(x, y, 0) }
                }
                e.type == PointerEventType.Release && pressed == 0 -> {
                    when {
                        twoFinger -> Unit
                        dragging -> send(x, y, 0)
                        longFired -> Unit
                        c.uptimeMillis - downAt > longPressMs -> { send(x, y, RIGHT); send(x, y, 0) }
                        else -> { send(x, y, LEFT); send(x, y, 0) }
                    }
                    twoFinger = false
                }
            }
            e.changes.forEach { it.consume() }
        }
    }
}
