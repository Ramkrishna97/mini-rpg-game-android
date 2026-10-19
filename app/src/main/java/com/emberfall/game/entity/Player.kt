package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.PlayerStats
import com.emberfall.game.util.length
import com.emberfall.game.world.Collision
import com.emberfall.game.world.TileQuery
import kotlin.math.exp
import kotlin.math.max

/**
 * The player avatar. Position is the body centre in world pixels; the movement
 * model uses acceleration + damping so touch input feels analog instead of
 * binary.
 */
class Player(var x: Float, var y: Float) {

    val stats = PlayerStats()

    var vx = 0f
        private set
    var vy = 0f
        private set

    var facingX = 0f
        private set
    var facingY = 1f
        private set

    var walkPhase = 0f
        private set

    var swingTimer = 0f
        private set

    var invulnTimer = 0f
        private set

    var hurtFlash = 0f
        private set

    val speed: Float get() = length(vx, vy)
    val isSwinging: Boolean get() = swingTimer > 0f
    val isAlive: Boolean get() = stats.health > 0f

    private val moveResult = Collision.MoveResult()

    fun swing() {
        if (swingTimer <= 0f) swingTimer = SWING_DURATION
    }

    /** Returns true when the hit landed (not blocked by invulnerability). */
    fun takeDamage(amount: Float): Boolean {
        if (invulnTimer > 0f || !isAlive) return false
        stats.damage(amount)
        invulnTimer = 0.75f
        hurtFlash = 0.45f
        return true
    }

    fun faceTowards(dx: Float, dy: Float) {
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length > 0.001f) {
            facingX = dx / length
            facingY = dy / length
        }
    }

    fun grantSpawnProtection() {
        invulnTimer = 2.2f
    }

    fun update(dt: Float, inputX: Float, inputY: Float, run: Boolean, world: TileQuery) {
        if (invulnTimer > 0f) invulnTimer = max(0f, invulnTimer - dt)
        if (hurtFlash > 0f) hurtFlash = max(0f, hurtFlash - dt)
        if (!isAlive) {
            vx = 0f
            vy = 0f
            return
        }

        val maxSpeed = if (run) Config.RUN_SPEED else Config.WALK_SPEED
        val blend = 1f - exp(-11f * dt)
        vx += (inputX * maxSpeed - vx) * blend
        vy += (inputY * maxSpeed - vy) * blend

        val concreteWorld = world as? com.emberfall.game.world.World
        val tile = concreteWorld?.tileAt(concreteWorld.tileX(x), concreteWorld.tileY(y))
        val swimming = tile == com.emberfall.game.world.TileType.WATER || tile == com.emberfall.game.world.TileType.DEEP_WATER
        val moveScale = if (swimming) 0.72f else 1f
        // Water is solid to land creatures, but the player may swim through it.
        val playerCollision = object : com.emberfall.game.world.TileQuery {
            override fun isSolid(tx: Int, ty: Int): Boolean {
                val type = concreteWorld?.tileAt(tx, ty)
                return type != com.emberfall.game.world.TileType.WATER && type != com.emberfall.game.world.TileType.DEEP_WATER && world.isSolid(tx, ty)
            }
        }
        Collision.moveAndSlide(playerCollision, x, y, vx * dt * moveScale, vy * dt * moveScale, Config.PLAYER_RADIUS, moveResult)
        x = moveResult.x
        y = moveResult.y

        val currentSpeed = length(vx, vy)
        if (currentSpeed > 4f) {
            facingX = vx / currentSpeed
            facingY = vy / currentSpeed
            walkPhase += dt * (5f + currentSpeed * 0.04f)
        } else {
            walkPhase = 0f
        }

        if (swingTimer > 0f) swingTimer = max(0f, swingTimer - dt)
    }

    companion object {
        const val SWING_DURATION = 0.32f
    }
}
