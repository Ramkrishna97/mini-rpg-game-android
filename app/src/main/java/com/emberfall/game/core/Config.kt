package com.emberfall.game.core

/**
 * Central tuning values. Keeping them in one place makes balancing a matter of
 * editing numbers instead of hunting through the engine.
 */
object Config {
    /** World tile size in world-space pixels (before camera zoom). */
    const val TILE = 32f

    /** Width/height of a generated chunk, in tiles. */
    const val CHUNK_TILES = 32

    /** Fixed simulation step. Rendering may run faster, simulation stays stable. */
    const val STEP_SECONDS = 1f / 60f

    /** Player collision radius in world pixels. */
    const val PLAYER_RADIUS = 10f

    const val WALK_SPEED = 112f
    const val RUN_SPEED = 170f

    /** A full in-game day (24h) lasts this long in real seconds. */
    const val DAY_LENGTH_SECONDS = 1200f
    const val MINUTES_PER_DAY = 24f * 60f

    const val SAVE_INTERVAL_SECONDS = 30f

    /** Chunks kept loaded around the player (radius, in chunks). */
    const val LOAD_CHUNK_RADIUS = 2
    const val KEEP_CHUNK_RADIUS = 6
}
