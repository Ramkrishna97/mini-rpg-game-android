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
    private var audioSync: ((Boolean, Boolean) -> Unit)? = null

    fun setAudioSync(callback: (Boolean, Boolean) -> Unit) { audioSync = callback }


    val refreshRate: Float = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            context.display?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.refreshRate
        }
    }.getOrDefault(60f)

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
        loop = GameThread(this, holder, refreshRate).also { it.start() }
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
        private val refreshRate: Float,
    ) : Thread("EmberfallLoop") {

        @Volatile
        private var running = true

        private var useHardwareCanvas = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)

            var targetFps = minOf(refreshRate.coerceIn(30f, 120f), view.game.graphics.targetFps.toFloat())
            var stepNanos = (1_000_000_000f / targetFps).toLong()
            var stepSeconds = stepNanos / 1_000_000_000f
            var observedCap = targetFps
            var settingsCheck = 0
            var syncedMusic = !view.input.musicEnabled
            var syncedSound = !view.input.soundEnabled
            var last = System.nanoTime()
            var accumulator = 0L

            val bossTone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 75) }.getOrNull()
            while (running) {
                if (view.input.bossSoundRequested.getAndSet(false) && view.input.soundEnabled) {
                    bossTone?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                }
                if (view.input.musicEnabled != syncedMusic || view.input.soundEnabled != syncedSound) {
                    syncedMusic = view.input.musicEnabled
                    syncedSound = view.input.soundEnabled
                    view.audioSync?.invoke(syncedMusic, syncedSound)
                }
                if (++settingsCheck >= 30) {
                    settingsCheck = 0
                    val requested = minOf(refreshRate.coerceIn(30f, 120f), view.game.graphics.targetFps.toFloat())
                    if (requested != observedCap) {
                        targetFps = requested
                        observedCap = requested
                        stepNanos = (1_000_000_000f / targetFps).toLong()
                        stepSeconds = stepNanos / 1_000_000_000f
                    }
                    view.audioSync?.invoke(view.input.musicEnabled, view.input.soundEnabled)
                }
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
                    view.game.update(stepSeconds)
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
            bossTone?.release()
        }

        private fun lockCanvas(): Canvas? {
            if (useHardwareCanvas && android.os.Build.VERSION.SDK_INT >= 26) {
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
