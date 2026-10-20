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
import com.emberfall.game.entity.ProjectileKind
import com.emberfall.game.entity.Player
import com.emberfall.game.entity.BossSignatures
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
import com.emberfall.game.util.hypot
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
    val inventory = Inventory(36)
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
    var meleeWeapon: ItemId? = null
        private set
    var rangedWeapon: ItemId? = null
        private set
    var lastSpell = Spell.FIREBALL
        private set

    private val defeatedBosses = mutableSetOf<String>()
    val bossAttackCount: Int get() = (defeatedBosses.size + 1).coerceIn(1, 5)
    private var footstepTimer = 0f
    fun bossMagic(x: Float, y: Float, damage: Float) {
        spawnParticles(x, y, 0xFFCE8AFF.toInt(), 24)
        if (hypot(player.x - x, player.y - y) < 65f) hurtPlayer(damage, x, y)
    }
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
    private var dragonSpawnTimer = -1f

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

    private var worldRenderer = WorldRenderer(context)
    private var playerRenderer = PlayerRenderer(context)
    private var creatureRenderer = CreatureRenderer(context)
    private val objectRenderer = ObjectRenderer()
    private val hud = HudRenderer(context)
    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val juice = JuiceManager()

    private var biomeTimer = 0f
    private var saveTimer = 0f
    private var spawnTimer = 0f
    var elapsedSeconds = 0f
    private var fireParticleTimer = 0f
    private var resourceTimer = 240f
    private var deathDuration = 1.8f
    val weaponLevels = mutableMapOf<ItemId, Int>()
    val weaponStars = mutableMapOf<ItemId, Int>()
    val spellLevels = mutableMapOf<Spell, Int>()

    private var spellCooldown = 0f
    private var powerPotionTimer = 0f
    private var regenPotionTimer = 0f
    private var rapidFireTimer = 0f

    /** Recreates render-only objects after an atomically activated art pack. */
    fun reloadVisuals() {
        worldRenderer = WorldRenderer(context)
        playerRenderer = PlayerRenderer(context)
        creatureRenderer = CreatureRenderer(context)
    }

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
        val dragonSaved = defeatedBosses.size == Bosses.ALL.size
        if (dragonSaved) dragonSpawnTimer = 15f

        val fireSpot = dryCampSpotNear(world.spawn.x + 12, world.spawn.y + 12)
        campfires.add(Campfire(0, fireSpot.first, fireSpot.second))
        if (restored == null) {
            respawnX = fireSpot.first
            respawnY = fireSpot.second
        }
        for (index in 1 until WorldPoints.campfires.size) {
            val point = WorldPoints.campfires[index]
            val spot = dryCampSpotNear(point.tileX, point.tileY)
            campfires.add(Campfire(index, spot.first, spot.second))
        }

        if (restored != null) {
            val checkpoint = campfires.minByOrNull { hypot(it.x - respawnX, it.y - respawnY) }!!
            respawnX = checkpoint.x
            respawnY = checkpoint.y
        }

        if (Bosses.ALL.any { hypot(respawnX-it.tileX*Config.TILE, respawnY-it.tileY*Config.TILE) < (it.arenaRadiusTiles+5f)*Config.TILE }) {
            val checkpoint = campfires.minByOrNull { hypot(it.x-respawnX,it.y-respawnY) }!!
            respawnX = checkpoint.x; respawnY = checkpoint.y
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
        equippedWeapon?.let { if (it.style == WeaponStyle.PROJECTILE) rangedWeapon = it else meleeWeapon = it }

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

    private fun dryCampSpotNear(tileX: Int, tileY: Int): Pair<Float, Float> {
        for (radius in 0..32) {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (max(abs(dx), abs(dy)) != radius) continue
                val tx = tileX + dx
                val ty = tileY + dy
                if (world.isSolid(tx, ty)) continue
                val touchesWater = (-1..1).any { oy -> (-1..1).any { ox ->
                    val tile = world.tileAt(tx + ox, ty + oy)
                    tile == TileType.WATER || tile == TileType.DEEP_WATER
                } }
                if (!touchesWater) return (tx + .5f) * Config.TILE to (ty + .5f) * Config.TILE
            }
        }
        return (world.spawn.x + .5f) * Config.TILE to (world.spawn.y + .5f) * Config.TILE
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
        rapidFireTimer = max(0f, rapidFireTimer - dt)
        if (input.attackDown && rangedWeapon == ItemId.GUN && rapidFireTimer <= 0f) {
            rapidFireTimer = .14f
            fireProjectile(ItemId.GUN)
        }
        if (input.consumeMagicRequest()) castSelectedSpell()
        val interactRequested = input.consumeInteractRequest()

        val screenMoveX = input.moveX
        val screenMoveY = input.moveY
        val gridMoveX = screenMoveX * .5f + screenMoveY
        val gridMoveY = screenMoveY - screenMoveX * .5f
        val gridMoveLength = hypot(gridMoveX.toDouble(), gridMoveY.toDouble()).toFloat()
        val screenMoveLength = hypot(screenMoveX.toDouble(), screenMoveY.toDouble()).toFloat()
        val moveScale = if (gridMoveLength > .001f) screenMoveLength / gridMoveLength else 0f
        val oldX = player.x; val oldY = player.y
        player.update(dt, gridMoveX * moveScale, gridMoveY * moveScale, input.run, world)
        footstepTimer -= dt
        if (hypot(player.x-oldX, player.y-oldY) > .05f) {
            worldRenderer.bendGrass(world.tileX(player.x), world.tileY(player.y), elapsedSeconds)
            if (graphics.particles && footstepTimer <= 0f) {
                footstepTimer = .075f
                repeat(2) { particles.add(Particle(oldX, oldY, (Random.nextFloat()-.5f)*20f,
                    (Random.nextFloat()-.5f)*20f, .3f, 0xFFB3BD80.toInt(), 3f, 0f, shrink = true)) }
                particles.cap(200)
            }
        }
        player.stats.update(dt)
        clampToArena()

        if (interactRequested) interact()

        val tileX = floor(player.x / Config.TILE).toInt()
        val tileY = floor(player.y / Config.TILE).toInt()
        world.ensureAround(tileX, tileY)
        world.pruneAround(tileX, tileY)

        camera.update(player.x, player.y, player.facingX, player.facingY, dt)

        updateDragonSpawn(dt)
        updateCreatures(dt)
        updateProjectiles(dt)
        if (stageAdvancePending && bosses.none { it.isDragon && elapsedSeconds - it.defeatedAtSeconds < 1f }) advanceStage()
        updateEffects(dt)
        objectRenderer.update(dt)
        updateSpawns(dt)

        resourceTimer -= dt
        if (resourceTimer <= 0f) {
            resourceTimer = 240f
            val drops = listOf(ItemId.WOOD, ItemId.STONE, ItemId.IRON, ItemId.FIBER, ItemId.MAGIC_STONE)
            repeat(4) {
                val point = randomSpawnPoint(3f, 9f) ?: return@repeat
                dropLoot(point.first, point.second, drops[Random.nextInt(drops.size)], 1)
            }
            toast("Resources regenerated")
        }

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
                command == UiCommand.UPGRADE_MAGIC -> upgradeMagic()
                command == UiCommand.EXCHANGE_STONE -> exchangeResources(ItemId.STONE)
                command == UiCommand.EXCHANGE_IRON -> exchangeResources(ItemId.IRON)
                command == UiCommand.EXCHANGE_MAGIC -> exchangeResources(ItemId.MAGIC_STONE)
                command == UiCommand.SELL_WEAPON -> sellWeapon()
                command in UiCommand.EQUIP_WEAPON_BASE until UiCommand.EQUIP_WEAPON_BASE + ItemId.values().size -> {
                    val id = ItemId.values().getOrNull(command - UiCommand.EQUIP_WEAPON_BASE)
                    if (id?.category == ItemCategory.WEAPON && inventory.count(id) > 0) equipWeapon(id)
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
                equipWeapon(id)
            }
            ItemCategory.MATERIAL -> toast("${id.displayName}: crafting material")
            ItemCategory.CONSUMABLE, ItemCategory.VALUABLE -> {
                val stats = player.stats
                val helpsHealth = id.heal > 0f && stats.health < stats.maxHealth
                val helpsMana = id.mana > 0f && stats.mana < stats.maxMana
                val helpsHunger = id.food > 0f && stats.hunger < 100f
                when (id) {
                    ItemId.POWER_POTION -> {
                        if (powerPotionTimer > 0f) { toast("Power is already active"); return }
                        powerPotionTimer = 30f
                    }
                    ItemId.REGEN_POTION -> {
                        if (stats.health >= stats.maxHealth || regenPotionTimer > 0f) {
                            toast("Health is full or regeneration is active"); return
                        }
                        regenPotionTimer = 15f
                    }
                    else -> if (!helpsHealth && !helpsMana && !helpsHunger) {
                        toast("Health, mana and hunger are already full"); return
                    }
                }
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
            equipWeapon(recipe.result)
        }
        banner("CRAFTED ${recipe.result.displayName.uppercase()}")
        toast("${recipe.result.displayName} ready")
        spawnParticles(player.x, player.y, recipe.result.color, 16)
    }

    // ---- Combat ------------------------------------------------------------

    fun activeWeapon(): ItemId? {
        val melee = meleeWeapon
        if (melee != null && inventory.count(melee) > 0) return melee
        val equipped = equippedWeapon
        if (equipped != null && inventory.count(equipped) > 0) return equipped
        return inventory.bestWeapon()
    }

    fun magicCost(spell: Spell): Float = spell.manaCost * 0.9f.pow((stage - 1) * Bosses.ALL.size + defeatedBosses.size)
    fun magicDamage(spell: Spell): Float = spell.damage * 1.18f.pow((spellLevels[spell] ?: 1) - 1)

    fun upgradeMagic(spell: Spell = lastSpell) {
        val level = spellLevels[spell] ?: 1
        val cost = level * 2
        if (inventory.count(ItemId.MAGIC_STONE) < cost) { toast("Need $cost Magic Stone"); return }
        inventory.remove(ItemId.MAGIC_STONE, cost)
        spellLevels[spell] = level + 1
        toast("${spell.displayName} upgraded to level ${level + 1}")
    }

    fun weaponDamage(weapon: ItemId): Float = (10f + weapon.damage) * 1.5f.pow((weaponStars[weapon] ?: 1) - 1) * (1f + ((weaponLevels[weapon] ?: 1) - 1) * .025f)

    fun weaponUpgradeCosts(weapon: ItemId): List<Pair<ItemId, Int>> {
        val level = weaponLevels[weapon] ?: 1
        val target = level + 1
        val base = kotlin.math.ceil(4f * 1.5f.pow(level - 1)).toInt().coerceAtLeast(4)
        val costs = mutableListOf(ItemId.WOOD to base)
        if (target >= 11) costs += ItemId.STONE to kotlin.math.ceil(base * .25f).toInt().coerceAtLeast(1)
        if (target >= 30) costs += ItemId.IRON to kotlin.math.ceil(base * .2f).toInt().coerceAtLeast(1)
        if (target in listOf(10, 20, 30, 40, 45) || (target > 40 && target % 5 == 0)) costs += ItemId.MAGIC_STONE to 1
        return costs
    }

    private fun equipWeapon(weapon: ItemId) {
        equippedWeapon = weapon
        if (weapon.style == WeaponStyle.PROJECTILE) rangedWeapon = weapon else meleeWeapon = weapon
        toast("${weapon.displayName} equipped as ${if (weapon.style == WeaponStyle.PROJECTILE) "RANGED" else "MELEE"}")
    }

    fun exchangeResources(result: ItemId) {
        val (input, amount) = when (result) {
            ItemId.STONE -> ItemId.WOOD to 2
            ItemId.IRON -> ItemId.WOOD to 4
            ItemId.MAGIC_STONE -> ItemId.WOOD to 10
            else -> return
        }
        if (inventory.count(input) < amount) { toast("Need $amount ${input.displayName}"); return }
        inventory.remove(input, amount); inventory.add(result, 1)
        toast("Exchanged $amount ${input.displayName} for 1 ${result.displayName}")
    }

    fun defeatedBossIds(): Set<String> = defeatedBosses

    private fun playerAttack() {
        if (!player.isAlive) return
        val weapon = activeWeapon()?.takeIf { it.style != WeaponStyle.PROJECTILE }
        val ranged = rangedWeapon?.takeIf { inventory.count(it) > 0 }
        val style = weapon?.style ?: WeaponStyle.MELEE

        // Auto-aim: snap toward the closest enemy before committing the swing.
        val target = nearestEnemy(if (ranged != null) 14f * Config.TILE else 6f * Config.TILE)
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

        val weaponPower = weapon?.let(::weaponDamage) ?: 10f
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
        if (ranged != null) fireProjectile(ranged)
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
        if (!creature.alive || (creature is Boss && creature.isBurrowed) || campfires.any { it.isSafe(creature.x, creature.y) }) return
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
        val isGun = weapon == ItemId.GUN
        val speed = if (isGun) 900f else 610f
        val weaponPower = weapon?.let(::weaponDamage) ?: 10f
        val damage = weaponPower * if (powerPotionTimer > 0f) 1.5f else 1f
        val color = if (isGun) 0xFFFFD45A.toInt() else 0xFFE9D2A2.toInt()
        projectiles.add(
            Projectile(
                x = player.x + player.facingX * 18f,
                y = player.y + player.facingY * 18f,
                vx = player.facingX * speed,
                vy = player.facingY * speed,
                damage = damage,
                color = color,
                radius = if (isGun) 3.5f else 4.5f,
                kind = if (isGun) ProjectileKind.BULLET else ProjectileKind.ARROW,
                pierces = weapon?.pierces == true,
                life = if (isGun) .85f else 1.3f,
            ),
        )
        spawnParticles(player.x + player.facingX * 20f, player.y + player.facingY * 20f, color, if (isGun) 9 else 3)
    }

    private fun updateProjectiles(dt: Float) {
        if (projectiles.isEmpty()) return
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            projectile.update(dt)

            if (graphics.particles && !projectile.hostile && projectile.kind == ProjectileKind.BULLET) {
                particles.add(Particle(projectile.x, projectile.y, 0f, 0f, .10f, projectile.color, 1.8f, 0f, true))
            }

            var finished = projectile.dead
            if (!finished && world.isSolid(
                    world.tileX(projectile.x),
                    world.tileY(projectile.y),
                )
            ) {
                finished = true
            }

            if (projectile.hostile) {
                if (activeBoss == null || activeBoss?.engaged != true) finished = true
                if (!finished && hypot(player.x-projectile.x, player.y-projectile.y) < Config.PLAYER_RADIUS + projectile.radius) {
                    hurtPlayer(projectile.damage, projectile.x, projectile.y); finished = true
                }
            }
            if (!finished && !projectile.hostile) {
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
                        damage = magicDamage(spell),
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
                applySpellAoE(centerX, centerY, 115f, magicDamage(spell), freeze = 2.6f, knockback = 0f)
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
                    damageCreature(creature, magicDamage(spell))
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
                applySpellAoE(player.x, player.y, 150f, magicDamage(spell), 0f, knockback = 220f)
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
            else -> {
                addXp(10)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 1)
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
        boss.defeatedAtSeconds = elapsedSeconds
        activeBoss = null
        defeatedBosses.add(boss.def.id)

        val stats = player.stats
        stats.addXp(boss.def.xpReward)
        stats.grantBossReward(boss.def.healthReward, boss.def.manaReward)
        stats.restoreMana(60f)
        // Every boss always drops a visible Magic Stone, even if the inventory is full.
        dropLoot(boss.x, boss.y, ItemId.MAGIC_STONE, 1)
        for ((id, count) in boss.def.loot) dropLoot(boss.x, boss.y, id, count)
        for (material in ItemId.values().filter { it.category == ItemCategory.MATERIAL || it == ItemId.MAGIC_STONE }) {
            dropLoot(boss.x, boss.y, material, 1)
        }
        val newLevel = stage + defeatedBosses.size
        for (other in bosses) if (!other.defeated) other.setLevel(newLevel)

        banner("${boss.def.name.uppercase()} DEFEATED")
        toast("+${boss.def.xpReward} XP   MAX HP +${boss.def.healthReward.toInt()}   MAX MANA +${boss.def.manaReward.toInt()}")
        spawnParticles(boss.x, boss.y, boss.def.color, 28)
        BossSignatures.byId(boss.def.id)?.let {
            texts.add(FloatingText(boss.x, boss.y - 36f, it.defeatLine, boss.def.color, 3.6f, 12f))
        }
        val tier = defeatedBosses.size
        val unique = ItemId.values().firstOrNull { it.category == ItemCategory.WEAPON && inventory.count(it) == 0 }
        if (unique != null) dropLoot(boss.x, boss.y, unique, 1)
        for (material in listOf(ItemId.RAW_MEAT, ItemId.STONE, ItemId.WOOD, ItemId.FRUIT, ItemId.IRON, ItemId.FIBER, ItemId.MAGIC_STONE)) {
            dropLoot(boss.x, boss.y, material, tier)
        }
        if (boss.def.id == Bosses.DRAGON.id) {
            stageAdvancePending = true
        } else if (defeatedBosses.size == Bosses.ALL.size) {
            dragonSpawnTimer = 15f
            banner("THE DRAGON ARRIVES IN 15 SECONDS")
            toast("Prepare for the Reincarnation Dragon")
            saveNow()
        } else saveNow()
    }

    private fun advanceStage() {
        stageAdvancePending = false
        dragonSpawnTimer = -1f
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
        input.bossSoundRequested.set(true)
        banner("${boss.def.name.uppercase()} AWAKENS")
        toast(BossSignatures.byId(boss.def.id)?.introLine ?: "The arena is sealed — defeat the boss to escape")
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
        val pickup = nearestPickup(Config.PICKUP_REACH)
        if (pickup != null) return "PICK UP ${pickup.item.displayName.uppercase()}"
        val chest = nearestChest(Config.CHEST_REACH)
        if (chest != null) return "OPEN CHEST"
        val fire = nearestCampfire(Config.CAMP_REACH)
        if (fire != null) return "REST AT CAMPFIRE"
        val node = harvestTarget()
        if (node != null) {
            return when (world.tileAt(node.x, node.y)) {
                TileType.TREE, TileType.FOREST_TREE -> "CHOP TREE"
                TileType.ROCK -> "MINE ROCK"
                TileType.FLOWERS -> "GATHER FIBER"
                else -> null
            }
        }
        return null
    }

    private fun interact() {
        val pickup = nearestPickup(Config.PICKUP_REACH)
        if (pickup != null) {
            collectPickup(pickup)
            return
        }
        val chest = nearestChest(Config.CHEST_REACH)
        if (chest != null) {
            openChest(chest)
            return
        }
        val fire = nearestCampfire(Config.CAMP_REACH)
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
        var bestDistance = Config.HARVEST_REACH
        for (dy in -1..1) {
            for (dx in -1..1) {
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
        val rewards = mutableListOf<Pair<ItemId, Int>>()
        when (type) {
            TileType.TREE, TileType.FOREST_TREE -> {
                rewards += ItemId.WOOD to (2 + Random.nextInt(2))
                if (Random.nextFloat() < 0.45f) rewards += ItemId.FIBER to 1
                if (Random.nextFloat() < 0.30f) rewards += ItemId.FRUIT to (1 + Random.nextInt(2))
            }

            TileType.ROCK -> {
                rewards += ItemId.STONE to (2 + Random.nextInt(2))
                if (Random.nextFloat() < 0.35f) rewards += ItemId.IRON to 1
                if (Random.nextFloat() < 0.12f) rewards += ItemId.MAGIC_STONE to 1
            }

            TileType.FLOWERS -> {
                rewards += ItemId.FIBER to 1
            }

            else -> return
        }
        if (!inventory.canFit(rewards)) { showInventoryFull(cx, cy); return }
        rewards.forEach { (id, count) -> giveItem(id, count) }
        world.setTile(node.x, node.y, if (type == TileType.FOREST_TREE) TileType.FOREST_FLOOR else TileType.GRASS)
        spawnParticles(cx, cy, when (type) {
            TileType.TREE, TileType.FOREST_TREE -> 0xFF8A6A43.toInt()
            TileType.ROCK -> 0xFF8C8C94.toInt()
            else -> 0xFFD95F6A.toInt()
        }, if (type == TileType.FLOWERS) 8 else 10)
        addXp(2)
    }

    private fun nearestChest(range: Float): LootChest? {
        var best: LootChest? = null
        var bestDistance = range
        for (chest in chests) {
            if (chest.opened) continue
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

    private fun nearestPickup(range: Float): Pickup? {
        var best: Pickup? = null
        var bestDistance = range
        for (pickup in pickups) {
            val distance = hypot(
                (pickup.x - player.x).toDouble(),
                (pickup.y - player.y).toDouble(),
            ).toFloat()
            if (distance <= bestDistance) {
                bestDistance = distance
                best = pickup
            }
        }
        return best
    }

    private fun collectPickup(pickup: Pickup) {
        val leftover = inventory.add(pickup.item, pickup.count)
        val collected = pickup.count - leftover
        if (collected > 0) {
            texts.add(FloatingText(player.x, player.y - 34f, "+$collected ${pickup.item.displayName}", 0xFFFFE08A.toInt(), 1.2f))
        }
        if (leftover == 0) {
            pickups.remove(pickup)
        } else {
            pickup.count = leftover
            showInventoryFull(pickup.x, pickup.y)
        }
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
        val rewards = mutableListOf<Pair<ItemId, Int>>()
        repeat(2 + random.nextInt(2)) {
            val id = pool[random.nextInt(pool.size)]
            val count = 1 + random.nextInt(if (id == ItemId.ELIXIR) 2 else 5)
            rewards += id to count
        }
        if (chest.id % 3 == 1) rewards += ItemId.WOODEN_SWORD to 1
        if (chest.id == 0) rewards += ItemId.MAGIC_STONE to 1
        if (chest.id == 5) rewards += ItemId.IRON_SWORD to 1
        if (!inventory.canFit(rewards)) { showInventoryFull(chest.x, chest.y); return }
        chest.opened = true
        openedChests.add(chest.id)
        rewards.forEach { (id, count) -> giveItem(id, count) }

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

    private fun updateDragonSpawn(dt: Float) {
        if (dragonSpawnTimer < 0f) return
        dragonSpawnTimer -= dt
        if (dragonSpawnTimer > 0f) return
        dragonSpawnTimer = -1f
        if (bosses.any { it.isDragon && !it.defeated }) return

        val centreX = 0f
        val centreY = 0f
        val fromCentre = hypot(player.x - centreX, player.y - centreY)
        val (spawnX, spawnY) = if (fromCentre >= 10f * Config.TILE) {
            centreX to centreY
        } else {
            val dx = if (fromCentre > 1f) player.x / fromCentre else 1f
            val dy = if (fromCentre > 1f) player.y / fromCentre else 0f
            player.x + dx * 12f * Config.TILE to player.y + dy * 12f * Config.TILE
        }
        bosses.add(Boss(Bosses.DRAGON).also {
            it.setLevel(stage + 1)
            it.x = spawnX
            it.y = spawnY
        })
        banner("THE REINCARNATION DRAGON AWAKENS")
        toast("The dragon is flying toward you")
    }

    private fun updateCreatures(dt: Float) {
        for (animal in animals) {
            if (campfires.any { it.isSafe(animal.x, animal.y) }) { animal.calm(); continue }
            animal.update(dt, this)
        }
        for (wolf in wolves) {
            val inSafeCamp = campfires.any { it.isSafe(wolf.x, wolf.y) }
            if (inSafeCamp) { wolf.disengage(); wolf.calm(); continue }
            val oldX = wolf.x; val oldY = wolf.y
            wolf.update(dt, this)
            if (hypot(wolf.x-oldX, wolf.y-oldY) > .05f) worldRenderer.bendGrass(world.tileX(wolf.x),world.tileY(wolf.y),elapsedSeconds)
        }
        wolves.removeAll { wolf -> campfires.any { it.isSafe(wolf.x, wolf.y) } }
        val playerInCamp = campfires.any { it.isSafe(player.x, player.y) }
        if (playerInCamp) {
            for (boss in bosses) if (!boss.isDragon && !boss.defeated && boss.engaged) boss.resetFight()
            if (activeBoss?.isDragon != true) activeBoss = null
        }

        for (boss in bosses) {
            if (boss.defeated) continue
            if (playerInCamp && !boss.isDragon) continue
            if (!boss.isDragon && campfires.any { it.isSafe(boss.x, boss.y) } && !boss.engaged) { boss.calm(); continue }
            val distance = hypot(
                (boss.x - player.x).toDouble(),
                (boss.y - player.y).toDouble(),
            ).toFloat()
            if (boss.isDragon || boss.engaged || (distance < 90f * Config.TILE && campfires.none { it.isSafe(boss.x, boss.y) })) {
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
            pickupIterator.next().update(dt)
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
            roll < 0.30f -> AnimalSpecies.RABBIT
            roll < 0.43f -> AnimalSpecies.SHEEP
            roll < 0.53f -> AnimalSpecies.CHICKEN
            roll < 0.65f -> AnimalSpecies.DEER
            roll < 0.75f -> AnimalSpecies.BOAR
            roll < 0.80f -> AnimalSpecies.BIRD
            roll < 0.85f -> AnimalSpecies.FOX
            roll < 0.90f -> AnimalSpecies.DOG
            roll < 0.95f -> AnimalSpecies.SHARK
            else -> AnimalSpecies.RAT
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
            showInventoryFull(player.x, player.y)
        }
        val received = count - leftover
        if (received <= 0) return
        texts.add(
            FloatingText(
                player.x,
                player.y - 40f,
                "+$received ${id.displayName}",
                0xFFFFE08A.toInt(),
                1.4f,
            ),
        )
    }

    private fun showInventoryFull(x: Float, y: Float) {
        texts.add(FloatingText(x, y - 18f, "FULL", 0xFFFF4D4D.toInt(), .7f, 10f))
        toast("Inventory full")
    }

    private fun dropLoot(x: Float, y: Float, id: ItemId, count: Int) {
        val adjustedCount = scaledLootCount(count)
        val angle = Random.nextFloat() * (PI * 2).toFloat()
        val speed = 42f + Random.nextFloat() * 56f
        pickups.add(
            Pickup(
                id,
                adjustedCount,
                x + (Random.nextFloat() - 0.5f) * 6f,
                y + (Random.nextFloat() - 0.5f) * 6f,
                cos(angle) * speed,
                sin(angle) * speed,
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
        worldRenderer.drawAmbient(canvas, world, camera, player.x, player.y, width, height, elapsedSeconds, graphics)

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
        for (wolf in wolves) add(wolf.x, wolf.y) { drawAtIso(canvas, wolf.x, wolf.y) { creatureRenderer.drawWolf(canvas, wolf, elapsedSeconds) } }
        for (boss in bosses) if (!boss.defeated || elapsedSeconds - boss.defeatedAtSeconds < 1f) add(boss.x, boss.y) { drawAtIso(canvas, boss.x, boss.y) { creatureRenderer.drawBoss(canvas, boss, graphics, elapsedSeconds) } }
        add(player.x, player.y) { drawAtIso(canvas, player.x, player.y) { playerRenderer.draw(canvas, player, elapsedSeconds) } }
        for (projectile in projectiles) add(projectile.x, projectile.y) {
            drawProjectile(canvas, projectile)
        }
        renderables.sortWith(compareBy<Renderable> { it.gridDepth }.thenBy { it.isoX })
        for (item in renderables) item.draw()
        for (text in texts) drawAtIso(canvas, text.x, text.y) { objectRenderer.drawFloatingText(canvas, text) }
        for (particle in particles) drawAtIso(canvas, particle.x, particle.y) { objectRenderer.drawParticle(canvas, particle) }

        canvas.restore()

        val lights = ArrayList<WorldRenderer.Light>()
        for (pickup in pickups) if (pickup.item == ItemId.MAGIC_STONE && onScreen(pickup.x,pickup.y))
            lights.add(WorldRenderer.Light(pickup.x,pickup.y,55f + 4f*sin(elapsedSeconds*7f)))
        for (projectile in projectiles) if (onScreen(projectile.x,projectile.y)) lights.add(WorldRenderer.Light(projectile.x,projectile.y,40f))
        for (wolf in wolves) if (onScreen(wolf.x,wolf.y)) lights.add(WorldRenderer.Light(wolf.x,wolf.y,22f))
        for (boss in bosses) if (!boss.defeated && onScreen(boss.x,boss.y)) lights.add(WorldRenderer.Light(boss.x,boss.y,38f))
        worldRenderer.drawNight(canvas, camera, player, campfires, minutes, width, height, lights)
        worldRenderer.drawFog(canvas, width, height)
        hud.draw(canvas, this, input, width, height)
    }

    private fun scaledLootCount(count: Int): Int {
        val bonus = if (stage >= 2) 1.2f + Random.nextFloat() * .8f else 1f
        return max(1, kotlin.math.ceil(count * bonus).toInt())
    }

    /** Draws a recognisable arrow or bullet in isometric canvas space. */
    private fun drawProjectile(canvas: Canvas, projectile: Projectile) {
        val head = IsoMath.worldToIso(projectile.x, projectile.y)
        val tail = IsoMath.worldToIso(
            projectile.x - projectile.vx * projectile.trailSeconds,
            projectile.y - projectile.vy * projectile.trailSeconds,
        )
        val dx = head.x - tail.x
        val dy = head.y - tail.y
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.01f)
        val nx = -dy / length
        val ny = dx / length

        when (projectile.kind) {
            ProjectileKind.ARROW -> {
                arenaPaint.style = Paint.Style.STROKE
                arenaPaint.strokeWidth = 2.7f
                arenaPaint.color = 0xFF5A351F.toInt()
                canvas.drawLine(tail.x, tail.y, head.x, head.y, arenaPaint)
                arenaPaint.strokeWidth = 1.3f
                arenaPaint.color = projectile.color
                canvas.drawLine(tail.x + dx * .35f, tail.y + dy * .35f, head.x, head.y, arenaPaint)
                arenaPaint.style = Paint.Style.FILL
                arenaPaint.color = 0xFFECE6D4.toInt()
                canvas.drawCircle(head.x, head.y, 2.7f, arenaPaint)
                arenaPaint.color = 0xFFE8D8B0.toInt()
                canvas.drawLine(tail.x - nx * 3f, tail.y - ny * 3f, tail.x + nx * 3f, tail.y + ny * 3f, arenaPaint)
            }
            ProjectileKind.BULLET -> {
                arenaPaint.style = Paint.Style.STROKE
                arenaPaint.strokeWidth = 3.3f
                arenaPaint.color = projectile.color
                canvas.drawLine(tail.x, tail.y, head.x, head.y, arenaPaint)
                arenaPaint.style = Paint.Style.FILL
                arenaPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(head.x, head.y, 3.2f, arenaPaint)
            }
            ProjectileKind.MAGIC -> {
                arenaPaint.style = Paint.Style.FILL
                arenaPaint.color = projectile.color
                canvas.drawCircle(head.x, head.y, projectile.radius * .55f, arenaPaint)
            }
        }
        arenaPaint.style = Paint.Style.FILL
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
        val costs = weaponUpgradeCosts(weapon)
        if (costs.any { inventory.count(it.first) < it.second }) { toast("Need ${costs.joinToString { "${it.second} ${it.first.displayName}" }}"); return }
        costs.forEach { inventory.remove(it.first, it.second) }
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

    private fun sellWeapon() {
        val weapon = equippedWeapon ?: activeWeapon() ?: run { toast("Select a weapon first"); return }
        val level = weaponLevels[weapon] ?: 1
        val refund = kotlin.math.ceil(4f * 1.5f.pow((level - 1).coerceAtLeast(0)) * .5f).toInt()
        val rewards = mutableListOf(ItemId.WOOD to refund)
        if (level >= 11) rewards += ItemId.STONE to (refund / 4).coerceAtLeast(1)
        if (level >= 30) rewards += ItemId.IRON to (refund / 5).coerceAtLeast(1)
        if (level >= 40) rewards += ItemId.MAGIC_STONE to 1
        if (!inventory.canFitAfterRemoving(weapon, 1, rewards)) {
            showInventoryFull(player.x, player.y)
            return
        }
        if (inventory.remove(weapon, 1) == 0) { toast("Weapon not found"); return }
        rewards.forEach { (id, count) -> inventory.add(id, count) }
        if (inventory.count(weapon) == 0) {
            equippedWeapon = null
            if (meleeWeapon == weapon) meleeWeapon = null
            if (rangedWeapon == weapon) rangedWeapon = null
        }
        toast("Sold ${weapon.displayName} for upgrade materials")
    }

    private fun loadSavedState() {
        val state = saveManager.load() ?: run { toast("No save found"); return }
        if (state.seed != world.seed) { toast("This save belongs to another world"); return }
        activeBoss = null; projectiles.clear()
        player.x = state.x; player.y = state.y
        player.stats.level = state.level; player.stats.xp = state.xp
        player.stats.maxHealth = state.maxHealth; player.stats.maxMana = state.maxMana
        player.stats.bonusHealth = state.bonusHealth; player.stats.bonusMana = state.bonusMana
        defeatedBosses.clear(); defeatedBosses.addAll(state.defeatedBosses)
        stage = state.stage
        stageAdvancePending = false
        dragonSpawnTimer = if (defeatedBosses.size == Bosses.ALL.size) 15f else -1f
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
