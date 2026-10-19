package com.emberfall.game.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.emberfall.game.entity.Animal
import com.emberfall.game.entity.AnimalSpecies
import com.emberfall.game.entity.Boss
import com.emberfall.game.entity.Campfire
import com.emberfall.game.entity.Creature
import com.emberfall.game.entity.FloatingText
import com.emberfall.game.entity.LootChest
import com.emberfall.game.entity.Particle
import com.emberfall.game.entity.Pickup
import com.emberfall.game.entity.Projectile
import com.emberfall.game.entity.Player
import com.emberfall.game.entity.Wolf
import com.emberfall.game.entity.cap
import com.emberfall.game.input.InputState
import com.emberfall.game.input.UiCommand
import com.emberfall.game.item.Inventory
import com.emberfall.game.item.ItemCategory
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.Recipes
import com.emberfall.game.item.WeaponStyle
import com.emberfall.game.render.CreatureRenderer
import com.emberfall.game.render.ObjectRenderer
import com.emberfall.game.render.PlayerRenderer
import com.emberfall.game.render.WorldRenderer
import com.emberfall.game.render.IsoMath
import com.emberfall.game.render.Renderable
import com.emberfall.game.save.SaveManager
import com.emberfall.game.save.SaveState
import com.emberfall.game.ui.HudRenderer
import com.emberfall.game.world.Bosses
import com.emberfall.game.world.Collision
import com.emberfall.game.world.TilePos
import com.emberfall.game.world.TileType
import com.emberfall.game.world.World
import com.emberfall.game.world.WorldPoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns the simulation. Runs on the game thread; [InputState] is the only
 * channel from the UI thread, and panel commands arrive as an integer queue.
 */
class Game(private val context: Context, private val input: InputState) {

    private val saveManager = SaveManager(context)
    private val restored = saveManager.load()

    val world = World(restored?.seed ?: Random.nextInt())
    val player: Player
    val camera = Camera()
    val inventory = Inventory()
    val graphics: GraphicsSettings = restored?.graphics ?: GraphicsSettings()

    val animals = mutableListOf<Animal>()
    val wolves = mutableListOf<Wolf>()
    val bosses = mutableListOf<Boss>()
    val pickups = mutableListOf<Pickup>()
    val campfires = mutableListOf<Campfire>()
    val chests = mutableListOf<LootChest>()
    val texts = mutableListOf<FloatingText>()
    val particles = mutableListOf<Particle>()
    val projectiles = mutableListOf<Projectile>()

    var equippedWeapon: ItemId? = null
        private set
    var lastSpell = Spell.FIREBALL
        private set

    private val defeatedBosses = mutableSetOf<String>()
    private val openedChests = mutableSetOf<Int>()

    var respawnX = 0f
        private set
    var respawnY = 0f
        private set

    var minutes: Float = restored?.minutes ?: (7.5f * 60f)
        private set
    var day: Int = restored?.day ?: 1
        private set
    var stage: Int = restored?.stage ?: 1
        private set
    var victoryTimer = 0f
        private set
    private var stageAdvancePending = false

    var screenWidth = 0
        private set
    var screenHeight = 0
        private set

    var fps = 60f
        private set

    var biomeLabel = "PLAINS"
        private set

    var hintTimer = 8f
        private set

    var activeBoss: Boss? = null
        private set

    var bossBannerText = ""
        private set
    var bossBannerTimer = 0f
        private set

    var toastText = ""
        private set
    var toastTimer = 0f
        private set

    var deathTimer = 0f
        private set

    private val worldRenderer = WorldRenderer(context)
    private val playerRenderer = PlayerRenderer(context)
    private val creatureRenderer = CreatureRenderer(context)
    private val objectRenderer = ObjectRenderer()
    private val hud = HudRenderer(context)
    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val juice = JuiceManager()

    private var biomeTimer = 0f
    private var saveTimer = 0f
    private var spawnTimer = 0f
    private var elapsedSeconds = 0f
    private var fireParticleTimer = 0f
    private var deathDuration = 1.8f
    val weaponLevels = mutableMapOf<ItemId, Int>()
    val weaponStars = mutableMapOf<ItemId, Int>()

    private var spellCooldown = 0f
    private var powerPotionTimer = 0f
    private var regenPotionTimer = 0f

    init {
        input.pushAudioState(graphics.music, graphics.sound)
        val spawnX = (world.spawn.x + 0.5f) * Config.TILE
        val spawnY = (world.spawn.y + 0.5f) * Config.TILE
        val startX = restored?.x ?: spawnX
        val startY = restored?.y ?: spawnY

        player = if (!Collision.isBlocked(world, startX, startY, Config.PLAYER_RADIUS)) {
            Player(startX, startY)
        } else {
            Player(spawnX, spawnY)
        }

        respawnX = spawnX
        respawnY = spawnY

        if (restored != null) {
            applySavedProgress(restored)
        }
        if (defeatedBosses.size == Bosses.ALL.size) {
            stage += 1
            defeatedBosses.clear()
        }

        val fireSpot = openSpotNear(world.spawn.x + 1, world.spawn.y)
        campfires.add(Campfire(0, fireSpot.first, fireSpot.second))
        if (restored == null) {
            respawnX = fireSpot.first
            respawnY = fireSpot.second
        }
        for (index in 1 until WorldPoints.campfires.size) {
            val point = WorldPoints.campfires[index]
            val spot = openSpotNear(point.tileX, point.tileY)
            campfires.add(Campfire(index, spot.first, spot.second))
        }

        for ((index, point) in WorldPoints.chests.withIndex()) {
            val spot = openSpotNear(point.x, point.y)
            chests.add(
                LootChest(index, spot.first, spot.second).also {
                    it.opened = openedChests.contains(index)
                },
            )
        }

        for (def in Bosses.ALL) {
            val boss = Boss(def)
            boss.setLevel(stage + defeatedBosses.size)
            if (defeatedBosses.contains(def.id)) boss.markDefeated()
            bosses.add(boss)
        }

        camera.snapTo(player.x, player.y)
        biomeLabel = world.biomeAt(world.tileX(player.x), world.tileY(player.y)).label
    }

    private fun applySavedProgress(state: SaveState) {
        inventory.clear()
        for ((id, count) in state.inventory) inventory.add(id, count)

        val stats = player.stats
        stats.level = state.level
        stats.xp = state.xp
        stats.maxHealth = state.maxHealth.coerceAtLeast(50f)
        stats.health = state.health.coerceIn(1f, stats.maxHealth)
        stats.maxMana = state.maxMana.coerceAtLeast(10f)
        stats.mana = state.mana.coerceIn(0f, stats.maxMana)
        stats.hunger = state.hunger.coerceIn(0f, 100f)
        stats.bonusHealth = state.bonusHealth
        stats.bonusMana = state.bonusMana

        defeatedBosses.addAll(state.defeatedBosses)
        openedChests.addAll(state.openedChests)

        equippedWeapon = state.equippedWeapon?.let { name ->
            runCatching { ItemId.valueOf(name) }.getOrNull()
        }

        for ((name, level) in state.weaponLevels) runCatching { ItemId.valueOf(name) }.getOrNull()?.let { weaponLevels[it] = level.coerceIn(1, 50) }
        for ((name, stars) in state.weaponStars) runCatching { ItemId.valueOf(name) }.getOrNull()?.let { weaponStars[it] = stars.coerceIn(1, 5) }
        if (state.campfireX != 0f || state.campfireY != 0f) {
            respawnX = state.campfireX
            respawnY = state.campfireY
        }
    }

