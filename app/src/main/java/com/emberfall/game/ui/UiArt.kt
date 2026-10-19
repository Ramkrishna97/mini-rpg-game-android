package com.emberfall.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Spell
import com.emberfall.game.item.ItemCategory
import com.emberfall.game.item.ItemId

/** Shared gold and wood UI trim plus original 32x32 icon drawings. */
class UiArt(context: Context) {
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
        canvas.save(); canvas.translate(cx-size/2f, cy-size/2f); canvas.scale(size/32f, size/32f)
        fun block(x1:Int,y1:Int,x2:Int,y2:Int,color:Int) { pixel.color=color; canvas.drawRect(x1.toFloat(),y1.toFloat(),x2.toFloat(),y2.toFloat(),pixel) }
        when {
            item.category == ItemCategory.WEAPON -> {
                block(14,3,18,20,0xFFD5E1EA.toInt()); block(15,1,17,5,0xFFF5F9ED.toInt())
                block(10,19,22,22,0xFFE8C073.toInt()); block(14,22,18,29,0xFF7E5130.toInt())
                if (item == ItemId.BOW) {
                    block(14,3,18,29,0xFF8F683B.toInt()); block(9,8,11,25,0xFFF3E7BE.toInt())
                }
            }
            item.name.contains("POTION") || item == ItemId.ELIXIR -> {
                block(13,3,19,8,0xFFD8C391.toInt()); block(10,8,22,28,0xFFCFD7D3.toInt())
                block(12,13,20,25,item.color); block(11,10,21,12,0xFFF9F4DF.toInt())
                block(14,15,16,22,0xAAFFFFFF.toInt())
            }
            item == ItemId.MAGIC_STONE -> {
                block(14,3,18,7,0xFFE0D6FF.toInt()); block(10,7,22,24,0xFF7561BD.toInt())
                block(13,10,18,23,0xFFB9A9F9.toInt()); block(14,24,18,29,0xFF413876.toInt())
            }
            item == ItemId.WOOD -> {
                block(7,12,26,23,0xFF694527.toInt()); block(9,14,24,20,0xFFAD7945.toInt())
                block(13,14,15,20,0xFFE3BA77.toInt())
            }
            item == ItemId.STONE || item == ItemId.IRON -> {
                block(8,14,25,27,0xFF555E64.toInt()); block(12,7,22,15,item.color)
                block(10,16,18,19,0xFFDCE2D9.toInt())
            }
            item == ItemId.FRUIT -> {
                block(11,10,23,26,0xFFD6505A.toInt()); block(15,5,18,12,0xFF70472E.toInt())
                block(18,6,24,10,0xFF66A45A.toInt())
            }
            else -> {
                block(9,10,23,25,item.color); block(12,7,20,11,0xFFE8DFC5.toInt())
                block(11,14,14,21,0x99FFFFFF.toInt())
            }
        }
        canvas.restore()
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
