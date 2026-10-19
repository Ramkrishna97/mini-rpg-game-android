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

    private val bentGrass = mutableMapOf<Pair<Int, Int>, Float>()
    fun bendGrass(tx: Int, ty: Int, time: Float) { bentGrass[tx to ty] = time }
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
        bentGrass.entries.removeAll { timeSeconds - it.value > .1f }
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
                val touched = bentGrass[tx to ty]
                if (touched != null && (type == com.emberfall.game.world.TileType.GRASS || type == com.emberfall.game.world.TileType.FLOWERS || type == com.emberfall.game.world.TileType.FOREST_FLOOR)) {
                    val bend = (1f - (timeSeconds-touched)/.1f).coerceIn(0f,1f)
                    propPaint.color = 0xFF93B35A.toInt(); propPaint.strokeWidth = 1f
                    repeat(5) { i ->
                        val gx = center.x + (i-2)*4f
                        canvas.drawLine(gx, center.y+2f, gx+(if(i<2) -5f else 5f)*bend, center.y-4f, propPaint)
                    }
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

    data class Light(val x: Float, val y: Float, val radius: Float)
    private val packedLights = FloatArray(96)
    private val darknessPaint = Paint()
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightMatrix = android.graphics.Matrix()
    private val lightShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF),
        floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)

    fun drawNight(canvas: Canvas, camera: Camera, player: Player, campfires: List<Campfire>,
                  minutes: Float, screenW: Float, screenH: Float, extraLights: List<Light>) {
        ambientAt(minutes)
        val alpha = ambient[3]
        if (alpha <= .01f) return
        val sources = ArrayList<Light>()
        sources.add(Light(player.x, player.y, 175f))
        for (fire in campfires) sources.add(Light(fire.x, fire.y,
            115f * (1f + .07f * kotlin.math.sin(fire.flamePhase*2.3f))))
        sources.addAll(extraLights)
        val center = IsoMath.worldToIso(camera.x, camera.y)
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && !gpuLightingTried) {
            gpuLightingTried = true
            gpuLighting = runCatching { GpuLighting() }.getOrNull()
        }
        val gpu = if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) gpuLighting as? GpuLighting else null
        if (gpu != null) {
            for (i in 0 until 32) { packedLights[i*3] = -10000f; packedLights[i*3+1] = -10000f; packedLights[i*3+2] = 1f }
            var count = 0
            for (light in sources) {
                val iso = IsoMath.worldToIso(light.x,light.y)
                val sx = screenW*.5f+(iso.x-center.x)*camera.zoom
                val sy = screenH*.5f+(iso.y-center.y)*camera.zoom
                val r = light.radius*camera.zoom
                if(sx+r<0 || sy+r<0 || sx-r>screenW || sy-r>screenH) continue
                packedLights[count*3] = sx; packedLights[count*3+1] = sy; packedLights[count*3+2] = r
                if (++count == 32) break
            }
            try { gpu.draw(canvas,screenW,screenH,alpha,packedLights); return }
            catch (_: Exception) { gpuLighting = null }
        }
        val layer = canvas.saveLayer(0f, 0f, screenW, screenH, null)
        darknessPaint.color = Color.argb((alpha*255f).toInt(), ambient[0].toInt(), ambient[1].toInt(), ambient[2].toInt())
        canvas.drawRect(0f, 0f, screenW, screenH, darknessPaint)
        // Compatible fallback: overlapping radial masks progressively remove darkness.
        lightPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        lightPaint.shader = lightShader
        for (light in sources) {
            val iso = IsoMath.worldToIso(light.x, light.y)
            val sx = screenW*.5f + (iso.x-center.x)*camera.zoom
            val sy = screenH*.5f + (iso.y-center.y)*camera.zoom
            val r = light.radius*camera.zoom
            if (sx+r<0f || sy+r<0f || sx-r>screenW || sy-r>screenH) continue
            lightMatrix.setScale(r,r); lightMatrix.postTranslate(sx,sy); lightShader.setLocalMatrix(lightMatrix)
            canvas.drawCircle(sx,sy,r,lightPaint)
        }
        canvas.restoreToCount(layer)
        lightPaint.xfermode = null
        lightPaint.shader = null
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
