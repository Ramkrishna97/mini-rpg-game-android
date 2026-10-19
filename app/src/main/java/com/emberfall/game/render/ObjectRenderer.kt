package com.emberfall.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.entity.Campfire
import com.emberfall.game.entity.FloatingText
import com.emberfall.game.entity.LootChest
import com.emberfall.game.entity.Particle
import com.emberfall.game.entity.Pickup
import kotlin.math.PI
import kotlin.math.sin

/** Campfires, chests, loot and floating combat text. */
class ObjectRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()

    /** 8-frame flame sheet driven by [SpriteAnimation]. */
    private val flame = SpriteAnimation(createFlameSheet(), 8, 1, 0.085f, true)

    init {
        textPaint.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE,
            android.graphics.Typeface.BOLD,
        )
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 9f
    }

    fun update(dt: Float) {
        flame.update(dt)
    }

    fun drawCampfire(canvas: Canvas, fire: Campfire, graphics: GraphicsSettings) {
        val x = fire.x
        val y = fire.y

        paint.color = 0x40000000
        rect.set(x - 15f, y + 6f, x + 15f, y + 13f)
        canvas.drawOval(rect, paint)

        // Stone ring.
        paint.color = 0xFF7A7468.toInt()
        for (i in 0 until 7) {
            val angle = i * 0.9f
            canvas.drawCircle(
                x + kotlin.math.cos(angle) * 14f,
                y + kotlin.math.sin(angle) * 7f + 6f,
                3.2f,
                paint,
            )
        }

        // Logs.
        paint.color = 0xFF5A3D25.toInt()
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - 9f, y + 8f, x + 9f, y + 2f, paint)
        canvas.drawLine(x - 9f, y + 2f, x + 9f, y + 8f, paint)

        // Sprite-sheet flame: smoother and cheaper than re-drawing paths.
        flame.draw(canvas, x, y - 11f, 30f, 38f, flamePaint)

        if (graphics.quality == com.emberfall.game.core.GraphicsQuality.HIGH) {
            paint.color = 0x33FFB347
            canvas.drawCircle(x, y - 4f, 22f, paint)
        }
    }

    private fun createFlameSheet(): Bitmap {
        val frames = 8
        val size = 64
        val sheet = Bitmap.createBitmap(frames * size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        for (frame in 0 until frames) {
            val phase = frame / frames.toFloat() * (PI * 2).toFloat()
            val cx = frame * size + size / 2f
            val baseY = 58f
            val sway = sin(phase) * 3f
            val height = 38f + sin(phase * 2f) * 5f

            p.color = 0xFFFF7A2F.toInt()
            path.reset()
            path.moveTo(cx - 9f, baseY)
            path.quadTo(cx - 7f + sway, baseY - height * 0.55f, cx + sway, baseY - height)
            path.quadTo(cx + 7f + sway, baseY - height * 0.55f, cx + 9f, baseY)
            path.close()
            canvas.drawPath(path, p)

            p.color = 0xFFFFC24A.toInt()
            path.reset()
            val innerHeight = height * 0.62f
            path.moveTo(cx - 5f, baseY)
            path.quadTo(cx - 3f + sway * 0.5f, baseY - innerHeight * 0.55f, cx + sway * 0.5f, baseY - innerHeight)
            path.quadTo(cx + 3f + sway * 0.5f, baseY - innerHeight * 0.55f, cx + 5f, baseY)
            path.close()
            canvas.drawPath(path, p)

            p.color = 0xFFFFF0B0.toInt()
            canvas.drawCircle(cx + sway * 0.4f, baseY - height * 0.25f, 3.2f, p)
        }
        return sheet
    }

    fun drawChest(canvas: Canvas, chest: LootChest) {
        val x = chest.x
        val y = chest.y

        paint.color = 0x40000000
        rect.set(x - 13f, y + 8f, x + 13f, y + 14f)
        canvas.drawOval(rect, paint)

        paint.color = 0xFF6B4A2A.toInt()
        rect.set(x - 12f, y - 4f, x + 12f, y + 10f)
        canvas.drawRoundRect(rect, 2f, 2f, paint)

        paint.color = 0xFF8A6238.toInt()
        rect.set(x - 13f, y - 9f, x + 13f, y - 1f)
        canvas.drawRoundRect(rect, 3f, 3f, paint)

        paint.color = 0xFFD9B45A.toInt()
        rect.set(x - 2.5f, y - 4f, x + 2.5f, y + 3f)
        canvas.drawRect(rect, paint)

        if (chest.opened) {
            paint.color = 0x66FFE08A
            canvas.drawCircle(x, y - 2f, 10f, paint)
        }
    }

    fun drawPickup(canvas: Canvas, pickup: Pickup) {
        val x = pickup.x
        val y = pickup.y + pickup.bobOffset
        paint.color = pickup.item.color
        path.reset()
        path.moveTo(x, y - 7f)
        path.lineTo(x + 6f, y)
        path.lineTo(x, y + 7f)
        path.lineTo(x - 6f, y)
        path.close()
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        if (pickup.count > 1) {
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textSize = 7f
            canvas.drawText("x${pickup.count}", x, y - 10f, textPaint)
        }
    }

    fun drawFloatingText(canvas: Canvas, text: FloatingText) {
        val alpha = (text.life.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        textPaint.color = Color.argb(
            alpha,
            (text.color shr 16) and 0xFF,
            (text.color shr 8) and 0xFF,
            text.color and 0xFF,
        )
        textPaint.textSize = 9.5f
        canvas.drawText(text.text, text.x, text.y, textPaint)
    }

    fun drawParticle(canvas: Canvas, particle: Particle) {
        val alpha = (particle.life.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        paint.color = Color.argb(alpha, Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color))
        canvas.drawRect(
            particle.x - particle.size / 2f,
            particle.y - particle.size / 2f,
            particle.x + particle.size / 2f,
            particle.y + particle.size / 2f,
            paint,
        )
    }
}
