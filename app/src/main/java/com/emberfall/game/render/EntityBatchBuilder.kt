package com.emberfall.game.render

import android.graphics.PointF
import com.emberfall.game.core.Config
import com.emberfall.game.core.Game
import com.emberfall.game.entity.Boss
import com.emberfall.game.entity.BossPhase
import com.emberfall.game.entity.BossSignatures
import com.emberfall.game.entity.BossMove
import com.emberfall.game.entity.PlayerAction
import com.emberfall.game.entity.Projectile
import com.emberfall.game.entity.ProjectileKind
import com.emberfall.game.world.TileType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Props, campfires, chests, pickups, creatures, bosses, the player, projectiles
 * and particles, as sprites. Items are sorted with the same rule as
 * Game.render(): depth = (x + y) / TILE, then isometric x.
 *
 * Call after WorldBatchBuilder.build(), which clears the batch.
 */
class EntityBatchBuilder(private val layout: AtlasLayout) {

    private class Item(val depth: Float, val isoX: Float, val draw: () -> Unit)

    private val items = ArrayList<Item>()
    private lateinit var batch: SpriteBatch
    private var zoom = 1f
    private var originX = 0f
    private var originY = 0f
    private var elapsed = 0f

    fun build(
        batch: SpriteBatch,
        game: Game,
        cameraX: Float,
        cameraY: Float,
        zoom: Float,
        viewWidth: Float,
        viewHeight: Float,
        elapsed: Float,
        shakeX: Float,
        shakeY: Float,
    ) {
        this.batch = batch
        this.zoom = zoom
        this.elapsed = elapsed
        val cameraIso = IsoMath.worldToIso(cameraX, cameraY)
        originX = viewWidth * 0.5f + shakeX - cameraIso.x * zoom
        originY = viewHeight * 0.5f + shakeY - cameraIso.y * zoom
        items.clear()

        // Arena seal sits on the ground, under every entity, as in Game.render().
        drawArenaSeal(game)

        // Props, same culling as the terrain plus a 3-tile margin.
        val pb = IsoMath.visibleGridBounds(cameraX, cameraY, viewWidth, viewHeight, zoom, 3)
        for (ty in pb[2]..pb[3]) {
            for (tx in pb[0]..pb[1]) {
                val type = game.world.tileAt(tx, ty)
                if (type in AtlasLayout.PROP_TYPES) {
                    addItem((tx + .5f) * Config.TILE, (ty + .5f) * Config.TILE) { iso -> prop(type, iso) }
                }
            }
        }

        for (fire in game.campfires) {
            addItem(fire.x, fire.y) { iso -> cell("campfire", iso, 1f) }
        }
        for (chest in game.chests) {
            addItem(chest.x, chest.y) { iso -> cell(when {
                !chest.opened -> "chest"
                chest.openTimer < .3f -> "chest_open"
                chest.openTimer < .65f -> "chest_opening"
                else -> "chest_glow"
            }, iso, 1f) }
        }
        for (pickup in game.pickups) {
            addItem(pickup.x, pickup.y) { _ ->
                cell("pickup_${pickup.item.name}",
                    IsoMath.worldToIso(pickup.x, pickup.y + pickup.bobOffset), 1f)
            }
        }
        for (animal in game.animals) {
            addItem(animal.x, animal.y) { iso ->
                cell("animal_${animal.species.name}_${(animal.walkCycle * .65f).toInt() and 3}",
                    iso, 1f, animal.facingX < -.12f)
            }
        }
        for (wolf in game.wolves) {
            addItem(wolf.x, wolf.y) { iso -> cell("wolf", iso, 1f) }
        }
        for (boss in game.bosses) {
            // A burrowed mole is out of sight, as it is in the Canvas path's underground state.
            if ((boss.defeated && elapsed - boss.defeatedAtSeconds >= 1f) || (boss.isBurrowed && !boss.defeated)) continue
            addItem(boss.x, boss.y) { iso -> drawBoss(boss, iso) }
        }
        addItem(game.player.x, game.player.y) { iso -> drawPlayer(game, iso) }
        for (projectile in game.projectiles) {
            addItem(projectile.x, projectile.y) { iso ->
                drawProjectile(projectile, iso)
            }
        }

        items.sortWith(compareBy<Item> { it.depth }.thenBy { it.isoX })
        for (item in items) item.draw()

        // Particles draw after everything else, as in Game.render().
        for (particle in game.particles) {
            val iso = IsoMath.worldToIso(particle.x, particle.y)
            val fraction = (particle.life / particle.maxLife).coerceIn(0f, 1f)
            val size = if (particle.shrink) particle.size * fraction else particle.size
            solid(iso, size * .5f, particle.color, fraction)
        }
    }

