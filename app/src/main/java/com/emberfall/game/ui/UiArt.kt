package com.emberfall.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Spell
import com.emberfall.game.item.ItemId
import com.emberfall.game.render.ArtSheets

/** Shared panel trim, Unity item art, and spell icons. */
class UiArt(context: Context) {
    private val art = ArtSheets.from(context)
    val font: Typeface = Typeface.createFromAsset(context.assets, "fonts/MedievalSharp.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixel = Paint().apply { isAntiAlias = false }

    fun panel(canvas: Canvas, area: RectF, density: Float) {
        paint.color = 0xFF493323.toInt()
        canvas.drawRoundRect(area, 11f*density, 11f*density, paint)
        val middle = RectF(area.left+4f*density, area.top+4f*density, area.right-4f*density, area.bottom-4f*density)
        paint.color = 0xFFC1944C.toInt(); canvas.drawRoundRect(middle, 8f*density, 8f*density, paint)
        val inner = RectF(area.left+8f*density, area.top+8f*density, area.right-8f*density, area.bottom-8f*density)
        paint.color = 0xFF1A2825.toInt(); canvas.drawRoundRect(inner, 6f*density, 6f*density, paint)
        paint.color = 0xFFEDD397.toInt()
        for (x in floatArrayOf(area.left+13f*density, area.right-13f*density))
            for (y in floatArrayOf(area.top+13f*density, area.bottom-13f*density))
                canvas.drawCircle(x, y, 2f*density, paint)
    }

    fun item(canvas: Canvas, item: ItemId, cx: Float, cy: Float, size: Float) {
        art.item(canvas, item, cx, cy, size)
    }

    fun spell(canvas: Canvas, spell: Spell, cx: Float, cy: Float, size: Float) {
        canvas.save(); canvas.translate(cx-size/2f,cy-size/2f); canvas.scale(size/32f,size/32f)
        pixel.color = spell.color
        when (spell) {
            Spell.FIREBALL -> {
                val flame = android.graphics.Path().apply { moveTo(16f,2f); lineTo(25f,17f); lineTo(21f,28f)
                    lineTo(10f,28f); lineTo(7f,18f); close() }
                canvas.drawPath(flame,pixel)
                pixel.color=0xFFFFD766.toInt(); canvas.drawRect(13f,15f,19f,27f,pixel)
            }
            Spell.ICE_FREEZE -> {
                pixel.strokeWidth=3f
                canvas.drawLine(16f,3f,16f,29f,pixel); canvas.drawLine(4f,16f,28f,16f,pixel)
                canvas.drawLine(7f,7f,25f,25f,pixel); canvas.drawLine(25f,7f,7f,25f,pixel)
            }
            Spell.WATER_TSUNAMI -> {
                canvas.drawArc(3f,8f,29f,27f,190f,200f,false,pixel)
                pixel.color=0xFFBCEBFF.toInt(); canvas.drawRect(5f,22f,26f,25f,pixel)
            }
            Spell.WIND_TORNADO -> {
                pixel.strokeWidth=3f
                canvas.drawArc(3f,4f,29f,21f,25f,285f,false,pixel)
                canvas.drawArc(8f,15f,24f,28f,200f,285f,false,pixel)
            }
        }
        canvas.restore()
    }
}
