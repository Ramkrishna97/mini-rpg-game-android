package com.emberfall.game

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Transparent surface stacked above the Vulkan world surface. The game thread
 * draws the HUD onto it; touches are forwarded to GameView by MainActivity.
 */
class HudOverlayView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val landscapeW = maxOf(width, height)
        val landscapeH = minOf(width, height)
        if (landscapeW > 0 && landscapeH > 0 && (width != landscapeW || height != landscapeH)) {
            holder.setFixedSize(landscapeW, landscapeH)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }
}
