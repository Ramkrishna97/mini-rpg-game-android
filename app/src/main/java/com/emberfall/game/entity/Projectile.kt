package com.emberfall.game.entity

/**
 * Arrow, bullet, or spell orb. Travels in a straight line, damages the first
 * creature it touches (or several when [pierces]), and optionally explodes on
 * impact for area damage.
 */
class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val color: Int,
    val radius: Float = 7f,
    val pierces: Boolean = false,
    val explosive: Boolean = false,
    val aoeRadius: Float = 0f,
    var life: Float = 1.4f,
    val hostile: Boolean = false,
) {
    val hitCreatures = mutableSetOf<Creature>()
    var dead = false

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
        if (life <= 0f) dead = true
    }
}
