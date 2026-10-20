package com.emberfall.game.render

import com.emberfall.game.core.Game
import com.emberfall.game.item.ItemId
import kotlin.math.abs
import kotlin.math.sin

/**
 * Night tint and light sources, matching WorldRenderer.drawNight and the
 * GPU lighting path (GpuLighting) that Canvas uses on API 33+ with hardware
 * acceleration.
 *
 * update() fills [lights] with 4 floats per light (screen x, screen y,
 * radius in pixels, unused) and sets [ambient] to the darkness alpha.
 * ambient == 0 means no night pass this frame.
 */
class NightLighting {

    val lights = FloatArray(MAX_LIGHTS * 4)
    var count = 0
        private set
    var ambient = 0f
        private set

    fun update(game: Game, cameraX: Float, cameraY: Float, zoom: Float, viewWidth: Float, viewHeight: Float) {
        count = 0
        ambient = ambientAlpha(game.minutes)
        if (ambient <= .01f) {
            ambient = 0f
            return
        }

        val centre = IsoMath.worldToIso(cameraX, cameraY)
        val ctx = Frame(centre, zoom, viewWidth, viewHeight)

        // Same order as Game.render(): player, campfires, then the extra lights.
        val player = game.player
        add(ctx, player.x, player.y, 175f)
        for (fire in game.campfires) {
            add(ctx, fire.x, fire.y, 115f * (1f + .07f * sin(fire.flamePhase * 2.3f)))
        }
        for (pickup in game.pickups) {
            if (pickup.item == ItemId.MAGIC_STONE && onScreen(ctx, cameraX, cameraY, pickup.x, pickup.y)) {
                add(ctx, pickup.x, pickup.y, 55f + 4f * sin(game.elapsedSeconds * 7f))
            }
        }
        for (projectile in game.projectiles) {
            if (onScreen(ctx, cameraX, cameraY, projectile.x, projectile.y)) add(ctx, projectile.x, projectile.y, 40f)
        }
        for (wolf in game.wolves) {
            if (onScreen(ctx, cameraX, cameraY, wolf.x, wolf.y)) add(ctx, wolf.x, wolf.y, 22f)
        }
        for (boss in game.bosses) {
            if (!boss.defeated && onScreen(ctx, cameraX, cameraY, boss.x, boss.y)) add(ctx, boss.x, boss.y, 38f)
        }
    }

    private class Frame(val centre: android.graphics.PointF, val zoom: Float, val viewWidth: Float, val viewHeight: Float)

    private fun add(ctx: Frame, worldX: Float, worldY: Float, radius: Float) {
        if (count >= MAX_LIGHTS) return
        val iso = IsoMath.worldToIso(worldX, worldY)
        val sx = ctx.viewWidth * .5f + (iso.x - ctx.centre.x) * ctx.zoom
        val sy = ctx.viewHeight * .5f + (iso.y - ctx.centre.y) * ctx.zoom
        val r = radius * ctx.zoom
        // Same culling as the Canvas GPU path.
        if (sx + r < 0f || sy + r < 0f || sx - r > ctx.viewWidth || sy - r > ctx.viewHeight) return
        val i = count * 4
        lights[i] = sx
        lights[i + 1] = sy
        lights[i + 2] = r
        lights[i + 3] = 0f
        count++
    }

    /** Same isometric box as Game.onScreen(): camera-centred, plus an 80px margin. */
    private fun onScreen(ctx: Frame, cameraX: Float, cameraY: Float, worldX: Float, worldY: Float): Boolean {
        val point = IsoMath.worldToIso(worldX, worldY)
        val halfW = ctx.viewWidth / (2f * ctx.zoom) + 80f
        val halfH = ctx.viewHeight / (2f * ctx.zoom) + 80f
        return abs(point.x - ctx.centre.x) <= halfW && abs(point.y - ctx.centre.y) <= halfH
    }

    companion object {
        const val MAX_LIGHTS = 32

        /** hour, r, g, b, alpha. Copied from WorldRenderer.STOPS. */
        private val STOPS = arrayOf(
            floatArrayOf(0f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(4.5f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(6.0f, 48f, 34f, 50f, 0.48f),
            floatArrayOf(7.0f, 255f, 140f, 80f, 0.28f),
            floatArrayOf(8.5f, 255f, 255f, 255f, 0f),
            floatArrayOf(16.5f, 255f, 255f, 255f, 0f),
            floatArrayOf(18.0f, 255f, 120f, 60f, 0.30f),
            floatArrayOf(19.5f, 44f, 32f, 54f, 0.55f),
            floatArrayOf(21.0f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(24f, 14f, 22f, 48f, 0.68f),
        )

        /** Darkness alpha at [minutes] past midnight, interpolated exactly as WorldRenderer.ambientAt(). */
        fun ambientAlpha(minutes: Float): Float {
            val hour = (minutes / 60f).coerceIn(0f, 24f)
            var index = 0
            while (index < STOPS.size - 2 && hour > STOPS[index + 1][0]) index++
            val a = STOPS[index]
            val b = STOPS[index + 1]
            val span = (b[0] - a[0]).coerceAtLeast(0.0001f)
            val t = ((hour - a[0]) / span).coerceIn(0f, 1f)
            return a[4] + (b[4] - a[4]) * t
        }
    }
}
