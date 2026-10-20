package com.emberfall.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Crops a sprite sheet into equal frames and advances them on a fixed frame
 * duration. Supports looping animations (idle/walk) and one-shot animations
 * (attack).
 */
class SpriteAnimation(
    val sheet: Bitmap,
    val columns: Int,
    val rows: Int,
    val frameDuration: Float,
    val loop: Boolean = true,
) {
    val frameWidth: Int = sheet.width / columns
    val frameHeight: Int = sheet.height / rows

    var frameIndex = 0
        private set

    private var accumulator = 0f
    private var finished = false

    private val src = Rect()
    private val dst = Rect()

    val isFinished: Boolean get() = finished

    fun reset() {
        frameIndex = 0
        accumulator = 0f
        finished = false
    }

    fun update(dt: Float) {
        if (frameWidth <= 0 || frameHeight <= 0) return
        if (!loop && finished) return
        accumulator += dt
        while (accumulator >= frameDuration) {
            accumulator -= frameDuration
            if (frameIndex + 1 < columns) {
                frameIndex++
            } else if (loop) {
                frameIndex = 0
            } else {
                finished = true
                break
            }
        }
    }

    /** Draws centred at (centerX, centerY) with an explicit on-screen size. */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        paint: Paint,
    ) {
        if (frameWidth <= 0 || frameHeight <= 0) return
        val sx = frameIndex * frameWidth
        val sy = 0
        src.set(sx, sy, sx + frameWidth, sy + frameHeight)
        val halfW = width / 2f
        val halfH = height / 2f
        dst.set(
            (centerX - halfW).toInt(),
            (centerY - halfH).toInt(),
            (centerX + halfW).toInt(),
            (centerY + halfH).toInt(),
        )
        canvas.drawBitmap(sheet, src, dst, paint)
    }
}

/** Optional helper for creatures with multiple animation states. */
enum class AnimationState { IDLE, WALK, ATTACK, DEATH }

class AnimationSet(private val animations: Map<AnimationState, SpriteAnimation>) {

    var state: AnimationState = AnimationState.IDLE
        private set

    fun play(next: AnimationState) {
        if (next == state) return
        state = next
        animations[next]?.reset()
    }

    fun update(dt: Float) {
        animations[state]?.update(dt)
    }

    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        paint: Paint,
    ) {
        animations[state]?.draw(canvas, centerX, centerY, width, height, paint)
    }
}
