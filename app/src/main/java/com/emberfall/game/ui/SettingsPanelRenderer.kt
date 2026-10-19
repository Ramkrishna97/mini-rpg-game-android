package com.emberfall.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Context
import com.emberfall.game.core.Game
import com.emberfall.game.core.GraphicsQuality

/** Graphics options overlay. Every toggle applies immediately. */
class SettingsPanelRenderer(context: Context) {

    private val art = UiArt(context)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = art.font
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, game: Game, scroll: Int, width: Float, height: Float, density: Float) {
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        val panel = UiLayout.settingsPanel(width, height, density)
        art.panel(canvas, panel, density)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 14f * density
        canvas.drawText("PAUSED / SETTINGS", panel.left + 18f * density, panel.top + 30f * density, textPaint)

        val labels = arrayOf("QUALITY: LOW", "QUALITY: MEDIUM", "QUALITY: HIGH")
        for (tab in 0..2) {
            val bounds = UiLayout.settingsTab(tab,width,height,density)
            paint.color = if (tab == scroll) 0xFF466D51.toInt() else 0xFF25382D.toInt()
            canvas.drawRoundRect(bounds,6f*density,6f*density,paint)
            textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 9f*density; textPaint.color = 0xFFFFE2AD.toInt()
            canvas.drawText(arrayOf("Visuals", "Audio", "Save")[tab],bounds.centerX()+8f*density,bounds.centerY()+3f*density,textPaint)
            val ix = bounds.left+15f*density; val iy = bounds.centerY()
            paint.color = 0xFFFFE2AD.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f*density
            when(tab) {
                0 -> { canvas.drawRect(ix-6f*density,iy-4f*density,ix+6f*density,iy+4f*density,paint)
                    canvas.drawLine(ix,iy+4f*density,ix,iy+7f*density,paint) }
                1 -> { canvas.drawCircle(ix,iy,5f*density,paint)
                    canvas.drawLine(ix,iy-7f*density,ix,iy+7f*density,paint) }
                else -> { canvas.drawRect(ix-5f*density,iy-6f*density,ix+5f*density,iy+6f*density,paint)
                    canvas.drawLine(ix-3f*density,iy+2f*density,ix+3f*density,iy+2f*density,paint) }
            }
            paint.style = Paint.Style.FILL
        }
        canvas.save(); canvas.clipRect(panel.left, panel.top + 74f*density, panel.right, panel.bottom - 8f*density)
        for (index in UiLayout.settingsIndices(scroll)) {
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
                3 -> "Smooth textures"
                4 -> "Anti-aliasing"
                5 -> "Particles"
                6 -> "Music"
                7 -> "Sound"
                8 -> "Vibration"
                9 -> "Render scale  ${game.graphics.resolutionScale}x"
                10 -> "Frame limit  ${game.graphics.targetFps} Hz"
                11 -> "SAVE GAME NOW"
                12 -> "LOAD SAVED GAME"
                else -> "RESUME GAME"
            }
            val available = row.width() - (if(index in 3..8) 64f else 24f)*density
            if (textPaint.measureText(label) > available) textPaint.textSize *= available / textPaint.measureText(label)
            canvas.drawText(label, row.left + 12f * density, row.centerY() + 4f * density, textPaint)

            paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            paint.color = if(selected) 0xFFE8A64C.toInt() else 0xFF435B48.toInt()
            canvas.drawRoundRect(row,5f*density,5f*density,paint); paint.style = Paint.Style.FILL
            if (index in 3..8) {
                val enabled = when(index) {
                    3 -> game.graphics.smoothTextures; 4 -> game.graphics.antiAliasing
                    5 -> game.graphics.particles; 6 -> game.graphics.music
                    7 -> game.graphics.sound; else -> game.graphics.vibration
                }
                val h = minOf(16f*density,row.height()*.7f)
                rect.set(row.right-46f*density,row.centerY()-h/2f,row.right-10f*density,row.centerY()+h/2f)
                paint.color = if(enabled) 0xFF62AD77.toInt() else 0xFF59615C.toInt()
                canvas.drawRoundRect(rect,h,h,paint)
                paint.color = 0xFFFFF4DD.toInt()
                canvas.drawCircle(if(enabled) rect.right-h/2f else rect.left+h/2f,rect.centerY(),h*.38f,paint)
            }

        }
        canvas.restore()
    }

    private fun onOff(value: Boolean): String = if (value) "ON" else "OFF"
}
