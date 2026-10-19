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
    def.maxHealth * 3f,
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
    var attackType = 0
        private set
    private var attackCycle = 0
    private var shotTimer = 0f
    private var magicX = 0f
    private var magicY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    val magicTargetX get() = magicX
    val magicTargetY get() = magicY

    private fun shoot(game: Game, dx: Float, dy: Float) {
        game.projectiles.add(Projectile(x, y, dx * 125f, dy * 125f,
            def.damage * 3f * (1f + .15f * (level - 1)), 0xFFFF9860.toInt(),
            radius = 6f, life = 4f, hostile = true))
    }
    private var chargeX = 0f
    private var chargeY = 1f

    val chargeDirX: Float get() = chargeX
    val chargeDirY: Float get() = chargeY

    val displayName: String get() = def.name
    val isBurrowed: Boolean get() = def.id == "mirewitch" && phase == BossPhase.RECOVER && phaseTimer > .2f

    var level = 1
        private set

    fun setLevel(newLevel: Int) {
        val healthRatio = if (maxHp > 0f) health / maxHp else 1f
        level = newLevel.coerceAtLeast(1)
        maxHp = def.maxHealth * 3f * (1f + .22f * (level - 1))
        if (!defeated) health = maxHp * healthRatio
    }

    fun engage() {
        if (defeated) return
        engaged = true
        phase = BossPhase.CHASE
        phaseTimer = 2.0f
        attackCycle = 0; shotTimer = 0f; velocityX = 0f; velocityY = 0f
    }

    fun markDefeated() {
        defeated = true
        engaged = false
        health = 0f
    }

    fun resetFight() {
        if (defeated) return
        health = maxHp
        engaged = false
        phase = BossPhase.CHASE
        phaseTimer = 2.4f
        velocityX = 0f; velocityY = 0f
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
                    val blend = 1f - kotlin.math.exp(-6f * dt)
                    velocityX += (dx / distance * def.speed - velocityX) * blend
                    velocityY += (dy / distance * def.speed - velocityY) * blend
                    moveWithCollision(game, velocityX * dt, velocityY * dt)
                }
                if (phaseTimer <= 0f) {
                    chargeX = if (distance > 0.01f) dx / distance else 0f
                    chargeY = if (distance > 0.01f) dy / distance else 1f
                    attackType = attackCycle++ % game.bossAttackCount
                    magicX = player.x; magicY = player.y
                    phase = BossPhase.TELEGRAPH
                    phaseTimer = 0.75f
                }
            }

            BossPhase.TELEGRAPH -> {
                if (attackType == 0) moveWithCollision(game, -chargeX * 20f * dt, -chargeY * 20f * dt)
                if (phaseTimer <= 0f) {
                    phase = BossPhase.DASH
                    phaseTimer = when (attackType) { 2 -> .9f; 3 -> 2f; else -> .38f }
                    // Each animal keeps the shared attacks but moves with its own trait.
                    if (def.id == "stonewarden") phaseTimer = .28f // Vulchar swoops from above.
                    if (def.id == "ashtitan") phaseTimer = .52f // Tiger makes a longer running pounce.
                    shotTimer = 0f
                    if (attackType == 1) shoot(game, chargeX, chargeY)
                    if (attackType == 4) {
                        game.bossMagic(magicX, magicY, def.damage * 3f * (1f + .15f * (level - 1)))
                    }
                }
            }

            BossPhase.DASH -> {
                if (attackType == 0) moveWithCollision(
                    game,
                    chargeX * def.speed * (1f + .04f * (level - 1)) * when (def.id) {
                        "gloomfang" -> 4.8f // Armadillo roll
                        "stonewarden" -> 5.2f // Vulchar dive
                        "ashtitan" -> 4.3f // Tiger run
                        "hollowking" -> 5.0f // Wolf ambush
                        else -> 3.4f
                    } * dt,
                    chargeY * def.speed * (1f + .04f * (level - 1)) * when (def.id) {
                        "gloomfang" -> 4.8f; "stonewarden" -> 5.2f; "ashtitan" -> 4.3f; "hollowking" -> 5.0f; else -> 3.4f
                    } * dt,
                )
                shotTimer -= dt
                if ((attackType == 2 || attackType == 3) && shotTimer <= 0f && phaseTimer > 0f) {
                    shotTimer += if (attackType == 2) .18f else .4f
                    if (attackType == 2) shoot(game, chargeX, chargeY)
                    else repeat(12) { index ->
                        val angle = index * kotlin.math.PI.toFloat() / 6f
                        shoot(game, kotlin.math.cos(angle), kotlin.math.sin(angle))
                    }
                }
                if (phaseTimer <= 0f) {
                    phase = BossPhase.RECOVER
                    phaseTimer = if (def.id == "mirewitch") 2f else .8f
                }
            }

            BossPhase.RECOVER -> {
                if (phaseTimer <= 0f) {
                    phase = BossPhase.CHASE
                    phaseTimer = 2.1f
                }
            }
        }

        val arenaDistance = hypot(x - arenaX, y - arenaY)
        val limit = arenaRadius - radius
        if (arenaDistance > limit) { x = arenaX + (x-arenaX)/arenaDistance*limit; y = arenaY + (y-arenaY)/arenaDistance*limit }
        if (phase == BossPhase.DASH && attackType == 0 && hypot(player.x-x, player.y-y) < radius + Config.PLAYER_RADIUS + 6f && contactCooldown <= 0f) {
            game.hurtPlayer(def.damage * 3f * (1f + .15f * (level - 1)), x, y)
            contactCooldown = 1.35f
        }
    }
}
