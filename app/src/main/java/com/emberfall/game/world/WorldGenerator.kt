package com.emberfall.game.world

import com.emberfall.game.core.Config
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** A fixed island silhouette with seed-dependent detail and five themed boss regions. */
class WorldGenerator(val seed: Int) {
    private val elevationNoise = ValueNoise(seed)
    private val moistureNoise = ValueNoise(seed + 1013)
    private val detailNoise = ValueNoise(seed + 7717)
    private val treeNoise = ValueNoise(seed + 4241)
    private val scatterNoise = ValueNoise(seed + 555)

    fun elevationAt(tx: Int, ty: Int): Float =
        elevationNoise.fbm(tx * .016f, ty * .016f, 4)

    fun moistureAt(tx: Int, ty: Int): Float =
        moistureNoise.fbm(tx * .024f + 31.7f, ty * .024f - 12.4f, 3)

    private fun islandEdge(tx: Int, ty: Int): Float {
        val warp = (elevationAt(tx, ty) - .5f) * .20f
        val x = tx / 146f
        val y = (ty + 1f) / 128f
        return x * x + y * y + warp
    }

    private fun river(tx: Int, ty: Int): Boolean {
        if (ty !in -110..106) return false
        val center = 51f + 15f * sin(ty * .047f) + ty * .16f
        return abs(tx - center) < 2.3f
    }

    private val routes = arrayOf(
        intArrayOf(0, 0, -20, -52), intArrayOf(-20, -52, -26, -98),
        intArrayOf(-20, -52, -106, -57), intArrayOf(0, 0, 58, -32),
        intArrayOf(58, -32, 95, -66), intArrayOf(0, 0, 26, 43),
        intArrayOf(0, 0, -58, 33), intArrayOf(-58, 33, -94, 72),
        intArrayOf(26, 43, 17, 67),
    )

    private fun path(tx: Int, ty: Int): Boolean = routes.any {
        val ax = it[0].toFloat(); val ay = it[1].toFloat()
        val vx = it[2] - ax; val vy = it[3] - ay
        val t = (((tx - ax) * vx + (ty - ay) * vy) / (vx * vx + vy * vy)).coerceIn(0f, 1f)
        hypot(tx - ax - t * vx, ty - ay - t * vy) < 1.8f
    }

    private fun arenaAt(tx: Int, ty: Int): BossDef? = Bosses.ALL.firstOrNull {
        val dx = tx - it.tileX; val dy = ty - it.tileY
        dx * dx + dy * dy <= it.arenaRadiusTiles * it.arenaRadiusTiles
    }

    fun biomeAt(tx: Int, ty: Int): Biome {
        arenaAt(tx, ty)?.let { return when (it.id) {
            "hollowking" -> Biome.SNOW
            "ashtitan" -> Biome.VOLCANIC
            "mirewitch" -> Biome.DESERT
            "stonewarden" -> Biome.HIGHLANDS
            else -> Biome.RUINS
        } }
        val edge = islandEdge(tx, ty)
        if (edge > 1.03f) return Biome.OCEAN
        if (edge > .91f) return Biome.SHORE
        if (hypot((tx + 106f) / 43f, (ty + 57f) / 41f) < 1f) return Biome.VOLCANIC
        if (ty < -65 && tx in -72..31) return Biome.SNOW
        if (tx > 64 && ty < -23) return Biome.HIGHLANDS
        if (tx < -42 && ty > 27) return Biome.DESERT
        if (hypot((tx - 26f) / 25f, (ty - 43f) / 23f) < 1f) return Biome.RUINS
        return if (moistureAt(tx, ty) > .49f) Biome.FOREST else Biome.PLAINS
    }

    fun generateChunk(cx: Int, cy: Int): ByteArray {
        val size = Config.CHUNK_TILES
        val out = ByteArray(size * size)
        for (ly in 0 until size) for (lx in 0 until size) {
            out[ly * size + lx] = tileAt(cx * size + lx, cy * size + ly).id.toByte()
        }
        return out
    }

    fun tileAt(tx: Int, ty: Int): TileType {
        arenaAt(tx, ty)?.let { return arenaTile(it, tx, ty) }
        val biome = biomeAt(tx, ty)
        if (biome == Biome.OCEAN) return TileType.DEEP_WATER
        if (biome == Biome.SHORE) return if (islandEdge(tx, ty) > 1f) TileType.WATER else TileType.SAND

        // Each boss has a continuous route from the starting camp, including bridges.
        if (path(tx, ty)) return if (river(tx, ty)) TileType.ARENA_STONE else TileType.DIRT
        if (river(tx, ty) && biome != Biome.VOLCANIC) return TileType.WATER

        val detail = detailNoise.fbm(tx * .11f, ty * .11f, 2)
        return when (biome) {
            Biome.SNOW -> if (detail > .78f && scatterNoise.unit(tx, ty) > .58f) TileType.ROCK else TileType.SNOW
            Biome.VOLCANIC -> when {
                hypot((tx + 106f) / 25f, (ty + 57f) / 25f) < .9f && detail > .50f -> TileType.LAVA
                detail > .77f -> TileType.ROCK
                else -> TileType.ASH
            }
            Biome.DESERT -> if (detail > .79f && scatterNoise.unit(tx, ty) > .7f) TileType.ROCK else TileType.SAND
            Biome.HIGHLANDS -> if (detail > .65f) TileType.ROCK else if (ty < -52) TileType.SNOW else TileType.GRASS
            Biome.RUINS -> if (detail > .83f) TileType.ROCK else TileType.RUIN_FLOOR
            Biome.FOREST -> when {
                treeNoise.noise(tx * .30f, ty * .30f) > .59f -> TileType.FOREST_TREE
                detail > .70f -> TileType.FLOWERS
                else -> TileType.FOREST_FLOOR
            }
            Biome.PLAINS -> when {
                scatterNoise.unit(tx, ty) > .982f -> TileType.TREE
                detail > .70f -> TileType.FLOWERS
                detail < .22f -> TileType.DIRT
                else -> TileType.GRASS
            }
            Biome.SHORE -> TileType.SAND
            Biome.OCEAN -> TileType.WATER
        }
    }

    private fun arenaTile(boss: BossDef, tx: Int, ty: Int): TileType {
        val dx = tx - boss.tileX; val dy = ty - boss.tileY
        val distance = hypot(dx.toFloat(), dy.toFloat())
        val gate = abs(dx) <= 2 || abs(dy) <= 2 || path(tx, ty)
        if (distance > boss.arenaRadiusTiles - 1.4f && !gate) {
            return if (boss.id == "ashtitan") TileType.LAVA else TileType.ARENA_STONE
        }
        if (abs(distance - boss.arenaRadiusTiles * .60f) < .65f ||
            (abs(dx) <= 1 && abs(dy) <= 1)) return TileType.ARENA_STONE
        return when (boss.id) {
            "hollowking" -> TileType.SNOW
            "ashtitan" -> TileType.ASH
            "mirewitch" -> TileType.DIRT
            "gloomfang" -> TileType.RUIN_FLOOR
            else -> TileType.ARENA_STONE
        }
    }
}
