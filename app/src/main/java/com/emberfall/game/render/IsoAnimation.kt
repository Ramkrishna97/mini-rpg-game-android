package com.emberfall.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.emberfall.game.content.ContentAssetIds
import com.emberfall.game.content.ContentRepository
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** 32px frames: eight directions; boss sheets add a fourth death clip. */
class IsoAnimation(context: Context, assetName: String) {
    private val isBoss = assetName != "player"
    private val sheet: Bitmap = requireNotNull(ContentRepository.from(context).bitmap(
        ContentAssetIds.sprite(assetName),
        "sprites/$assetName.png",
    ) { bitmap -> bitmap.width == (if (isBoss) 512 else 384) && bitmap.height == 256 }) {
        "Missing sprite sheet '$assetName'"
    }
    private val outlineOffsets = arrayOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)
    private val source = Rect()
    private val destination = RectF()
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    fun draw(canvas: Canvas, x: Float, y: Float, facingX: Float, facingY: Float,
             state: AnimationState, elapsed: Float, size: Float) {
        val isoX = facingX - facingY
        val isoY = (facingX + facingY) * .5f
        val radians = atan2(isoX.toDouble(), -isoY.toDouble())
        val direction = ((radians / (PI / 4.0)).roundToInt() + 8) % 8
        val speed = when (state) {
            AnimationState.IDLE -> 2f
            AnimationState.WALK -> 8f
            AnimationState.ATTACK -> 12f
            AnimationState.DEATH -> 4f
        }
        val frame = if (state == AnimationState.DEATH)
            (elapsed * speed).toInt().coerceIn(0, 3)
        else (elapsed * speed).toInt().mod(4)
        val column = state.ordinal * 4 + frame
        source.set(column * 32, direction * 32, column * 32 + 32, direction * 32 + 32)
        destination.set(x - size * .5f, y - size * .9f, x + size * .5f, y + size * .1f)
        canvas.save()
        val breath = if (state == AnimationState.IDLE) .05f * kotlin.math.sin(elapsed * 4f + x * .01f) else 0f
        canvas.scale(1f - breath, 1f + breath, x, y)
        // Tint the alpha silhouette at four neighboring pixels, then cover its interior.
        paint.colorFilter = android.graphics.PorterDuffColorFilter(0xFFFFE6A0.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        for ((ox, oy) in outlineOffsets) {
            destination.offset(ox, oy)
            canvas.drawBitmap(sheet, source, destination, paint)
            destination.offset(-ox, -oy)
        }
        paint.colorFilter = null
        canvas.drawBitmap(sheet, source, destination, paint)
        canvas.restore()
    }
}
