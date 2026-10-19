package com.emberfall.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Game
import com.emberfall.game.core.Spell

/** Four-card spell picker. Tapping a card selects the magic used by the action button. */
class SpellPickerRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, game: Game, width: Float, height: Float, density: Float) {
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        val panel = UiLayout.spellPanel(width, height, density)
        paint.color = 0xF21A231C.toInt()
        canvas.drawRoundRect(panel, 12f * density, 12f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0x55E8A64C
        canvas.drawRoundRect(panel, 12f * density, 12f * density, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 14f * density
        canvas.drawText("CHOOSE MAGIC", panel.left + 18f * density, panel.top + 30f * density, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        canvas.drawText(
            "MP ${game.player.stats.mana.toInt()}/${game.player.stats.maxMana.toInt()}",
            panel.right - 18f * density,
            panel.top + 30f * density,
            textPaint,
        )

        for (index in 0 until 4) {
            val spell = Spell.values()[index]
            val card = UiLayout.spellCard(index, width, height, density)
            val selected = game.lastSpell == spell
            paint.color = if (selected) 0x332E4A38 else 0xFF232D25.toInt()
            canvas.drawRoundRect(card, 8f * density, 8f * density, paint)
            if (selected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f * density
                paint.color = spell.color
                canvas.drawRoundRect(card, 8f * density, 8f * density, paint)
                paint.style = Paint.Style.FILL
            }

            paint.color = spell.color
            canvas.drawCircle(card.left + 24f * density, card.top + 24f * density, 13f * density, paint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = 0xFFE8E8DE.toInt()
            textPaint.textSize = 10f * density
            canvas.drawText(
                spell.displayName,
                card.left + 44f * density,
                card.top + 22f * density,
                textPaint,
            )

            textPaint.color = 0xFFFFD08A.toInt()
            textPaint.textSize = 8.5f * density
            val cost = game.magicCost(spell).toInt()
            canvas.drawText(
                "${if (selected) "SELECTED" else "TAP TO SELECT"}  |  $cost MP",
                card.left + 44f * density,
                card.top + 36f * density,
                textPaint,
            )

            textPaint.color = 0xFF9FB39F.toInt()
            textPaint.textSize = 7.5f * density
            val lines = wrap(spell.description, 34)
            var y = card.top + 56f * density
            for (line in lines) {
                canvas.drawText(line, card.left + 12f * density, y, textPaint)
                y += 10f * density
            }

            rect.set(card.left + 10f * density, card.bottom - 16f * density, card.right - 10f * density, card.bottom - 8f * density)
            drawManaBar(canvas, game, spell, rect)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8f * density
        canvas.drawText(
            "Choose a magic, then press CAST to use it",
            width / 2f,
            panel.bottom - 10f * density,
            textPaint,
        )
    }

    private fun drawManaBar(canvas: Canvas, game: Game, spell: Spell, bar: RectF) {
        paint.color = 0x66000000
        canvas.drawRoundRect(bar, bar.height() / 2f, bar.height() / 2f, paint)
        val fraction = (game.player.stats.mana / game.magicCost(spell)).coerceIn(0f, 1f)
        paint.color = if (fraction >= 1f) spell.color else 0xFF6B4A3A.toInt()
        rect.set(bar.left, bar.top, bar.left + bar.width() * fraction, bar.bottom)
        canvas.drawRoundRect(rect, bar.height() / 2f, bar.height() / 2f, paint)
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > maxChars) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.take(3)
    }
}
