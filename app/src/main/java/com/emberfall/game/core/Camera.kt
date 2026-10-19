package com.emberfall.game.core

import com.emberfall.game.util.clamp
import com.emberfall.game.util.damp
import kotlin.math.min

/**
 * Follow camera with a small look-ahead in the direction the player faces and
 * resolution-independent zoom.
 */
class Camera {

    var x = 0f
        private set
    var y = 0f
        private set
    var zoom = 3f
        private set

    var viewWidth = 0f
        private set
    var viewHeight = 0f
        private set

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        // Always show at least 9 tiles across and 10 down; wider screens see more.
        val byWidth = width / (9f * Config.TILE)
        val byHeight = height / (11f * Config.TILE)
        zoom = clamp(min(byWidth, byHeight), 1.6f, 6f)
    }

    fun snapTo(targetX: Float, targetY: Float) {
        x = targetX
        y = targetY
    }

    fun update(targetX: Float, targetY: Float, facingX: Float, facingY: Float, dt: Float) {
        val lookX = targetX + facingX * 18f
        val lookY = targetY + facingY * 18f
        x = damp(x, lookX, 6.5f, dt)
        y = damp(y, lookY, 6.5f, dt)
    }
}
