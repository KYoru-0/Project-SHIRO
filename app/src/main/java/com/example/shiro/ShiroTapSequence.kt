package com.example.shiro

import kotlin.math.hypot

/** Remembers a tap without delaying its bounce or holding the cat in place. */
internal class ShiroTapSequence {
    private var index = -1
    private var x = 0f
    private var y = 0f
    private var time = 0L

    fun record(target: Int, atX: Float, atY: Float, uptime: Long) {
        index = target; x = atX; y = atY; time = uptime
    }

    fun clear() { index = -1 }

    fun secondTarget(hit: Int?, atX: Float, atY: Float, uptime: Long,
        minimum: Long, timeout: Long, tolerance: Float): Int? {
        if (index < 0 || uptime - time !in minimum..timeout) return null
        // Follow the actual cat, or accept its old tap location after the first bounce moved it.
        // A different cat under the finger always wins over this small fallback target.
        return index.takeIf { hit == index || (hit == null && hypot(atX - x, atY - y) <= tolerance) }
    }
}
