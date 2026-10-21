package com.emberfall.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.emberfall.game.content.ContentAssetIds
import com.emberfall.game.content.ContentRepository
import com.emberfall.game.entity.AnimalSpecies
import com.emberfall.game.item.ItemId

/** Unity exported sheets shared by the world, inventory, and atlas renderers. */
class ArtSheets private constructor(context: Context) {
    private val items = load(context, "sprites/items.png", 256, 96)
    private val wildlife = load(context, "sprites/wildlife.png", 192, 480)
    private val chests = load(context, "sprites/chests.png", 128, 32)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val target = RectF()

    fun item(canvas: Canvas, item: ItemId, x: Float, y: Float, size: Float) {
        val i = item.ordinal
        source.set((i % 8) * 32, (i / 8) * 32, (i % 8 + 1) * 32, (i / 8 + 1) * 32)
        target.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        canvas.drawBitmap(items, source, target, paint)
    }

    fun animal(canvas: Canvas, species: AnimalSpecies, x: Float, y: Float,
               frame: Int, facingX: Float, size: Float = 44f) {
        val f = frame and 3
        source.set(f * 48, species.ordinal * 48, (f + 1) * 48, (species.ordinal + 1) * 48)
        target.set(x - size / 2f, y - size * .79f, x + size / 2f, y + size * .21f)
        canvas.save()
        if (facingX < -.12f) canvas.scale(-1f, 1f, x, y)
        canvas.drawBitmap(wildlife, source, target, paint)
        canvas.restore()
    }

    fun chest(canvas: Canvas, x: Float, y: Float, frame: Int) {
        val f = frame.coerceIn(0, 3)
        source.set(f * 32, 0, (f + 1) * 32, 32)
        target.set(x - 18f, y - 25f, x + 18f, y + 11f)
        canvas.drawBitmap(chests, source, target, paint)
    }

    private fun load(context: Context, path: String, width: Int, height: Int): Bitmap {
        val name = path.substringAfterLast('/').removeSuffix(".png")
        val bitmap = ContentRepository.from(context).bitmap(
            ContentAssetIds.sprite(name), path,
        ) { it.width == width && it.height == height }
        require(bitmap != null && bitmap.width == width && bitmap.height == height) {
            "Missing or invalid art sheet: $path"
        }
        return bitmap
    }

    companion object {
        @Volatile private var shared: ArtSheets? = null
        fun from(context: Context): ArtSheets = shared ?: synchronized(this) {
            shared ?: ArtSheets(context.applicationContext).also { shared = it }
        }
    }
}
