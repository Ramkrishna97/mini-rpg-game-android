package com.emberfall.game.entity

import com.emberfall.game.item.ItemId
import kotlin.math.max
import kotlin.math.sin

/** An item lying on the ground, bobbing until the player walks over it. */
class Pickup(
    val item: ItemId,
    var count: Int,
    var x: Float,
    var y: Float,
) {
    var life = 70f
    var bob = 0f

    fun update(dt: Float) {
        life -= dt
        bob += dt * 3.2f
    }

    val bobOffset: Float get() = sin(bob) * 2.5f
}

/** A campfire checkpoint. Interacting with it sets the respawn point and saves. */
class Campfire(
    val index: Int,
    var x: Float,
    var y: Float,
) {
    var flamePhase = 0f

    fun isSafe(px: Float, py: Float): Boolean = kotlin.math.hypot((px - x).toDouble(), (py - y).toDouble()) < 145.0

    fun update(dt: Float) {
        flamePhase += dt * 6f
    }
}

/** A one-shot loot chest. */
class LootChest(
    val id: Int,
    var x: Float,
    var y: Float,
) {
    var opened = false
    var openTimer = 0f

    fun update(dt: Float) {
        if (opened && openTimer < 1f) openTimer += dt
    }
}

/** Floating combat text / loot toast. */
class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    var life: Float = 1.1f,
    val rise: Float = 30f,
) {
    val maxLife = life
    var offsetX = 0f
    var offsetY = 0f
    private val driftX = (Math.random().toFloat() - .5f) * 25f
    private var velocityY = -rise

    fun update(dt: Float): Boolean {
        life -= dt
        offsetX += driftX * dt
        offsetY += velocityY * dt
        velocityY += if (maxLife <= .8f) 120f * dt else 25f * dt
        return life > 0f
    }
}

/** Small hit/death particle. */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val size: Float,
) {
    fun update(dt: Float): Boolean {
        life -= dt
        x += vx * dt
        y += vy * dt
        vx *= 0.94f
        vy = vy * 0.94f + 180f * dt
        return life > 0f
    }
}

/** Utility to keep particle counts bounded on low-end devices. */
fun MutableList<Particle>.cap(maxCount: Int) {
    if (size > maxCount) {
        val overflow = size - maxCount
        repeat(overflow) { removeAt(0) }
    }
}
