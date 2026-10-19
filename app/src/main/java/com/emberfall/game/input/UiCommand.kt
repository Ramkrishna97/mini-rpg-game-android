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
    const val TOGGLE_MUSIC = 701
    const val TOGGLE_SOUND = 702
    const val TOGGLE_VIBRATION = 703
    const val RESOLUTION_BASE = 710
    const val FPS_BASE = 720
    const val SAVE_NOW = 730
    const val LOAD_SAVE = 731
    const val UPGRADE_WEAPON = 732
    const val COMBINE_WEAPON = 733
    const val EQUIP_WEAPON_BASE = 740
}