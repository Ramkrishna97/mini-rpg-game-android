package com.emberfall.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Game

/** Full-screen inventory overlay. Simulation is paused while it is open. */
class InventoryPanelRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, game: Game, width: Float, height: Float, density: Float) {
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        val panel = UiLayout.inventoryPanel(width, height, density)
        paint.color = 0xF21A231C.toInt()
        canvas.drawRoundRect(panel, 12f * density, 12f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0x55E8A64C
        canvas.drawRoundRect(panel, 12f * density, 12f * density, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 15f * density
        canvas.drawText("INVENTORY", panel.left + 18f * density, panel.top + 30f * density, textPaint)

        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "LV ${game.player.stats.level}  ${game.player.stats.xp}/${game.player.stats.xpToNext()} XP",
            panel.right - 18f * density,
            panel.top + 30f * density,
            textPaint,
        )

        val equipped = game.inventory.bestWeapon()
        for (index in 0 until UiLayout.INVENTORY_COLUMNS * UiLayout.INVENTORY_ROWS) {
            val cell = UiLayout.inventoryCell(index, width, height, density)
            paint.color = 0xFF232D25.toInt()
            canvas.drawRoundRect(cell, 5f * density, 5f * density, paint)

            val stack = game.inventory.slot(index)
            if (stack == null) continue

            val inset = 7f * density
            rect.set(cell.left + inset, cell.top + inset, cell.right - inset, cell.top + cell.height() * 0.68f)
            paint.color = stack.id.color
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = 0xFF10140F.toInt()
            textPaint.textSize = 16f * density
            canvas.drawText(
                stack.id.iconLetter,
                cell.centerX(),
                cell.top + cell.height() * 0.47f,
                textPaint,
            )

            textPaint.color = 0xFFE8E8DE.toInt()
            textPaint.textSize = 7.5f * density
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                shorten(stack.id.displayName, 13),
                cell.left + 5f * density,
                cell.bottom - 5f * density,
                textPaint,
            )

            if (stack.count > 1) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = 0xFFFFE08A.toInt()
                textPaint.textSize = 9f * density
                canvas.drawText(
                    "x${stack.count}",
                    cell.right - 5f * density,
                    cell.bottom - 5f * density,
                    textPaint,
                )
            }

            if (stack.id == equipped) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = 0xFFE8A64C.toInt()
                canvas.drawRoundRect(cell, 5f * density, 5f * density, paint)
                paint.style = Paint.Style.FILL
            }
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        val hint = "Tap a consumable to use it  /  best weapon equips automatically  /  tap outside to close"
        canvas.drawText(hint, panel.left + 18f * density, panel.bottom - 10f * density, textPaint)
    }

    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"


}
