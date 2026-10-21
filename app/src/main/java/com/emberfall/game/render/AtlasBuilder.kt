package com.emberfall.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.emberfall.game.content.ContentAssetIds
import com.emberfall.game.content.ContentRepository
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.entity.Animal
import com.emberfall.game.entity.Boss as BossEntity
import com.emberfall.game.entity.AnimalSpecies
import com.emberfall.game.entity.Campfire
import com.emberfall.game.entity.LootChest
import com.emberfall.game.entity.Pickup
import com.emberfall.game.entity.Wolf
import com.emberfall.game.item.ItemId
import com.emberfall.game.world.Bosses
import com.emberfall.game.world.TileType

/**
 * Builds the whole atlas as ARGB pixels on the CPU, once at start-up.
 * Terrain, props, character sheets and entity art all come from the same
 * code and assets the Canvas path uses, so the two renderers match.
 */
object AtlasBuilder {

    private const val TAG = "EmberfallAtlas"

    fun build(context: Context, layout: AtlasLayout): IntArray {
        val width = AtlasLayout.ATLAS_W
        val pixels = IntArray(width * layout.height)
        buildTerrain(context, pixels, layout)
        buildProps(context, pixels, layout)
        buildSheets(context, pixels, layout)
        buildEntityCells(context, pixels, layout)
        return pixels
    }

    // ---- Terrain ----------------------------------------------------------

    private fun buildTerrain(context: Context, pixels: IntArray, layout: AtlasLayout) {
        val width = AtlasLayout.ATLAS_W
        val tiles = TileAtlas(context)
        for (type in TileType.values()) {
            for (variant in 0 until AtlasLayout.VARIANTS) {
                val index = layout.terrainIndex(type, variant)
                blitBitmap(tiles.isoBitmap(type, variant), pixels, width,
                    layout.cellX(index) + AtlasLayout.GUTTER, layout.cellY(index) + AtlasLayout.GUTTER)
            }
        }
        for (target in TileType.values()) {
            for (maskIndex in 0 until layout.masks) {
                val index = layout.transitionIndex(target, maskIndex)
                blitBitmap(tiles.transitionBitmap(target, IsoAutotile.masks[maskIndex]), pixels, width,
                    layout.cellX(index) + AtlasLayout.GUTTER, layout.cellY(index) + AtlasLayout.GUTTER)
            }
        }
    }

    // ---- Props ------------------------------------------------------------

    private fun buildProps(context: Context, pixels: IntArray, layout: AtlasLayout) {
        val width = AtlasLayout.ATLAS_W
        val worldRenderer = WorldRenderer(context)
        for (type in AtlasLayout.PROP_TYPES) {
            val cell = Bitmap.createBitmap(AtlasLayout.PROP_CELL, AtlasLayout.PROP_CELL, Bitmap.Config.ARGB_8888)
            worldRenderer.drawProp(Canvas(cell), type,
                AtlasLayout.PROP_ANCHOR_X.toFloat(), AtlasLayout.PROP_ANCHOR_Y.toFloat())
            val index = layout.propIndex(type)
            blitBitmap(cell, pixels, width, layout.propX(index), layout.propPixelY(index))
            cell.recycle()
        }
    }

    // ---- Character sheets -------------------------------------------------

    private fun buildSheets(context: Context, pixels: IntArray, layout: AtlasLayout) {
        val width = AtlasLayout.ATLAS_W
        val content = ContentRepository.from(context)
        for (name in AtlasLayout.SHEET_NAMES) {
            val bitmap = content.bitmap(
                ContentAssetIds.sprite(name),
                "sprites/$name.png",
            ) { it.width == (if (name == "player") 384 else AtlasLayout.SHEET_W) && it.height == AtlasLayout.SHEET_H } ?: continue
            if (bitmap.width != (if (name == "player") 384 else AtlasLayout.SHEET_W) || bitmap.height != AtlasLayout.SHEET_H) {
                Log.w(TAG, "sprite sheet $name is ${bitmap.width}x${bitmap.height}, expected " +
                        "${AtlasLayout.SHEET_W}x${AtlasLayout.SHEET_H}; skipped")
                bitmap.recycle()
                continue
            }
            val w = bitmap.width
            val h = bitmap.height
            val argb = IntArray(w * h)
            bitmap.getPixels(argb, 0, w, 0, 0, w, h)
            bitmap.recycle()

            blitArray(argb, w, h, pixels, width, layout.sheetX(name, false), layout.sheetY(name, false))

            // Silhouette: white with the same alpha. Multiplying by the rim tint gives the outline.
            val silhouette = IntArray(argb.size) { i -> ((argb[i] ushr 24) shl 24) or 0x00FFFFFF }
            blitArray(silhouette, w, h, pixels, width, layout.sheetX(name, true), layout.sheetY(name, true))
        }
    }