    private fun addItem(x: Float, y: Float, draw: (PointF) -> Unit) {
        val iso = IsoMath.worldToIso(x, y)
        items.add(Item((x + y) / Config.TILE, iso.x) { draw(iso) })
    }

    /** Visible physical shaft/tracer rather than the previous nearly invisible square. */
    private fun drawProjectile(projectile: Projectile, head: PointF) {
        val tail = IsoMath.worldToIso(
            projectile.x - projectile.vx * projectile.trailSeconds,
            projectile.y - projectile.vy * projectile.trailSeconds,
        )
        val vectorX = head.x - tail.x
        val vectorY = head.y - tail.y
        when (projectile.kind) {
            ProjectileKind.ARROW -> {
                bar(tail.x, tail.y, vectorX, vectorY, 2.5f, 0xFF5A351F.toInt(), 1f)
                bar(tail.x + vectorX * .72f, tail.y + vectorY * .72f, vectorX * .28f, vectorY * .28f, 1.2f, projectile.color, 1f)
                solid(head, 3.4f, 0xFFECE6D4.toInt(), 1f)
            }
            ProjectileKind.BULLET -> {
                bar(tail.x, tail.y, vectorX, vectorY, 3.2f, projectile.color, .9f)
                solid(head, 3.6f, 0xFFFFFFFF.toInt(), 1f)
            }
            ProjectileKind.MAGIC -> {
                solid(head, projectile.radius * 1.25f, projectile.color, .25f)
                solid(head, projectile.radius * .6f, projectile.color, 1f)
                solid(head, projectile.radius * .22f, 0xFFFFFFFF.toInt(), 1f)
                for (i in 0..2) {
                    val a = elapsed * 15f + i * (PI * 2.0 / 3.0).toFloat()
                    solid(PointF(head.x + cos(a) * projectile.radius,
                        head.y + sin(a) * projectile.radius * .6f), 1.4f,
                        projectile.color, .8f)
                }
            }
        }
    }

    // ---- Characters -------------------------------------------------------

    private fun drawPlayer(game: Game, iso: PointF) {
        val player = game.player
        cell("shadow", iso, PLAYER_SHADOW_SCALE)
        val state = when {
            player.isSwinging -> AnimationState.ATTACK
            player.speed > 8f -> AnimationState.WALK
            else -> AnimationState.IDLE
        }
        sheet("player", iso, player.facingX, player.facingY, state, 42f,
            if (state == AnimationState.ATTACK)
                (1f - player.swingTimer / com.emberfall.game.entity.Player.SWING_DURATION)
                    .coerceIn(0f, .999f) / 3f else elapsed)
        if (player.isSwinging) {
            val direction = IsoMath.worldToIso(player.facingX, player.facingY)
            val length = sqrt(direction.x * direction.x + direction.y * direction.y).coerceAtLeast(.01f)
            val fx = direction.x / length
            val fy = direction.y / length
            val progress = (1f - player.swingTimer / com.emberfall.game.entity.Player.SWING_DURATION).coerceIn(0f, 1f)
            when (player.action) {
                PlayerAction.MELEE -> {
                    val end = PointF(iso.x + fx * (22f + progress * 9f), iso.y - 17f + fy * 18f)
                    bar(iso.x, iso.y - 17f, end.x - iso.x, end.y - iso.y + 17f,
                        5f, 0xFFFFE3A0.toInt(), .9f)
                    solid(end, 3f, 0xFFFFFFFF.toInt(), 1f)
                }
                PlayerAction.RANGED -> {
                    val muzzle = PointF(iso.x + fx * 23f, iso.y - 16f + fy * 15f)
                    solid(muzzle, 6f * (1f - progress), 0xFFFFC45C.toInt(), .85f)
                    solid(muzzle, 2.5f, 0xFFFFFFFF.toInt(), 1f)
                }
                PlayerAction.MAGIC -> {
                    cellSized("magic_ring", PointF(iso.x, iso.y - 17f),
                        22f + progress * 32f, 22f + progress * 24f,
                        player.actionColor, .8f)
                    solid(PointF(iso.x + fx * 16f, iso.y - 18f + fy * 10f),
                        4f, player.actionColor, 1f)
                }
            }
        }
    }

