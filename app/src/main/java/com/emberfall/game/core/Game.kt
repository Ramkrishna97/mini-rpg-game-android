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
import com.emberfall.game.entity.Player
import com.emberfall.game.entity.Wolf
import com.emberfall.game.entity.cap
import com.emberfall.game.input.InputState
import com.emberfall.game.input.UiCommand
import com.emberfall.game.item.Inventory
import com.emberfall.game.item.ItemCategory
import com.emberfall.game.item.ItemId
import com.emberfall.game.render.CreatureRenderer
import com.emberfall.game.render.ObjectRenderer
import com.emberfall.game.render.PlayerRenderer
import com.emberfall.game.render.WorldRenderer
import com.emberfall.game.save.SaveManager
import com.emberfall.game.save.SaveState
import com.emberfall.game.ui.HudRenderer
import com.emberfall.game.world.Bosses
import com.emberfall.game.world.Collision
import com.emberfall.game.world.World
import com.emberfall.game.world.WorldPoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns the simulation. Runs on the game thread; [InputState] is the only
 * channel from the UI thread, and panel commands arrive as an integer queue.
 */
class Game(context: Context, private val input: InputState) {

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
    private val playerRenderer = PlayerRenderer()
    private val creatureRenderer = CreatureRenderer()
    private val objectRenderer = ObjectRenderer()
    private val hud = HudRenderer(context)
    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var biomeTimer = 0f
    private var saveTimer = 0f
    private var spawnTimer = 0f
    private var elapsedSeconds = 0f
    private var deathDuration = 1.8f

