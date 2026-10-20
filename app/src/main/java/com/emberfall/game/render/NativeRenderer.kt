package com.emberfall.game.render

import android.content.res.AssetManager
import android.view.Surface

/**
 * Kotlin handle to the native Vulkan renderer. All calls must come from the
 * game loop thread. A handle of 0 means initialisation failed; callers should
 * then fall back to the Canvas renderer.
 */
class NativeRenderer(surface: Surface, assets: AssetManager) {

    private var handle: Long = nativeCreate(surface, assets)

    val isReady: Boolean get() = handle != 0L

    fun requestRecreate() {
        if (handle != 0L) nativeRequestRecreate(handle)
    }

    fun uploadAtlas(pixels: IntArray, width: Int, height: Int): Boolean =
        handle != 0L && nativeUploadAtlas(handle, pixels, width, height)

    /**
     * Draws the sprite batch, then the night pass if [ambient] > 0.
     * [lights] holds 4 floats per light (x, y, radius, unused); only the first [lightCount] are used.
     */
    fun drawFrame(
        sprites: FloatArray, count: Int,
        clearR: Float, clearG: Float, clearB: Float,
        lights: FloatArray, lightCount: Int, ambient: Float,
    ): Boolean = handle != 0L &&
            nativeDrawFrame(handle, sprites, count, clearR, clearG, clearB, lights, lightCount, ambient)

    fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(surface: Surface, assets: AssetManager): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeRequestRecreate(handle: Long)
    private external fun nativeUploadAtlas(handle: Long, pixels: IntArray, width: Int, height: Int): Boolean
    private external fun nativeDrawFrame(
        handle: Long, sprites: FloatArray, count: Int,
        clearR: Float, clearG: Float, clearB: Float,
        lights: FloatArray, lightCount: Int, ambient: Float,
    ): Boolean

    companion object {
        init {
            System.loadLibrary("emberfall_vk")
        }
    }
}