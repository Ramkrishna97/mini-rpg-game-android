package com.emberfall.game.render

/**
 * Fills a flat float array with sprite records for the native renderer.
 * Layout per sprite (13 floats):
 *   x y w h   (screen pixels, top-left of the unrotated rectangle)
 *   u0 v0 u1 v1 (atlas UV rectangle)
 *   r g b a   (tint, 0..1)
 *   rotation  (radians, about the rectangle's centre)
 */
class SpriteBatch(val capacity: Int = 16384) {

    val data = FloatArray(capacity * STRIDE)

    var count = 0
        private set

    fun clear() {
        count = 0
    }

    fun add(
        x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float, g: Float, b: Float, a: Float,
        rotation: Float = 0f,
    ): Boolean {
        if (count >= capacity) return false
        var i = count * STRIDE
        data[i++] = x; data[i++] = y; data[i++] = w; data[i++] = h
        data[i++] = u0; data[i++] = v0; data[i++] = u1; data[i++] = v1
        data[i++] = r; data[i++] = g; data[i++] = b; data[i++] = a
        data[i] = rotation
        count++
        return true
    }

    /** Solid colour rectangle. UV 0,0 samples the atlas's white texel. */
    fun addSolid(
        x: Float, y: Float, w: Float, h: Float,
        r: Float, g: Float, b: Float, a: Float = 1f,
        rotation: Float = 0f,
    ): Boolean = add(x, y, w, h, 0f, 0f, 0f, 0f, r, g, b, a, rotation)

    companion object {
        const val STRIDE = 13
    }
}