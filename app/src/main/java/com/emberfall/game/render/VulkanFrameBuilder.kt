package com.emberfall.game.render

import com.emberfall.game.core.Game
import com.emberfall.game.util.clamp
import kotlin.math.min

/**
 * Step 1 projection check. Places debug markers with the same isometric
 * projection and zoom rule the Canvas renderer uses, so the two paths can be
 * compared object by object.
 *
 * Projection centre is the player, not the camera. The camera's look-ahead is
 * not exposed to this file yet; that comes with the full port in step 2.
 */
object VulkanFrameBuilder {

    fun buildDebugMarkers(game: Game, batch: SpriteBatch, width: Float, height: Float) {
        batch.clear()

        // Same rule as Camera.resize().
        val zoom = clamp(
            min(width / (9f * IsoMath.TILE_WIDTH), height / (11f * IsoMath.TILE_HEIGHT)),
            1.4f, 5f,
        )
        val player = game.player
        val centre = IsoMath.worldToIso(player.x, player.y)

        fun marker(worldX: Float, worldY: Float, sizePx: Float, colour: Int) {
            val iso = IsoMath.worldToIso(worldX, worldY)
            val sx = width * 0.5f + (iso.x - centre.x) * zoom
            val sy = height * 0.5f + (iso.y - centre.y) * zoom
            val s = sizePx * zoom
            batch.addSolid(
                sx - s * 0.5f, sy - s * 0.5f, s, s,
                red(colour), green(colour), blue(colour), 1f,
            )
        }

        for (fire in game.campfires) marker(fire.x, fire.y, 8f, 0xFFFFA64C.toInt())
        for (chest in game.chests) if (!chest.opened) marker(chest.x, chest.y, 6f, 0xFFE8C85A.toInt())
        for (animal in game.animals) marker(animal.x, animal.y, 4f, 0xFFEAEAEA.toInt())
        for (wolf in game.wolves) marker(wolf.x, wolf.y, 5f, 0xFFFF6B5A.toInt())
        for (boss in game.bosses) if (!boss.defeated) marker(boss.x, boss.y, 14f, 0xFFFF3B30.toInt())
        marker(player.x, player.y, 10f, 0xFFFFD040.toInt())
    }

    private fun red(c: Int): Float = ((c shr 16) and 0xFF) / 255f
    private fun green(c: Int): Float = ((c shr 8) and 0xFF) / 255f
    private fun blue(c: Int): Float = (c and 0xFF) / 255f
}
