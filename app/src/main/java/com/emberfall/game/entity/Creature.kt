package com.emberfall.game.entity

import com.emberfall.game.core.Game
import com.emberfall.game.world.Collision
import kotlin.math.exp
import kotlin.math.max

/**
 * Shared base for every living thing in the world. Subclasses own their AI and
 * call [moveWithCollision] so nothing walks through trees or water.
 */
abstract class Creature(
    var x: Float,
    var y: Float,
    val radius: Float,
    val maxHp: Float,
) {
    var health: Float = maxHp
    var freezeTimer = 0f
    var hitFlash = 0f
        protected set

    var facingX = 0f
        private set
    var facingY = 1f
        private set

    var knockbackX = 0f
        protected set
    var knockbackY = 0f
        protected set

    protected val moveResult = Collision.MoveResult()

    val alive: Boolean get() = health > 0f

    open fun update(dt: Float, game: Game) {
        hitFlash = max(0f, hitFlash - dt)
    }

    open fun hurt(amount: Float, sourceX: Float, sourceY: Float) {
        if (!alive) return
        health -= amount
        hitFlash = 0.22f
        val dx = x - sourceX
        val dy = y - sourceY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        knockbackX = dx / dist * 90f
        knockbackY = dy / dist * 90f
    }

    protected fun moveWithCollision(game: Game, dx: Float, dy: Float): Boolean {
        if (dx == 0f && dy == 0f) return false
        val dirLength = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dirLength > 0.001f) {
            facingX = dx / dirLength
            facingY = dy / dirLength
        }
        Collision.moveAndSlide(game.world, x, y, dx, dy, radius, moveResult)
        val moved = moveResult.x != x || moveResult.y != y
        x = moveResult.x
        y = moveResult.y
        return moved
    }

    fun calm() { knockbackX = 0f; knockbackY = 0f; freezeTimer = 0f }

    fun applyImpulse(dx: Float, dy: Float, power: Float) {
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        knockbackX = dx / length * power
        knockbackY = dy / length * power
    }

    protected fun applyKnockback(dt: Float, game: Game) {
        if (knockbackX == 0f && knockbackY == 0f) return
        moveWithCollision(game, knockbackX * dt, knockbackY * dt)
        val decay = exp(-9f * dt)
        knockbackX *= decay
        knockbackY *= decay
        if (kotlin.math.abs(knockbackX) < 1f) knockbackX = 0f
        if (kotlin.math.abs(knockbackY) < 1f) knockbackY = 0f
    }
}
