package com.emberfall.game.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseTest {

    @Test
    fun noiseIsDeterministicForSameSeed() {
        val a = ValueNoise(1234)
        val b = ValueNoise(1234)
        for (i in 0 until 200) {
            val x = i * 0.37f - 10f
            val y = i * 0.71f + 3f
            assertEquals(a.noise(x, y), b.noise(x, y), 0f)
            assertEquals(a.fbm(x, y), b.fbm(x, y), 0f)
        }
    }

    @Test
    fun differentSeedsProduceDifferentTerrain() {
        val a = ValueNoise(1)
        val b = ValueNoise(2)
        var differences = 0
        for (i in 0 until 100) {
            if (a.fbm(i * 0.5f, i * 0.25f) != b.fbm(i * 0.5f, i * 0.25f)) differences++
        }
        assertTrue("seeds should diverge", differences > 90)
    }

    @Test
    fun noiseStaysInUnitRange() {
        val noise = ValueNoise(42)
        for (x in -20..20) {
            for (y in -20..20) {
                val v = noise.fbm(x * 0.3f, y * 0.3f, 4)
                assertTrue("fbm out of range: $v", v in 0f..1f)
                assertTrue(noise.unit(x, y) in 0f..1f)
            }
        }
    }

    @Test
    fun neighbouringSamplesDiffer() {
        val noise = ValueNoise(7)
        assertNotEquals(noise.unit(10, 10), noise.unit(11, 10))
    }
}