    private fun drawBoss(boss: Boss, iso: PointF) {
        drawTelegraph(boss, iso)
        cell("shadow", iso, boss.radius * 1.15f / SHADOW_RADIUS)
        val state = if (boss.defeated) AnimationState.DEATH else if (boss.dragonFlying) AnimationState.WALK else when (boss.phase) {
            BossPhase.CHASE -> if (boss.engaged) AnimationState.WALK else AnimationState.IDLE
            BossPhase.TELEGRAPH, BossPhase.DASH -> AnimationState.ATTACK
            BossPhase.RECOVER -> AnimationState.IDLE
        }
        sheet(boss.def.id, PointF(iso.x, iso.y - boss.airborne), boss.facingX, boss.facingY, state,
            if (boss.isDragon) 76f else 52f,
            if (boss.defeated) elapsed - boss.defeatedAtSeconds
                else if (state == AnimationState.ATTACK) boss.visualProgress.coerceIn(0f, .999f) / 3f
                else elapsed)
        drawBossAttackEffect(boss, iso)
    }

    private fun drawBossAttackEffect(boss: Boss, iso: PointF) {
        if (!boss.engaged || boss.defeated) return
        val color = BossSignatures.byId(boss.def.id)?.telegraphColor ?: boss.def.color
        val releasing = boss.phase == BossPhase.DASH
        if (boss.phase == BossPhase.TELEGRAPH || releasing) {
            val direction = IsoMath.worldToIso(boss.chargeDirX, boss.chargeDirY)
            val length = sqrt(direction.x * direction.x + direction.y * direction.y).coerceAtLeast(.01f)
            val fx = direction.x / length
            val fy = direction.y / length
            val progress = boss.visualProgress
            when (boss.activeMove) {
                BossMove.RING, BossMove.HOWL, BossMove.MAGIC ->
                    cellSized("magic_ring", PointF(iso.x, iso.y - 12f),
                        32f + progress * (if (releasing) 125f else 30f),
                        20f + progress * (if (releasing) 65f else 15f), color, .75f)
                BossMove.BREATH -> {
                    val reach = if (releasing) 75f else 30f + 30f * progress
                    for (i in -2..2) bar(iso.x + fx * 12f, iso.y - 14f + fy * 12f,
                        fx * reach - fy * i * 11f, fy * reach + fx * i * 7f,
                        if (releasing) 6f else 2.5f, color, .55f)
                }
                BossMove.SHOT, BossMove.SPINE_FAN, BossMove.DIRT_ARC, BossMove.TALONS -> {
                    val impact = PointF(iso.x + fx * (boss.radius + 10f),
                        iso.y - 14f + fy * (boss.radius + 10f))
                    solid(impact, if (releasing) 10f else 4f + progress * 5f, color, .9f)
                    solid(impact, 2.5f, 0xFFFFFFFF.toInt(), 1f)
                }
                else -> {
                    val reach = if (releasing) 35f + progress * 30f else 17f
                    bar(iso.x, iso.y - 13f, fx * reach, fy * reach,
                        if (releasing) 6f else 3f, color, .85f)
                    solid(PointF(iso.x + fx * reach, iso.y - 13f + fy * reach),
                        4f, 0xFFFFFFFF.toInt(), .8f)
                }
            }
        }
        if (boss.magicImpactTimer > 0f) {
            val target = IsoMath.worldToIso(boss.magicTargetX, boss.magicTargetY)
            val t = 1f - boss.magicImpactTimer / .55f
            cellSized("magic_ring", target, 38f + t * 105f, 24f + t * 55f,
                color, 1f - t)
        }
    }

