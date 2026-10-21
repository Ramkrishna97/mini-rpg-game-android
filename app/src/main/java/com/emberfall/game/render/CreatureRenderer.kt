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
import com.emberfall.game.entity.BossMove
import com.emberfall.game.entity.BossSignatures
import com.emberfall.game.entity.Wolf
import kotlin.math.sin

/** Wildlife sheets, boss sheets, and a vector fallback for hostile wolves. */
class CreatureRenderer(context: Context) {

    private val art = ArtSheets.from(context)

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
        val size = if (graphics.quality == com.emberfall.game.core.GraphicsQuality.LOW) 39f else 43f
        art.animal(canvas, animal.species, animal.x, animal.y,
            (animal.walkCycle * .65f).toInt(), animal.facingX,
            if (animal.species == AnimalSpecies.SHARK) size + 7f else size)
        if (animal.species == AnimalSpecies.SHARK) {
            paint.color = 0x997AD2E7.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            val wake = (animal.walkCycle * 5f) % 8f
            canvas.drawOval(animal.x - 16f - wake, animal.y - 4f,
                animal.x + 16f + wake, animal.y + 5f, paint)
            paint.style = Paint.Style.FILL
        }
        flash(canvas, animal.x, animal.y - 10f, animal.radius, animal.hitFlash)
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

