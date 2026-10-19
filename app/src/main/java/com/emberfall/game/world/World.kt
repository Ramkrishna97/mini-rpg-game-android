package com.emberfall.game.world

import com.emberfall.game.core.Config
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** A tile coordinate. */
data class TilePos(val x: Int, val y: Int)

/** Minimal query surface used by collision so it can be faked in tests. */
interface TileQuery {
    fun isSolid(tx: Int, ty: Int): Boolean
}

/**
 * Infinite chunked world. Chunks are generated on demand and evicted once the
 * player wanders far enough away, keeping memory flat no matter how long the
 * session runs.
 *
 * Only the game thread touches this class; no locking is required.
 */
class World(val seed: Int, private val generator: WorldGenerator = WorldGenerator(seed)) : TileQuery {

    private val chunks = HashMap<Long, ByteArray>()

    var loadedChunks = 0
        private set

    /** Player spawn tile, guaranteed to be a walkable 3x3 clearing. */
    val spawn: TilePos = findSpawn()

    init {
        ensureAround(spawn.x, spawn.y, 1)
        clearSpawnArea()
    }

    fun tileAt(tx: Int, ty: Int): TileType {
        val cx = Math.floorDiv(tx, Config.CHUNK_TILES)
        val cy = Math.floorDiv(ty, Config.CHUNK_TILES)
        val lx = tx - cx * Config.CHUNK_TILES
        val ly = ty - cy * Config.CHUNK_TILES
        return TileType.fromId(chunk(cx, cy)[ly * Config.CHUNK_TILES + lx].toInt())
    }

    override fun isSolid(tx: Int, ty: Int): Boolean = tileAt(tx, ty).solid

    fun biomeAt(tx: Int, ty: Int): Biome = generator.biomeAt(tx, ty)

    fun tileAtWorld(worldX: Float, worldY: Float): TileType =
        tileAt(floor(worldX / Config.TILE).toInt(), floor(worldY / Config.TILE).toInt())

    fun tileX(worldX: Float): Int = floor(worldX / Config.TILE).toInt()

    fun tileY(worldY: Float): Int = floor(worldY / Config.TILE).toInt()

    /** Generate every chunk within [radius] of the given tile. */
    fun ensureAround(tileX: Int, tileY: Int, radius: Int = Config.LOAD_CHUNK_RADIUS) {
        val cx = Math.floorDiv(tileX, Config.CHUNK_TILES)
        val cy = Math.floorDiv(tileY, Config.CHUNK_TILES)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                chunk(cx + dx, cy + dy)
            }
        }
    }

    /** Drop chunks that are no longer near the player. */
    fun pruneAround(tileX: Int, tileY: Int, keepRadius: Int = Config.KEEP_CHUNK_RADIUS) {
        if (chunks.size <= (keepRadius * 2 + 1) * (keepRadius * 2 + 1)) return
        val cx = Math.floorDiv(tileX, Config.CHUNK_TILES)
        val cy = Math.floorDiv(tileY, Config.CHUNK_TILES)
        val iterator = chunks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.key
            val chunkX = (key shr 32).toInt()
            val chunkY = key.toInt()
            if (abs(chunkX - cx) > keepRadius || abs(chunkY - cy) > keepRadius) {
                iterator.remove()
            }
        }
        loadedChunks = chunks.size
    }

    private fun chunk(cx: Int, cy: Int): ByteArray {
        val key = key(cx, cy)
        val existing = chunks[key]
        if (existing != null) return existing
        val generated = generator.generateChunk(cx, cy)
        chunks[key] = generated
        loadedChunks = chunks.size
        return generated
    }

    private fun key(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private fun findSpawn(): TilePos {
        // Spiral outwards from the origin until a 3x3 walkable patch is found.
        for (radius in 0..72) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (max(abs(dx), abs(dy)) != radius) continue
                    if (isClearSpawn(dx, dy)) return TilePos(dx, dy)
                }
            }
        }
        return TilePos(0, 0)
    }

    private fun isClearSpawn(tx: Int, ty: Int): Boolean {
        for (oy in -1..1) {
            for (ox in -1..1) {
                val tile = tileAt(tx + ox, ty + oy)
                if (tile.solid) return false
                // Keep new games out of ruin fields and off the shoreline sand.
                if (tile == TileType.RUIN_FLOOR || tile == TileType.SAND) return false
            }
        }
        return true
    }

    private fun clearSpawnArea() {
        // Guarantee breathing room even if the search had to fall back, using
        // ground that matches the surrounding biome.
        val ground =
            if (generator.biomeAt(spawn.x, spawn.y) == Biome.FOREST) {
                TileType.FOREST_FLOOR
            } else {
                TileType.GRASS
            }
        for (oy in -1..1) {
            for (ox in -1..1) {
                setTile(spawn.x + ox, spawn.y + oy, ground)
            }
        }
    }

    private fun setTile(tx: Int, ty: Int, type: TileType) {
        val cx = Math.floorDiv(tx, Config.CHUNK_TILES)
        val cy = Math.floorDiv(ty, Config.CHUNK_TILES)
        val lx = tx - cx * Config.CHUNK_TILES
        val ly = ty - cy * Config.CHUNK_TILES
        chunk(cx, cy)[ly * Config.CHUNK_TILES + lx] = type.id.toByte()
    }
}
