package com.emberfall.game.entity

/**
 * Arrow, bullet, or spell orb. Travels in a straight line, damages the first
 * creature it touches (or several when [pierces]), and optionally explodes on
 * impact for area damage.
 */
enum class ProjectileKind { ARROW, BULLET, MAGIC }

class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val color: Int,
    val radius: Float = 7f,
    val kind: ProjectileKind = ProjectileKind.MAGIC,
    val pierces: Boolean = false,
    val explosive: Boolean = false,
    val aoeRadius: Float = 0f,
    var life: Float = 1.4f,
    val hostile: Boolean = false,
) {
    val hitCreatures = mutableSetOf<Creature>()
    var dead = false

    /** Seconds of travel rendered as the visible shaft/tracer behind the head. */
    val trailSeconds: Float
        get() = when (kind) {
            ProjectileKind.ARROW -> .075f
            ProjectileKind.BULLET -> .032f
            ProjectileKind.MAGIC -> .018f
        }

    fun update(dt: Float) {
        x += vx * dt
        y += vy * dt
        life -= dt
        if (life <= 0f) dead = true
    }
}
