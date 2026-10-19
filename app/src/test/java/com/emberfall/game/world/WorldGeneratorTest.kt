package com.emberfall.game.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldGeneratorTest {

    @Test
    fun chunkHasExpectedSizeAndValidTiles() {
        val generator = WorldGenerator(2026)
        val chunk = generator.generateChunk(0, 0)
        assertEquals(
            com.emberfall.game.core.Config.CHUNK_TILES *
                com.emberfall.game.core.Config.CHUNK_TILES,
            chunk.size,
        )
        for (value in chunk) {
            assertTrue(value.toInt() in 0..TileType.values().lastIndex)
        }
    }

    @Test
    fun generationIsDeterministic() {
        val a = WorldGenerator(31337)
        val b = WorldGenerator(31337)
        val chunkA = a.generateChunk(-3, 7)
        val chunkB = b.generateChunk(-3, 7)
        assertTrue(chunkA.contentEquals(chunkB))
    }

    @Test
    fun oceanTilesAreWaterAndImpassable() {
        val generator = WorldGenerator(99)
        var checked = 0
        for (ty in -300..300 step 3) {
            for (tx in -300..300 step 3) {
                if (generator.biomeAt(tx, ty) != Biome.OCEAN) continue
                val tile = generator.tileAt(tx, ty)
                assertTrue(
                    "ocean must be water, was $tile",
                    tile == TileType.WATER || tile == TileType.DEEP_WATER,
                )
                assertTrue(tile.solid)
                checked++
            }
        }
        assertTrue("expected ocean somewhere in the sampled region", checked > 0)
    }

    @Test
    fun forestBiomeProducesForestTiles() {
        val generator = WorldGenerator(5150)
        var checked = 0
        for (ty in -80..80 step 2) {
            for (tx in -80..80 step 2) {
                if (generator.biomeAt(tx, ty) != Biome.FOREST) continue
                val tile = generator.tileAt(tx, ty)
                assertTrue(
                    "unexpected tile $tile in forest",
                    tile == TileType.FOREST_TREE ||
                        tile == TileType.FOREST_FLOOR ||
                        tile == TileType.FLOWERS,
                )
                checked++
            }
        }
        assertTrue("expected to find forest in sample area", checked > 0)
    }

    @Test
    fun worldContainsEveryMajorBiome() {
        val generator = WorldGenerator(99)
        val counts = IntArray(Biome.values().size)
        var total = 0
        for (ty in -300..300 step 2) {
            for (tx in -300..300 step 2) {
                counts[generator.biomeAt(tx, ty).ordinal]++
                total++
            }
        }
        val ocean = counts[Biome.OCEAN.ordinal] * 100f / total
        val plains = counts[Biome.PLAINS.ordinal] * 100f / total
        val forest = counts[Biome.FOREST.ordinal] * 100f / total
        val ruins = counts[Biome.RUINS.ordinal] * 100f / total

        assertTrue("ocean should cover a noticeable area, was $ocean%", ocean > 2f)
        assertTrue("plains should be common, was $plains%", plains > 10f)
        assertTrue("forest should be common, was $forest%", forest > 10f)
        assertTrue("ruins should stay rare, was $ruins%", ruins < 12f)
    }

    @Test
    fun spawnAreaIsDryGround() {
        for (seed in intArrayOf(3, 44, 9001)) {
            val world = World(seed)
            val tile = world.tileAt(world.spawn.x, world.spawn.y)
            assertTrue(
                "spawn tile should be dry ground, was $tile",
                tile == TileType.GRASS || tile == TileType.FOREST_FLOOR,
            )
            assertTrue(!tile.solid)
        }
    }
}
