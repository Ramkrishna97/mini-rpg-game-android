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
import com.emberfall.game.entity.BossSignatures
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
        "dragon" to IsoAnimation(context, "dragon"),
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
            AnimalSpecies.BIRD -> 0xFF8AA7B8.toInt()
            AnimalSpecies.FOX -> 0xFFC06B3E.toInt()
            AnimalSpecies.DOG -> 0xFF9C8469.toInt()
            AnimalSpecies.SHARK -> 0xFF568092.toInt()
            AnimalSpecies.RAT -> 0xFF77716D.toInt()
            AnimalSpecies.SHEEP -> 0xFFE8E4D7.toInt()
            AnimalSpecies.CHICKEN -> 0xFFF2D56B.toInt()
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
        val tellColor = BossSignatures.byId(boss.def.id)?.telegraphColor
            ?: ((boss.def.color and 0x00FFFFFF) or 0x66000000)

        // Telegraph: a red corridor in the locked lunge direction.
        if (boss.engaged && boss.phase == BossPhase.TELEGRAPH && boss.attackType == 4) {
            val offset = IsoMath.worldToIso(boss.magicTargetX - x, boss.magicTargetY - y)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = tellColor
            canvas.drawOval(x+offset.x-65f, y+offset.y-32f, x+offset.x+65f, y+offset.y+32f, paint)
            paint.style = Paint.Style.FILL
        }
        if (boss.engaged && boss.phase == BossPhase.TELEGRAPH && boss.attackType != 4) {
            paint.color = tellColor
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
        val state = if (boss.defeated) AnimationState.DEATH else if (boss.dragonFlying) AnimationState.WALK else when (boss.phase) {
            BossPhase.CHASE -> if (boss.engaged) AnimationState.WALK else AnimationState.IDLE
            BossPhase.TELEGRAPH, BossPhase.DASH -> AnimationState.ATTACK
            BossPhase.RECOVER -> AnimationState.IDLE
        }
        val lift = boss.airborne
        val sprite = bossSprites[boss.def.id]
        when {
            boss.isBurrowed && !boss.defeated -> drawMoleMound(canvas, x, y, r, elapsed)
            sprite != null -> sprite.draw(canvas, x, y - lift, boss.facingX, boss.facingY, state,
                if (boss.defeated) elapsed - boss.defeatedAtSeconds else elapsed,
                if (boss.isDragon) 76f else 52f)
            else -> drawDragon(canvas, x, y - lift, boss.facingX, boss.facingY, r * 1.6f, boss.def.color, elapsed)
        }
        flash(canvas, x, y - 15f, r, boss.hitFlash)
    }

    /** Dirt mound with puffs while the mole is underground. */
    private fun drawMoleMound(canvas: Canvas, x: Float, y: Float, r: Float, elapsed: Float) {
        paint.color = 0xFF6B5030.toInt()
        rect.set(x - r * 1.1f, y - r * .35f, x + r * 1.1f, y + r * .3f)
        canvas.drawOval(rect, paint)
        paint.color = 0xFF8A6A44.toInt()
        for (i in 0 until 4) {
            val puff = (elapsed * 3f + i * .25f) % 1f
            canvas.drawCircle(x + (i - 1.5f) * 6f, y - r * .4f - puff * 10f, 2.2f * (1f - puff), paint)
        }
    }

    /** Vector dragon, flapping wings. Used because no dragon sprite is bundled. */
    private fun drawDragon(
        canvas: Canvas, x: Float, y: Float, fx: Float, fy: Float,
        size: Float, color: Int, elapsed: Float,
    ) {
        val flap = sin(elapsed * 5f) * size * .35f
        val wing = Color.rgb(
            (Color.red(color) * .7f).toInt(), (Color.green(color) * .7f).toInt(), (Color.blue(color) * .7f).toInt(),
        )

        // Tail
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .18f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - fx * size * .5f, y, x - fx * size * 1.05f, y + size * .2f, paint)
        paint.style = Paint.Style.FILL

        // Wings, behind the body
        paint.color = wing
        path.reset()
        path.moveTo(x - size * .1f, y - size * .15f)
        path.lineTo(x - size * .85f, y - size * .75f - flap)
        path.lineTo(x - size * .45f, y + size * .05f)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(x + size * .1f, y - size * .15f)
        path.lineTo(x + size * .85f, y - size * .75f - flap)
        path.lineTo(x + size * .45f, y + size * .05f)
        path.close()
        canvas.drawPath(path, paint)

        // Body
        paint.color = color
        rect.set(x - size * .42f, y - size * .3f, x + size * .42f, y + size * .3f)
        canvas.drawOval(rect, paint)

        // Head, horns and glowing eye
        val hx = x + fx * size * .45f
        val hy = y - size * .32f + fy * size * .15f
        canvas.drawCircle(hx, hy, size * .22f, paint)
        paint.color = 0xFF3A2A22.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .06f
        canvas.drawLine(hx - size * .08f, hy - size * .16f, hx - size * .18f, hy - size * .36f, paint)
        canvas.drawLine(hx + size * .08f, hy - size * .16f, hx + size * .18f, hy - size * .36f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFE066.toInt()
        canvas.drawCircle(hx + fx * size * .08f, hy - size * .04f, size * .05f, paint)
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
