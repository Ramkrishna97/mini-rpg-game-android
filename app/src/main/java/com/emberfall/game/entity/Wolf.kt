package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.LootRoll
import com.emberfall.game.util.hypot
import kotlin.math.*
import kotlin.random.Random

enum class EnemyState { IDLE_WANDER, ALERT, CHASE, ATTACK_WINDUP, LUNGE, RECOVERY }

class Wolf(x: Float, y: Float, val level: Int = 1) :
    Creature(x, y, 9f, maxHpFor(level)) {

    companion object {
        private const val BASE_HP = 46f
        private const val BASE_LUNGE_DAMAGE = 9f
        private const val BASE_CHASE_SPEED = 92f

        private class Tier(val xp: Int, val drops: List<LootRoll>)

        /** Index 0 = level 1 ... index 4 = level 5. Levels above 5 reuse the last tier. */
        private val TIERS = listOf(
            Tier(25, listOf(LootRoll(ItemId.RAW_MEAT, 1), LootRoll(ItemId.HIDE, 2), LootRoll(ItemId.MAGIC_STONE, 1, 0.35f))),
            Tier(35, listOf(LootRoll(ItemId.RAW_MEAT, 1), LootRoll(ItemId.HIDE, 2), LootRoll(ItemId.MANA_POTION, 1, 0.18f))),
            Tier(50, listOf(LootRoll(ItemId.RAW_MEAT, 2), LootRoll(ItemId.HIDE, 2), LootRoll(ItemId.POWER_POTION, 1, 0.12f))),
            Tier(70, listOf(LootRoll(ItemId.RAW_MEAT, 2), LootRoll(ItemId.HIDE, 3), LootRoll(ItemId.REGEN_POTION, 1, 0.08f))),
            Tier(95, listOf(LootRoll(ItemId.RAW_MEAT, 2), LootRoll(ItemId.HIDE, 3), LootRoll(ItemId.ELIXIR, 1, 0.15f))),
        )

        private fun tierFor(level: Int) = TIERS[(level - 1).coerceIn(0, TIERS.lastIndex)]

        fun maxHpFor(level: Int): Float = BASE_HP * (1f + .25f * (level - 1))
        fun lungeDamageFor(level: Int): Float = BASE_LUNGE_DAMAGE * (1f + .16f * (level - 1))
        fun chaseSpeedFor(level: Int): Float = BASE_CHASE_SPEED * (1f + .04f * (level - 1))
    }

    val xpReward: Int get() = tierFor(level).xp

    /** Rolls this wolf's drops. Call once, at the moment it dies. */
    fun rollDrops(): List<Pair<ItemId, Int>> = LootRoll.resolve(tierFor(level).drops)

    var state = EnemyState.IDLE_WANDER
        private set
    val moving get() = vx * vx + vy * vy > 4f
    val aggro get() = state != EnemyState.IDLE_WANDER
    private var timer = 0f
    private var dirX = 0f
    private var dirY = 1f
    private var vx = 0f
    private var vy = 0f
    private var struck = false

    fun disengage() { enter(EnemyState.IDLE_WANDER, 1f) }

    private fun enter(next: EnemyState, duration: Float) {
        state = next; timer = duration
        if (next == EnemyState.ALERT || next == EnemyState.RECOVERY || next == EnemyState.IDLE_WANDER) { vx = 0f; vy = 0f }
    }

    override fun update(dt: Float, game: Game) {
        super.update(dt, game)
        if (freezeTimer > 0f) { freezeTimer -= dt; applyKnockback(dt, game); return }
        timer -= dt
        val dx = game.player.x - x; val dy = game.player.y - y
        val distance = hypot(dx, dy)
        if (distance > 12f * Config.TILE && aggro) disengage()
        var speed = 0f
        when (state) {
            EnemyState.IDLE_WANDER -> {
                if (distance < 9f * Config.TILE) enter(EnemyState.ALERT, .3f)
                else {
                    if (timer <= 0f) {
                        val angle = Random.nextFloat() * 2f * PI.toFloat()
                        val moving = Random.nextBoolean()
                        dirX = if (moving) cos(angle) else 0f; dirY = if (moving) sin(angle) else 0f
                        timer = .6f + Random.nextFloat() * 2f
                    }
                    speed = 24f
                }
            }
            EnemyState.ALERT -> if (timer <= 0f) enter(EnemyState.CHASE, 0f)
            EnemyState.CHASE -> {
                dirX = dx / distance.coerceAtLeast(.01f); dirY = dy / distance.coerceAtLeast(.01f)
                speed = chaseSpeedFor(level)
                if (distance < 52f) { enter(EnemyState.ATTACK_WINDUP, .35f); speed = 0f }
            }
            EnemyState.ATTACK_WINDUP -> {
                speed = -28f
                if (timer <= 0f) { enter(EnemyState.LUNGE, .22f); struck = false }
            }
            EnemyState.LUNGE -> {
                speed = 230f
                if (!struck && distance < radius + Config.PLAYER_RADIUS + 8f) {
                    game.hurtPlayer(lungeDamageFor(level), x, y); struck = true
                }
                if (timer <= 0f) { enter(EnemyState.RECOVERY, .65f); speed = 0f }
            }
            EnemyState.RECOVERY -> if (timer <= 0f) enter(EnemyState.CHASE, 0f)
        }
        val acceleration = 1f - exp(-12f * dt)
        vx += (dirX * speed - vx) * acceleration; vy += (dirY * speed - vy) * acceleration
        moveWithCollision(game, vx * dt, vy * dt)
        applyKnockback(dt, game)
    }
}