        // A visible wind-up / release layer makes boss attacks readable even
        // when the sprite's attack frame is momentarily obscured by terrain.
        if (boss.engaged && (boss.phase == BossPhase.TELEGRAPH || boss.phase == BossPhase.DASH)) {
            val pulse = .65f + .35f * sin(elapsed * 18f)
            paint.color = (tellColor and 0x00FFFFFF) or (((if (boss.phase == BossPhase.DASH) 120 else 70) * pulse).toInt() shl 24)
            when (boss.activeMove) {
                com.emberfall.game.entity.BossMove.BREATH -> {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                    canvas.drawArc(x - r*1.7f, y-r*1.4f, x+r*1.7f, y+r*1.1f, -75f, 150f, false, paint); paint.style = Paint.Style.FILL
                }
                com.emberfall.game.entity.BossMove.SHOT, com.emberfall.game.entity.BossMove.SPINE_FAN,
                com.emberfall.game.entity.BossMove.DIRT_ARC, com.emberfall.game.entity.BossMove.TALONS -> {
                    canvas.drawCircle(x + boss.chargeDirX*r, y - r*.55f + boss.chargeDirY*r*.25f, r*.52f*pulse, paint)
                }
                else -> {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
                    canvas.drawCircle(x, y, r * (1.15f + .2f*pulse), paint); paint.style = Paint.Style.FILL
                }
            }
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
                if (boss.defeated) elapsed - boss.defeatedAtSeconds
                    else if (state == AnimationState.ATTACK) boss.visualProgress.coerceIn(0f, .999f) / 3f
                    else elapsed,
                if (boss.isDragon) 76f else 52f)
            else -> drawDragon(canvas, x, y - lift, boss.facingX, boss.facingY, r * 1.6f, boss.def.color, elapsed)
        }
        drawBossAttackEffect(canvas, boss, tellColor, elapsed)
        flash(canvas, x, y - 15f, r, boss.hitFlash)
    }

    /** Attack effects draw in front of the boss sprite and track the simulation phase. */
    private fun drawBossAttackEffect(canvas: Canvas, boss: Boss, color: Int, elapsed: Float) {
        if (!boss.engaged || boss.defeated) return
        val active = boss.phase == BossPhase.TELEGRAPH || boss.phase == BossPhase.DASH
        if (!active && boss.magicImpactTimer <= 0f) return
        val x = boss.x
        val y = boss.y - boss.airborne * .6f
        val vx = boss.chargeDirX - boss.chargeDirY
        val vy = (boss.chargeDirX + boss.chargeDirY) * .5f
        val len = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(.01f)
        val fx = vx / len
        val fy = vy / len
        val progress = boss.visualProgress
        val releasing = boss.phase == BossPhase.DASH
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        paint.color = (color and 0x00FFFFFF) or (if (releasing) 0xDD000000.toInt() else 0x88000000.toInt())
        paint.strokeWidth = if (releasing) 5f else 2.5f

        if (active) when (boss.activeMove) {
            BossMove.ROLL, BossMove.SWOOP, BossMove.POUNCE, BossMove.AMBUSH, BossMove.BURROW -> {
                val reach = if (releasing) 20f + 33f * progress else 14f + 12f * progress
                val cx = x + fx * reach
                val cy = y - 11f + fy * reach
                canvas.drawArc(cx - 25f, cy - 18f, cx + 25f, cy + 18f,
                    Math.toDegrees(kotlin.math.atan2(fy.toDouble(), fx.toDouble())).toFloat() - 65f,
                    130f, false, paint)
                paint.strokeWidth = 1.5f; paint.color = 0xDDFFFFFF.toInt()
                canvas.drawLine(x, y - 13f, cx, cy, paint)
            }
            BossMove.BREATH -> {
                val spread = if (releasing) 30f else 13f + progress * 14f
                val reach = if (releasing) 78f else 32f + progress * 30f
                path.reset(); path.moveTo(x + fx * 12f, y - 12f + fy * 12f)
                path.lineTo(x + fx * reach - fy * spread, y - 12f + fy * reach + fx * spread * .55f)
                path.lineTo(x + fx * reach + fy * spread, y - 12f + fy * reach - fx * spread * .55f)
                path.close()
                paint.style = Paint.Style.FILL
                paint.color = (color and 0x00FFFFFF) or (if (releasing) 0x99000000.toInt() else 0x55000000)
                canvas.drawPath(path, paint)
                paint.color = 0xCCFFE4A4.toInt()
                canvas.drawCircle(x + fx * reach * .7f, y - 12f + fy * reach * .7f,
                    3f + 2f * kotlin.math.sin(elapsed * 25f), paint)
            }
            BossMove.RING, BossMove.HOWL -> {
                val radius = boss.radius + 10f + (if (releasing) progress * 60f else progress * 13f)
                canvas.drawOval(x - radius, y - radius * .48f, x + radius, y + radius * .48f, paint)
                paint.color = 0xCCFFFFFF.toInt(); paint.strokeWidth = 1.4f
                canvas.drawOval(x - radius + 4f, y - radius * .48f + 2f,
                    x + radius - 4f, y + radius * .48f - 2f, paint)
            }
            BossMove.SHOT, BossMove.SPINE_FAN, BossMove.DIRT_ARC, BossMove.TALONS -> {
                val bx = x + fx * (boss.radius + 7f)
                val by = y - 14f + fy * (boss.radius + 7f)
                paint.style = Paint.Style.FILL
                paint.color = (color and 0x00FFFFFF) or 0xCC000000.toInt()
                canvas.drawCircle(bx, by, if (releasing) 9f else 4f + 5f * progress, paint)
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawCircle(bx, by, 2.5f, paint)
                if (releasing) for (i in -1..1) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
                    canvas.drawLine(bx, by, bx + fx * 25f - fy * i * 9f,
                        by + fy * 25f + fx * i * 9f, paint)
                }
            }
            BossMove.MAGIC -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(x, y - 15f, 16f + progress * 13f, paint)
                canvas.drawLine(x - 12f, y - 14f, x + 12f, y - 14f, paint)
            }
        }
        if (boss.magicImpactTimer > 0f) {
            val offset = IsoMath.worldToIso(boss.magicTargetX - boss.x, boss.magicTargetY - boss.y)
            val t = 1f - boss.magicImpactTimer / .55f
            val radius = 18f + 52f * t
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f * (1f - t) + 1f
            paint.color = (color and 0x00FFFFFF) or (((180f * (1f - t)).toInt()) shl 24)
            canvas.drawOval(x + offset.x - radius, boss.y + offset.y - radius * .48f,
                x + offset.x + radius, boss.y + offset.y + radius * .48f, paint)
            paint.color = 0xCCFFFFFF.toInt(); paint.strokeWidth = 2f
            canvas.drawLine(x + offset.x - radius * .4f, boss.y + offset.y,
                x + offset.x + radius * .4f, boss.y + offset.y, paint)
        }
        paint.style = Paint.Style.FILL
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
