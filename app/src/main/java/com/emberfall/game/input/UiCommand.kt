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
    const val NEW_GAME = 734
    const val SAVE_SLOT_1 = 761
    const val SAVE_SLOT_2 = 762
    const val SAVE_SLOT_3 = 763
    const val LOAD_SLOT_1 = 771
    const val LOAD_SLOT_2 = 772
    const val LOAD_SLOT_3 = 773
    const val UPGRADE_WEAPON = 732
    const val COMBINE_WEAPON = 733
    const val HACK_TOGGLE_BOOSTS = 740
    const val HACK_SUMMON_DRAGON = 741
    const val EQUIP_WEAPON_BASE = 800
    const val UPGRADE_MAGIC = 750
    const val EXCHANGE_STONE = 751
    const val EXCHANGE_IRON = 752
    const val EXCHANGE_MAGIC = 753
    const val SELL_WEAPON = 754
    const val BUY_ITEM_BASE = 900
    const val SELL_ITEM_BASE = 950
    const val TELEPORT_BASE = 1000
}