    init {
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

        if (input.inventoryOpen || input.settingsOpen) {
            return
        }

        hud.update(dt, this)

        if (deathTimer > 0f) {
            deathTimer -= dt
            updateEffects(dt)
            if (deathTimer <= 0f) respawn()
            return
        }

        if (input.consumeAttackRequest()) playerAttack()
        val interactRequested = input.consumeInteractRequest()

        player.update(dt, input.moveX, input.moveY, input.run, world)
        player.stats.update(dt)
        clampToArena()

        if (interactRequested) interact()

        val tileX = floor(player.x / Config.TILE).toInt()
        val tileY = floor(player.y / Config.TILE).toInt()
        world.ensureAround(tileX, tileY)
        world.pruneAround(tileX, tileY)

        camera.update(player.x, player.y, player.facingX, player.facingY, dt)

        updateCreatures(dt)
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

                command == UiCommand.TOGGLE_PARTICLES -> {
                    graphics.particles = !graphics.particles
                }

                command == UiCommand.CLOSE_PANELS -> input.closePanels()
            }
        }
    }

    private fun useSlot(index: Int) {
        val stack = inventory.slot(index) ?: return
        val id = stack.id
        when (id.category) {
            ItemCategory.WEAPON -> toast("${id.displayName} equipped")
            ItemCategory.MATERIAL -> toast("${id.displayName}: crafting material")
            ItemCategory.CONSUMABLE, ItemCategory.VALUABLE -> {
                if (id.heal > 0f) player.stats.heal(id.heal)
                if (id.mana > 0f) player.stats.restoreMana(id.mana)
                if (id.food > 0f) player.stats.feed(id.food)
                inventory.removeAt(index)
                spawnParticles(player.x, player.y, id.color, 8)
                toast("Used ${id.displayName}")
            }
        }
    }

    // ---- Combat ------------------------------------------------------------

    private fun playerAttack() {
        if (!player.isAlive) return
        player.swing()

        val weaponDamage = inventory.bestWeapon()?.damage ?: 0f
        val damage = 10f + weaponDamage

        val deadAnimals = mutableListOf<Animal>()
        for (animal in animals) {
            if (!inAttackArc(animal)) continue
            animal.hurt(damage, player.x, player.y)
            damageText(animal.x, animal.y - animal.radius, damage)
            spawnParticles(animal.x, animal.y, 0xFFC26262.toInt(), 5)
            if (!animal.alive) deadAnimals.add(animal)
        }
        for (animal in deadAnimals) {
            animals.remove(animal)
            killAnimal(animal)
        }

        val deadWolves = mutableListOf<Wolf>()
        for (wolf in wolves) {
            if (!inAttackArc(wolf)) continue
            wolf.hurt(damage, player.x, player.y)
            damageText(wolf.x, wolf.y - wolf.radius, damage)
            spawnParticles(wolf.x, wolf.y, 0xFF767C86.toInt(), 5)
            if (!wolf.alive) deadWolves.add(wolf)
        }
        for (wolf in deadWolves) {
            wolves.remove(wolf)
            addXp(30)
            dropLoot(wolf.x, wolf.y, ItemId.HIDE, 2)
            dropLoot(wolf.x, wolf.y, ItemId.RAW_MEAT, 1 + Random.nextInt(2))
            if (Random.nextFloat() < 0.2f) dropLoot(wolf.x, wolf.y, ItemId.MAGIC_STONE, 1)
            spawnParticles(wolf.x, wolf.y, 0xFF767C86.toInt(), 10)
        }

        for (boss in bosses) {
            if (boss.defeated || !boss.engaged) continue
            if (!inAttackArc(boss)) continue
            boss.hurt(damage, player.x, player.y)
            damageText(boss.x, boss.y - boss.radius, damage)
            spawnParticles(boss.x, boss.y, boss.def.color, 8)
            if (boss.health <= 0f) defeatBoss(boss)
        }
    }

    private fun inAttackArc(creature: Creature): Boolean {
        val dx = creature.x - player.x
        val dy = creature.y - player.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance > 54f + creature.radius) return false
        if (distance < 1f) return true
        val dot = (dx * player.facingX + dy * player.facingY) / distance
        return dot > 0.15f
    }

    private fun killAnimal(animal: Animal) {
        when (animal.species) {
            AnimalSpecies.RABBIT -> {
                addXp(6)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 1)
                if (Random.nextFloat() < 0.6f) dropLoot(animal.x, animal.y, ItemId.HIDE, 1)
            }

            AnimalSpecies.DEER -> {
                addXp(12)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 2)
                dropLoot(animal.x, animal.y, ItemId.HIDE, 1 + Random.nextInt(2))
            }

            AnimalSpecies.BOAR -> {
                addXp(20)
                dropLoot(animal.x, animal.y, ItemId.RAW_MEAT, 3)
                dropLoot(animal.x, animal.y, ItemId.HIDE, 2)
            }
        }
        spawnParticles(animal.x, animal.y, 0xFFB5793F.toInt(), 8)
    }

    private fun defeatBoss(boss: Boss) {
        if (boss.defeated) return
        boss.markDefeated()
        activeBoss = null
        defeatedBosses.add(boss.def.id)

        val stats = player.stats
        stats.addXp(boss.def.xpReward)
        stats.grantBossReward(boss.def.healthReward, boss.def.manaReward)
        for ((id, count) in boss.def.loot) giveItem(id, count)

        banner("${boss.def.name.uppercase()} DEFEATED")
        toast("+${boss.def.xpReward} XP   MAX HP +${boss.def.healthReward.toInt()}   MAX MANA +${boss.def.manaReward.toInt()}")
        spawnParticles(boss.x, boss.y, boss.def.color, 28)
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

    fun hurtPlayer(amount: Float, sourceX: Float, sourceY: Float) {
        if (deathTimer > 0f || !player.isAlive) return
        if (player.takeDamage(amount)) {
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
            restAtCampfire(fire)
            return
        }
        toast("Nothing to interact with")
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
            animal.update(dt, this)
        }
        for (wolf in wolves) {
            wolf.update(dt, this)
        }

        for (boss in bosses) {
            if (boss.defeated) continue
            val distance = hypot(
                (boss.x - player.x).toDouble(),
                (boss.y - player.y).toDouble(),
            ).toFloat()
            if (boss.engaged || distance < 90f * Config.TILE) {
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
        wolves.add(Wolf(point.first, point.second))
    }

    private fun randomSpawnPoint(minTiles: Float, maxTiles: Float): Pair<Float, Float>? {
        repeat(12) {
            val angle = Random.nextFloat() * (PI * 2).toFloat()
            val distance = (minTiles + Random.nextFloat() * (maxTiles - minTiles)) * Config.TILE
            val x = player.x + cos(angle) * distance
            val y = player.y + sin(angle) * distance
            val tileX = world.tileX(x)
            val tileY = world.tileY(y)
            if (!world.isSolid(tileX, tileY)) return x to y
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
        pickups.add(
            Pickup(
                id,
                count,
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
        texts.add(
            FloatingText(
                x,
                y,
                "-${damage.toInt()}",
                0xFFFFD5C8.toInt(),
                0.8f,
                42f,
            ),
        )
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
        canvas.translate(width * 0.5f, height * 0.5f)
        canvas.scale(camera.zoom, camera.zoom)
        canvas.translate(-camera.x, -camera.y)

        worldRenderer.drawTiles(canvas, world, camera, width, height, elapsedSeconds)

        drawArenaSeal(canvas)

        for (fire in campfires) objectRenderer.drawCampfire(canvas, fire, graphics)
        for (chest in chests) objectRenderer.drawChest(canvas, chest)
        for (pickup in pickups) objectRenderer.drawPickup(canvas, pickup)

        for (animal in animals) {
            if (onScreen(animal.x, animal.y)) creatureRenderer.drawAnimal(canvas, animal, graphics)
        }
        for (wolf in wolves) {
            if (onScreen(wolf.x, wolf.y)) creatureRenderer.drawWolf(canvas, wolf)
        }
        for (boss in bosses) {
            if (!boss.defeated && onScreen(boss.x, boss.y)) {
                creatureRenderer.drawBoss(canvas, boss, graphics)
            }
        }

        if (onScreen(player.x, player.y)) playerRenderer.draw(canvas, player)

        for (text in texts) objectRenderer.drawFloatingText(canvas, text)
        for (particle in particles) objectRenderer.drawParticle(canvas, particle)

        canvas.restore()

        worldRenderer.drawNight(canvas, camera, player, campfires, minutes, width, height)
        hud.draw(canvas, this, input, width, height)
    }

    private fun drawArenaSeal(canvas: Canvas) {
        val boss = activeBoss ?: return
        if (!boss.engaged || boss.defeated) return
        arenaPaint.style = Paint.Style.STROKE
        arenaPaint.strokeWidth = 5f
        arenaPaint.color = 0x66FF4A3A
        canvas.drawCircle(boss.arenaX, boss.arenaY, boss.arenaRadius - 8f, arenaPaint)
        arenaPaint.style = Paint.Style.FILL
    }

    private fun onScreen(worldX: Float, worldY: Float): Boolean {
        val halfW = screenWidth / (2f * camera.zoom) + 80f
        val halfH = screenHeight / (2f * camera.zoom) + 80f
        return abs(worldX - camera.x) <= halfW && abs(worldY - camera.y) <= halfH
    }

    // ---- Persistence -------------------------------------------------------

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
                graphics = graphics,
            ),
        )
    }
}
