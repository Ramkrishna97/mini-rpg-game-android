package com.emberfall.game.world

/**
 * Hand-placed points of interest. Tile coordinates are world-absolute, so they
 * are identical for every seed (the arenas carve themselves into the terrain).
 */
object WorldPoints {

    data class Camp(val tileX: Int, val tileY: Int)

    /** Campfires double as checkpoints and save points. */
    val campfires: List<Camp> = listOf(
        Camp(0, 0),
        Camp(-20, -52),
        Camp(58, -32),
        Camp(-58, 33),
        Camp(17, 67),
    )

    /** Lootable chests sprinkled around the starting region. */
    val chests: List<TilePos> = listOf(
        TilePos(6, 5),
        TilePos(-9, 8),
        TilePos(14, -6),
        TilePos(-4, -13),
        TilePos(22, 14),
        TilePos(-18, -7),
        TilePos(11, 24),
        TilePos(-27, 19),
    )
}
