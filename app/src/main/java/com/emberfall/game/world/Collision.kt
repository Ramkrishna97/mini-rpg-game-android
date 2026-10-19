package com.emberfall.game.world

import com.emberfall.game.core.Config
import com.emberfall.game.util.clamp
import kotlin.math.floor

/**
 * Circle-vs-grid collision with axis-separated resolution, which gives the
 * player the "slide along the wall" feel that free-roam games need.
 *
 * Pure Kotlin so it can be unit tested without an emulator.
 */
object Collision {

    class MoveResult(var x: Float = 0f, var y: Float = 0f)

    fun circleIntersectsRect(
        cx: Float,
        cy: Float,
        radius: Float,
        rx: Float,
        ry: Float,
        rw: Float,
        rh: Float,
    ): Boolean {
        val nearestX = clamp(cx, rx, rx + rw)
        val nearestY = clamp(cy, ry, ry + rh)
        val dx = cx - nearestX
        val dy = cy - nearestY
        return dx * dx + dy * dy < radius * radius
    }

    fun isBlocked(query: TileQuery, x: Float, y: Float, radius: Float): Boolean {
        val tile = Config.TILE
        val minTx = floor((x - radius) / tile).toInt()
        val maxTx = floor((x + radius) / tile).toInt()
        val minTy = floor((y - radius) / tile).toInt()
        val maxTy = floor((y + radius) / tile).toInt()

        for (ty in minTy..maxTy) {
            for (tx in minTx..maxTx) {
                if (!query.isSolid(tx, ty)) continue
                if (circleIntersectsRect(
                        x, y, radius,
                        tx * tile, ty * tile, tile, tile,
                    )
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Move by (dx, dy), stopping on the axis that collides so the other axis
     * can keep sliding. Writes the resolved position into [out].
     */
    fun moveAndSlide(
        query: TileQuery,
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        radius: Float,
        out: MoveResult,
    ) {
        var nextX = x
        var nextY = y

        if (dx != 0f) {
            val candidate = x + dx
            if (!isBlocked(query, candidate, nextY, radius)) nextX = candidate
        }
        if (dy != 0f) {
            val candidate = nextY + dy
            if (!isBlocked(query, nextX, candidate, radius)) nextY = candidate
        }

        out.x = nextX
        out.y = nextY
    }
}
