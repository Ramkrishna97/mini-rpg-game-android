package com.emberfall.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Path
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

    data class Prop(val x: Float, val y: Float, val type: com.emberfall.game.world.TileType)

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
    private val propPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val propRect = RectF()
    private val propPath = Path()
    private val ambient = FloatArray(4)

    fun drawTiles(canvas: Canvas, world: World, camera: Camera, screenW: Float, screenH: Float, timeSeconds: Float) {
        val bounds = IsoMath.visibleGridBounds(camera.x, camera.y, screenW, screenH, camera.zoom)
        val minTx = bounds[0]; val maxTx = bounds[1]
        val minTy = bounds[2]; val maxTy = bounds[3]

        // Diagonal traversal is the isometric painter order for the ground plane.
        for (depth in minTx + minTy..maxTx + maxTy) {
            for (tx in minTx..maxTx) {
                val ty = depth - tx
                if (ty < minTy || ty > maxTy) continue
                val type = IsoAutotile.surface(world.tileAt(tx, ty))
                val variant = if (type == com.emberfall.game.world.TileType.WATER || type == com.emberfall.game.world.TileType.DEEP_WATER) {
                    tx * 3 + ty * 5 + (timeSeconds * 2.5f).toInt()
                } else {
                    (tx * 73856093) xor (ty * 19349663)
                }
                val center = IsoMath.gridToIso(tx + .5f, ty + .5f)
                tileDst.set(center.x - IsoMath.TILE_WIDTH / 2f, center.y - IsoMath.TILE_HEIGHT / 2f,
                    center.x + IsoMath.TILE_WIDTH / 2f, center.y + IsoMath.TILE_HEIGHT / 2f)
                canvas.drawBitmap(atlas.isoBitmap(type, variant), null, tileDst, tilePaint)
                for ((target, mask) in IsoAutotile.transitions(world, tx, ty, type)) {
                    canvas.drawBitmap(atlas.transitionBitmap(target, mask), null, tileDst, tilePaint)
                }
            }
        }
    }

    fun visibleProps(world: World, camera: Camera, screenW: Float, screenH: Float): List<Prop> {
        val bounds = IsoMath.visibleGridBounds(camera.x, camera.y, screenW, screenH, camera.zoom, 3)
        val result = ArrayList<Prop>()
        for (ty in bounds[2]..bounds[3]) for (tx in bounds[0]..bounds[1]) {
            val type = world.tileAt(tx, ty)
            if (type == com.emberfall.game.world.TileType.TREE || type == com.emberfall.game.world.TileType.FOREST_TREE ||
                type == com.emberfall.game.world.TileType.ROCK || type == com.emberfall.game.world.TileType.RUIN_WALL) {
                result.add(Prop((tx + .5f) * Config.TILE, (ty + .5f) * Config.TILE, type))
            }
        }
        return result
    }

    fun drawProp(canvas: Canvas, type: com.emberfall.game.world.TileType, x: Float, y: Float) {
        propPaint.color = 0x59000000
        propRect.set(x - 19f, y - 4f, x + 19f, y + 6f)
        canvas.drawOval(propRect, propPaint)
        when (type) {
            com.emberfall.game.world.TileType.TREE, com.emberfall.game.world.TileType.FOREST_TREE -> {
                propPaint.color = 0xFF5C3E25.toInt()
                canvas.drawRect(x - 4f, y - 24f, x + 5f, y + 1f, propPaint)
                propPaint.color = 0xFF2B5730.toInt()
                propPath.reset(); propPath.moveTo(x, y - 55f); propPath.lineTo(x + 22f, y - 17f)
                propPath.lineTo(x, y - 10f); propPath.lineTo(x - 22f, y - 17f); propPath.close()
                canvas.drawPath(propPath, propPaint)
                propPaint.color = if (type == com.emberfall.game.world.TileType.FOREST_TREE) 0xFF3C7440.toInt() else 0xFF559243.toInt()
                propPath.reset(); propPath.moveTo(x - 2f, y - 53f); propPath.lineTo(x + 16f, y - 21f)
                propPath.lineTo(x - 2f, y - 15f); propPath.lineTo(x - 16f, y - 22f); propPath.close()
                canvas.drawPath(propPath, propPaint)
                propPaint.color = 0xFF76A852.toInt()
                canvas.drawRect(x - 11f, y - 30f, x - 5f, y - 27f, propPaint)
            }
            com.emberfall.game.world.TileType.ROCK -> {
                propPaint.color = 0xFF4C505D.toInt()
                propPath.reset(); propPath.moveTo(x - 17f, y - 2f); propPath.lineTo(x - 12f, y - 18f)
                propPath.lineTo(x + 7f, y - 25f); propPath.lineTo(x + 18f, y - 11f)
                propPath.lineTo(x + 17f, y - 2f); propPath.close(); canvas.drawPath(propPath, propPaint)
                propPaint.color = 0xFF9197A0.toInt()
                propPath.reset(); propPath.moveTo(x - 12f, y - 18f); propPath.lineTo(x + 7f, y - 25f)
                propPath.lineTo(x + 2f, y - 13f); propPath.lineTo(x - 15f, y - 9f); propPath.close()
                canvas.drawPath(propPath, propPaint)
            }
            com.emberfall.game.world.TileType.RUIN_WALL -> {
                propPaint.color = 0xFF595967.toInt()
                canvas.drawRect(x - 19f, y - 22f, x + 19f, y - 1f, propPaint)
                propPaint.color = 0xFF858592.toInt()
                canvas.drawRect(x - 19f, y - 27f, x + 19f, y - 21f, propPaint)
            }
            else -> Unit
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
        val cameraIso = IsoMath.worldToIso(camera.x, camera.y)
        val playerIso = IsoMath.worldToIso(player.x, player.y)
        val playerScreenX = screenW * 0.5f + (playerIso.x - cameraIso.x) * zoom
        val playerScreenY = screenH * 0.5f + (playerIso.y - cameraIso.y) * zoom
        lights[0] = playerScreenX
        lights[1] = playerScreenY
        lights[2] = 175f * zoom
        var lightCount = 1

        for (fire in campfires) {
            if (lightCount >= 3) break
            if (abs(fire.x - player.x) > 16f * Config.TILE) continue
            if (abs(fire.y - player.y) > 16f * Config.TILE) continue
            val fireIso = IsoMath.worldToIso(fire.x, fire.y)
            val sx = screenW * 0.5f + (fireIso.x - cameraIso.x) * zoom
            val sy = screenH * 0.5f + (fireIso.y - cameraIso.y) * zoom
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
            val fireIso = IsoMath.worldToIso(fire.x, fire.y)
            val sx = screenW * 0.5f + (fireIso.x - cameraIso.x) * zoom
            val sy = screenH * 0.5f + (fireIso.y - cameraIso.y) * zoom
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
