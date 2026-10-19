package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.world.BossDef
import kotlin.math.hypot
import kotlin.math.max

enum class BossPhase { CHASE, TELEGRAPH, DASH, RECOVER }

/**
 * A world boss bound to its arena. It wakes when the player enters, and the
 * game clamps the player inside until the fight ends.
 */
class Boss(val def: BossDef) : Creature(
    def.tileX * Config.TILE,
    def.tileY * Config.TILE,
    20f,
    def.maxHealth,
) {

    val arenaX: Float = def.tileX * Config.TILE
    val arenaY: Float = def.tileY * Config.TILE
    val arenaRadius: Float = def.arenaRadiusTiles * Config.TILE

    var engaged = false
        private set
    var defeated = false
        private set

    var phase = BossPhase.CHASE
        private set
    private var phaseTimer = 2.4f
    private var contactCooldown = 0f
    private var chargeX = 0f
    private var chargeY = 1f

    val chargeDirX: Float get() = chargeX
    val chargeDirY: Float get() = chargeY

    val displayName: String get() = def.name

    fun engage() {
        if (defeated) return
        engaged = true
        phase = BossPhase.CHASE
        phaseTimer = 2.0f
    }

    fun markDefeated() {
        defeated = true
        engaged = false
        health = 0f
    }

    fun resetFight() {
        if (defeated) return
        health = def.maxHealth
        engaged = false
        phase = BossPhase.CHASE
        phaseTimer = 2.4f
        x = arenaX
        y = arenaY
        knockbackX = 0f
        knockbackY = 0f
    }

    override fun update(dt: Float, game: Game) {
        if (defeated) return
        super.update(dt, game)
        if (freezeTimer > 0f) { freezeTimer -= dt; return }

        val player = game.player
        val dx = player.x - x
        val dy = player.y - y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (!engaged) {
            if (distance < arenaRadius * 0.85f) {
                engage()
                game.onBossEngaged(this)
            }
            return
        }

        phaseTimer -= dt
        contactCooldown = max(0f, contactCooldown - dt)

        when (phase) {
            BossPhase.CHASE -> {
                if (distance > 1f) {
                    moveWithCollision(
                        game,
                        dx / distance * def.speed * dt,
                        dy / distance * def.speed * dt,
                    )
                }
                if (phaseTimer <= 0f) {
                    chargeX = if (distance > 0.01f) dx / distance else 0f
                    chargeY = if (distance > 0.01f) dy / distance else 1f
                    phase = BossPhase.TELEGRAPH
                    phaseTimer = 0.75f
                }
            }

            BossPhase.TELEGRAPH -> {
                // Hold still, wind up, then lunge along the locked direction.
                if (phaseTimer <= 0f) {
                    phase = BossPhase.DASH
                    phaseTimer = 0.38f
                }
            }

            BossPhase.DASH -> {
                moveWithCollision(
                    game,
                    chargeX * def.speed * 3.4f * dt,
                    chargeY * def.speed * 3.4f * dt,
                )
                if (phaseTimer <= 0f) {
                    phase = BossPhase.RECOVER
                    phaseTimer = 0.8f
                }
            }

            BossPhase.RECOVER -> {
                if (phaseTimer <= 0f) {
                    phase = BossPhase.CHASE
                    phaseTimer = 2.1f
                }
            }
        }

        if (distance < radius + Config.PLAYER_RADIUS + 6f && contactCooldown <= 0f) {
            game.hurtPlayer(def.damage, x, y)
            contactCooldown = 1.35f
        }
    }
}
