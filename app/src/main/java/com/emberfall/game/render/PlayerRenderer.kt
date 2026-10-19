package com.emberfall.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.emberfall.game.entity.Player

/** Animated directional pixel art hero anchored to the projected ground point. */
class PlayerRenderer(context: Context) {
    private val sprite = IsoAnimation(context, "player")
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    fun draw(canvas: Canvas, player: Player, elapsed: Float) {
        val x = player.x; val y = player.y
        oval.set(x - 15f, y - 5f, x + 15f, y + 5f)
        canvas.drawOval(oval, shadow)
        val state = when {
            player.isSwinging -> AnimationState.ATTACK
            player.speed > 8f -> AnimationState.WALK
            else -> AnimationState.IDLE
        }
        sprite.draw(canvas, x, y, player.facingX, player.facingY, state, elapsed, 42f)
        if (player.hurtFlash > 0f) {
            flash.color = 0x55FF4545
            canvas.drawOval(x - 18f, y - 35f, x + 18f, y + 2f, flash)
        }
    }
}
