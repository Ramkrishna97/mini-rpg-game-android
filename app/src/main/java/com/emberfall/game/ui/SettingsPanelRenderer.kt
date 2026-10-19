package com.emberfall.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Game
import com.emberfall.game.core.GraphicsQuality

/** Graphics options overlay. Every toggle applies immediately. */
class SettingsPanelRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, game: Game, scroll: Int, width: Float, height: Float, density: Float) {
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        val panel = UiLayout.settingsPanel(width, height, density)
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
        canvas.drawText("SETTINGS", panel.left + 18f * density, panel.top + 30f * density, textPaint)

        val labels = arrayOf("QUALITY: LOW", "QUALITY: MEDIUM", "QUALITY: HIGH")
        textPaint.color = 0xFF90A090.toInt(); textPaint.textSize = 7f*density; textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SCROLL FOR MORE",panel.right-18f*density,panel.top+29f*density,textPaint)
        canvas.save(); canvas.clipRect(panel.left, panel.top + 42f*density, panel.right, panel.bottom - 8f*density)
        for (index in 0..UiLayout.SETTINGS_CLOSE) {
            val row = UiLayout.settingsRow(index, scroll, width, height, density)
            val selected = when (index) {
                0 -> game.graphics.quality == GraphicsQuality.LOW
                1 -> game.graphics.quality == GraphicsQuality.MEDIUM
                2 -> game.graphics.quality == GraphicsQuality.HIGH
                else -> false
            }
            paint.color = if (selected) 0x33E8A64C else 0xFF232D25.toInt()
            canvas.drawRoundRect(row, 5f * density, 5f * density, paint)

            textPaint.color = if (selected) 0xFFFFD08A.toInt() else 0xFFE8E8DE.toInt()
            textPaint.textSize = 10f * density
            textPaint.textAlign = Paint.Align.LEFT
            val label = when (index) {
                0, 1, 2 -> labels[index]
                3 -> "SMOOTH TEXTURES: ${onOff(game.graphics.smoothTextures)}"
                4 -> "ANTI-ALIASING: ${onOff(game.graphics.antiAliasing)}"
                5 -> "PARTICLES: ${onOff(game.graphics.particles)}"
                6 -> "MUSIC: ${onOff(game.graphics.music)}"
                7 -> "SOUND: ${onOff(game.graphics.sound)}"
                8 -> "VIBRATION: ${onOff(game.graphics.vibration)}"
                9 -> "RENDER SCALE: ${game.graphics.resolutionScale}X  TAP TO CYCLE"
                10 -> "FRAME CAP: ${game.graphics.targetFps} HZ  TAP TO CYCLE"
                11 -> "SAVE GAME NOW"
                12 -> "LOAD SAVED GAME"
                else -> "CLOSE"
            }
            canvas.drawText(label, row.left + 12f * density, row.centerY() + 4f * density, textPaint)

            if (index < 3) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = 0xFF90A090.toInt()
                textPaint.textSize = 8f * density
                canvas.drawText(
                    "${game.fps.toInt()} FPS",
                    row.right - 12f * density,
                    row.centerY() + 3f * density,
                    textPaint,
                )
            }
        }
        canvas.restore()
    }

    private fun onOff(value: Boolean): String = if (value) "ON" else "OFF"
}
