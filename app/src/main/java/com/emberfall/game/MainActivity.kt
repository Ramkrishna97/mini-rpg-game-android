package com.emberfall.game

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.emberfall.game.audio.MusicPlayer

/**
 * Single-activity host. Locks into landscape and hides every system bar so the
 * canvas owns the whole display.
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView
    private val music by lazy { MusicPlayer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        gameView = GameView(this)
        setContentView(gameView)
        gameView.setAudioSync { enabled, _ -> runOnUiThread {
            if (enabled) music.start() else music.pause()
            music.setFinalBossMode(gameView.game.activeBoss?.def?.id == "hollowking")
        } }

        // Must run after setContentView: the window's insets controller does not
        // exist until the decor view has been created.
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        gameView.onResumeGame()
        if (gameView.game.graphics.music) music.start() else music.pause()
    }

    override fun onPause() {
        music.pause()
        gameView.onPauseGame()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
            if (::gameView.isInitialized) {
                if (gameView.game.graphics.music) music.start() else music.pause()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
