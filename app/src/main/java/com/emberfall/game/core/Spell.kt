package com.emberfall.game.core

enum class Spell(
    val displayName: String,
    val shortName: String,
    val manaCost: Float,
    val damage: Float,
    val cooldownSeconds: Float,
    /** Seconds enemies stay frozen. Only Ice Freeze uses this. */
    val stunSeconds: Float,
    val color: Int,
    val description: String,
) {
    FIREBALL(
        displayName = "Fireball",
        shortName = "FIRE",
        manaCost = 35f,
        damage = 78f,
        cooldownSeconds = 2.5f,
        stunSeconds = 0f,
        color = 0xFFFF6B2F.toInt(),
        description = "Hurls an exploding orb at the nearest enemy",
    ),
    ICE_FREEZE(
        displayName = "Ice Freeze",
        shortName = "ICE",
        manaCost = 30f,
        damage = 52f,
        cooldownSeconds = 4.0f,
        stunSeconds = 2.5f,
        color = 0xFF6FC8FF.toInt(),
        description = "Freezes a cluster of enemies solid",
    ),
    WATER_TSUNAMI(
        displayName = "Water Tsunami",
        shortName = "WAVE",
        manaCost = 45f,
        damage = 68f,
        cooldownSeconds = 5.0f,
        stunSeconds = 0f,
        color = 0xFF3D8ED0.toInt(),
        description = "A crushing wave in the facing direction",
    ),
    WIND_TORNADO(
        displayName = "Wind Tornado",
        shortName = "WIND",
        manaCost = 42f,
        damage = 60f,
        cooldownSeconds = 3.5f,
        stunSeconds = 0f,
        color = 0xFF8FE08A.toInt(),
        description = "Spinning vortex around the caster",
    ),
}