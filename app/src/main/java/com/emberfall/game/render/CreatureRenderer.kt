package com.emberfall.game.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.entity.Animal
import com.emberfall.game.entity.AnimalSpecies
import com.emberfall.game.entity.Boss
import com.emberfall.game.entity.BossPhase
import com.emberfall.game.entity.Wolf
import kotlin.math.sin

/**
 * Vector creature art. Everything is drawn with antialiased primitives and
 * scales cleanly with the camera; quality settings drop small details on
 * low-end devices (the 2D equivalent of a low-poly LOD).
 */
class CreatureRenderer(context: Context) {

    private val bossSprites = mapOf(
        "gloomfang" to IsoAnimation(context, "gloomfang"),
        "stonewarden" to IsoAnimation(context, "stonewarden"),
        "mirewitch" to IsoAnimation(context, "mirewitch"),
        "ashtitan" to IsoAnimation(context, "ashtitan"),
        "hollowking" to IsoAnimation(context, "hollowking"),
    )

    private val rim = android.graphics.PorterDuffColorFilter(0xFFFFE6A0.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
    private val offsets = arrayOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    fun configure(graphics: GraphicsSettings) {
        paint.isAntiAlias = graphics.antiAliasing
    }

    fun drawAnimal(canvas: Canvas, animal: Animal, graphics: GraphicsSettings) {
        val x = animal.x
        val y = animal.y
        val r = animal.radius
        val detailed = graphics.quality != com.emberfall.game.core.GraphicsQuality.LOW

        shadow(canvas, x, y + r * 0.7f, r)

        val bodyColor = when (animal.species) {
            AnimalSpecies.RABBIT -> 0xFFCFC7B8.toInt()
            AnimalSpecies.DEER -> 0xFFA97C50.toInt()
            AnimalSpecies.BOAR -> 0xFF6E5A48.toInt()
        }

        paint.color = bodyColor
        rect.set(x - r, y - r * 0.6f, x + r, y + r * 0.55f)
        canvas.drawOval(rect, paint)

        // Head sits toward the facing direction.
        val headX = x + animal.facingX * r * 0.85f
        val headY = y - r * 0.55f + animal.facingY * r * 0.3f
        canvas.drawCircle(headX, headY, r * 0.52f, paint)

        paint.color = 0xFF3A2D22.toInt()
        if (animal.species == AnimalSpecies.DEER && detailed) {
            paint.strokeWidth = 1.6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(headX - 2f, headY - 4f, headX - 5f, headY - 11f, paint)
            canvas.drawLine(headX + 2f, headY - 4f, headX + 5f, headY - 11f, paint)
            canvas.drawLine(headX - 5f, headY - 9f, headX - 9f, headY - 12f, paint)
            canvas.drawLine(headX + 5f, headY - 9f, headX + 9f, headY - 12f, paint)
            paint.style = Paint.Style.FILL
        } else if (detailed) {
            canvas.drawCircle(headX - 2f, headY - 4f, 1.6f, paint)
            canvas.drawCircle(headX + 2f, headY - 4f, 1.6f, paint)
        }

        paint.color = 0xFF211A14.toInt()
        canvas.drawCircle(headX - 1.8f, headY, 0.9f, paint)
        canvas.drawCircle(headX + 1.8f, headY, 0.9f, paint)

        if (detailed) {
            paint.color = 0xFF4A3A2C.toInt()
            paint.strokeWidth = 1.4f
            paint.style = Paint.Style.STROKE
            val step = sin(animal.walkCycle)
            canvas.drawLine(x - r * 0.5f, y + r * 0.4f, x - r * 0.5f + step, y + r * 0.95f, paint)
            canvas.drawLine(x + r * 0.4f, y + r * 0.4f, x + r * 0.4f - step, y + r * 0.95f, paint)
            paint.style = Paint.Style.FILL
        }

        flash(canvas, animal.x, animal.y, r, animal.hitFlash)
    }

    fun drawWolf(canvas: Canvas, wolf: Wolf, elapsed: Float) {
        for ((dx,dy) in offsets) {
            canvas.save(); canvas.translate(dx,dy); paint.colorFilter = rim
            drawWolfBody(canvas,wolf,elapsed,outline = true)
            canvas.restore()
        }
        paint.colorFilter = null
        drawWolfBody(canvas,wolf,elapsed,outline = false)
    }

    private fun drawWolfBody(canvas: Canvas, wolf: Wolf, elapsed: Float, outline: Boolean) {
        val x = wolf.x
        val y = wolf.y
        val r = wolf.radius
        if (!outline) shadow(canvas, x, y + r * 0.7f, r)

        canvas.save()
        val breath = if (!wolf.moving) .05f * sin(elapsed * 4f + x) else 0f
        canvas.scale(1f - breath, 1f + breath, x, y)
        paint.color = 0xFFFFE6A0.toInt()
        canvas.drawOval(x-r-1f, y-r*.55f-1f, x+r+1f, y+r*.5f+1f, paint)
        paint.color = 0xFF767C86.toInt()
        rect.set(x - r, y - r * 0.55f, x + r, y + r * 0.5f)
        canvas.drawOval(rect, paint)

        // Tail.
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            x - wolf.facingX * r,
            y,
            x - wolf.facingX * (r + 8f),
            y - 6f,
            paint,
        )

        val headX = x + wolf.facingX * r * 0.9f
        val headY = y - r * 0.45f + wolf.facingY * r * 0.4f
        canvas.drawCircle(headX, headY, r * 0.55f, paint)

        // Ears.
        path.reset()
        path.moveTo(headX - 6f, headY - 4f)
        path.lineTo(headX - 2f, headY - 12f)
        path.lineTo(headX + 1f, headY - 5f)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(headX + 6f, headY - 4f)
        path.lineTo(headX + 2f, headY - 12f)
        path.lineTo(headX - 1f, headY - 5f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = 0xFFFF5A4A.toInt()
        canvas.drawCircle(headX - 2.4f, headY, 1.1f, paint)
        canvas.drawCircle(headX + 2.4f, headY, 1.1f, paint)

        if (wolf.aggro && !outline) {
            paint.style = Paint.Style.STROKE
            paint.color = 0x66FF5A4A
            paint.strokeWidth = 1.5f
            canvas.drawCircle(x, y, r + 6f, paint)
            paint.style = Paint.Style.FILL
        }

        flash(canvas, x, y, r, wolf.hitFlash)
        canvas.restore()
        if (!outline && (wolf.state == com.emberfall.game.entity.EnemyState.ALERT || wolf.state == com.emberfall.game.entity.EnemyState.ATTACK_WINDUP)) {
            paint.color = 0xFFFFDA70.toInt(); paint.textSize = 20f
            canvas.drawText("!", x - 3f, y - 23f, paint)
        }
    }

    fun drawBoss(canvas: Canvas, boss: Boss, graphics: GraphicsSettings, elapsed: Float) {
        val x = boss.x
        val y = boss.y
        val r = boss.radius

        // Telegraph: a red corridor in the locked lunge direction.
        if (boss.engaged && boss.phase == BossPhase.TELEGRAPH && boss.attackType == 4) {
            val offset = IsoMath.worldToIso(boss.magicTargetX - x, boss.magicTargetY - y)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = 0xFFDA86FF.toInt()
            canvas.drawOval(x+offset.x-65f, y+offset.y-32f, x+offset.x+65f, y+offset.y+32f, paint)
            paint.style = Paint.Style.FILL
        }
        if (boss.engaged && boss.phase == BossPhase.TELEGRAPH && boss.attackType != 4) {
            paint.color = 0x55FF3B30
            paint.strokeWidth = r * 1.4f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(
                x,
                y,
                x + (boss.chargeDirX - boss.chargeDirY) * 150f,
                y + (boss.chargeDirX + boss.chargeDirY) * 75f,
                paint,
            )
        }

        shadow(canvas, x, y, r * 1.15f)
        val state = when (boss.phase) {
            BossPhase.CHASE -> if (boss.engaged) AnimationState.WALK else AnimationState.IDLE
            BossPhase.TELEGRAPH, BossPhase.DASH -> AnimationState.ATTACK
            BossPhase.RECOVER -> AnimationState.IDLE
        }
        bossSprites[boss.def.id]?.draw(canvas, x, y, boss.facingX, boss.facingY, state, elapsed, 52f)
        flash(canvas, x, y - 15f, r, boss.hitFlash)
    }

    private fun shadow(canvas: Canvas, x: Float, y: Float, r: Float) {
        paint.color = 0x22000000
        rect.set(x - r * 1.15f, y - r * 0.45f, x + r * 1.15f, y + r * 0.5f)
        canvas.drawOval(rect, paint)
        paint.color = 0x4A000000
        rect.set(x - r * 0.85f, y - r * 0.28f, x + r * 0.85f, y + r * 0.38f)
        canvas.drawOval(rect, paint)
    }

    private fun flash(canvas: Canvas, x: Float, y: Float, r: Float, hitFlash: Float) {
        if (hitFlash <= 0f) return
        val alpha = (hitFlash / 0.22f * 130f).toInt().coerceIn(0, 130)
        paint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(x, y, r, paint)
    }
}
