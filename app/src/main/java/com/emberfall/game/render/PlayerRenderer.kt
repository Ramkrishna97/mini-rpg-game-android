package com.emberfall.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.emberfall.game.entity.Player
import com.emberfall.game.entity.PlayerAction
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
        val animationTime = if (player.isSwinging)
            (1f - player.swingTimer / Player.SWING_DURATION).coerceIn(0f, .999f) / 3f
            else elapsed
        sprite.draw(canvas, x, y, player.facingX, player.facingY, state, animationTime, 42f)
        drawWeapon(canvas, player, weapon)
        if (player.hurtFlash > 0f) {
            flash.color = 0x55FF4545
            canvas.drawOval(x - 18f, y - 35f, x + 18f, y + 2f, flash)
        }
    }

    private fun drawWeapon(canvas: Canvas, player: Player, weapon: ItemId?) {
        val x = player.x
        val y = player.y - 16f
        val angle = atan2((player.facingX + player.facingY) * .5f,
            player.facingX - player.facingY)
        val progress = (1f - player.swingTimer / Player.SWING_DURATION).coerceIn(0f, 1f)
        flash.strokeCap = Paint.Cap.ROUND
        flash.style = Paint.Style.STROKE

        if (player.isSwinging && player.action == PlayerAction.MAGIC) {
            val radius = 8f + 10f * progress
            flash.color = (player.actionColor and 0x00FFFFFF) or 0xDD000000.toInt()
            flash.strokeWidth = 3.2f
            canvas.drawCircle(x, y - 4f, radius, flash)
            flash.color = 0xCCFFFFFF.toInt(); flash.strokeWidth = 1.4f
            canvas.drawArc(x - radius, y - 4f - radius, x + radius, y - 4f + radius,
                progress * 240f, 135f, false, flash)
            flash.style = Paint.Style.FILL
            flash.color = player.actionColor
            canvas.drawCircle(x + cos(angle) * 15f, y - 4f + sin(angle) * 9f,
                4f + 3f * (1f - progress), flash)
            return
        }

        val item = weapon ?: return
        if (item.style == WeaponStyle.PROJECTILE) {
            val handX = x + cos(angle) * 9f
            val handY = y + sin(angle) * 7f
            if (item == ItemId.BOW) {
                flash.color = 0xFF8A5D32.toInt(); flash.strokeWidth = 3f
                canvas.drawArc(handX - 8f, handY - 12f, handX + 8f, handY + 12f,
                    Math.toDegrees(angle.toDouble()).toFloat() - 78f, 156f, false, flash)
                flash.color = 0xFFEADBB8.toInt(); flash.strokeWidth = 1f
                canvas.drawLine(handX - 5f, handY - 8f, handX - 5f, handY + 8f, flash)
                if (player.isSwinging) {
                    flash.color = 0xFFFFE7B0.toInt(); flash.strokeWidth = 2f
                    canvas.drawLine(handX, handY, handX + cos(angle) * (17f + progress * 14f),
                        handY + sin(angle) * (17f + progress * 14f), flash)
                }
            } else {
                flash.color = 0xFF69452E.toInt(); flash.strokeWidth = 5f
                canvas.drawLine(handX, handY + 3f, handX + cos(angle) * 10f,
                    handY + sin(angle) * 7f, flash)
                flash.color = 0xFFC3D0D7.toInt(); flash.strokeWidth = 3f
                val tipX = handX + cos(angle) * 23f
                val tipY = handY + sin(angle) * 16f
                canvas.drawLine(handX + cos(angle) * 8f, handY + sin(angle) * 6f, tipX, tipY, flash)
                if (player.isSwinging && progress < .45f) {
                    flash.style = Paint.Style.FILL; flash.color = 0xFFFFC45C.toInt()
                    canvas.drawCircle(tipX, tipY, 7f * (1f - progress), flash)
                    flash.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(tipX, tipY, 3f, flash)
                }
            }
            flash.style = Paint.Style.FILL
            return
        }

        val sweep = if (player.isSwinging) -1.15f + progress * 2.3f else .3f
        val bladeAngle = angle + sweep
        val hiltX = x + cos(bladeAngle) * 12f
        val hiltY = y + sin(bladeAngle) * 9f
        flash.color = 0xFF704A2D.toInt(); flash.strokeWidth = 3.5f
        canvas.drawLine(x, y + 6f, hiltX, hiltY, flash)
        val tipX = hiltX + cos(bladeAngle) * 20f
        val tipY = hiltY + sin(bladeAngle) * 15f
        flash.color = if (item == ItemId.WOODEN_SWORD) 0xFFC9985D.toInt() else 0xFFE3EFF5.toInt()
        flash.strokeWidth = if (item == ItemId.RING_BLADE) 2.5f else 4f
        if (item == ItemId.RING_BLADE) canvas.drawCircle(hiltX + cos(bladeAngle) * 14f,
            hiltY + sin(bladeAngle) * 11f, 9f, flash)
        else canvas.drawLine(hiltX, hiltY, tipX, tipY, flash)
        flash.color = 0xFFE8BD6D.toInt(); flash.strokeWidth = 3f
        canvas.drawLine(hiltX - sin(bladeAngle) * 5f, hiltY + cos(bladeAngle) * 5f,
            hiltX + sin(bladeAngle) * 5f, hiltY - cos(bladeAngle) * 5f, flash)
        if (player.isSwinging) {
            val arcColor = if (item == ItemId.CROSS_SWORD) 0xFF79D6FF.toInt() else 0xFFFFE3A0.toInt()
            flash.color = (arcColor and 0x00FFFFFF) or 0xCC000000.toInt()
            flash.strokeWidth = if (item.style == WeaponStyle.RING) 5f else 4f
            val start = Math.toDegrees((angle - 1.2f + progress * 1.6f).toDouble()).toFloat()
            val sweepAngle = if (item.style == WeaponStyle.RING) 250f else 70f
            canvas.drawArc(x - 34f, y - 30f, x + 34f, y + 30f, start,
                sweepAngle, false, flash)
            flash.color = 0xCCFFFFFF.toInt(); flash.strokeWidth = 1.4f
            canvas.drawArc(x - 32f, y - 28f, x + 32f, y + 28f, start + 9f,
                sweepAngle - 16f, false, flash)
        }
        flash.style = Paint.Style.FILL
    }
}
