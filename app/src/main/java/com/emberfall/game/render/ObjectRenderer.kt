package com.emberfall.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.content.Context
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.entity.Campfire
import com.emberfall.game.entity.FloatingText
import com.emberfall.game.entity.LootChest
import com.emberfall.game.entity.Particle
import com.emberfall.game.entity.Pickup
import kotlin.math.PI
import kotlin.math.sin

/** Campfires, chests, loot and floating combat text. */
class ObjectRenderer(context: Context) {

    private val art = ArtSheets.from(context)

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
        val flicker = sin(fire.flamePhase * 2.3f) * .06f
        flame.draw(canvas, x, y - 11f, 30f * (1f-flicker), 38f * (1f+flicker), flamePaint)

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
        val frame = if (!chest.opened) 0 else (1 + (chest.openTimer * 5f).toInt()).coerceAtMost(3)
        art.chest(canvas, chest.x, chest.y, frame)
        if (chest.opened) {
            paint.color = 0x66FFE08A
            canvas.drawCircle(chest.x, chest.y - 11f, 6f + chest.openTimer * 10f, paint)
        }
    }

    fun drawPickup(canvas: Canvas, pickup: Pickup) {
        val x = pickup.x
        val y = pickup.y + pickup.bobOffset
        art.item(canvas, pickup.item, x, y - 4f, 26f)
        if (pickup.item == com.emberfall.game.item.ItemId.MAGIC_STONE) {
            paint.color = 0x557A6BD9
            canvas.drawCircle(x, y - 4f, 13f, paint)
        }
        if (pickup.count > 1) {
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textSize = 7f
            canvas.drawText("x${pickup.count}", x, y - 15f, textPaint)
        }
    }

    fun drawFloatingText(canvas: Canvas, text: FloatingText) {
        val alpha = ((text.life / text.maxLife).coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        textPaint.color = Color.argb(
            alpha,
            (text.color shr 16) and 0xFF,
            (text.color shr 8) and 0xFF,
            text.color and 0xFF,
        )
        textPaint.textSize = 9.5f
        canvas.drawText(text.text, text.x + text.offsetX, text.y + text.offsetY, textPaint)
    }

    fun drawParticle(canvas: Canvas, particle: Particle) {
        val alpha = ((particle.life / particle.maxLife).coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        paint.color = Color.argb(alpha, Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color))
        val size = particle.size * if (particle.shrink) (particle.life / particle.maxLife).coerceIn(0f, 1f) else 1f
        canvas.drawRect(
            particle.x - size / 2f,
            particle.y - size / 2f,
            particle.x + size / 2f,
            particle.y + size / 2f,
            paint,
        )
    }
}
