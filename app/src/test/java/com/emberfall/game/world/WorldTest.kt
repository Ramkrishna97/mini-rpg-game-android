package com.emberfall.game.world

import com.emberfall.game.core.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {

    @Test
    fun worldTilesMatchDirectGeneration() {
        val seed = 98765
        val world = World(seed)
        val generator = WorldGenerator(seed)
        for (i in 0 until 50) {
            val tx = -40 + i * 3
            val ty = 25 - i * 2
            assertEquals(generator.tileAt(tx, ty), world.tileAt(tx, ty))
        }
    }

    @Test
    fun negativeCoordinatesUseCorrectChunks() {
        val seed = 424242
        val world = World(seed)
        val generator = WorldGenerator(seed)
        val chunk = generator.generateChunk(-1, -1)
        val expected = TileType.fromId(chunk[31 * Config.CHUNK_TILES + 31].toInt())
        assertEquals(expected, world.tileAt(-1, -1))
    }

    @Test
    fun spawnIsAlwaysWalkableAndClear() {
        for (seed in intArrayOf(1, 7, 1234, -99, Int.MAX_VALUE)) {
            val world = World(seed)
            for (oy in -1..1) {
                for (ox in -1..1) {
                    assertFalse(
                        "seed $seed spawn blocked at $ox,$oy",
                        world.isSolid(world.spawn.x + ox, world.spawn.y + oy),
                    )
                }
            }
        }
    }

    @Test
    fun chunksAreCachedNotRegenerated() {
        val world = World(555)
        world.ensureAround(0, 0, 1)
        val loaded = world.loadedChunks
        world.ensureAround(0, 0, 1)
        assertEquals(loaded, world.loadedChunks)
        assertTrue(loaded >= 9)
    }

    @Test
    fun pruningKeepsNearbyChunks() {
        val world = World(31)
        world.ensureAround(0, 0, 2)
        world.pruneAround(0, 0, 1)
        assertEquals(9, world.loadedChunks)
    }
}