    /** Sprite sheet frame, with the same direction and frame rules as IsoAnimation. */
    private fun sheet(name: String, iso: PointF, facingX: Float, facingY: Float,
                      state: AnimationState, size: Float, animationTime: Float = elapsed) {
        val speed = when (state) {
            AnimationState.IDLE -> 2f
            AnimationState.WALK -> 8f
            AnimationState.ATTACK -> 12f
            AnimationState.DEATH -> 4f
        }
        val frame = (animationTime * speed).toInt().coerceAtLeast(0)
        val column = state.ordinal * 4 + (if (state == AnimationState.DEATH) frame.coerceAtMost(3) else frame.mod(4))
        val row = direction(facingX, facingY)
        val left = iso.x - size * .5f
        val top = iso.y - size * .9f

        // Outline rim: the silhouette tinted gold, offset by one unit in each direction.
        for ((dx, dy) in RIM_OFFSETS) {
            sheetQuad(name, true, column, row, left + dx, top + dy, size, RIM_R, RIM_G, RIM_B)
        }
        sheetQuad(name, false, column, row, left, top, size, 1f, 1f, 1f)
    }

    private fun sheetQuad(name: String, silhouette: Boolean, column: Int, row: Int,
                          isoLeft: Float, isoTop: Float, size: Float,
                          r: Float, g: Float, b: Float) {
        val sx = layout.sheetX(name, silhouette) + column * FRAME
        val sy = layout.sheetY(name, silhouette) + row * FRAME
        batch.add(
            originX + isoLeft * zoom, originY + isoTop * zoom, size * zoom, size * zoom,
            layout.uMin(sx), layout.vMin(sy), layout.uMax(sx + FRAME), layout.vMax(sy + FRAME),
            r, g, b, 1f,
        )
    }

    /** Eight-way direction index, identical to IsoAnimation.draw(). */
    private fun direction(facingX: Float, facingY: Float): Int {
        val isoX = facingX - facingY
        val isoY = (facingX + facingY) * .5f
        val radians = atan2(isoX.toDouble(), -isoY.toDouble())
        return ((radians / (PI / 4.0)).roundToInt() + 8) % 8
    }

    // ---- Simple items -----------------------------------------------------

    private fun prop(type: TileType, iso: PointF) {
        val index = layout.propIndex(type)
        if (index < 0) return
        val x0 = layout.propX(index)
        val y0 = layout.propPixelY(index)
        val size = AtlasLayout.PROP_CELL * zoom
        batch.add(
            originX + iso.x * zoom - AtlasLayout.PROP_ANCHOR_X * zoom,
            originY + iso.y * zoom - AtlasLayout.PROP_ANCHOR_Y * zoom,
            size, size,
            layout.uMin(x0), layout.vMin(y0),
            layout.uMax(x0 + AtlasLayout.PROP_CELL), layout.vMax(y0 + AtlasLayout.PROP_CELL),
            1f, 1f, 1f, 1f,
        )
    }

    // ---- Telegraphs and arena seal -----------------------------------------

    /** Matches CreatureRenderer.drawBoss: a magic ring or a charge corridor while telegraphing. */
    private fun drawTelegraph(boss: Boss, iso: PointF) {
        if (!boss.engaged || boss.phase != BossPhase.TELEGRAPH) return
        val tellColor = BossSignatures.byId(boss.def.id)?.telegraphColor
            ?: ((boss.def.color and 0x00FFFFFF) or 0x66000000)
        if (boss.attackType == MAGIC_ATTACK_SLOT) {
            val target = IsoMath.worldToIso(boss.magicTargetX, boss.magicTargetY)
            cellSized("magic_ring", target, 130f, 64f, tellColor, 0.6f)
        } else {
            // Canvas draws the corridor from the boss centre, 150 units along and 75 down-right.
            val vx = (boss.chargeDirX - boss.chargeDirY) * 150f
            val vy = (boss.chargeDirX + boss.chargeDirY) * 75f
            bar(iso.x, iso.y, vx, vy, boss.radius * 1.4f, tellColor, 0.4f)
        }
    }

    /** Matches Game.drawArenaSeal: a 48-segment ring 8 units inside the arena edge, 5-unit stroke. */
    private fun drawArenaSeal(game: Game) {
        val boss = game.activeBoss ?: return
        if (!boss.engaged || boss.defeated || boss.isDragon) return
        val segments = 48
        val radius = boss.arenaRadius - 8f
        var previous: PointF? = null
        for (i in 0..segments) {
            val angle = i * (PI * 2).toFloat() / segments
            val point = IsoMath.worldToIso(
                boss.arenaX + cos(angle) * radius,
                boss.arenaY + sin(angle) * radius,
            )
            previous?.let { start ->
                bar(start.x, start.y, point.x - start.x, point.y - start.y, 5f, 0xFFFF4A3A.toInt(), 0.4f)
            }
            previous = point
        }
    }

