package com.emberfall.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.world.TileType

/**
 * North-up minimap. Terrain is sampled into a small bitmap a few times per
 * second; markers are drawn on top every frame, so the player arrow never lags.
 */
class MinimapRenderer {

    companion object {
        const val SAMPLE = 56
        const val VIEW_TILES = SAMPLE
    }

    private val bitmap = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(SAMPLE * SAMPLE)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var timer = 0f
    private var lastTileX = Int.MIN_VALUE
    private var lastTileY = Int.MIN_VALUE
    private var lastSampleTime = Float.NEGATIVE_INFINITY

    fun update(dt: Float, game: Game) {
        timer -= dt
        val tileX = game.world.tileX(game.player.x)
        val tileY = game.world.tileY(game.player.y)
        val moved = tileX != lastTileX || tileY != lastTileY
        if (timer > 0f && !moved) return
        timer = 0.45f
        lastTileX = tileX
        lastTileY = tileY
        refresh(game, tileX, tileY)
        lastSampleTime = game.elapsedSeconds
    }

    private fun refresh(game: Game, centerX: Int, centerY: Int) {
        val half = SAMPLE / 2
        var index = 0
        for (dy in -half until half) {
            for (dx in -half until half) {
                val tile = game.world.tileAt(centerX + dx, centerY + dy)
                pixels[index++] = colorFor(tile)
            }
        }
        bitmap.setPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
    }

    fun draw(canvas: Canvas, game: Game, width: Float, height: Float, density: Float) {
        val tileX = game.world.tileX(game.player.x)
        val tileY = game.world.tileY(game.player.y)
        // The Vulkan overlay owns a separate HUD instance from the simulation.
        if (tileX != lastTileX || tileY != lastTileY || game.elapsedSeconds - lastSampleTime >= .45f) {
            lastTileX = tileX
            lastTileY = tileY
            lastSampleTime = game.elapsedSeconds
            refresh(game, tileX, tileY)
        }
        val size = UiLayout.minimapSize(density)
        val left = width - 14f * density - size
        val top = 14f * density
        rect.set(left, top, left + size, top + size)

        canvas.save()
        canvas.clipRect(rect)

        paint.color = 0xFF101812.toInt()
        canvas.drawRect(rect, paint)
        canvas.drawBitmap(bitmap, null, rect, paint)

        markerPaint.color = 0x16000000
        canvas.drawRect(rect, markerPaint)

        val scale = size / VIEW_TILES.toFloat()
        val centerScreenX = left + size / 2f
        val centerScreenY = top + size / 2f

        fun marker(worldX: Float, worldY: Float, color: Int, radius: Float) {
            val dx = (worldX - game.player.x) / Config.TILE * scale
            val dy = (worldY - game.player.y) / Config.TILE * scale
            val x = centerScreenX + dx
            val y = centerScreenY + dy
            if (x < left - 4f || x > left + size + 4f) return
            if (y < top - 4f || y > top + size + 4f) return
            markerPaint.color = color
            canvas.drawCircle(x, y, radius, markerPaint)
        }

        // Campfires.
        for (fire in game.campfires) {
            marker(fire.x, fire.y, 0xFFFFA64C.toInt(), 2.6f * density)
        }

        // Chests (only unopened).
        for (chest in game.chests) {
            if (chest.opened) continue
            marker(chest.x, chest.y, 0xFFE8C85A.toInt(), 2.0f * density)
        }

        // Wildlife and wolves.
        for (animal in game.animals) marker(animal.x, animal.y, 0xCCEAEAEA.toInt(), 1.5f * density)
        for (wolf in game.wolves) marker(wolf.x, wolf.y, 0xFFFF6B5A.toInt(), 1.8f * density)

        // Arena markers use each boss's home position, even while a fight moves it.
        for (boss in game.bosses) {
            if (boss.defeated) continue
            if (boss.isDragon) continue
            val color = when (boss.def.id) {
                "hollowking" -> 0xFFD8F5FF.toInt()
                "ashtitan" -> 0xFFFF713B.toInt()
                "stonewarden" -> 0xFFBBD7FF.toInt()
                "mirewitch" -> 0xFFE8B871.toInt()
                else -> 0xFFD2C4A0.toInt()
            }
            marker(boss.arenaX, boss.arenaY, color, 3.6f * density)
            markerPaint.style = Paint.Style.STROKE
            markerPaint.strokeWidth = 1.2f * density
            markerPaint.color = color
            val dx = (boss.arenaX - game.player.x) / Config.TILE * scale
            val dy = (boss.arenaY - game.player.y) / Config.TILE * scale
            canvas.drawCircle(centerScreenX + dx, centerScreenY + dy, 6f * density, markerPaint)
            markerPaint.style = Paint.Style.FILL
        }

        // Player arrow.
        markerPaint.color = 0xFFFFFFFF.toInt()
        canvas.save()
        canvas.translate(centerScreenX, centerScreenY)
        val angle = Math.toDegrees(
            kotlin.math.atan2(game.player.facingY.toDouble(), game.player.facingX.toDouble()),
        ).toFloat()
        canvas.rotate(angle)
        canvas.drawPath(playerArrow(), markerPaint)
        canvas.restore()

        canvas.restore()

        // Border.
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = 2f * density
        markerPaint.color = 0x66FFFFFF
        rect.set(left, top, left + size, top + size)
        canvas.drawRoundRect(rect, 6f * density, 6f * density, markerPaint)
        markerPaint.style = Paint.Style.FILL
    }

    private val arrow = android.graphics.Path()

    private fun playerArrow(): android.graphics.Path {
        arrow.reset()
        arrow.moveTo(7f, 0f)
        arrow.lineTo(-5f, 5f)
        arrow.lineTo(-2f, 0f)
        arrow.lineTo(-5f, -5f)
        arrow.close()
        return arrow
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
