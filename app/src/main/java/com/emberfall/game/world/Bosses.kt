package com.emberfall.game.world

import com.emberfall.game.item.ItemId

/**
 * Static definitions for the five world bosses. Each one owns an arena carved
 * into the terrain by [WorldGenerator]; the boss stays dormant until the
 * player steps inside and seals the arena until one of them dies.
 */
data class BossDef(
    val id: String,
    val name: String,
    val tileX: Int,
    val tileY: Int,
    val arenaRadiusTiles: Float,
    val maxHealth: Float,
    val damage: Float,
    val speed: Float,
    val color: Int,
    val xpReward: Int,
    val healthReward: Float,
    val manaReward: Float,
    val loot: List<Pair<ItemId, Int>>,
)

object Bosses {

    val ALL: List<BossDef> = listOf(
        BossDef(
            id = "gloomfang",
            name = "Gloomfang, the Alpha",
            tileX = 0,
            tileY = -40,
            arenaRadiusTiles = 10f,
            maxHealth = 170f,
            damage = 12f,
            speed = 78f,
            color = 0xFF6E6A86.toInt(),
            xpReward = 120,
            healthReward = 10f,
            manaReward = 4f,
            loot = listOf(ItemId.ELIXIR to 1, ItemId.RAW_MEAT to 3, ItemId.MAGIC_STONE to 1),
        ),
        BossDef(
            id = "stonewarden",
            name = "Stonewarden",
            tileX = 68,
            tileY = 30,
            arenaRadiusTiles = 11f,
            maxHealth = 280f,
            damage = 16f,
            speed = 62f,
            color = 0xFF7B7468.toInt(),
            xpReward = 190,
            healthReward = 14f,
            manaReward = 5f,
            loot = listOf(ItemId.IRON to 4, ItemId.STONE to 8, ItemId.ELIXIR to 1),
        ),
        BossDef(
            id = "mirewitch",
            name = "Mirewitch of the Fen",
            tileX = -82,
            tileY = 52,
            arenaRadiusTiles = 10f,
            maxHealth = 340f,
            damage = 18f,
            speed = 88f,
            color = 0xFF5E8B6A.toInt(),
            xpReward = 240,
            healthReward = 16f,
            manaReward = 8f,
            loot = listOf(ItemId.ELIXIR to 2, ItemId.MAGIC_STONE to 2),
        ),
        BossDef(
            id = "ashtitan",
            name = "Ash Titan",
            tileX = 40,
            tileY = 108,
            arenaRadiusTiles = 12f,
            maxHealth = 470f,
            damage = 22f,
            speed = 60f,
            color = 0xFF9A5A3C.toInt(),
            xpReward = 330,
            healthReward = 20f,
            manaReward = 6f,
            loot = listOf(ItemId.IRON_SWORD to 1, ItemId.IRON to 6, ItemId.ELIXIR to 2),
        ),
        BossDef(
            id = "hollowking",
            name = "The Hollow King",
            tileX = -122,
            tileY = -94,
            arenaRadiusTiles = 12f,
            maxHealth = 620f,
            damage = 26f,
            speed = 70f,
            color = 0xFF8E7BB8.toInt(),
            xpReward = 460,
            healthReward = 25f,
            manaReward = 10f,
            loot = listOf(ItemId.IRON_SWORD to 1, ItemId.ELIXIR to 3, ItemId.MAGIC_STONE to 3),
        ),
    )

    fun byId(id: String): BossDef? = ALL.firstOrNull { it.id == id }
}
