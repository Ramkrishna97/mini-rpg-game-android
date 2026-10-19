package com.emberfall.game

import android.content.Context
import android.graphics.Canvas
import android.os.Process
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.input.InputState
import com.emberfall.game.input.TouchController

/**
 * SurfaceView host. The UI thread owns touch handling and lifecycle; a
 * dedicated render thread runs the fixed-step loop and draws.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val input = InputState()
    val game = Game(context, input)

    private val touch = TouchController(input, resources.displayMetrics.density)
    private var loop: GameThread? = null

    @Volatile
    var viewWidth = 0
        private set

    @Volatile
    var viewHeight = 0
        private set

    init {
        holder.addCallback(this)
        isFocusable = true
        isClickable = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        touch.handle(event, width, height)
        return true
    }

    fun onResumeGame() {
        if (holder.surface.isValid) startLoop()
    }

    fun onPauseGame() {
        stopLoop()
        game.saveNow()
    }

    private fun startLoop() {
        if (loop != null) return
        loop = GameThread(this, holder).also { it.start() }
    }

    private fun stopLoop() {
        val thread = loop ?: return
        loop = null
        thread.shutdown()
    }

    /**
     * Fixed-timestep game loop: accumulate real time, simulate in constant
     * 1/60 s steps, then render once. Sleeping keeps the loop at ~60 Hz and
     * saves battery compared to an unthrottled spin.
     */
    private class GameThread(
        private val view: GameView,
        private val holder: SurfaceHolder,
    ) : Thread("EmberfallLoop") {

        @Volatile
        private var running = true

        private var useHardwareCanvas = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)

            var last = System.nanoTime()
            var accumulator = 0L
            val stepNanos = (Config.STEP_SECONDS * 1_000_000_000f).toLong()

            while (running) {
                val now = System.nanoTime()
                var delta = now - last
                last = now
                if (delta > 250_000_000L) delta = 250_000_000L
                if (delta < 0L) delta = 0L

                val width = view.viewWidth
                val height = view.viewHeight

                if (width > 0 && height > 0) {
                    view.game.setViewport(width, height)
                    view.game.reportFrame(delta / 1_000_000_000f)
                }

                accumulator += delta
                if (accumulator > stepNanos * 5) accumulator = stepNanos * 5
                while (accumulator >= stepNanos) {
                    view.game.update(Config.STEP_SECONDS)
                    accumulator -= stepNanos
                }

                if (width > 0 && height > 0) {
                    val canvas = lockCanvas()
                    if (canvas != null) {
                        try {
                            view.game.render(canvas)
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }
                }

                val frameNanos = System.nanoTime() - now
                val sleepNanos = stepNanos - frameNanos
                if (sleepNanos > 1_000_000L) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000L)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        }

        private fun lockCanvas(): Canvas? {
            if (useHardwareCanvas) {
                try {
                    return holder.lockHardwareCanvas()
                } catch (t: Throwable) {
                    useHardwareCanvas = false
                }
            }
            return try {
                holder.lockCanvas()
            } catch (t: Throwable) {
                null
            }
        }

        fun shutdown() {
            running = false
            try {
                join(800)
            } catch (e: InterruptedException) {
                // Nothing useful to do; the thread is already exiting.
            }
        }
    }
}
