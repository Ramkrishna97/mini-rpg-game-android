package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

enum class AnimalSpecies(
    val label: String,
    val maxHp: Float,
    val speed: Float,
    val radius: Float,
) {
    RABBIT("Rabbit", 14f, 58f, 6f),
    DEER("Deer", 34f, 46f, 9f),
    BOAR("Boar", 55f, 40f, 10f),
    BIRD("Bird", 18f, 78f, 6f),
    FOX("Fox", 28f, 68f, 8f),
    DOG("Dog", 42f, 58f, 9f),
    SHARK("Shark", 50f, 52f, 10f),
    RAT("Rat", 12f, 84f, 5f),
    SHEEP("Sheep", 32f, 40f, 9f),
    CHICKEN("Chicken", 14f, 62f, 5f),
}

/**
 * Passive wildlife. Wanders until hit, then runs away for a few seconds.
 * Killing one drops meat/hide (handled by the game).
 */
class Animal(
    val species: AnimalSpecies,
    x: Float,
    y: Float,
) : Creature(x, y, species.radius, species.maxHp) {

    private var wanderTimer = 0f
    private var dirX = 0f
    private var dirY = 1f

    private var fleeTimer = 0f
    private var animationPhase = 0f
    val walkCycle: Float get() = animationPhase

    override fun update(dt: Float, game: Game) {
        super.update(dt, game)
        animationPhase += dt * if (fleeTimer > 0f) 9f else 4f
        if (freezeTimer > 0f) { freezeTimer -= dt; applyKnockback(dt, game); return }
        fleeTimer = max(0f, fleeTimer - dt)
        wanderTimer -= dt

        val player = game.player
        val toPlayerX = x - player.x
        val toPlayerY = y - player.y
        val playerDistance = hypot(toPlayerX.toDouble(), toPlayerY.toDouble()).toFloat()

        val speed: Float
        if (fleeTimer > 0f && playerDistance < 22f * Config.TILE && playerDistance > 0.01f) {
            dirX = toPlayerX / playerDistance
            dirY = toPlayerY / playerDistance
            speed = species.speed * 1.05f
        } else {
            if (wanderTimer <= 0f) {
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                dirX = kotlin.math.cos(angle)
                dirY = kotlin.math.sin(angle)
                wanderTimer = 1.2f + Random.nextFloat() * 2.8f
            }
            speed = species.speed * 0.35f
        }

        applyKnockback(dt, game)
        val moved = moveWithCollision(game, dirX * speed * dt, dirY * speed * dt)
        if (!moved && fleeTimer <= 0f) wanderTimer = 0f
    }

    override fun hurt(amount: Float, sourceX: Float, sourceY: Float) {
        super.hurt(amount, sourceX, sourceY)
        fleeTimer = 5f
    }
}
