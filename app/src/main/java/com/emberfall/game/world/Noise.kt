package com.emberfall.game.world

import com.emberfall.game.util.lerp
import com.emberfall.game.util.smoothstep
import kotlin.math.floor

/**
 * Small, dependency-free value-noise generator.
 *
 * Everything is driven by integer hashing so a (seed, coordinate) pair always
 * produces the same value on every device and every run. That property is what
 * makes the infinite world stable across save/load and allows unit testing.
 */
class ValueNoise(seed: Int) {

    private val seed: Int = seed xor 0x5F3759DF

    fun unit(x: Int, y: Int): Float {
        val h = hash(x, y)
        // Use the high 24 bits: they mix better than the low bits.
        return (h ushr 8) / 16_777_216f
    }

    /** Smooth bilinear value noise in [0, 1]. */
    fun noise(x: Float, y: Float): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smoothstep(x - x0)
        val fy = smoothstep(y - y0)

        val a = unit(x0, y0)
        val b = unit(x0 + 1, y0)
        val c = unit(x0, y0 + 1)
        val d = unit(x0 + 1, y0 + 1)

        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
    }

    /** Fractal Brownian motion in [0, 1]. */
    fun fbm(x: Float, y: Float, octaves: Int = 4): Float {
        var amplitude = 0.5f
        var frequency = 1f
        var sum = 0f
        var norm = 0f
        for (i in 0 until octaves) {
            sum += amplitude * noise(x * frequency, y * frequency)
            norm += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        return if (norm == 0f) 0f else sum / norm
    }

    private fun hash(x: Int, y: Int): Int {
        var h = seed
        h = h xor (x * 374761393)
        h = h xor (y * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return h
    }
}
