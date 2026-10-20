package com.emberfall.game.entity

import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.util.hypot
import com.emberfall.game.world.BossDef
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

enum class BossPhase { CHASE, TELEGRAPH, DASH, RECOVER }

/** Named moves. Each boss maps its five attack slots onto these. */
enum class BossMove {
    ROLL,       // Armadillo: curls into a shell and rolls, bouncing off the arena wall
    SPINE_FAN,  // Armadillo: fans spines in a cone
    SWOOP,      // Vulchar / dragon: lifts off, then sweeps down and drops feathers
    TALONS,     // Vulchar: three quick aimed strikes from the air
    BURROW,     // Mole: digs underground, unhittable, and erupts beneath the player
    DIRT_ARC,   // Mole: throws dirt clods in an arc
    POUNCE,     // Tiger: long leap that leaves embers behind
    BREATH,     // Tiger / dragon: sustained cone of flame
    AMBUSH,     // Wolf King: circles the player, then lunges from the side
    HOWL,       // Wolf King: pulsing rings of icy shards
    SHOT,       // Shared: aimed bolt
    RING,       // Shared: radial burst
    MAGIC,      // Shared: delayed ground zone under the player
}

/**
 * A world boss bound to its arena. It wakes when the player enters and the
 * game seals the arena until it dies. The dragon is summoned after the other
 * five fall, flies toward the player, then fights on the ground without an arena.
 */
