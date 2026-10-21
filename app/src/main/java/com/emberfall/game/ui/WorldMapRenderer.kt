package com.emberfall.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.input.InputState
import com.emberfall.game.world.Bosses
import com.emberfall.game.world.TileType
import kotlin.math.abs
import kotlin.math.min

/**
 * Full-screen region map, opened by tapping the minimap.
 *
 * Samples a 256x256-tile window at 2-tile resolution into a small bitmap, then
 * draws all five bosses (pinned to the edge when out of range), campfires,
 * chests and the player.
 */
class WorldMapRenderer {

    companion object {
        const val SAMPLE = 128
        const val TILES_PER_PIXEL = 2
        const val HALF_RANGE = SAMPLE * TILES_PER_PIXEL / 2
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()
    private val arrow = Path()

    private var bitmap: Bitmap? = null
    private var lastBuildTileX = Int.MIN_VALUE
    private var lastBuildTileY = Int.MIN_VALUE

    fun draw(canvas: Canvas, game: Game, input: InputState, width: Float, height: Float, density: Float) {
        val playerTileX = game.world.tileX(game.player.x)
        val playerTileY = game.world.tileY(game.player.y)
        if (bitmap == null || playerTileX != lastBuildTileX || playerTileY != lastBuildTileY) {
            build(game, playerTileX, playerTileY)
        }

        paint.color = 0xE6000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        rect.set(UiLayout.regionMapRect(width, height, density))
        val side = rect.width()

        paint.color = 0xFF0E1410.toInt()
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)
        bitmap?.let { canvas.drawBitmap(it, null, rect, mapPaint) }

        val scale = side / (HALF_RANGE * 2f)
        val centerX = rect.centerX()
        val centerY = rect.centerY()

        fun mapX(tileX: Int): Float = centerX + (tileX - playerTileX) * scale
        fun mapY(tileY: Int): Float = centerY + (tileY - playerTileY) * scale

        canvas.save()
        canvas.clipRect(rect)
        paint.color = 0xFFDAE8DF.toInt()
        paint.strokeWidth = 1.2f * density
        val compassX = rect.left + 16f * density
        val compassY = rect.top + 18f * density
        canvas.drawLine(compassX, compassY - 7f * density, compassX, compassY + 7f * density, paint)
        canvas.drawLine(compassX - 7f * density, compassY, compassX + 7f * density, compassY, paint)
        textPaint.color = 0xFFF7F6E8.toInt()
        textPaint.textSize = 7f * density
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("N", compassX, compassY - 9f * density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 7f * density
        for ((label, position, color) in listOf(
            Triple("FROST PEAKS", -26 to -111, 0xFFECFAFF.toInt()),
            Triple("EMBER CRATER", -107 to -76, 0xFFFFAA76.toInt()),
            Triple("SKY RIDGE", 92 to -84, 0xFFCBDEFF.toInt()),
            Triple("DUNE SEA", -91 to 93, 0xFFFFD99A.toInt()),
            Triple("OLD STONES", 23 to 56, 0xFFE1D8BD.toInt()),
        )) {
            textPaint.color = color
            canvas.drawText(label, mapX(position.first), mapY(position.second), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        for (fire in game.campfires) {
            val point = UiLayout.regionMapPoint(game.world.tileX(fire.x), game.world.tileY(fire.y),
                playerTileX, playerTileY, width, height, density)
            val fx = point.x; val fy = point.y
            paint.color = 0x55FFA64C
            canvas.drawCircle(fx, fy, 7f * density, paint)
            mark(canvas, fx, fy, 0xFFFFA64C.toInt(), 3.2f * density)
            textPaint.color = 0xFFFFD08A.toInt(); textPaint.textSize = 6f * density; textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("CAMP", fx + 5f * density, fy - 4f * density, textPaint)
        }
        for (chest in game.chests) {
            if (chest.opened) continue
            mark(canvas, mapX(game.world.tileX(chest.x)), mapY(game.world.tileY(chest.y)), 0xFFE8C85A.toInt(), 2.4f * density)
        }

        for (boss in Bosses.ALL) {
            if (boss.id in game.defeatedBossIds()) continue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = 0xCCF0DFB8.toInt()
            canvas.drawCircle(mapX(boss.tileX), mapY(boss.tileY), boss.arenaRadiusTiles * scale, paint)
            paint.style = Paint.Style.FILL
            var x = mapX(boss.tileX)
            var y = mapY(boss.tileY)
            val pad = 10f * density
            x = x.coerceIn(rect.left + pad, rect.right - pad)
            y = y.coerceIn(rect.top + pad, rect.bottom - pad)
            val iconColor = when (boss.id) {
                "gloomfang" -> 0xFFD2C4A0.toInt()
                "stonewarden" -> 0xFFBBD7FF.toInt()
                "mirewitch" -> 0xFFE8B871.toInt()
                "ashtitan" -> 0xFFFF713B.toInt()
                else -> 0xFFD8F5FF.toInt()
            }
            mark(canvas, x, y, iconColor, 5f * density)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = iconColor
            canvas.drawCircle(x, y, 8f * density, paint)
            paint.style = Paint.Style.FILL
            textPaint.color = 0xCCFFD0C8.toInt()
            textPaint.textSize = 7f * density
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(boss.name, x + 10f * density, y + 2.5f * density, textPaint)
        }
        for (dragon in game.bosses) {
            if (!dragon.isDragon || dragon.defeated) continue
            val pad = 10f * density
            val x = mapX(game.world.tileX(dragon.x)).coerceIn(rect.left + pad, rect.right - pad)
            val y = mapY(game.world.tileY(dragon.y)).coerceIn(rect.top + pad, rect.bottom - pad)
            mark(canvas, x, y, 0xFFFF6A42.toInt(), 6f * density)
            textPaint.color = 0xFFFFB49A.toInt(); textPaint.textSize = 7f * density
            canvas.drawText("DRAGON", x + 10f * density, y + 2f * density, textPaint)
        }

        // Player arrow.
        paint.color = 0xFFFFFFFF.toInt()
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(
            Math.toDegrees(
                kotlin.math.atan2(game.player.facingY.toDouble(), game.player.facingX.toDouble()),
            ).toFloat(),
        )
        arrow.reset()
        arrow.moveTo(9f * density, 0f)
        arrow.lineTo(-6f * density, 6f * density)
        arrow.lineTo(-3f * density, 0f)
        arrow.lineTo(-6f * density, -6f * density)
        arrow.close()
        canvas.drawPath(arrow, paint)
        canvas.restore()
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = 0x66FFFFFF
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)
        paint.style = Paint.Style.FILL

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 16f * density
        canvas.drawText("EMBERFALL WORLD MAP", width / 2f, 32f * density, textPaint)

        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        canvas.drawText("Tap a campfire marker to teleport", width / 2f, height - 29f * density, textPaint)
        canvas.drawText(
            "Arena rings  /  Orange camps  /  Yellow chests  /  Tap outside to close",
            width / 2f,
            height - 16f * density,
            textPaint,
        )
        if (input.selectedCampfireId >= 0) {
            val popup = UiLayout.teleportPopup(width, height, density)
            paint.color = 0xF01C281E.toInt()
            canvas.drawRoundRect(popup, 8f * density, 8f * density, paint)
            textPaint.color = 0xFFFFD08A.toInt()
            textPaint.textSize = 12f * density
            canvas.drawText("CAMPFIRE ${input.selectedCampfireId + 1}", popup.centerX(), popup.top + 30f * density, textPaint)
            val button = UiLayout.teleportButton(width, height, density)
            paint.color = 0xFF386A48.toInt()
            canvas.drawRoundRect(button, 5f * density, 5f * density, paint)
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textSize = 10f * density
            canvas.drawText("TELEPORT HERE", button.centerX(), button.centerY() + 3f * density, textPaint)
        }
    }

    private fun mark(canvas: Canvas, x: Float, y: Float, color: Int, radius: Float) {
        paint.color = color
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun build(game: Game, playerTileX: Int, playerTileY: Int) {
        lastBuildTileX = playerTileX
        lastBuildTileY = playerTileY
        val pixels = IntArray(SAMPLE * SAMPLE)
        var index = 0
        for (py in 0 until SAMPLE) {
            for (px in 0 until SAMPLE) {
                val tx = playerTileX - HALF_RANGE + px * TILES_PER_PIXEL
                val ty = playerTileY - HALF_RANGE + py * TILES_PER_PIXEL
                pixels[index++] = colorFor(game.world.tileAt(tx, ty))
            }
        }
        val image = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888)
        image.setPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        bitmap = image
    }

    private fun colorFor(tile: TileType): Int = when (tile) {
        TileType.GRASS, TileType.FLOWERS -> 0xFF4F7A3B.toInt()
        TileType.FOREST_FLOOR -> 0xFF33502B.toInt()
        TileType.TREE, TileType.FOREST_TREE -> 0xFF244021.toInt()
        TileType.DIRT -> 0xFF7A6142.toInt()
        TileType.SAND -> 0xFFC8B27C.toInt()
        TileType.WATER -> 0xFF2C5A78.toInt()
        TileType.DEEP_WATER -> 0xFF1B3C50.toInt()
        TileType.ROCK -> 0xFF66666E.toInt()
        TileType.RUIN_FLOOR -> 0xFF565660.toInt()
        TileType.RUIN_WALL -> 0xFF3A3A44.toInt()
        TileType.SNOW -> 0xFFE3F2F8.toInt()
        TileType.ASH -> 0xFF49313A.toInt()
        TileType.LAVA -> 0xFFFF7030.toInt()
        TileType.ARENA_STONE -> 0xFF9DB8C5.toInt()
    }
}
