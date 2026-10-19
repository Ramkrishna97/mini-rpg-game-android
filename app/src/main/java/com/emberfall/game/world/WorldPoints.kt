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
        Camp(0, -42),
        Camp(80, 20),
        Camp(-102, 55),
        Camp(25, 145),
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
