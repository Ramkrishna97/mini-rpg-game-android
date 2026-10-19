package com.emberfall.game.world

import com.emberfall.game.core.Config

/**
 * World generation. Pure Kotlin (no Android imports) so the terrain rules can
 * be unit tested on the JVM.
 *
 * Climate model:
 *  - elevation noise decides water / shore / highlands
 *  - moisture noise decides plains vs forest
 *  - a rare "ruin" mask stamps small dungeon outposts onto dry land
 */
class WorldGenerator(val seed: Int) {

    private val elevationNoise = ValueNoise(seed)
    private val moistureNoise = ValueNoise(seed + 1013)
    private val detailNoise = ValueNoise(seed + 7717)
    private val treeNoise = ValueNoise(seed + 4241)
    private val scatterNoise = ValueNoise(seed + 555)
    private val ruinNoise = ValueNoise(seed + 9091)

    fun elevationAt(tx: Int, ty: Int): Float =
        elevationNoise.fbm(tx * 0.016f, ty * 0.016f, 4)

    fun moistureAt(tx: Int, ty: Int): Float =
        moistureNoise.fbm(tx * 0.024f + 31.7f, ty * 0.024f - 12.4f, 3)

    fun biomeAt(tx: Int, ty: Int): Biome {
        if (inBossArena(tx, ty)) return Biome.RUINS
        val e = elevationAt(tx, ty)
        val m = moistureAt(tx, ty)
        return classify(e, m, tx, ty)
    }

    private fun inBossArena(tx: Int, ty: Int): Boolean {
        for (boss in Bosses.ALL) {
            val dx = tx - boss.tileX
            val dy = ty - boss.tileY
            if (dx * dx + dy * dy <= boss.arenaRadiusTiles * boss.arenaRadiusTiles) {
                return true
            }
        }
        return false
    }

    /** Generate one whole chunk as compact tile ids (one byte per tile). */
    fun generateChunk(cx: Int, cy: Int): ByteArray {
        val size = Config.CHUNK_TILES
        val out = ByteArray(size * size)
        val originX = cx * size
        val originY = cy * size
        var index = 0
        for (ly in 0 until size) {
            for (lx in 0 until size) {
                out[index++] = tileAt(originX + lx, originY + ly).id.toByte()
            }
        }
        return out
    }

    fun tileAt(tx: Int, ty: Int): TileType {
        // Boss arenas are guaranteed flat, cleared ruin floors.
        if (inBossArena(tx, ty)) return TileType.RUIN_FLOOR

        val e = elevationAt(tx, ty)
        val m = moistureAt(tx, ty)
        val detail = detailNoise.fbm(tx * 0.11f, ty * 0.11f, 2)

        // Water bodies and their sandy rim.
        if (e < 0.355f) {
            return if (e < 0.305f) TileType.DEEP_WATER else TileType.WATER
        }
        if (e < 0.392f) {
            return if (detail < 0.36f) TileType.DIRT else TileType.SAND
        }

        return when (classify(e, m, tx, ty)) {
            Biome.HIGHLANDS -> when {
                detail > 0.70f || e > 0.875f -> TileType.ROCK
                detail < 0.22f -> TileType.DIRT
                else -> TileType.GRASS
            }

            Biome.RUINS -> if (detail > 0.74f) TileType.RUIN_WALL else TileType.RUIN_FLOOR

            Biome.FOREST -> {
                val tree = treeNoise.noise(tx * 0.30f, ty * 0.30f)
                when {
                    tree > 0.56f -> TileType.FOREST_TREE
                    detail > 0.70f -> TileType.FLOWERS
                    else -> TileType.FOREST_FLOOR
                }
            }

            Biome.PLAINS -> when {
                // Isolated trees instead of grid-like orchards.
                scatterNoise.unit(tx, ty) > 0.982f -> TileType.TREE
                detail > 0.70f -> TileType.FLOWERS
                detail < 0.22f -> TileType.DIRT
                else -> TileType.GRASS
            }

            Biome.SHORE -> TileType.SAND
            Biome.OCEAN -> TileType.WATER
        }
    }

    private fun classify(e: Float, m: Float, tx: Int, ty: Int): Biome {
        if (e < 0.355f) return Biome.OCEAN
        if (e < 0.392f) return Biome.SHORE
        if (e > 0.760f) return Biome.HIGHLANDS
        if (isRuin(tx, ty, e)) return Biome.RUINS
        return if (m > 0.520f) Biome.FOREST else Biome.PLAINS
    }

    private fun isRuin(tx: Int, ty: Int, elevation: Float): Boolean {
        if (elevation < 0.40f || elevation > 0.72f) return false
        return ruinNoise.fbm(tx * 0.035f, ty * 0.035f, 2) > 0.740f
    }
}