    private fun openSpotNear(tileX: Int, tileY: Int): Pair<Float, Float> {
        for (radius in 0..6) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (max(abs(dx), abs(dy)) != radius) continue
                    val tx = tileX + dx
                    val ty = tileY + dy
                    if (!world.isSolid(tx, ty)) {
                        return (tx + 0.5f) * Config.TILE to (ty + 0.5f) * Config.TILE
                    }
                }
            }
        }
        return (tileX + 0.5f) * Config.TILE to (tileY + 0.5f) * Config.TILE
    }

    fun setViewport(width: Int, height: Int) {
        if (width == screenWidth && height == screenHeight) return
        screenWidth = width
        screenHeight = height
        camera.resize(width, height)
    }

    fun reportFrame(frameSeconds: Float) {
        if (frameSeconds <= 0.0001f) return
        val instant = (1f / frameSeconds).coerceIn(1f, 240f)
        fps = fps * 0.9f + instant * 0.1f
    }

    // ---- Update ------------------------------------------------------------

    fun update(dt: Float) {
        processCommands()
        juice.update(dt)

        if (input.inventoryOpen || input.settingsOpen ||
            input.spellPickerOpen || input.weaponPickerOpen || input.mapOpen || input.campMenuOpen
        ) return
        if (juice.isFrozen()) return

        hud.update(dt, this)

        if (deathTimer > 0f) {
            deathTimer -= dt
            updateEffects(dt)
            if (deathTimer <= 0f) respawn()
            return
        }

        if (spellCooldown > 0f) spellCooldown = max(0f, spellCooldown - dt)
        powerPotionTimer = max(0f, powerPotionTimer - dt)
        if (regenPotionTimer > 0f) { regenPotionTimer = max(0f, regenPotionTimer - dt); player.stats.heal(2f * dt) }

        if (input.consumeAttackRequest()) playerAttack()
        if (input.consumeMagicRequest()) castSelectedSpell()
        val interactRequested = input.consumeInteractRequest()

        val screenMoveX = input.moveX
        val screenMoveY = input.moveY
        val gridMoveX = screenMoveX * .5f + screenMoveY
        val gridMoveY = screenMoveY - screenMoveX * .5f
        val gridMoveLength = hypot(gridMoveX.toDouble(), gridMoveY.toDouble()).toFloat()
        val screenMoveLength = hypot(screenMoveX.toDouble(), screenMoveY.toDouble()).toFloat()
        val moveScale = if (gridMoveLength > .001f) screenMoveLength / gridMoveLength else 0f
        player.update(dt, gridMoveX * moveScale, gridMoveY * moveScale, input.run, world)
        player.stats.update(dt)
        clampToArena()

        if (interactRequested) interact()

        val tileX = floor(player.x / Config.TILE).toInt()
        val tileY = floor(player.y / Config.TILE).toInt()
        world.ensureAround(tileX, tileY)
        world.pruneAround(tileX, tileY)

        camera.update(player.x, player.y, player.facingX, player.facingY, dt)

        updateCreatures(dt)
        updateProjectiles(dt)
        if (stageAdvancePending) advanceStage()
        updateEffects(dt)
        objectRenderer.update(dt)
        updateSpawns(dt)

        elapsedSeconds += dt
        minutes += dt * (Config.MINUTES_PER_DAY / Config.DAY_LENGTH_SECONDS)
        if (minutes >= Config.MINUTES_PER_DAY) {
            minutes -= Config.MINUTES_PER_DAY
            day += 1
        }

        if (hintTimer > 0f) hintTimer -= dt
        if (bossBannerTimer > 0f) bossBannerTimer -= dt
        if (victoryTimer > 0f) victoryTimer -= dt
        if (toastTimer > 0f) toastTimer -= dt

        biomeTimer -= dt
        if (biomeTimer <= 0f) {
            biomeTimer = 0.5f
            biomeLabel = world.biomeAt(tileX, tileY).label
        }

        if (!player.isAlive && deathTimer <= 0f) startDeath()

        saveTimer += dt
        if (saveTimer >= Config.SAVE_INTERVAL_SECONDS) {
            saveTimer = 0f
            saveNow()
        }
    }

    private fun processCommands() {
        while (true) {
            val command = input.pollCommand() ?: break
            when {
                command in UiCommand.USE_SLOT_BASE until UiCommand.QUALITY_BASE -> {
                    useSlot(command - UiCommand.USE_SLOT_BASE)
                }

                command in UiCommand.QUALITY_BASE until UiCommand.QUALITY_BASE + 10 -> {
                    val ordinal = command - UiCommand.QUALITY_BASE
                    GraphicsQuality.values().getOrNull(ordinal)?.let { graphics.quality = it }
                }

                command == UiCommand.TOGGLE_SMOOTH -> {
                    graphics.smoothTextures = !graphics.smoothTextures
                }

                command == UiCommand.TOGGLE_ANTIALIAS -> {
                    graphics.antiAliasing = !graphics.antiAliasing
                }

                command == UiCommand.TOGGLE_PARTICLES -> graphics.particles = !graphics.particles
                command == UiCommand.TOGGLE_MUSIC -> { graphics.music = !graphics.music; input.pushAudioState(graphics.music, graphics.sound) }
                command == UiCommand.TOGGLE_SOUND -> { graphics.sound = !graphics.sound; input.pushAudioState(graphics.music, graphics.sound) }
                command == UiCommand.TOGGLE_VIBRATION -> graphics.vibration = !graphics.vibration
                command in UiCommand.RESOLUTION_BASE until UiCommand.FPS_BASE -> {
                    graphics.resolutionScale = listOf(0.5f, 0.75f, 1f, 1.5f, 2f).getOrElse(command - UiCommand.RESOLUTION_BASE) { 1f }
                }
                command in UiCommand.FPS_BASE until UiCommand.SAVE_NOW -> {
                    graphics.targetFps = listOf(30, 60, 90, 120).getOrElse(command - UiCommand.FPS_BASE) { 60 }
                }
                command == UiCommand.SAVE_NOW -> { saveNow(); toast("Game saved") }
                command == UiCommand.LOAD_SAVE -> { loadSavedState(); toast("Saved state loaded") }
                command == UiCommand.UPGRADE_WEAPON -> upgradeWeapon()
                command == UiCommand.COMBINE_WEAPON -> combineWeapon()
                command in UiCommand.EQUIP_WEAPON_BASE until UiCommand.EQUIP_WEAPON_BASE + ItemId.values().size -> {
                    val id = ItemId.values().getOrNull(command - UiCommand.EQUIP_WEAPON_BASE)
                    if (id?.category == ItemCategory.WEAPON && inventory.count(id) > 0) { equippedWeapon = id; toast("${id.displayName} equipped") }
                }

                command in UiCommand.CAST_BASE until UiCommand.CRAFT_BASE -> {
                    val spell = Spell.values().getOrNull(command - UiCommand.CAST_BASE)
                    if (spell != null) lastSpell = spell
                }

                command in UiCommand.CRAFT_BASE until UiCommand.TOGGLE_CRAFT -> {
                    craft(command - UiCommand.CRAFT_BASE)
                }

                command == UiCommand.TOGGLE_CRAFT -> {
                    input.craftingOpen = !input.craftingOpen
                }

                command == UiCommand.CLOSE_PANELS -> input.closePanels()
            }
        }
    }

    private fun useSlot(index: Int) {
        val stack = inventory.slot(index) ?: return
        val id = stack.id
        when (id.category) {
            ItemCategory.WEAPON -> {
                equippedWeapon = id
                toast("${id.displayName} equipped")
            }
            ItemCategory.MATERIAL -> toast("${id.displayName}: crafting material")
            ItemCategory.CONSUMABLE, ItemCategory.VALUABLE -> {
                if ((id.heal > 0f || id == ItemId.REGEN_POTION) && player.stats.health >= player.stats.maxHealth) {
                    toast("Health is already full"); return
                }
                if (id == ItemId.POWER_POTION) powerPotionTimer = 30f
                if (id == ItemId.REGEN_POTION) regenPotionTimer = 15f
                if (id.heal > 0f) player.stats.heal(id.heal)
                if (id.mana > 0f) player.stats.restoreMana(id.mana)
                if (id.food > 0f) player.stats.feed(id.food)
                inventory.removeAt(index)
                spawnParticles(player.x, player.y, id.color, 8)
                toast("Used ${id.displayName}")
            }
        }
    }

    private fun craft(index: Int) {
        val recipe = Recipes.ALL.getOrNull(index) ?: return
        for ((id, count) in recipe.costs) {
            if (inventory.count(id) < count) {
                toast("Need ${count - inventory.count(id)} more ${id.displayName}")
                return
            }
        }
        for ((id, count) in recipe.costs) inventory.remove(id, count)
        inventory.add(recipe.result, 1)
        if (recipe.result.category == ItemCategory.WEAPON) {
            equippedWeapon = recipe.result
        }
        banner("CRAFTED ${recipe.result.displayName.uppercase()}")
        toast("${recipe.result.displayName} ready")
        spawnParticles(player.x, player.y, recipe.result.color, 16)
    }

    // ---- Combat ------------------------------------------------------------

    fun activeWeapon(): ItemId? {
        val equipped = equippedWeapon
        if (equipped != null && inventory.count(equipped) > 0) return equipped
        return inventory.bestWeapon()
    }

    fun magicCost(spell: Spell): Float = spell.manaCost * 0.9f.pow((stage - 1) * Bosses.ALL.size + defeatedBosses.size)

    fun defeatedBossIds(): Set<String> = defeatedBosses

    private fun playerAttack() {
        if (!player.isAlive) return
        val weapon = activeWeapon()
        val style = weapon?.style ?: WeaponStyle.MELEE

        // Auto-aim: snap toward the closest enemy before committing the swing.
        val target = nearestEnemy(if (style == WeaponStyle.PROJECTILE) 14f * Config.TILE else 6f * Config.TILE)
        if (target != null) player.faceTowards(target.x - player.x, target.y - player.y)

        player.swing()
        juice.triggerShake(1.3f, 90)
        if (graphics.particles) {
            val centerX = player.x + player.facingX * 23f
            val centerY = player.y + player.facingY * 23f
            for (i in -2..2) {
                val angle = i * .35f
                val px = centerX + (-player.facingY * cos(angle) + player.facingX * sin(angle)) * 15f
                val py = centerY + (player.facingX * cos(angle) + player.facingY * sin(angle)) * 15f
                particles.add(Particle(px, py, player.facingX * 24f, player.facingY * 24f - 14f,
                    .22f, 0xFFFFDEA0.toInt(), 2.5f, 0f))
            }
        }

        if (style == WeaponStyle.PROJECTILE) {
            fireProjectile(weapon)
            return
        }

        val weaponPower = weapon?.let { (10f + it.damage) * 1.5f.pow((weaponStars[it] ?: 1) - 1) * (1f + ((weaponLevels[it] ?: 1) - 1) * 0.025f) } ?: 10f
        val damage = weaponPower * if (powerPotionTimer > 0f) 1.5f else 1f
        val range = when (style) {
            WeaponStyle.ARC -> 70f
            WeaponStyle.RING -> 64f
            else -> 54f
        }

        for (animal in animals) {
            if (inWeaponArc(animal, style, range)) damageCreature(animal, damage)
        }
        for (wolf in wolves) {
            if (inWeaponArc(wolf, style, range)) damageCreature(wolf, damage)
        }
        for (boss in bosses) {
            if (boss.defeated || !boss.engaged) continue
            if (inWeaponArc(boss, style, range)) damageCreature(boss, damage)
        }
        processDeaths()
    }

    private fun nearestEnemy(maxDistance: Float): Creature? {
        var best: Creature? = null
        var bestDistance = maxDistance
        for (animal in animals) {
            if (!animal.alive) continue
            val distance = hypot(
                (animal.x - player.x).toDouble(),
                (animal.y - player.y).toDouble(),
            ).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                best = animal
            }
        }
        for (wolf in wolves) {
            if (!wolf.alive) continue
            val distance = hypot(
                (wolf.x - player.x).toDouble(),
                (wolf.y - player.y).toDouble(),
            ).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                best = wolf
            }
        }
        for (boss in bosses) {
            if (boss.defeated || !boss.engaged) continue
            val distance = hypot(
                (boss.x - player.x).toDouble(),
                (boss.y - player.y).toDouble(),
            ).toFloat()
            if (distance < bestDistance) {
                bestDistance = distance
                best = boss
            }
        }
        return best
    }

    private fun inWeaponArc(creature: Creature, style: WeaponStyle, range: Float): Boolean {
        val dx = creature.x - player.x
        val dy = creature.y - player.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance > range + creature.radius) return false
        if (style == WeaponStyle.RING) return true
        if (distance < 1f) return true
        val dot = (dx * player.facingX + dy * player.facingY) / distance
        return if (style == WeaponStyle.ARC) dot > -0.2f else dot > 0.15f
    }

    private fun damageCreature(creature: Creature, damage: Float) {
        if (!creature.alive || campfires.any { it.isSafe(creature.x, creature.y) }) return
        creature.hurt(damage, player.x, player.y)
        juice.triggerHitstop(if (creature is Boss) 35 else 22)
        juice.triggerShake(if (creature is Boss) 3f else 1.7f, 120)
        damageText(creature.x, creature.y - creature.radius, damage)
        val color = when (creature) {
            is Boss -> creature.def.color
            is Wolf -> 0xFF767C86.toInt()
            else -> 0xFFC26262.toInt()
        }
        spawnParticles(creature.x, creature.y, color, 5)
    }

    private fun processDeaths() {
        val deadAnimals = animals.filter { !it.alive }
        for (animal in deadAnimals) {
            animals.remove(animal)
            killAnimal(animal)
        }
        val deadWolves = wolves.filter { !it.alive }
        for (wolf in deadWolves) {
            wolves.remove(wolf)
            killWolf(wolf)
        }
        for (boss in bosses) {
            if (!boss.defeated && boss.health <= 0f) defeatBoss(boss)
        }
    }

    private fun fireProjectile(weapon: ItemId?) {
        val speed = 560f
        val weaponPower = weapon?.let { (10f + it.damage) * 1.5f.pow((weaponStars[it] ?: 1) - 1) * (1f + ((weaponLevels[it] ?: 1) - 1) * 0.025f) } ?: 10f
        val damage = weaponPower * if (powerPotionTimer > 0f) 1.5f else 1f
        val color = if (weapon == ItemId.GUN) 0xFFFFD45A.toInt() else 0xFFD9C08A.toInt()
        projectiles.add(
            Projectile(
                x = player.x + player.facingX * 18f,
                y = player.y + player.facingY * 18f,
                vx = player.facingX * speed,
                vy = player.facingY * speed,
                damage = damage,
                color = color,
                pierces = weapon?.pierces == true,
                life = 1.1f,
            ),
        )
        spawnParticles(player.x + player.facingX * 20f, player.y + player.facingY * 20f, color, 5)
    }

    private fun updateProjectiles(dt: Float) {
        if (projectiles.isEmpty()) return
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            projectile.update(dt)

            var finished = projectile.dead
            if (!finished && world.isSolid(
                    world.tileX(projectile.x),
                    world.tileY(projectile.y),
                )
            ) {
                finished = true
            }

            if (!finished) {
                for (creature in allCreatures()) {
                    if (!creature.alive || projectile.hitCreatures.contains(creature)) continue
                    val distance = hypot(
                        (creature.x - projectile.x).toDouble(),
                        (creature.y - projectile.y).toDouble(),
                    ).toFloat()
                    if (distance > projectile.radius + creature.radius) continue
                    damageCreature(creature, projectile.damage)
                    projectile.hitCreatures.add(creature)
                    if (projectile.explosive) {
                        finished = true
                    } else if (!projectile.pierces) {
                        finished = true
                    }
                    break
                }
            }

            if (finished) {
                if (projectile.explosive && !projectile.dead) explode(projectile)
                iterator.remove()
            }
        }
        processDeaths()
    }

    private fun allCreatures(): List<Creature> {
        val list = ArrayList<Creature>(animals.size + wolves.size + bosses.size)
        list.addAll(animals)
        list.addAll(wolves)
        for (boss in bosses) if (!boss.defeated) list.add(boss)
        return list
    }

    private fun explode(projectile: Projectile) {
        if (projectile.aoeRadius <= 0f) return
        spawnParticles(projectile.x, projectile.y, projectile.color, 20)
        applySpellAoE(projectile.x, projectile.y, projectile.aoeRadius, projectile.damage, 0f, 0f)
    }

    private fun castSelectedSpell() { castSpell(lastSpell) }

    private fun castSpell(spell: Spell) {
        if (!player.isAlive) return
        val cost = magicCost(spell)
        if (spellCooldown > 0f) {
            toast("Spell is still recharging")
            return
        }
        if (player.stats.mana < cost) {
            toast("Not enough mana (${cost.toInt()} MP)")
            return
        }
        player.stats.mana -= cost
        spellCooldown = 0.85f
        juice.triggerShake(1.8f, 130)
        spawnParticles(player.x, player.y, spell.color, 12)

        val target = nearestEnemy(20f * Config.TILE)
        if (target != null) player.faceTowards(target.x - player.x, target.y - player.y)

        when (spell) {
            Spell.FIREBALL -> {
                projectiles.add(
                    Projectile(
                        x = player.x + player.facingX * 18f,
                        y = player.y + player.facingY * 18f,
                        vx = player.facingX * 520f,
                        vy = player.facingY * 520f,
                        damage = spell.damage,
                        color = spell.color,
                        radius = 9f,
                        explosive = true,
                        aoeRadius = 95f,
                        life = 1.4f,
                    ),
                )
            }

            Spell.ICE_FREEZE -> {
                val centerX = target?.x ?: (player.x + player.facingX * 140f)
                val centerY = target?.y ?: (player.y + player.facingY * 140f)
                spawnParticles(centerX, centerY, spell.color, 24)
                applySpellAoE(centerX, centerY, 115f, spell.damage, freeze = 2.6f, knockback = 0f)
            }

            Spell.WATER_TSUNAMI -> {
                for (creature in allCreatures()) {
                    if (!creature.alive) continue
                    val dx = creature.x - player.x
                    val dy = creature.y - player.y
                    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (distance > 280f + creature.radius || distance < 1f) continue
                    val dot = (dx * player.facingX + dy * player.facingY) / distance
                    if (dot < 0.35f) continue
                    damageCreature(creature, spell.damage)
                    creature.applyImpulse(dx, dy, 180f)
                }
                for (i in 1..8) {
                    spawnParticles(
                        player.x + player.facingX * i * 28f + (Random.nextFloat() - 0.5f) * 40f,
                        player.y + player.facingY * i * 28f + (Random.nextFloat() - 0.5f) * 40f,
                        spell.color,
                        5,
                    )
                }
                processDeaths()
            }

            Spell.WIND_TORNADO -> {
                spawnParticles(player.x, player.y, spell.color, 30)
                applySpellAoE(player.x, player.y, 150f, spell.damage, 0f, knockback = 220f)
            }
        }

        spawnParticles(player.x, player.y, spell.color, 12)
        toast("${spell.displayName}  -${cost.toInt()} MP")
    }

    private fun applySpellAoE(
        x: Float,
        y: Float,
        radius: Float,
        damage: Float,
        freeze: Float,
        knockback: Float,
    ) {
        for (creature in allCreatures()) {
            if (!creature.alive) continue
            val dx = creature.x - x
            val dy = creature.y - y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (distance > radius + creature.radius) continue
            damageCreature(creature, damage)
            if (freeze > 0f) creature.freezeTimer = max(creature.freezeTimer, freeze)
            if (knockback > 0f) creature.applyImpulse(dx, dy, knockback)
        }
        processDeaths()
    }

    private fun killAnimal(animal: Animal) {
        when (animal.species) {
            AnimalSpecies.RABBIT -> {
                addXp(8)
                player.stats.heal(6f)
                player.stats.restoreMana(6f)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 1)
                if (Random.nextFloat() < 0.6f) dropLoot(animal.x, animal.y, ItemId.HIDE, 1)
            }

            AnimalSpecies.DEER -> {
                addXp(14)
                player.stats.heal(10f)
                player.stats.restoreMana(8f)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 2)
                dropLoot(animal.x, animal.y, ItemId.HIDE, 1 + Random.nextInt(2))
            }

            AnimalSpecies.BOAR -> {
                addXp(22)
                player.stats.heal(14f)
                player.stats.restoreMana(10f)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 3)
                dropLoot(animal.x, animal.y, ItemId.HIDE, 2)
            }
        }
        spawnParticles(animal.x, animal.y, 0xFFB5793F.toInt(), 8)
    }

    private fun killWolf(wolf: Wolf) {
        addXp(32)
        player.stats.heal(5f)
        player.stats.restoreMana(10f)
        dropLoot(wolf.x, wolf.y, ItemId.HIDE, 2)
        dropLoot(wolf.x, wolf.y, ItemId.RAW_MEAT, 1 + Random.nextInt(2))
        if (Random.nextFloat() < 0.35f) dropLoot(wolf.x, wolf.y, ItemId.MAGIC_STONE, 1)
        if (Random.nextFloat() < 0.18f) dropLoot(wolf.x, wolf.y, ItemId.MANA_POTION, 1)
        if (Random.nextFloat() < 0.12f) dropLoot(wolf.x, wolf.y, ItemId.POWER_POTION, 1)
        if (Random.nextFloat() < 0.08f) dropLoot(wolf.x, wolf.y, ItemId.REGEN_POTION, 1)
        spawnParticles(wolf.x, wolf.y, 0xFF767C86.toInt(), 10)
    }

    private fun defeatBoss(boss: Boss) {
        if (boss.defeated) return
        boss.markDefeated()
        activeBoss = null
        defeatedBosses.add(boss.def.id)

        val stats = player.stats
        stats.addXp(boss.def.xpReward)
        stats.grantBossReward(boss.def.healthReward, boss.def.manaReward)
        stats.restoreMana(60f)
        for ((id, count) in boss.def.loot) giveItem(id, scaledLootCount(count))
        for (material in ItemId.values().filter { it.category == ItemCategory.MATERIAL || it == ItemId.MAGIC_STONE }) {
            giveItem(material, 1)
            dropLoot(boss.x, boss.y, material, 1)
        }
        val newLevel = stage + defeatedBosses.size
        for (other in bosses) if (!other.defeated) other.setLevel(newLevel)

        banner("${boss.def.name.uppercase()} DEFEATED")
        toast("+${boss.def.xpReward} XP   MAX HP +${boss.def.healthReward.toInt()}   MAX MANA +${boss.def.manaReward.toInt()}")
        spawnParticles(boss.x, boss.y, boss.def.color, 28)
        if (defeatedBosses.size == Bosses.ALL.size) stageAdvancePending = true else saveNow()
    }

    private fun advanceStage() {
        stageAdvancePending = false
        stage += 1
        victoryTimer = 4f
        banner("VICTORY! LEVEL $stage")
        defeatedBosses.clear()
        bosses.clear()
        for (def in Bosses.ALL) bosses.add(Boss(def).also { it.setLevel(stage) })
        wolves.clear()
        projectiles.clear()
        openedChests.clear()
        for (chest in chests) chest.opened = false
        player.x = respawnX; player.y = respawnY
        player.stats.health = player.stats.maxHealth
        player.stats.mana = player.stats.maxMana
        camera.snapTo(player.x, player.y)
        saveNow()
    }

    fun onBossEngaged(boss: Boss) {
        activeBoss = boss
        banner("${boss.def.name.uppercase()} AWAKENS")
        toast("The arena is sealed — defeat the boss to escape")
    }

    private fun clampToArena() {
        val boss = activeBoss ?: return
        if (!boss.engaged || boss.defeated) return
        val dx = player.x - boss.arenaX
        val dy = player.y - boss.arenaY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val limit = boss.arenaRadius - 12f
        if (distance > limit && distance > 0.01f) {
            player.x = boss.arenaX + dx / distance * limit
            player.y = boss.arenaY + dy / distance * limit
        }
    }

    private fun hapticHit() {
        if (!graphics.vibration) return
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(android.os.VibrationEffect.createOneShot(18, 35))
            else vibrator?.vibrate(18)
        }
    }

    fun hurtPlayer(amount: Float, sourceX: Float, sourceY: Float) {
        if (deathTimer > 0f || !player.isAlive) return
        if (player.takeDamage(amount)) {
            juice.triggerShake(2.2f, 120)
            hapticHit()
            spawnParticles(player.x, player.y, 0xFFD9534F.toInt(), 5)
            if (!player.isAlive) startDeath()
        }
    }

    private fun startDeath() {
        deathTimer = deathDuration
        activeBoss?.resetFight()
        activeBoss = null
        input.closePanels()
    }

    private fun respawn() {
        player.x = respawnX
        player.y = respawnY
        player.grantSpawnProtection()

        val stats = player.stats
        stats.health = stats.maxHealth * 0.65f
        stats.mana = stats.maxMana * 0.5f
        stats.hunger = max(stats.hunger, 60f)

        camera.snapTo(player.x, player.y)
        deathTimer = 0f
        toast("Respawned at the campfire")
    }

    // ---- Interaction -------------------------------------------------------

    fun interactionPrompt(): String? {
        if (deathTimer > 0f || !player.isAlive) return null
        val chest = nearestChest(52f)
        if (chest != null) return if (chest.opened) null else "OPEN CHEST"
        val fire = nearestCampfire(56f)
        if (fire != null) return "REST AT CAMPFIRE"
        val node = harvestTarget()
        if (node != null) {
            return when (world.tileAt(node.x, node.y)) {
                TileType.TREE, TileType.FOREST_TREE -> "CHOP TREE"
                TileType.ROCK -> "MINE ROCK"
                TileType.FLOWERS -> "PICK FRUIT"
                else -> null
            }
        }
        return null
    }

    private fun interact() {
        val chest = nearestChest(52f)
        if (chest != null && !chest.opened) {
            openChest(chest)
            return
        }
        val fire = nearestCampfire(56f)
        if (fire != null) {
            respawnX = fire.x
            respawnY = fire.y
            restAtCampfire(fire)
            input.campMenuOpen = true
            return
        }
        val node = harvestTarget()
        if (node != null) {
            harvest(node)
            return
        }
        toast("Nothing to interact with")
    }

    private fun harvestTarget(): TilePos? {
        val ptx = floor(player.x / Config.TILE).toInt()
        val pty = floor(player.y / Config.TILE).toInt()
        var best: TilePos? = null
        var bestDistance = Float.MAX_VALUE
        for (dy in -2..2) {
            for (dx in -2..2) {
                val tx = ptx + dx
                val ty = pty + dy
                val type = world.tileAt(tx, ty)
                if (type != TileType.TREE && type != TileType.FOREST_TREE &&
                    type != TileType.ROCK && type != TileType.FLOWERS
                ) {
                    continue
                }
                val cx = (tx + 0.5f) * Config.TILE
                val cy = (ty + 0.5f) * Config.TILE
                val distance = hypot(
                    (cx - player.x).toDouble(),
                    (cy - player.y).toDouble(),
                ).toFloat()
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = TilePos(tx, ty)
                }
            }
        }
        return best
    }

    private fun harvest(node: TilePos) {
        val type = world.tileAt(node.x, node.y)
        val cx = (node.x + 0.5f) * Config.TILE
        val cy = (node.y + 0.5f) * Config.TILE
        when (type) {
            TileType.TREE, TileType.FOREST_TREE -> {
                giveItem(ItemId.WOOD, 2 + Random.nextInt(2))
                if (Random.nextFloat() < 0.45f) giveItem(ItemId.FIBER, 1)
                world.setTile(
                    node.x,
                    node.y,
                    if (type == TileType.TREE) TileType.GRASS else TileType.FOREST_FLOOR,
                )
                spawnParticles(cx, cy, 0xFF8A6A43.toInt(), 10)
            }

            TileType.ROCK -> {
                giveItem(ItemId.STONE, 2 + Random.nextInt(2))
                if (Random.nextFloat() < 0.35f) giveItem(ItemId.IRON, 1)
                if (Random.nextFloat() < 0.12f) giveItem(ItemId.MAGIC_STONE, 1)
                world.setTile(node.x, node.y, TileType.GRASS)
                spawnParticles(cx, cy, 0xFF8C8C94.toInt(), 10)
            }

            TileType.FLOWERS -> {
                giveItem(ItemId.FRUIT, 1 + Random.nextInt(2))
                world.setTile(node.x, node.y, TileType.GRASS)
                spawnParticles(cx, cy, 0xFFD95F6A.toInt(), 8)
            }

            else -> return
        }
        addXp(2)
    }

    private fun nearestChest(range: Float): LootChest? {
        var best: LootChest? = null
        var bestDistance = range
        for (chest in chests) {
            val distance = hypot(
                (chest.x - player.x).toDouble(),
                (chest.y - player.y).toDouble(),
            ).toFloat()
            if (distance <= bestDistance) {
                bestDistance = distance
                best = chest
            }
        }
        return best
    }

    private fun nearestCampfire(range: Float): Campfire? {
        var best: Campfire? = null
        var bestDistance = range
        for (fire in campfires) {
            val distance = hypot(
                (fire.x - player.x).toDouble(),
                (fire.y - player.y).toDouble(),
            ).toFloat()
            if (distance <= bestDistance) {
                bestDistance = distance
                best = fire
            }
        }
        return best
    }

    private fun openChest(chest: LootChest) {
        chest.opened = true
        openedChests.add(chest.id)

        val random = Random(chest.id * 7919 + world.seed)
        val pool = listOf(
            ItemId.WOOD,
            ItemId.STONE,
            ItemId.FIBER,
            ItemId.IRON,
            ItemId.RAW_MEAT,
            ItemId.ELIXIR,
            ItemId.MAGIC_STONE,
        )
        repeat(2 + random.nextInt(2)) {
            val id = pool[random.nextInt(pool.size)]
            val count = 1 + random.nextInt(if (id == ItemId.ELIXIR) 2 else 5)
            giveItem(id, count)
        }
        if (chest.id % 3 == 1) giveItem(ItemId.WOODEN_SWORD, 1)
        if (chest.id == 0) giveItem(ItemId.MAGIC_STONE, 1)
        if (chest.id == 5) giveItem(ItemId.IRON_SWORD, 1)

        banner("CHEST OPENED")
        spawnParticles(chest.x, chest.y, 0xFFE8C85A.toInt(), 14)
    }

    private fun restAtCampfire(fire: Campfire) {
        respawnX = fire.x
        respawnY = fire.y
        val stats = player.stats
        stats.health = stats.maxHealth
        stats.mana = stats.maxMana
        stats.hunger = 100f
        banner("CHECKPOINT SET")
        toast("Health and mana restored · progress saved")
        spawnParticles(fire.x, fire.y, 0xFFFFA64C.toInt(), 12)
        saveNow()
    }

    // ---- Entities ----------------------------------------------------------

    private fun updateCreatures(dt: Float) {
        for (animal in animals) {
            if (campfires.any { it.isSafe(animal.x, animal.y) }) { animal.calm(); continue }
            animal.update(dt, this)
        }
        for (wolf in wolves) {
            val inSafeCamp = campfires.any { it.isSafe(wolf.x, wolf.y) }
            if (inSafeCamp) { wolf.disengage(); wolf.calm(); continue }
            wolf.update(dt, this)
        }
        wolves.removeAll { wolf -> campfires.any { it.isSafe(wolf.x, wolf.y) } }
        val playerInCamp = campfires.any { it.isSafe(player.x, player.y) }
        if (playerInCamp) {
            for (boss in bosses) if (!boss.defeated && boss.engaged) boss.resetFight()
            activeBoss = null
        }

        for (boss in bosses) {
            if (boss.defeated) continue
            if (playerInCamp) continue
            if (campfires.any { it.isSafe(boss.x, boss.y) } && !boss.engaged) { boss.calm(); continue }
            val distance = hypot(
                (boss.x - player.x).toDouble(),
                (boss.y - player.y).toDouble(),
            ).toFloat()
            if (boss.engaged || (distance < 90f * Config.TILE && campfires.none { it.isSafe(boss.x, boss.y) })) {
                boss.update(dt, this)
            }
            if (boss.health <= 0f) defeatBoss(boss)
        }

        val despawnDistance = 65f * Config.TILE
        animals.removeAll { animal ->
            val distance = hypot(
                (animal.x - player.x).toDouble(),
                (animal.y - player.y).toDouble(),
            ).toFloat()
            !animal.alive || distance > despawnDistance
        }
        wolves.removeAll { wolf ->
            val distance = hypot(
                (wolf.x - player.x).toDouble(),
                (wolf.y - player.y).toDouble(),
            ).toFloat()
            !wolf.alive || distance > despawnDistance
        }
    }

    private fun updateEffects(dt: Float) {
        fireParticleTimer -= dt
        if (graphics.particles && fireParticleTimer <= 0f) {
            fireParticleTimer = .12f
            for (fire in campfires) {
                if (!onScreen(fire.x, fire.y)) continue
                val smoke = Random.nextFloat() < .45f
                particles.add(Particle(fire.x + (Random.nextFloat()-.5f)*12f, fire.y - 16f,
                    (Random.nextFloat()-.5f)*12f, -42f-Random.nextFloat()*25f,
                    if (smoke) .75f else .4f,
                    if (smoke) 0xFF9A9B91.toInt() else 0xFFFFB34F.toInt(),
                    if (smoke) 3f else 2f, -20f))
            }
            particles.cap(200)
        }
        val textIterator = texts.iterator()
        while (textIterator.hasNext()) {
            if (!textIterator.next().update(dt)) textIterator.remove()
        }

        for (particle in particles) particle.update(dt)
        particles.removeAll { it.life <= 0f }

        val pickupIterator = pickups.iterator()
        while (pickupIterator.hasNext()) {
            val pickup = pickupIterator.next()
            pickup.update(dt)
            val distance = hypot(
                (pickup.x - player.x).toDouble(),
                (pickup.y - player.y).toDouble(),
            ).toFloat()
            if (player.isAlive && distance < 34f) {
                val leftover = inventory.add(pickup.item, pickup.count)
                if (leftover == 0) {
                    texts.add(
                        FloatingText(
                            player.x,
                            player.y - 34f,
                            "+${pickup.count} ${pickup.item.displayName}",
                            0xFFFFE08A.toInt(),
                            1.2f,
                        ),
                    )
                    pickupIterator.remove()
                } else {
                    pickup.count = leftover
                }
            }
        }
        pickups.removeAll { it.life <= 0f }

        for (fire in campfires) fire.update(dt)
        for (chest in chests) chest.update(dt)
    }

    private fun updateSpawns(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer > 0f) return
        spawnTimer = 2.4f

        if (animals.size < 7 && Random.nextFloat() < 0.85f) {
            spawnAnimal()
        }
        if (wolves.size < 3 && Random.nextFloat() < 0.55f) {
            spawnWolf()
        }
    }

    private fun spawnAnimal() {
        val point = randomSpawnPoint(12f, 24f) ?: return
        val roll = Random.nextFloat()
        val species = when {
            roll < 0.45f -> AnimalSpecies.RABBIT
            roll < 0.85f -> AnimalSpecies.DEER
            else -> AnimalSpecies.BOAR
        }
        animals.add(Animal(species, point.first, point.second))
    }

    private fun spawnWolf() {
        val point = randomSpawnPoint(16f, 28f) ?: return
        wolves.add(Wolf(point.first, point.second, stage))
    }

    private fun randomSpawnPoint(minTiles: Float, maxTiles: Float): Pair<Float, Float>? {
        repeat(12) {
            val angle = Random.nextFloat() * (PI * 2).toFloat()
            val distance = (minTiles + Random.nextFloat() * (maxTiles - minTiles)) * Config.TILE
            val x = player.x + cos(angle) * distance
            val y = player.y + sin(angle) * distance
            val tileX = world.tileX(x)
            val tileY = world.tileY(y)
            if (!world.isSolid(tileX, tileY) && campfires.none { it.isSafe(x, y) }) return x to y
        }
        return null
    }

    private fun addXp(amount: Int) {
        val levels = player.stats.addXp(amount)
        if (levels > 0) {
            banner("LEVEL ${player.stats.level}")
            spawnParticles(player.x, player.y, 0xFFE8C85A.toInt(), 18)
        }
    }

    private fun giveItem(id: ItemId, count: Int) {
        val leftover = inventory.add(id, count)
        if (leftover > 0) {
            pickups.add(Pickup(id, leftover, player.x + 12f, player.y + 6f))
        }
        texts.add(
            FloatingText(
                player.x,
                player.y - 40f,
                "+$count ${id.displayName}",
                0xFFFFE08A.toInt(),
                1.4f,
            ),
        )
    }

    private fun dropLoot(x: Float, y: Float, id: ItemId, count: Int) {
        val adjustedCount = scaledLootCount(count)
        pickups.add(
            Pickup(
                id,
                adjustedCount,
                x + (Random.nextFloat() - 0.5f) * 18f,
                y + (Random.nextFloat() - 0.5f) * 18f,
            ),
        )
    }

    private fun spawnParticles(x: Float, y: Float, color: Int, count: Int) {
        if (!graphics.particles) return
        val scaled = (count * graphics.particleScale()).toInt().coerceAtLeast(1)
        repeat(scaled) {
            val angle = Random.nextFloat() * (PI * 2).toFloat()
            val speed = 30f + Random.nextFloat() * 70f
            particles.add(
                Particle(
                    x,
                    y,
                    cos(angle) * speed,
                    sin(angle) * speed - 30f,
                    0.35f + Random.nextFloat() * 0.4f,
                    color,
                    2f + Random.nextFloat() * 2f,
                ),
            )
        }
        particles.cap(200)
    }

    private fun damageText(x: Float, y: Float, damage: Float) {
        texts.add(juice.spawnFloatingText("-${damage.toInt()} HP", x, y, 0xFFFFD565.toInt()))
    }

    private fun banner(text: String) {
        bossBannerText = text
        bossBannerTimer = 2.6f
    }

    private fun toast(text: String) {
        toastText = text
        toastTimer = 2.8f
    }

    // ---- Draw --------------------------------------------------------------

    fun render(canvas: Canvas) {
        val width = screenWidth.toFloat()
        val height = screenHeight.toFloat()
        if (width <= 0f || height <= 0f) return

        worldRenderer.configure(graphics)
        creatureRenderer.configure(graphics)

        canvas.drawColor(0xFF0B100E.toInt())

        canvas.save()
        val shake = juice.shakeOffset()
        canvas.translate(width * 0.5f + shake.x, height * 0.5f + shake.y)
        canvas.scale(camera.zoom, camera.zoom)
        val cameraIso = IsoMath.worldToIso(camera.x, camera.y)
        canvas.translate(-cameraIso.x, -cameraIso.y)

        worldRenderer.drawTiles(canvas, world, camera, width, height, elapsedSeconds)

        drawArenaSeal(canvas)

        val renderables = ArrayList<Renderable>()
        fun add(x: Float, y: Float, action: () -> Unit) {
            if (onScreen(x, y)) renderables.add(Renderable((x + y) / Config.TILE,
                IsoMath.worldToIso(x, y).x, action))
        }
        for (prop in worldRenderer.visibleProps(world, camera, width, height)) {
            add(prop.x, prop.y) { drawAtIso(canvas, prop.x, prop.y) {
                worldRenderer.drawProp(canvas, prop.type, prop.x, prop.y)
            } }
        }
        for (fire in campfires) add(fire.x, fire.y) { drawAtIso(canvas, fire.x, fire.y) { objectRenderer.drawCampfire(canvas, fire, graphics) } }
        for (chest in chests) add(chest.x, chest.y) { drawAtIso(canvas, chest.x, chest.y) { objectRenderer.drawChest(canvas, chest) } }
        for (pickup in pickups) add(pickup.x, pickup.y) { drawAtIso(canvas, pickup.x, pickup.y) { objectRenderer.drawPickup(canvas, pickup) } }
        for (animal in animals) add(animal.x, animal.y) { drawAtIso(canvas, animal.x, animal.y) { creatureRenderer.drawAnimal(canvas, animal, graphics) } }
        for (wolf in wolves) add(wolf.x, wolf.y) { drawAtIso(canvas, wolf.x, wolf.y) { creatureRenderer.drawWolf(canvas, wolf) } }
        for (boss in bosses) if (!boss.defeated) add(boss.x, boss.y) { drawAtIso(canvas, boss.x, boss.y) { creatureRenderer.drawBoss(canvas, boss, graphics, elapsedSeconds) } }
        add(player.x, player.y) { drawAtIso(canvas, player.x, player.y) { playerRenderer.draw(canvas, player, elapsedSeconds) } }
        for (projectile in projectiles) add(projectile.x, projectile.y) {
            arenaPaint.style = Paint.Style.STROKE; arenaPaint.strokeWidth = 3f; arenaPaint.color = projectile.color
            val head = IsoMath.worldToIso(projectile.x, projectile.y)
            val tail = IsoMath.worldToIso(projectile.x - projectile.vx * .018f, projectile.y - projectile.vy * .018f)
            canvas.drawLine(head.x, head.y, tail.x, tail.y, arenaPaint)
            arenaPaint.style = Paint.Style.FILL
            canvas.drawCircle(head.x, head.y, projectile.radius * .55f, arenaPaint)
        }
        renderables.sortWith(compareBy<Renderable> { it.gridDepth }.thenBy { it.isoX })
        for (item in renderables) item.draw()
        for (text in texts) drawAtIso(canvas, text.x, text.y) { objectRenderer.drawFloatingText(canvas, text) }
        for (particle in particles) drawAtIso(canvas, particle.x, particle.y) { objectRenderer.drawParticle(canvas, particle) }

        canvas.restore()

        worldRenderer.drawNight(canvas, camera, player, campfires, minutes, width, height)
        hud.draw(canvas, this, input, width, height)
    }

    private fun scaledLootCount(count: Int): Int {
        val bonus = if (stage >= 2) 1.2f + Random.nextFloat() * .8f else 1f
        return max(1, kotlin.math.ceil(count * bonus).toInt())
    }

    private inline fun drawAtIso(canvas: Canvas, worldX: Float, worldY: Float, draw: () -> Unit) {
        val iso = IsoMath.worldToIso(worldX, worldY)
        canvas.save()
        canvas.translate(iso.x - worldX, iso.y - worldY)
        draw()
        canvas.restore()
    }

    private fun drawArenaSeal(canvas: Canvas) {
        val boss = activeBoss ?: return
        if (!boss.engaged || boss.defeated) return
        arenaPaint.style = Paint.Style.STROKE
        arenaPaint.strokeWidth = 5f
        arenaPaint.color = 0x66FF4A3A
        val ring = android.graphics.Path()
        for (i in 0..48) {
            val angle = i * (PI * 2).toFloat() / 48f
            val point = IsoMath.worldToIso(boss.arenaX + cos(angle) * (boss.arenaRadius - 8f),
                boss.arenaY + sin(angle) * (boss.arenaRadius - 8f))
            if (i == 0) ring.moveTo(point.x, point.y) else ring.lineTo(point.x, point.y)
        }
        canvas.drawPath(ring, arenaPaint)
        arenaPaint.style = Paint.Style.FILL
    }

    private fun onScreen(worldX: Float, worldY: Float): Boolean {
        val point = IsoMath.worldToIso(worldX, worldY)
        val center = IsoMath.worldToIso(camera.x, camera.y)
        val halfW = screenWidth / (2f * camera.zoom) + 80f
        val halfH = screenHeight / (2f * camera.zoom) + 80f
        return abs(point.x - center.x) <= halfW && abs(point.y - center.y) <= halfH
    }

    // ---- Persistence -------------------------------------------------------

    private fun upgradeWeapon() {
        val weapon = equippedWeapon ?: activeWeapon() ?: run { toast("Equip a weapon first"); return }
        if (equippedWeapon == null) { toast("Equip a weapon at the campfire first"); return }
        val level = weaponLevels[weapon] ?: 1
        if (level >= 50) { toast("Weapon is max level"); return }
        val cost = level * (if ((weaponStars[weapon] ?: 1) > 1) 3 else 2)
        if (inventory.count(ItemId.IRON) < cost) { toast("Need $cost Iron Ore"); return }
        inventory.remove(ItemId.IRON, cost)
        weaponLevels[weapon] = level + 1
        toast("${weapon.displayName} upgraded to level ${level + 1}")
    }

    private fun combineWeapon() {
        val weapon = equippedWeapon ?: activeWeapon() ?: run { toast("Equip a weapon first"); return }
        if (equippedWeapon == null) { toast("Equip a weapon at the campfire first"); return }
        val stars = weaponStars[weapon] ?: 1
        if (stars >= 5) { toast("Weapon is already 5 stars"); return }
        val copies = inventory.count(weapon)
        if (copies < 2) { toast("Need a duplicate ${weapon.displayName}"); return }
        inventory.remove(weapon, 2)
        inventory.add(weapon, 1)
        weaponStars[weapon] = stars + 1
        equippedWeapon = weapon
        toast("${weapon.displayName} upgraded to ${stars + 1} stars!")
    }

    private fun loadSavedState() {
        val state = saveManager.load() ?: run { toast("No save found"); return }
        if (state.seed != world.seed) { toast("This save belongs to another world"); return }
        player.x = state.x; player.y = state.y
        player.stats.level = state.level; player.stats.xp = state.xp
        player.stats.maxHealth = state.maxHealth; player.stats.maxMana = state.maxMana
        player.stats.bonusHealth = state.bonusHealth; player.stats.bonusMana = state.bonusMana
        defeatedBosses.clear(); defeatedBosses.addAll(state.defeatedBosses)
        stage = state.stage
        bosses.clear(); for (def in Bosses.ALL) bosses.add(Boss(def).also { it.setLevel(stage + defeatedBosses.size); if (defeatedBosses.contains(def.id)) it.markDefeated() })
        openedChests.clear(); openedChests.addAll(state.openedChests)
        for (chest in chests) chest.opened = openedChests.contains(chest.id)
        weaponLevels.clear(); state.weaponLevels.forEach { (name, value) -> runCatching { ItemId.valueOf(name) }.getOrNull()?.let { weaponLevels[it] = value } }
        weaponStars.clear(); state.weaponStars.forEach { (name, value) -> runCatching { ItemId.valueOf(name) }.getOrNull()?.let { weaponStars[it] = value } }
        minutes = state.minutes; day = state.day
        graphics.quality = state.graphics.quality
        graphics.smoothTextures = state.graphics.smoothTextures
        graphics.antiAliasing = state.graphics.antiAliasing
        graphics.particles = state.graphics.particles
        graphics.music = state.graphics.music
        graphics.sound = state.graphics.sound
        graphics.vibration = state.graphics.vibration
        graphics.resolutionScale = state.graphics.resolutionScale
        graphics.targetFps = state.graphics.targetFps
        input.pushAudioState(graphics.music, graphics.sound)
        player.stats.health = state.health.coerceIn(1f, player.stats.maxHealth)
        player.stats.mana = state.mana.coerceIn(0f, player.stats.maxMana)
        player.stats.hunger = state.hunger.coerceIn(0f, 100f)
        inventory.clear(); state.inventory.forEach { (id, count) -> inventory.add(id, count) }
        equippedWeapon = state.equippedWeapon?.let { runCatching { ItemId.valueOf(it) }.getOrNull() }
        camera.snapTo(player.x, player.y)
    }

    fun saveNow() {
        val items = mutableListOf<Pair<ItemId, Int>>()
        for (stack in inventory.allStacks()) {
            if (stack != null) items.add(stack.id to stack.count)
        }
        saveManager.save(
            SaveState(
                seed = world.seed,
                x = player.x,
                y = player.y,
                minutes = minutes,
                day = day,
                stage = stage,
                level = player.stats.level,
                xp = player.stats.xp,
                maxHealth = player.stats.maxHealth,
                health = player.stats.health,
                maxMana = player.stats.maxMana,
                mana = player.stats.mana,
                hunger = player.stats.hunger,
                bonusHealth = player.stats.bonusHealth,
                bonusMana = player.stats.bonusMana,
                inventory = items,
                campfireX = respawnX,
                campfireY = respawnY,
                defeatedBosses = defeatedBosses.toSet(),
                openedChests = openedChests.toSet(),
                equippedWeapon = equippedWeapon?.name,
                weaponLevels = weaponLevels.mapKeys { it.key.name },
                weaponStars = weaponStars.mapKeys { it.key.name },
                graphics = graphics,
            ),
        )
    }
}
