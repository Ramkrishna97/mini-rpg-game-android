package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.LootRoll
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

enum class AnimalSpecies(
    val label: String,
    val maxHp: Float,
    val speed: Float,
    val radius: Float,
    val xpYield: Int,
    val drops: List<LootRoll>,
) {
    RABBIT("Rabbit", 14f, 58f, 6f, 8, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.HIDE, 1, 0.30f),
    )),
    DEER("Deer", 34f, 46f, 9f, 20, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.HIDE, 1, 0.60f),
    )),
    BOAR("Boar", 55f, 40f, 10f, 30, listOf(
        LootRoll(ItemId.RAW_MEAT, 2),
        LootRoll(ItemId.HIDE, 1, 1.00f),
    )),
    BIRD("Bird", 18f, 78f, 6f, 8, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.FIBER, 1, 0.40f),
    )),
    FOX("Fox", 28f, 68f, 8f, 15, listOf(
        LootRoll(ItemId.HIDE, 1),
        LootRoll(ItemId.RAW_MEAT, 1, 0.50f),
    )),
    DOG("Dog", 42f, 58f, 9f, 22, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.HIDE, 1, 0.75f),
    )),
    SHARK("Shark", 50f, 52f, 10f, 35, listOf(
        LootRoll(ItemId.RAW_MEAT, 2),
        LootRoll(ItemId.HIDE, 1, 0.80f),
    )),
    RAT("Rat", 12f, 84f, 5f, 5, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
    )),
    SHEEP("Sheep", 32f, 40f, 9f, 18, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.FIBER, 1, 0.60f),
    )),
    CHICKEN("Chicken", 14f, 62f, 5f, 6, listOf(
        LootRoll(ItemId.RAW_MEAT, 1),
        LootRoll(ItemId.FIBER, 1, 0.20f),
    )),
}

/**
 * Passive wildlife. Wanders until hit, then runs away for a few seconds.
 * Call [rollDrops] once when it dies and grant [xpReward] to the player.
 */
class Animal(
    val species: AnimalSpecies,
    x: Float,
    y: Float,
) : Creature(x, y, species.radius, species.maxHp) {

    val xpReward: Int get() = species.xpYield

    /** Rolls this animal's drops. Call once, at the moment it dies. */
    fun rollDrops(): List<Pair<ItemId, Int>> = LootRoll.resolve(species.drops)

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
        // Sharks intentionally inhabit water. Land creatures use the normal
        // collision path; sharks may move only if their next tile is water.
        val moved = if (species == AnimalSpecies.SHARK) {
            val nextX = x + dirX * speed * dt
            val nextY = y + dirY * speed * dt
            val tile = game.world.tileAt(game.world.tileX(nextX), game.world.tileY(nextY))
            val inWater = tile == com.emberfall.game.world.TileType.WATER || tile == com.emberfall.game.world.TileType.DEEP_WATER
            if (inWater) { x = nextX; y = nextY; faceDirection(dirX, dirY); true } else false
        } else moveWithCollision(game, dirX * speed * dt, dirY * speed * dt)
        if (!moved && fleeTimer <= 0f) wanderTimer = 0f
    }

    override fun hurt(amount: Float, sourceX: Float, sourceY: Float) {
        super.hurt(amount, sourceX, sourceY)
        fleeTimer = 5f
    }
}