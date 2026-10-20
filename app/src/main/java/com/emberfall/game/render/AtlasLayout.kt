package com.emberfall.game.render

import com.emberfall.game.entity.AnimalSpecies
import com.emberfall.game.item.ItemId
import com.emberfall.game.world.TileType
import kotlin.math.ceil

/**
 * Where every sprite lives inside the single atlas texture. Regions, top to bottom:
 *  A. Terrain tiles and transition overlays, 66x34 cells (64x32 art + 1px gutter).
 *  B. Props (trees, rocks, ruin walls), 98x98 cells.
 *  C. Character sprite sheets (384x256 player, 512x256 bosses), two per sheet
 *     slot: the normal sheet and a white silhouette used for the outline rim.
 *  D. Entity cells (creatures, chests, campfire, pickups, shadows), 64x64 each.
 */
class AtlasLayout(val masks: Int = IsoAutotile.masks.size) {

    private val tileTypeCount: Int = TileType.values().size
    private val terrainCellCount: Int = tileTypeCount * VARIANTS + tileTypeCount * masks
    private val terrainRows: Int = ceil(terrainCellCount / TILE_COLS.toFloat()).toInt()
    private val propRegionTop: Int = terrainRows * TILE_CELL_H
    private val sheetRegionTop: Int = propRegionTop + PROP_CELL
    private val sheetSlotCount: Int = SHEET_NAMES.size * 2
    private val sheetRows: Int = ceil(sheetSlotCount / SHEET_COLS.toFloat()).toInt()
    private val entityRegionTop: Int = sheetRegionTop + sheetRows * SHEET_H

    val entityKeys: List<String> = listOf(
        "wolf", "dragon", "shadow", "magic_ring", "chest", "chest_open", "campfire",
    ) + AnimalSpecies.values().map { "animal_${it.name}" } +
            ItemId.values().map { "pickup_${it.name}" }

    private val entityIndexByKey: Map<String, Int> =
        entityKeys.withIndex().associate { it.value to it.index }
    private val entityRows: Int = ceil(entityKeys.size / ENT_COLS.toFloat()).toInt()

    /** Total atlas height in pixels. */
    val height: Int = entityRegionTop + entityRows * ENT_CELL

    // ---- Terrain ----------------------------------------------------------

    fun terrainIndex(type: TileType, variant: Int): Int =
        type.ordinal * VARIANTS + (variant and (VARIANTS - 1))

    fun transitionIndex(target: TileType, maskIndex: Int): Int =
        tileTypeCount * VARIANTS + target.ordinal * masks + maskIndex

    fun cellX(index: Int): Int = (index % TILE_COLS) * TILE_CELL_W
    fun cellY(index: Int): Int = (index / TILE_COLS) * TILE_CELL_H

    // ---- Props ------------------------------------------------------------

    fun propIndex(type: TileType): Int = PROP_TYPES.indexOf(type)
    fun propX(index: Int): Int = (index % PROP_COLS) * PROP_CELL
    fun propPixelY(index: Int): Int = propRegionTop + (index / PROP_COLS) * PROP_CELL

    // ---- Character sheets -------------------------------------------------

    private fun sheetSlot(name: String, silhouette: Boolean): Int {
        val i = SHEET_NAMES.indexOf(name)
        require(i >= 0) { "unknown sheet $name" }
        return if (silhouette) i + SHEET_NAMES.size else i
    }

    fun sheetX(name: String, silhouette: Boolean): Int =
        (sheetSlot(name, silhouette) % SHEET_COLS) * SHEET_W

    fun sheetY(name: String, silhouette: Boolean): Int =
        sheetRegionTop + (sheetSlot(name, silhouette) / SHEET_COLS) * SHEET_H

    // ---- Entity cells -----------------------------------------------------

    /** Atlas index for an entity key, or -1 if the key has no art. */
    fun entityIndexOf(key: String): Int = entityIndexByKey[key] ?: -1
    fun entityX(index: Int): Int = (index % ENT_COLS) * ENT_CELL
    fun entityPixelY(index: Int): Int = entityRegionTop + (index / ENT_COLS) * ENT_CELL

    // ---- UV helpers (half-texel inset keeps linear sampling inside the art) --

    fun uMin(px: Int): Float = (px + .5f) / ATLAS_W
    fun uMax(px: Int): Float = (px - .5f) / ATLAS_W
    fun vMin(py: Int): Float = (py + .5f) / height
    fun vMax(py: Int): Float = (py - .5f) / height

    companion object {
        const val ATLAS_W = 2048
        const val VARIANTS = 4
        const val GUTTER = 1

        const val TILE_W = 64
        const val TILE_H = 32
        const val TILE_CELL_W = TILE_W + 2 * GUTTER
        const val TILE_CELL_H = TILE_H + 2 * GUTTER
        const val TILE_COLS = ATLAS_W / TILE_CELL_W

        const val PROP_CELL = 98
        const val PROP_COLS = ATLAS_W / PROP_CELL
        /** Ground point inside a prop cell: gutter + 48, gutter + 80. */
        const val PROP_ANCHOR_X = 49
        const val PROP_ANCHOR_Y = 81

        const val SHEET_W = 512
        const val SHEET_H = 256
        const val SHEET_COLS = ATLAS_W / SHEET_W

        const val ENT_CELL = 64
        const val ENT_COLS = ATLAS_W / ENT_CELL
        /** Ground point inside an entity cell. */
        const val ENT_ANCHOR_X = 32
        const val ENT_ANCHOR_Y = 40

        val SHEET_NAMES: List<String> = listOf(
            "player", "gloomfang", "stonewarden", "mirewitch", "ashtitan", "hollowking", "dragon",
        )

        val PROP_TYPES: List<TileType> = listOf(
            TileType.TREE, TileType.FOREST_TREE, TileType.ROCK, TileType.RUIN_WALL,
        )
    }
}
