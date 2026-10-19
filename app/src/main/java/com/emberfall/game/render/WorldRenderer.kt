package com.emberfall.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.emberfall.game.core.Camera
import com.emberfall.game.core.Config
import com.emberfall.game.entity.Campfire
import com.emberfall.game.entity.Player
import com.emberfall.game.world.World
import kotlin.math.abs
import kotlin.math.floor

/**
 * Draws the visible chunk of terrain and applies the day/night colour grade.
 */
class WorldRenderer(context: Context) {

    private val atlas = TileAtlas(context)

    private var gpuLighting: Any? = null
    private var gpuLightingTried = false

    private val tilePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }

    fun drawVignette(canvas: Canvas, screenW: Float, screenH: Float) {
        vignetteMatrix.setScale(screenW, screenH)
        vignetteShader.setLocalMatrix(vignetteMatrix)
        canvas.drawRect(0f, 0f, screenW, screenH, vignettePaint)
    }

    fun configure(graphics: com.emberfall.game.core.GraphicsSettings) {
        // Tile bitmaps stay point-sampled/without AA: smoothing them produces
        // visible seams at the atlas edges.
        tilePaint.isFilterBitmap = graphics.smoothTextures
    }

    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowBitmap = createGlowBitmap()
    private val glowRect = RectF()
    private val vignetteMatrix = android.graphics.Matrix()
    private val vignetteShader = RadialGradient(
        0.5f,
        0.5f,
        0.72f,
        intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(95, 0, 0, 0)),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignetteShader }
    private val tileDst = RectF()
    private val ambient = FloatArray(4)

    fun drawTiles(canvas: Canvas, world: World, camera: Camera, screenW: Float, screenH: Float, timeSeconds: Float) {
        val zoom = camera.zoom
        val halfW = screenW / (2f * zoom)
        val halfH = screenH / (2f * zoom)

        val minTx = floor((camera.x - halfW) / Config.TILE).toInt() - 1
        val maxTx = floor((camera.x + halfW) / Config.TILE).toInt() + 1
        val minTy = floor((camera.y - halfH) / Config.TILE).toInt() - 1
        val maxTy = floor((camera.y + halfH) / Config.TILE).toInt() + 1

        for (ty in minTy..maxTy) {
            val worldY = ty * Config.TILE
            for (tx in minTx..maxTx) {
                val type = world.tileAt(tx, ty)
                val variant = if (type == com.emberfall.game.world.TileType.WATER || type == com.emberfall.game.world.TileType.DEEP_WATER) {
                    // Gently cycle the four water variants for a live shoreline.
                    tx * 3 + ty * 5 + (timeSeconds * 2.5f).toInt()
                } else {
                    (tx * 73856093) xor (ty * 19349663)
                }
                val left = tx * Config.TILE
                tileDst.set(left, worldY, left + Config.TILE, worldY + Config.TILE)
                canvas.drawBitmap(atlas.bitmap(type, variant), null, tileDst, tilePaint)
            }
        }
    }

    /**
     * Night colour grade with dynamic lights. On API 33+ a single AGSL
     * fragment pass handles ambient darkness and up to three light pools
     * (player + nearby campfires). Older devices use the original overlay.
     */
    fun drawNight(
        canvas: Canvas,
        camera: Camera,
        player: Player,
        campfires: List<Campfire>,
        minutes: Float,
        screenW: Float,
        screenH: Float,
    ) {
        ambientAt(minutes)
        val alpha = ambient[3]
        if (alpha <= 0.01f) return

        val zoom = camera.zoom
        val lights = FloatArray(9)
        val playerScreenX = screenW * 0.5f + (player.x - camera.x) * zoom
        val playerScreenY = screenH * 0.5f + (player.y - camera.y) * zoom
        lights[0] = playerScreenX
        lights[1] = playerScreenY
        lights[2] = 175f * zoom
        var lightCount = 1

        for (fire in campfires) {
            if (lightCount >= 3) break
            if (abs(fire.x - player.x) > 16f * Config.TILE) continue
            if (abs(fire.y - player.y) > 16f * Config.TILE) continue
            val sx = screenW * 0.5f + (fire.x - camera.x) * zoom
            val sy = screenH * 0.5f + (fire.y - camera.y) * zoom
            lights[lightCount * 3] = sx
            lights[lightCount * 3 + 1] = sy
            lights[lightCount * 3 + 2] = 105f * zoom
            lightCount++
        }
        for (i in lightCount until 3) {
            lights[i * 3] = -10000f
            lights[i * 3 + 1] = -10000f
            lights[i * 3 + 2] = 1f
        }

        val gpu = gpuLighting()
        if (gpu != null) {
            gpu.draw(canvas, screenW, screenH, alpha, lights)
        } else {
            canvas.drawColor(
                Color.argb(
                    (alpha * 255f).toInt().coerceIn(0, 255),
                    ambient[0].toInt().coerceIn(0, 255),
                    ambient[1].toInt().coerceIn(0, 255),
                    ambient[2].toInt().coerceIn(0, 255),
                ),
            )
        }

        if (alpha < 0.22f) return

        drawGlow(canvas, playerScreenX, playerScreenY, 165f * zoom, alpha, player = true)
        for (fire in campfires) {
            if (abs(fire.x - player.x) > 16f * Config.TILE) continue
            if (abs(fire.y - player.y) > 16f * Config.TILE) continue
            val sx = screenW * 0.5f + (fire.x - camera.x) * zoom
            val sy = screenH * 0.5f + (fire.y - camera.y) * zoom
            drawGlow(canvas, sx, sy, 95f * zoom, alpha, player = false)
        }
    }

    private fun drawGlow(
        canvas: Canvas,
        screenX: Float,
        screenY: Float,
        radius: Float,
        alpha: Float,
        player: Boolean,
    ) {
        glowRect.set(screenX - radius, screenY - radius, screenX + radius, screenY + radius)
        val base = if (player) 245f else 150f
        glowPaint.alpha = (((alpha - 0.2f) / 0.8f) * base).toInt().coerceIn(0, 255)
        canvas.drawBitmap(glowBitmap, null, glowRect, glowPaint)
    }

    private fun gpuLighting(): GpuLighting? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (!gpuLightingTried) {
            gpuLightingTried = true
            gpuLighting = try {
                GpuLighting()
            } catch (t: Throwable) {
                null
            }
        }
        return gpuLighting as? GpuLighting
    }

    /** Interpolates the ambient tint into [ambient] (r, g, b, alpha). */
    private fun ambientAt(minutes: Float) {
        val hour = (minutes / 60f).coerceIn(0f, 24f)
        var index = 0
        while (index < STOPS.size - 2 && hour > STOPS[index + 1][0]) index++

        val a = STOPS[index]
        val b = STOPS[index + 1]
        val span = (b[0] - a[0]).coerceAtLeast(0.0001f)
        val t = ((hour - a[0]) / span).coerceIn(0f, 1f)

        ambient[0] = a[1] + (b[1] - a[1]) * t
        ambient[1] = a[2] + (b[2] - a[2]) * t
        ambient[2] = a[3] + (b[3] - a[3]) * t
        ambient[3] = a[4] + (b[4] - a[4]) * t
    }

    private fun createGlowBitmap(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            size / 2f,
            size / 2f,
            size / 2f - 2f,
            intArrayOf(
                Color.argb(190, 255, 218, 165),
                Color.argb(95, 255, 196, 128),
                Color.argb(0, 255, 170, 90),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
        return bitmap
    }

    private companion object {
        /** hour, r, g, b, alpha */
        val STOPS = arrayOf(
            floatArrayOf(0f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(4.5f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(6.0f, 48f, 34f, 50f, 0.48f),
            floatArrayOf(7.0f, 255f, 140f, 80f, 0.28f),
            floatArrayOf(8.5f, 255f, 255f, 255f, 0f),
            floatArrayOf(16.5f, 255f, 255f, 255f, 0f),
            floatArrayOf(18.0f, 255f, 120f, 60f, 0.30f),
            floatArrayOf(19.5f, 44f, 32f, 54f, 0.55f),
            floatArrayOf(21.0f, 14f, 22f, 48f, 0.68f),
            floatArrayOf(24f, 14f, 22f, 48f, 0.68f),
        )
    }
}
