package com.emberfall.game.core

enum class Spell(
    val displayName: String,
    val shortName: String,
    val manaCost: Float,
    val damage: Float,
    val color: Int,
    val description: String,
) {
    FIREBALL(
        "Fireball",
        "FIRE",
        35f,
        78f,
        0xFFFF6B2F.toInt(),
        "Hurls an exploding orb at the nearest enemy",
    ),
    ICE_FREEZE(
        "Ice Freeze",
        "ICE",
        30f,
        52f,
        0xFF6FC8FF.toInt(),
        "Freezes a cluster of enemies solid",
    ),
    WATER_TSUNAMI(
        "Water Tsunami",
        "WAVE",
        45f,
        68f,
        0xFF3D8ED0.toInt(),
        "A crushing wave in the facing direction",
    ),
    WIND_TORNADO(
        "Wind Tornado",
        "WIND",
        42f,
        60f,
        0xFF8FE08A.toInt(),
        "Spinning vortex around the caster",
    ),
}