    // ---- Entity cells -----------------------------------------------------

    private fun buildEntityCells(context: Context, pixels: IntArray, layout: AtlasLayout) {
        val width = AtlasLayout.ATLAS_W
        val creatures = CreatureRenderer(context)
        val objects = ObjectRenderer(context)
        val art = ArtSheets.from(context)
        val graphics = GraphicsSettings()
        val ax = AtlasLayout.ENT_ANCHOR_X.toFloat()
        val ay = AtlasLayout.ENT_ANCHOR_Y.toFloat()

        for (key in layout.entityKeys) {
            val cell = Bitmap.createBitmap(AtlasLayout.ENT_CELL, AtlasLayout.ENT_CELL, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(cell)
            when {
                key == "shadow" -> drawShadow(canvas)
                key == "wolf" -> creatures.drawWolf(canvas, Wolf(ax, ay), 0f)
                key == "dragon" -> creatures.drawBoss(canvas,
                    BossEntity(Bosses.DRAGON).apply { x = ax; y = ay }, graphics, 0f)
                key.startsWith("animal_") -> {
                    val spec = key.removePrefix("animal_").substringBeforeLast('_')
                    val frame = key.substringAfterLast('_').toInt()
                    art.animal(canvas, AnimalSpecies.valueOf(spec), ax, ay, frame, 1f,
                        if (spec == "SHARK") 50f else 43f)
                }
                key == "magic_ring" -> drawRing(canvas)
                key == "chest" -> objects.drawChest(canvas, LootChest(0, ax, ay))
                key == "chest_open" -> objects.drawChest(canvas, LootChest(0, ax, ay).apply { opened = true })
                key == "chest_opening" -> art.chest(canvas, ax, ay, 2)
                key == "chest_glow" -> art.chest(canvas, ax, ay, 3)
                key == "campfire" -> objects.drawCampfire(canvas, Campfire(0, ax, ay), graphics)
                key.startsWith("pickup_") -> objects.drawPickup(canvas,
                    Pickup(ItemId.valueOf(key.removePrefix("pickup_")), 1, ax, ay))
            }
            val index = layout.entityIndexOf(key)
            blitBitmap(cell, pixels, width, layout.entityX(index), layout.entityPixelY(index))
            cell.recycle()
        }
    }

    /** Stroked circle, stretched into the magic zone ellipse at draw time. */
    private fun drawRing(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(AtlasLayout.ENT_CELL / 2f, AtlasLayout.ENT_CELL / 2f, 29f, paint)
    }

    private fun drawShadow(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
        canvas.drawOval(RectF(16f, 34f, 48f, 46f), paint)
    }

    // ---- Blitting ---------------------------------------------------------

    private fun blitBitmap(src: Bitmap, dst: IntArray, stride: Int, dstX: Int, dstY: Int) {
        val w = src.width
        val h = src.height
        val argb = IntArray(w * h)
        src.getPixels(argb, 0, w, 0, 0, w, h)
        blitArray(argb, w, h, dst, stride, dstX, dstY)
    }

    private fun blitArray(src: IntArray, srcW: Int, srcH: Int, dst: IntArray, stride: Int, dstX: Int, dstY: Int) {
        for (y in 0 until srcH) {
            System.arraycopy(src, y * srcW, dst, (dstY + y) * stride + dstX, srcW)
        }
    }
}
