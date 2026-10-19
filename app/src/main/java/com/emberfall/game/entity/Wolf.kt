package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * Hostile predator. Idles until the player gets close, then chases and bites
 * on a short cooldown. Slow enough to outrun, dangerous in pairs.
 */
class Wolf(x: Float, y: Float, val level: Int = 1) : Creature(x, y, 9f, 46f * (1f + .25f * (level - 1))) {

    private var attackCooldown = 0f
    private var wanderTimer = 0f
    private var dirX = 0f
    private var dirY = 1f

    var aggro = false
        private set

    fun disengage() { aggro = false }

    override fun update(dt: Float, game: Game) {
        super.update(dt, game)
        if (freezeTimer > 0f) { freezeTimer -= dt; applyKnockback(dt, game); return }
        attackCooldown = max(0f, attackCooldown - dt)
        wanderTimer -= dt

        val player = game.player
        val dx = player.x - x
        val dy = player.y - y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        aggro = distance < 9f * Config.TILE

        if (aggro && distance > 1f) {
            dirX = dx / distance
            dirY = dy / distance
            val speed = (if (distance > 28f) 92f else 70f) * (1f + .04f * (level - 1))
            moveWithCollision(game, dirX * speed * dt, dirY * speed * dt)
        } else {
            if (wanderTimer <= 0f) {
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                dirX = kotlin.math.cos(angle)
                dirY = kotlin.math.sin(angle)
                wanderTimer = 2f + Random.nextFloat() * 3f
            }
            moveWithCollision(game, dirX * 24f * dt, dirY * 24f * dt)
        }

        applyKnockback(dt, game)

        if (aggro && distance < radius + Config.PLAYER_RADIUS + 8f && attackCooldown <= 0f) {
            game.hurtPlayer(9f * (1f + .16f * (level - 1)), x, y)
            attackCooldown = 1.15f
        }
    }
}
