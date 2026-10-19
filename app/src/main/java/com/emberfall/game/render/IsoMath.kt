package com.emberfall.game.render

import android.graphics.PointF
import com.emberfall.game.core.Config
import kotlin.math.ceil
import kotlin.math.floor

/** Projection only. Physics and saves continue to use the original world coordinates. */
object IsoMath {
    const val TILE_WIDTH = 64f
    const val TILE_HEIGHT = 32f

    fun gridToIso(tileX: Float, tileY: Float): PointF = PointF(
        (tileX - tileY) * TILE_WIDTH / 2f,
        (tileX + tileY) * TILE_HEIGHT / 2f,
    )

    fun isoToGrid(screenX: Float, screenY: Float): PointF = PointF(
        screenX / TILE_WIDTH + screenY / TILE_HEIGHT,
        screenY / TILE_HEIGHT - screenX / TILE_WIDTH,
    )

    fun worldToIso(worldX: Float, worldY: Float): PointF =
        gridToIso(worldX / Config.TILE, worldY / Config.TILE)

    fun visibleGridBounds(cameraX: Float, cameraY: Float, width: Float, height: Float, zoom: Float, padding: Int = 2): IntArray {
        val center = worldToIso(cameraX, cameraY)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (sx in floatArrayOf(-width / 2f, width / 2f)) {
            for (sy in floatArrayOf(-height / 2f, height / 2f)) {
                val grid = isoToGrid(center.x + sx / zoom, center.y + sy / zoom)
                minX = minOf(minX, grid.x); maxX = maxOf(maxX, grid.x)
                minY = minOf(minY, grid.y); maxY = maxOf(maxY, grid.y)
            }
        }
        return intArrayOf(floor(minX).toInt() - padding, ceil(maxX).toInt() + padding,
            floor(minY).toInt() - padding, ceil(maxY).toInt() + padding)
    }
}
