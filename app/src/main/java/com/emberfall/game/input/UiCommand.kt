package com.emberfall.game.input

/** Integer commands passed from the touch thread to the simulation thread. */
object UiCommand {
    const val USE_SLOT_BASE = 100
    const val QUALITY_BASE = 200
    const val TOGGLE_SMOOTH = 300
    const val TOGGLE_ANTIALIAS = 301
    const val TOGGLE_PARTICLES = 302
    const val CLOSE_PANELS = 400
    const val CAST_BASE = 500
    const val CRAFT_BASE = 600
    const val TOGGLE_CRAFT = 700
}