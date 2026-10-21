package com.emberfall.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.os.Process
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.emberfall.game.core.Game
import com.emberfall.game.core.RendererConfig
import com.emberfall.game.input.InputState
import com.emberfall.game.input.TouchController
import com.emberfall.game.render.AtlasBuilder
import com.emberfall.game.render.AtlasLayout
import com.emberfall.game.render.EntityBatchBuilder
import com.emberfall.game.render.IsoMath
import com.emberfall.game.render.NativeRenderer
import com.emberfall.game.render.NightLighting
import com.emberfall.game.render.ObjectRenderer
import com.emberfall.game.render.SpriteBatch
import com.emberfall.game.render.WorldBatchBuilder
import com.emberfall.game.ui.HudRenderer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SurfaceView host. The UI thread owns touch handling and lifecycle; a
 * dedicated render thread runs the fixed-step loop and draws, using either
 * the Vulkan renderer or the Canvas renderer depending on RendererConfig.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val input = InputState()
    val game = Game(context, input)

    private val touch = TouchController(input, resources.displayMetrics.density, game.inventory.capacity, game)
    private var loop: GameThread? = null
    private var audioSync: ((Boolean, Boolean) -> Unit)? = null
    private val contentReloadRequested = AtomicBoolean(false)

    fun setAudioSync(callback: (Boolean, Boolean) -> Unit) { audioSync = callback }

    /** Applies an already verified content pack on the render thread. */
    fun reloadContent() { contentReloadRequested.set(true) }

    /** Set by MainActivity. Receives the HUD and floating text when the Vulkan renderer is active. */
    var hudOverlay: HudOverlayView? = null

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
        // Enforce landscape dimensions (width >= height)
        val finalWidth = maxOf(width, height)
        val finalHeight = minOf(width, height)
        if (width != finalWidth || height != finalHeight) {
            holder.setFixedSize(finalWidth, finalHeight)
        }
        viewWidth = finalWidth
        viewHeight = finalHeight
        hudOverlay?.holder?.setFixedSize(finalWidth, finalHeight)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val w = if (viewWidth > 0) viewWidth else maxOf(width, height)
        val h = if (viewHeight > 0) viewHeight else minOf(width, height)
        touch.handle(event, w, h)
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

            // Vulkan is created on this thread and used only from it. If it fails,
            // native stays null and the Canvas path draws as before.
            val native: NativeRenderer? = if (RendererConfig.useVulkanRenderer) {
                runCatching { NativeRenderer(holder.surface, view.context.assets) }
                    .getOrNull()
                    ?.takeIf { it.isReady }
            } else {
                null
            }

            val atlasLayout = AtlasLayout()
            val worldBuilder = WorldBatchBuilder(atlasLayout)
            val entityBuilder = EntityBatchBuilder(atlasLayout)
            var vulkanReady = native != null &&
                    native.uploadAtlas(AtlasBuilder.build(view.context, atlasLayout), AtlasLayout.ATLAS_W, atlasLayout.height)

            val batch = SpriteBatch()
            val hudRenderer = HudRenderer(view.context)
            val nightLighting = NightLighting()
            val objectRenderer = ObjectRenderer(view.context)
            val startNanos = System.nanoTime()
            var nativeWidth = -1
            var nativeHeight = -1
            var shakeX = 0f
            var shakeY = 0f

            var targetFps = minOf(refreshRate.coerceIn(30f, 120f), view.game.graphics.targetFps.toFloat())
            var stepNanos = (1_000_000_000f / targetFps).toLong()
            var stepSeconds = stepNanos / 1_000_000_000f
            var observedCap = targetFps
            var settingsCheck = 0
            var syncedMusic = !view.input.musicEnabled
            var syncedSound = !view.input.soundEnabled
            var last = System.nanoTime()
            var accumulator = 0L

            val bossTone = runCatching {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 75)
            }.getOrNull()

            while (running) {
                if (view.contentReloadRequested.getAndSet(false)) {
                    view.game.reloadVisuals()
                    vulkanReady = native != null && native.uploadAtlas(
                        AtlasBuilder.build(view.context, atlasLayout),
                        AtlasLayout.ATLAS_W,
                        atlasLayout.height,
                    )
                }
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

                val rawW = view.viewWidth
                val rawH = view.viewHeight
                val width = maxOf(rawW, rawH)
                val height = minOf(rawW, rawH)

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
                    val vw = width.toFloat()
                    val vh = height.toFloat()

                    if (vulkanReady && native != null) {
                        if (width != nativeWidth || height != nativeHeight) {
                            native.requestRecreate()
                            nativeWidth = width
                            nativeHeight = height
                        }
                        val g = view.game
                        val elapsed = (System.nanoTime() - startNanos) / 1_000_000_000f
                        val shake = g.juice.shakeOffset()
                        shakeX = shake.x
                        shakeY = shake.y
                        val camX = g.camera.x
                        val camY = g.camera.y
                        val zoom = g.camera.zoom

                        worldBuilder.build(batch, g.world, camX, camY, zoom, vw, vh, elapsed, shakeX, shakeY)
                        entityBuilder.build(batch, g, camX, camY, zoom, vw, vh, elapsed, shakeX, shakeY)
                        nightLighting.update(g, camX, camY, zoom, vw, vh)
                        native.drawFrame(batch.data, batch.count, CLEAR_R, CLEAR_G, CLEAR_B,
                            nightLighting.lights, nightLighting.count, nightLighting.ambient)
                        drawOverlay(hudRenderer, objectRenderer, true, shakeX, shakeY, vw, vh)
                    } else {
                        val canvas = lockCanvas()
                        if (canvas != null) {
                            try {
                                view.game.render(canvas)
                            } finally {
                                holder.unlockCanvasAndPost(canvas)
                            }
                        }
                        drawOverlay(hudRenderer, objectRenderer, false, 0f, 0f, vw, vh)
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

            native?.close()
            bossTone?.release()
        }

        /**
         * Draws floating text (in world space, with the camera transform) and the HUD
         * onto the transparent overlay. Clears it first. Runs on the game thread so
         * reads never race with simulation updates.
         */
        private fun drawOverlay(
            hud: HudRenderer,
            objects: ObjectRenderer,
            drawWorldLayers: Boolean,
            shakeX: Float,
            shakeY: Float,
            width: Float,
            height: Float,
        ) {
            val overlay = view.hudOverlay ?: return
            val surfaceHolder = overlay.holder
            if (!surfaceHolder.surface.isValid) return
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                if (drawWorldLayers) {
                    drawFloatingText(canvas, objects, shakeX, shakeY, width, height)
                    hud.draw(canvas, view.game, view.input, width, height)
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        /** Same transform chain as Game.render(): centre, zoom, camera, then each text's iso point. */
        private fun drawFloatingText(
            canvas: Canvas,
            objects: ObjectRenderer,
            shakeX: Float,
            shakeY: Float,
            width: Float,
            height: Float,
        ) {
            val g = view.game
            val camera = g.camera
            val cameraIso = IsoMath.worldToIso(camera.x, camera.y)
            canvas.save()
            canvas.translate(width * 0.5f + shakeX, height * 0.5f + shakeY)
            canvas.scale(camera.zoom, camera.zoom)
            canvas.translate(-cameraIso.x, -cameraIso.y)
            for (text in g.texts) {
                val iso = IsoMath.worldToIso(text.x, text.y)
                canvas.save()
                canvas.translate(iso.x - text.x, iso.y - text.y)
                objects.drawFloatingText(canvas, text)
                canvas.restore()
            }
            canvas.restore()
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

        private companion object {
            // Same background colour the Canvas path clears to (0xFF0B100E).
            const val CLEAR_R = 11f / 255f
            const val CLEAR_G = 16f / 255f
            const val CLEAR_B = 14f / 255f
        }
    }
}
