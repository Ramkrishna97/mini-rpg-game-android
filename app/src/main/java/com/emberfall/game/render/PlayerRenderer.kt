package com.emberfall.game.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.emberfall.game.entity.Player
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal vector wanderer: a cloak, a head and an animated walk cycle.
 * Drawn with a handful of primitives, no sprite sheets required.
 */
class PlayerRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(canvas: Canvas, player: Player) {
        val moving = player.speed > 8f
        val bob = if (moving) sin(player.walkPhase) * 1.3f else 0f
        val px = player.x
        val py = player.y + bob

        // Ground shadow stays put while the body bobs.
        paint.color = 0x22000000
        rect.set(px - 11f, player.y + 5f, px + 11f, player.y + 14f)
        canvas.drawOval(rect, paint)
        paint.color = 0x4A000000
        rect.set(px - 8f, player.y + 7f, px + 8f, player.y + 12f)
        canvas.drawOval(rect, paint)

        val step = if (moving) sin(player.walkPhase) * 2.2f else 0f
        paint.color = BOOTS
        canvas.drawRect(px - 5f, py + 4f, px - 1.5f, py + 10f + step, paint)
        canvas.drawRect(px + 1.5f, py + 4f, px + 5f, py + 10f - step, paint)

        paint.color = CLOAK
        rect.set(px - 7.5f, py - 8f, px + 7.5f, py + 7f)
        canvas.drawRoundRect(rect, 5f, 5f, paint)

        paint.color = CLOAK_LIGHT
        rect.set(px - 6f, py - 6f, px + 2f, py + 4f)
        canvas.drawRoundRect(rect, 4f, 4f, paint)

        paint.color = SKIN
        canvas.drawCircle(px + player.facingX * 1.2f, py - 11f, 5.6f, paint)

        paint.color = HAIR
        rect.set(px - 5.6f, py - 17.5f, px + 5.6f, py - 10f)
        canvas.drawArc(rect, 180f, 180f, true, paint)

        val eyeX = px + player.facingX * 2.6f
        val eyeY = py - 11.4f + player.facingY * 1.4f
        paint.color = 0xFF211A14.toInt()
        canvas.drawCircle(eyeX - 2f, eyeY, 0.9f, paint)
        canvas.drawCircle(eyeX + 2f, eyeY, 0.9f, paint)

        if (player.isSwinging) drawSwing(canvas, player, px, py)
    }

    private fun drawSwing(canvas: Canvas, player: Player, px: Float, py: Float) {
        val progress = 1f - player.swingTimer / Player.SWING_DURATION
        val facingAngle = Math.toDegrees(
            atan2(player.facingY.toDouble(), player.facingX.toDouble()),
        ).toFloat()
        val start = facingAngle - 55f + progress * 110f
        val alpha = (235f * (1f - progress)).toInt().coerceIn(0, 255)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(alpha, 255, 238, 200)
        rect.set(px - 27f, py - 27f, px + 27f, py + 27f)
        canvas.drawArc(rect, start, 24f, false, paint)

        val angle = Math.toRadians((start + 24f).toDouble())
        paint.color = Color.argb(alpha, 255, 250, 230)
        val innerX = px + cos(angle).toFloat() * 15f
        val innerY = py + sin(angle).toFloat() * 15f
        val outerX = px + cos(angle).toFloat() * 29f
        val outerY = py + sin(angle).toFloat() * 29f
        canvas.drawLine(innerX, innerY, outerX, outerY, paint)

        paint.style = Paint.Style.FILL
    }

    private companion object {
        val CLOAK = 0xFF3F6B4A.toInt()
        val CLOAK_LIGHT = 0xFF4C7D58.toInt()
        val SKIN = 0xFFE4BE93.toInt()
        val HAIR = 0xFF52351F.toInt()
        val BOOTS = 0xFF3A2B1E.toInt()
    }
}
