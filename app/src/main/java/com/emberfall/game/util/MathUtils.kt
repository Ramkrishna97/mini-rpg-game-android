package com.emberfall.game.util

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

fun clamp(value: Float, lo: Float, hi: Float): Float = max(lo, min(hi, value))

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun smoothstep(t: Float): Float {
    val x = clamp(t, 0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Frame-rate independent exponential smoothing.
 * [lambda] is the "speed" of the approach: bigger is snappier.
 */
fun damp(current: Float, target: Float, lambda: Float, dt: Float): Float =
    lerp(current, target, 1f - exp(-lambda * dt))

fun length(x: Float, y: Float): Float = sqrt(x * x + y * y)
