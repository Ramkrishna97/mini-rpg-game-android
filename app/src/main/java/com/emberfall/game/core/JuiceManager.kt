package com.emberfall.game.core

import android.graphics.PointF
import com.emberfall.game.entity.FloatingText
import kotlin.random.Random

/** Short combat pauses, small camera impulses and world-anchored combat text. */
class JuiceManager {
    private var freezeUntilNanos = 0L
    private var shakeSeconds = 0f
    private var shakeIntensity = 0f

    fun triggerShake(intensity: Float, durationMs: Long) {
        shakeIntensity = maxOf(shakeIntensity, intensity.coerceIn(0f, 5f))
        shakeSeconds = maxOf(shakeSeconds, durationMs.coerceAtLeast(0L) / 1000f)
    }

    fun triggerHitstop(milliseconds: Long) {
        freezeUntilNanos = maxOf(freezeUntilNanos, System.nanoTime() + milliseconds.coerceIn(0L, 50L) * 1_000_000L)
    }

    fun isFrozen(): Boolean = System.nanoTime() < freezeUntilNanos

    fun update(dt: Float) {
        shakeSeconds = (shakeSeconds - dt).coerceAtLeast(0f)
        if (shakeSeconds <= 0f) shakeIntensity = 0f
    }

    fun shakeOffset(): PointF = if (shakeSeconds > 0f) PointF(
        (Random.nextFloat() * 2f - 1f) * shakeIntensity,
        (Random.nextFloat() * 2f - 1f) * shakeIntensity,
    ) else PointF(0f, 0f)

    fun spawnFloatingText(text: String, x: Float, y: Float, color: Int): FloatingText =
        FloatingText(x, y, text, color, .6f, 58f)
}
