package com.emberfall.game.audio

import android.content.Context
import android.media.MediaPlayer
import com.emberfall.game.R

/**
 * Looping ambient soundtrack. MediaPlayer is only created on first play and is
 * paused with the activity so it never keeps playing in the background.
 */
class MusicPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var finalBossMode = false

    fun setFinalBossMode(enabled: Boolean) {
        finalBossMode = enabled
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 23) player?.let { it.playbackParams = it.playbackParams.setSpeed(if (enabled) 1.32f else 1f) }
        }
    }

    @Synchronized
    fun start() {
        val existing = player
        if (existing != null) {
            runCatching { existing.start() }
            return
        }
        player = try {
            MediaPlayer.create(context, R.raw.emberfall_theme)?.apply {
                isLooping = true
                setVolume(0.32f, 0.32f)
                start()
            }
        } catch (t: Throwable) {
            null
        }
    }

    @Synchronized
    fun pause() {
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
        }
    }

    fun release() {
        runCatching {
            player?.release()
        }
        player = null
    }
}