class Boss(val def: BossDef) : Creature(
    def.tileX * Config.TILE,
    def.tileY * Config.TILE,
    20f,
    def.maxHealth * 3f,
) {

    companion object {
        const val DRAGON_ID = "dragon"

        const val DRAGON_FLIGHT_SPEED = 280f
        const val DRAGON_GROUND_SPEED = 105f
        const val DRAGON_LAND_DISTANCE = 64f
        const val DRAGON_ATTACK_RANGE = 120f
        const val DRAGON_CANCEL_RANGE = 175f

        private val MOVESETS: Map<String, List<BossMove>> = mapOf(
            "gloomfang" to listOf(BossMove.ROLL, BossMove.SHOT, BossMove.SPINE_FAN, BossMove.RING, BossMove.MAGIC),
            "stonewarden" to listOf(BossMove.SWOOP, BossMove.SHOT, BossMove.TALONS, BossMove.RING, BossMove.MAGIC),
            "mirewitch" to listOf(BossMove.BURROW, BossMove.SHOT, BossMove.DIRT_ARC, BossMove.RING, BossMove.MAGIC),
            "ashtitan" to listOf(BossMove.POUNCE, BossMove.SHOT, BossMove.BREATH, BossMove.RING, BossMove.MAGIC),
            "hollowking" to listOf(BossMove.AMBUSH, BossMove.SHOT, BossMove.HOWL, BossMove.RING, BossMove.MAGIC),
            DRAGON_ID to listOf(BossMove.BREATH, BossMove.SHOT, BossMove.SWOOP, BossMove.RING, BossMove.MAGIC),
        )

        /** Moves that hurt the player on touch while they are underway. */
        private val CONTACT_MOVES = setOf(BossMove.ROLL, BossMove.SWOOP, BossMove.POUNCE, BossMove.AMBUSH)
    }

    val arenaX: Float = def.tileX * Config.TILE
    val arenaY: Float = def.tileY * Config.TILE
    val arenaRadius: Float = def.arenaRadiusTiles * Config.TILE

    val isDragon: Boolean = def.id == DRAGON_ID
    private val moveset: List<BossMove> = MOVESETS[def.id] ?: MOVESETS.getValue("gloomfang")
    private val projectileColor: Int = when (def.id) {
        "gloomfang" -> 0xFFB0A8C8.toInt()
        "stonewarden" -> 0xFFE4E9F2.toInt()
        "mirewitch" -> 0xFF8B6B45.toInt()
        "ashtitan" -> 0xFFFF7A2F.toInt()
        "hollowking" -> 0xFFB7D8FF.toInt()
        else -> 0xFFFF5A1E.toInt()
    }

    var engaged = false
        private set
    var defeated = false
        private set
    /** Presentation timestamp only; saved defeats retain the default and do not replay. */
    var defeatedAtSeconds = Float.NEGATIVE_INFINITY
    var phase = BossPhase.CHASE
        private set
    var attackType = 0
        private set
    var level = 1
        private set

    private var phaseTimer = 2.4f
    private var contactCooldown = 0f
    private var attackCycle = 0
    private var currentMove = BossMove.SHOT
    private var magicX = 0f
    private var magicY = 0f
    private var chargeX = 0f
    private var chargeY = 1f
    private var velocityX = 0f
    private var velocityY = 0f
    private var shotTimer = 0f
    private var dashElapsed = 0f
    private var shotsFired = 0
    private var rollBounces = 0
    private var burrowing = false
    private var rolling = false
    var dragonFlying = isDragon
        private set

    val magicTargetX: Float get() = magicX
    val magicTargetY: Float get() = magicY
    val chargeDirX: Float get() = chargeX
    val chargeDirY: Float get() = chargeY
    val displayName: String get() = def.name

    /** True while the mole is underground: unhittable and drawn as a mound. */
    val isBurrowed: Boolean
        get() = def.id == "mirewitch" &&
                (burrowing || (phase == BossPhase.RECOVER && phaseTimer > .2f))

    /** Pixels the sprite floats above the ground during a swoop. */
    val airborne: Float
        get() = if (dragonFlying) 38f else if (engaged && currentMove == BossMove.SWOOP &&
            (phase == BossPhase.TELEGRAPH || phase == BossPhase.DASH)) 26f else 0f

    private fun shotDamage(): Float = def.damage * 3f * (1f + .15f * (level - 1))

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
        attackCycle = 0
        shotTimer = 0f
        velocityX = 0f
        velocityY = 0f
        if (isDragon) dragonFlying = false
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
        velocityX = 0f
        velocityY = 0f
        burrowing = false
        rolling = false
        x = arenaX
        y = arenaY
        knockbackX = 0f
        knockbackY = 0f
    }

    override fun hurt(amount: Float, sourceX: Float, sourceY: Float) {
        if (burrowing) return                       // underground: out of reach
        super.hurt(if (rolling) amount * .35f else amount, sourceX, sourceY) // shell takes less damage
    }

    override fun update(dt: Float, game: Game) {
        if (defeated) return
        super.update(dt, game)
        if (freezeTimer > 0f) { freezeTimer -= dt; return }

        val player = game.player
        val dx = player.x - x
        val dy = player.y - y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dragonFlying) {
            flyTowardsPlayer(dt, game, dx, dy, distance)
            return
        }

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
            BossPhase.CHASE -> chase(dt, game, dx, dy, distance)
            BossPhase.TELEGRAPH -> telegraph(dt, game, dx, dy, distance)
            BossPhase.DASH -> dash(dt, game)
            BossPhase.RECOVER -> if (phaseTimer <= 0f) {
                phase = BossPhase.CHASE
                phaseTimer = 2.1f
            }
        }

        if (!isDragon) clampToArena()
    }

    // ---- Phases ------------------------------------------------------------

    private fun flyTowardsPlayer(dt: Float, game: Game, dx: Float, dy: Float, distance: Float) {
        if (distance > .01f) {
            faceDirection(dx, dy)
            val onLand = !game.world.isSolid(game.world.tileX(x), game.world.tileY(y))
            val approach = if (onLand) DRAGON_LAND_DISTANCE else 18f
            val speed = (distance / 10f).coerceIn(DRAGON_FLIGHT_SPEED, 480f)
            val step = (speed * dt).coerceAtMost((distance - approach).coerceAtLeast(0f))
            x += dx / distance * step
            y += dy / distance * step
        }
        val remaining = hypot((game.player.x - x).toDouble(), (game.player.y - y).toDouble()).toFloat()
        if (remaining <= DRAGON_LAND_DISTANCE &&
            !game.world.isSolid(game.world.tileX(x), game.world.tileY(y)) &&
            game.campfires.none { it.isSafe(game.player.x, game.player.y) }) {
            dragonFlying = false
            engage()
            game.onBossEngaged(this)
        }
    }

    private fun chase(dt: Float, game: Game, dx: Float, dy: Float, distance: Float) {
        if (distance > 1f) {
            val targetSpeed = if (isDragon) DRAGON_GROUND_SPEED else def.speed
            val blend = 1f - exp(-6f * dt)
            velocityX += (dx / distance * targetSpeed - velocityX) * blend
            velocityY += (dy / distance * targetSpeed - velocityY) * blend
            moveWithCollision(game, velocityX * dt, velocityY * dt)
        }
        if (phaseTimer <= 0f && (!isDragon || distance <= DRAGON_ATTACK_RANGE)) beginTelegraph(game, dx, dy, distance)
    }

    private fun beginTelegraph(game: Game, dx: Float, dy: Float, distance: Float) {
        chargeX = if (distance > .01f) dx / distance else 0f
        chargeY = if (distance > .01f) dy / distance else 1f
        attackType = attackCycle++ % game.bossAttackCount
        currentMove = moveset[attackType]
        magicX = game.player.x
        magicY = game.player.y
        phase = BossPhase.TELEGRAPH
        phaseTimer = telegraphTime(currentMove)
        shotTimer = 0f
        rollBounces = 0
        rolling = false
        burrowing = currentMove == BossMove.BURROW
    }

    private fun telegraph(dt: Float, game: Game, dx: Float, dy: Float, distance: Float) {
        if (isDragon && distance > DRAGON_CANCEL_RANGE) {
            phase = BossPhase.CHASE
            phaseTimer = 0f
            return
        }
        when (currentMove) {
            BossMove.ROLL -> moveWithCollision(game, -chargeX * 20f * dt, -chargeY * 20f * dt)
            BossMove.BURROW -> {
                // Digs toward the player through the ground, ignoring walls.
                val d = max(distance, 1f)
                val step = def.speed * 1.6f * dt
                x += dx / d * step
                y += dy / d * step
            }
            BossMove.AMBUSH -> orbit(dt, game, dx, dy, distance)
            else -> Unit
        }
        if (phaseTimer <= 0f) beginDash(game)
    }

    private fun beginDash(game: Game) {
        phase = BossPhase.DASH
        phaseTimer = dashTime(currentMove)
        dashElapsed = 0f
        shotTimer = 0f
        shotsFired = 0
        when (currentMove) {
            BossMove.AMBUSH -> {
                val (ax, ay) = dirToPlayer(game)
                chargeX = ax
                chargeY = ay
            }
            BossMove.ROLL -> rolling = true
            BossMove.BURROW -> {
                val toPlayer = hypot((game.player.x - x).toDouble(), (game.player.y - y).toDouble()).toFloat()
                erupt(game, toPlayer)
            }
            BossMove.SHOT -> shoot(game, chargeX, chargeY, speed = 125f)
            BossMove.SPINE_FAN -> fan(game, 6, .55f, chargeX, chargeY, 120f, projectileColor, 3f, 6f)
            BossMove.DIRT_ARC -> fan(game, 5, .75f, chargeX, chargeY, 95f, projectileColor, 3.5f, 5f)
            BossMove.MAGIC -> game.bossMagic(magicX, magicY, shotDamage())
            else -> Unit
        }
    }

    private fun dash(dt: Float, game: Game) {
        dashElapsed += dt
        when (currentMove) {
            BossMove.ROLL -> rollStep(game, dt)
            BossMove.SWOOP -> lunge(game, dt, if (isDragon) 3.6f else 5.2f)
            BossMove.POUNCE -> {
                lunge(game, dt, 4.3f)
                if (tick(dt, .14f)) {
                    // Embers drop behind the tiger as it runs.
                    shoot(game, -chargeX, -chargeY, speed = 40f, color = 0xFFFF8A3A.toInt(), life = 1.6f, size = 5f)
                }
            }
            BossMove.AMBUSH -> lunge(game, dt, 5f)
            BossMove.BREATH -> breath(game, dt)
            BossMove.TALONS -> talons(game, dt)
            BossMove.HOWL -> if (tick(dt, .5f)) ring(game, 10, dashElapsed * .6f, 110f, 0xFFB7D8FF.toInt())
            BossMove.RING -> if (tick(dt, .4f)) ring(game, 12, 0f, 125f, projectileColor)
            else -> Unit
        }
        if (currentMove in CONTACT_MOVES) checkContact(game)
        if (phaseTimer <= 0f) endDash(game)
    }

    private fun endDash(game: Game) {
        when (currentMove) {
            // Feathers scatter as the vulchar pulls up out of the dive.
            BossMove.SWOOP -> fan(game, 5, .5f, chargeX, chargeY, 105f, 0xFFE4E9F2.toInt(), 2.2f, 4f)
            BossMove.ROLL -> rolling = false
            else -> Unit
        }
        phase = BossPhase.RECOVER
        phaseTimer = recoverTime()
    }

    // ---- Movement helpers --------------------------------------------------

    private fun lunge(game: Game, dt: Float, multiplier: Float) {
        val speed = def.speed * (1f + .04f * (level - 1)) * multiplier
        moveWithCollision(game, chargeX * speed * dt, chargeY * speed * dt)
    }

    /** Rolls straight, reversing when blocked or when it hits the arena wall. */
    private fun rollStep(game: Game, dt: Float) {
        val speed = def.speed * 4.8f
        val moved = moveWithCollision(game, chargeX * speed * dt, chargeY * speed * dt)
        val fromCentre = hypot((x - arenaX).toDouble(), (y - arenaY).toDouble()).toFloat()
        val atWall = fromCentre > arenaRadius - radius - 6f
        if ((!moved || atWall) && rollBounces < 3) {
            rollBounces++
            if (atWall && fromCentre > .001f) {
                chargeX = (arenaX - x) / fromCentre
                chargeY = (arenaY - y) / fromCentre
            } else {
                chargeX = -chargeX
                chargeY = -chargeY
            }
        }
    }

    /** Circles the player at a fixed radius before the wolf king lunges. */
    private fun orbit(dt: Float, game: Game, dx: Float, dy: Float, distance: Float) {
        if (distance < 1f) return
        val nx = dx / distance
        val ny = dy / distance
        val radial = (distance - 80f) * 1.5f
        val speed = def.speed * 1.2f
        val vx = -ny * speed - nx * radial
        val vy = nx * speed - ny * radial
        moveWithCollision(game, vx * dt, vy * dt)
    }

    private fun erupt(game: Game, toPlayer: Float) {
        burrowing = false
        ring(game, 10, 0f, 115f, projectileColor)
        if (toPlayer < 64f) game.hurtPlayer(shotDamage(), x, y)
    }

    private fun checkContact(game: Game) {
        if (contactCooldown > 0f) return
        val p = game.player
        val d = hypot((p.x - x).toDouble(), (p.y - y).toDouble()).toFloat()
        if (d < radius + Config.PLAYER_RADIUS + 6f) {
            game.hurtPlayer(shotDamage(), x, y)
            contactCooldown = 1.35f
        }
    }

    private fun clampToArena() {
        val distance = hypot((x - arenaX).toDouble(), (y - arenaY).toDouble()).toFloat()
        val limit = arenaRadius - radius
        if (distance > limit && distance > .001f) {
            x = arenaX + (x - arenaX) / distance * limit
            y = arenaY + (y - arenaY) / distance * limit
        }
    }

    // ---- Attack helpers ----------------------------------------------------

    private fun breath(game: Game, dt: Float) {
        if (!tick(dt, .09f)) return
        val total = dashTime(BossMove.BREATH)
        val spread = if (isDragon) .9f else .35f
        val t = (dashElapsed / total).coerceIn(0f, 1f)
        val base = atan2(chargeY, chargeX)
        val angle = base + (t * 2f - 1f) * spread
        shoot(game, cos(angle), sin(angle), speed = 140f, color = 0xFFFF9A3A.toInt(), life = 1.1f, size = 7f)
    }

    private fun talons(game: Game, dt: Float) {
        if (shotsFired >= 3 || !tick(dt, .28f)) return
        shotsFired++
        val (ax, ay) = dirToPlayer(game)
        shoot(game, ax, ay, speed = 170f, color = 0xFFE4E9F2.toInt(), life = 3f, size = 5f)
    }

    private fun shoot(
        game: Game,
        dirX: Float,
        dirY: Float,
        speed: Float = 125f,
        color: Int = projectileColor,
        life: Float = 4f,
        size: Float = 6f,
    ) {
        game.projectiles.add(
            Projectile(
                x, y, dirX * speed, dirY * speed, shotDamage(), color,
                radius = size, life = life, hostile = true,
            ),
        )
    }

    private fun fan(
        game: Game,
        count: Int,
        spread: Float,
        dirX: Float,
        dirY: Float,
        speed: Float,
        color: Int,
        life: Float,
        size: Float,
    ) {
        val base = atan2(dirY, dirX)
        for (i in 0 until count) {
            val t = if (count == 1) 0f else i / (count - 1f) * 2f - 1f
            val a = base + t * spread
            shoot(game, cos(a), sin(a), speed, color, life, size)
        }
    }

    private fun ring(game: Game, count: Int, offset: Float, speed: Float, color: Int) {
        for (i in 0 until count) {
            val a = offset + i * 2f * PI.toFloat() / count
            shoot(game, cos(a), sin(a), speed, color)
        }
    }

    /** Fires once each [interval] seconds during a dash. */
    private fun tick(dt: Float, interval: Float): Boolean {
        shotTimer -= dt
        if (shotTimer > 0f) return false
        shotTimer += interval
        return true
    }

    private fun dirToPlayer(game: Game): Pair<Float, Float> {
        val dx = game.player.x - x
        val dy = game.player.y - y
        val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        return if (d > .01f) Pair(dx / d, dy / d) else Pair(chargeX, chargeY)
    }

    // ---- Timing tables -----------------------------------------------------

    private fun telegraphTime(move: BossMove): Float = when (move) {
        BossMove.ROLL -> .9f
        BossMove.SWOOP -> .7f
        BossMove.TALONS -> .45f
        BossMove.BURROW -> 2.2f
        BossMove.DIRT_ARC -> .55f
        BossMove.POUNCE -> .75f
        BossMove.BREATH -> 1f
        BossMove.AMBUSH -> 1.6f
        BossMove.HOWL -> .8f
        BossMove.SPINE_FAN -> .6f
        BossMove.SHOT -> .5f
        BossMove.RING -> .6f
        BossMove.MAGIC -> .75f
    }

    private fun dashTime(move: BossMove): Float = when (move) {
        BossMove.ROLL -> 1.7f
        BossMove.SWOOP -> if (isDragon) .9f else .28f
        BossMove.TALONS -> .9f
        BossMove.BURROW -> .3f
        BossMove.DIRT_ARC -> .5f
        BossMove.POUNCE -> .55f
        BossMove.BREATH -> if (isDragon) 2.4f else 1.2f
        BossMove.AMBUSH -> .5f
        BossMove.HOWL -> 1.6f
        BossMove.SPINE_FAN -> .25f
        BossMove.SHOT -> .38f
        BossMove.RING -> 2f
        BossMove.MAGIC -> .8f
    }

    private fun recoverTime(): Float = when {
        isDragon -> 1.6f
        def.id == "mirewitch" -> 2f
        else -> .8f
    }
}
