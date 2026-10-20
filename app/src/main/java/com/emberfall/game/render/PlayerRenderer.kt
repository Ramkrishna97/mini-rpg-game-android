package com.emberfall.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.emberfall.game.entity.Player
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.WeaponStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Animated directional pixel art hero anchored to the projected ground point. */
class PlayerRenderer(context: Context) {
    private val sprite = IsoAnimation(context, "player")
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    /** Draw the equipped weapon separately so it remains readable in every state. */
    fun draw(canvas: Canvas, player: Player, elapsed: Float, weapon: ItemId? = null) {
        val x = player.x; val y = player.y
        oval.set(x - 15f, y - 5f, x + 15f, y + 5f)
        canvas.drawOval(oval, shadow)
        val state = when {
            player.isSwinging -> AnimationState.ATTACK
            player.speed > 8f -> AnimationState.WALK
            else -> AnimationState.IDLE
        }
        sprite.draw(canvas, x, y, player.facingX, player.facingY, state, elapsed, 42f)
        drawWeapon(canvas, player, weapon)
        if (player.hurtFlash > 0f) {
            flash.color = 0x55FF4545
            canvas.drawOval(x - 18f, y - 35f, x + 18f, y + 2f, flash)
        }
    }

    private fun drawWeapon(canvas: Canvas, player: Player, weapon: ItemId?) {
        val item = weapon ?: return
        val x = player.x; val y = player.y - 16f
        val angle = atan2(player.facingY, player.facingX)
        val swing = if (player.isSwinging) (1f - player.swingTimer / Player.SWING_DURATION) else 0f
        val a = angle + if (player.isSwinging) -1.15f + swing * 2.3f else .22f
        val hx = x + cos(a) * 15f; val hy = y + sin(a) * 9f
        flash.color = if (item.style == WeaponStyle.PROJECTILE) 0xFF8B623A.toInt() else 0xFF6B4228.toInt()
        flash.strokeWidth = 3.2f; flash.strokeCap = Paint.Cap.ROUND
        if (item.style == WeaponStyle.PROJECTILE) {
            canvas.drawArc(x - 8f, y - 12f, x + 10f, y + 12f, -70f, 150f, false, flash)
            flash.color = 0xFFEEDDB2.toInt(); flash.strokeWidth = 1f
            canvas.drawLine(x - 2f, y + 6f, hx + cos(a) * 10f, hy + sin(a) * 6f, flash)
        } else {
            canvas.drawLine(x, y + 6f, hx, hy, flash)
            flash.color = if (item == ItemId.WOODEN_SWORD) 0xFFD2A56B.toInt() else 0xFFE6EDF4.toInt()
            flash.strokeWidth = 3.8f
            canvas.drawLine(hx, hy, hx + cos(a) * 12f, hy + sin(a) * 7f, flash)
            if (player.isSwinging) {
                flash.style = Paint.Style.STROKE; flash.strokeWidth = 2f; flash.color = 0x99FFE5A0.toInt()
                canvas.drawArc(x - 31f, y - 28f, x + 31f, y + 32f, Math.toDegrees((a - 1.05f).toDouble()).toFloat(), 72f, false, flash)
                flash.style = Paint.Style.FILL
            }
        }
    }
}