    /**
     * A rotated bar from (startX, startY) in iso units along the vector (vx, vy), [thickness] wide.
     * Round caps are approximated by square ends.
     */
    private fun bar(startX: Float, startY: Float, vx: Float, vy: Float, thickness: Float, colour: Int, alpha: Float) {
        val length = sqrt(vx * vx + vy * vy)
        if (length < .01f) return
        val centreX = startX + vx * .5f
        val centreY = startY + vy * .5f
        val rotation = atan2(vy, vx)
        rotatedQuad(centreX, centreY, length + thickness, thickness, rotation, colour, alpha)
    }

    private fun rotatedQuad(centreX: Float, centreY: Float, w: Float, h: Float,
                            rotation: Float, colour: Int, alpha: Float) {
        val sx = originX + centreX * zoom
        val sy = originY + centreY * zoom
        val sw = w * zoom
        val sh = h * zoom
        batch.addSolid(
            sx - sw * .5f, sy - sh * .5f, sw, sh,
            ((colour shr 16) and 0xFF) / 255f,
            ((colour shr 8) and 0xFF) / 255f,
            (colour and 0xFF) / 255f,
            alpha,
            rotation,
        )
    }

    /** A cached cell centred on [iso] with an explicit size in iso units. */
    private fun cellSized(key: String, iso: PointF, widthIso: Float, heightIso: Float,
                          colour: Int, alpha: Float) {
        val index = layout.entityIndexOf(key)
        if (index < 0) return
        val x0 = layout.entityX(index)
        val y0 = layout.entityPixelY(index)
        batch.add(
            originX + (iso.x - widthIso * .5f) * zoom,
            originY + (iso.y - heightIso * .5f) * zoom,
            widthIso * zoom, heightIso * zoom,
            layout.uMin(x0), layout.vMin(y0),
            layout.uMax(x0 + AtlasLayout.ENT_CELL), layout.vMax(y0 + AtlasLayout.ENT_CELL),
            ((colour shr 16) and 0xFF) / 255f,
            ((colour shr 8) and 0xFF) / 255f,
            (colour and 0xFF) / 255f,
            alpha,
        )
    }

    /** Draws a cached entity cell, anchored at its ground point and scaled by [scale]. */
    private fun cell(key: String, iso: PointF, scale: Float, flipX: Boolean = false) {
        val index = layout.entityIndexOf(key)
        if (index < 0) return
        val x0 = layout.entityX(index)
        val y0 = layout.entityPixelY(index)
        val size = AtlasLayout.ENT_CELL * scale * zoom
        val left = originX + iso.x * zoom - AtlasLayout.ENT_ANCHOR_X * scale * zoom
        val top = originY + iso.y * zoom - AtlasLayout.ENT_ANCHOR_Y * scale * zoom
        batch.add(
            left, top, size, size,
            if (flipX) layout.uMax(x0 + AtlasLayout.ENT_CELL) else layout.uMin(x0), layout.vMin(y0),
            if (flipX) layout.uMin(x0) else layout.uMax(x0 + AtlasLayout.ENT_CELL), layout.vMax(y0 + AtlasLayout.ENT_CELL),
            1f, 1f, 1f, 1f,
        )
    }

    /** Solid square centred on [iso], used for projectiles and particles. */
    private fun solid(iso: PointF, halfPx: Float, color: Int, alpha: Float) {
        val sx = originX + iso.x * zoom
        val sy = originY + iso.y * zoom
        val h = halfPx * zoom
        batch.addSolid(
            sx - h, sy - h, h * 2f, h * 2f,
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
            alpha,
        )
    }

    private companion object {
        const val FRAME = 32
        const val SHADOW_RADIUS = 16f
        /** Attack slot 4 is always the magic ground zone in every moveset. */
        const val MAGIC_ATTACK_SLOT = 4
        const val PLAYER_SHADOW_SCALE = 15f / SHADOW_RADIUS
        // Matches the rim tint used by IsoAnimation (0xFFFFE6A0).
        const val RIM_R = 1f
        const val RIM_G = 0xE6 / 255f
        const val RIM_B = 0xA0 / 255f
        val RIM_OFFSETS = arrayOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)
    }
}
