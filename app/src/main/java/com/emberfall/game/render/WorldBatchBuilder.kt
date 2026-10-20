package com.emberfall.game.render

import com.emberfall.game.world.TileType
import com.emberfall.game.world.World

/**
 * Terrain only: base tiles then transition overlays, in diagonal painter order.
 * Entities and props are added afterwards by EntityBatchBuilder so they sort
 * correctly against each other.
 *
 * Screen mapping matches the Canvas path: screen = viewport/2 + shake + (iso - cameraIso) * zoom.
 */
class WorldBatchBuilder(private val layout: AtlasLayout) {

    fun build(
        batch: SpriteBatch,
        world: World,
        cameraX: Float,
        cameraY: Float,
        zoom: Float,
        viewWidth: Float,
        viewHeight: Float,
        elapsed: Float,
        shakeX: Float = 0f,
        shakeY: Float = 0f,
    ) {
        batch.clear()

        val cameraIso = IsoMath.worldToIso(cameraX, cameraY)
        val originX = viewWidth * 0.5f + shakeX - cameraIso.x * zoom
        val originY = viewHeight * 0.5f + shakeY - cameraIso.y * zoom

        val bounds = IsoMath.visibleGridBounds(cameraX, cameraY, viewWidth, viewHeight, zoom)
        for (depth in bounds[0] + bounds[2]..bounds[1] + bounds[3]) {
            for (tx in bounds[0]..bounds[1]) {
                val ty = depth - tx
                if (ty < bounds[2] || ty > bounds[3]) continue

                val type = IsoAutotile.surface(world.tileAt(tx, ty))
                val variant = if (type == TileType.WATER || type == TileType.DEEP_WATER) {
                    tx * 3 + ty * 5 + (elapsed * 2.5f).toInt()
                } else {
                    (tx * 73856093) xor (ty * 19349663)
                }

                if (!addTile(batch, tx, ty, layout.terrainIndex(type, variant), originX, originY, zoom)) return

                for ((target, mask) in IsoAutotile.transitions(world, tx, ty, type)) {
                    val index = layout.transitionIndex(target, IsoAutotile.variant(mask))
                    if (!addTile(batch, tx, ty, index, originX, originY, zoom)) return
                }
            }
        }
    }

    private fun addTile(
        batch: SpriteBatch, tx: Int, ty: Int, index: Int,
        originX: Float, originY: Float, zoom: Float,
    ): Boolean {
        val centre = IsoMath.gridToIso(tx + .5f, ty + .5f)
        val left = originX + (centre.x - AtlasLayout.TILE_W / 2f) * zoom
        val top = originY + (centre.y - AtlasLayout.TILE_H / 2f) * zoom
        val x0 = layout.cellX(index) + AtlasLayout.GUTTER
        val y0 = layout.cellY(index) + AtlasLayout.GUTTER
        return batch.add(
            left, top, AtlasLayout.TILE_W * zoom, AtlasLayout.TILE_H * zoom,
            layout.uMin(x0), layout.vMin(y0),
            layout.uMax(x0 + AtlasLayout.TILE_W), layout.vMax(y0 + AtlasLayout.TILE_H),
            1f, 1f, 1f, 1f,
        )
    }
}