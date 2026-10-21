package com.emberfall.game.core

enum class GameMode { NORMAL, HACK }

/** This value is compiled into the APK. Set it before exporting a build. */
object GameModeConfig {
    const val HACK_MODE_ENABLED = false
    val MODE: GameMode = if (HACK_MODE_ENABLED) GameMode.HACK else GameMode.NORMAL
}
