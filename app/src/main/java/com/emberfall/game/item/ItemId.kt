package com.emberfall.game.item

import kotlin.random.Random

enum class ItemCategory { MATERIAL, WEAPON, CONSUMABLE, VALUABLE }

enum class Rarity { COMMON, UNCOMMON, RARE, EPIC }

enum class WeaponStyle { MELEE, ARC, RING, PROJECTILE }

/**
 * One possible drop. Use chance = 1f for guaranteed drops.
 */
data class LootRoll(val item: ItemId, val amount: Int, val chance: Float = 1f) {
    companion object {
        /** Resolves a list of rolls into concrete item stacks. */
        fun resolve(rolls: List<LootRoll>): List<Pair<ItemId, Int>> =
            rolls
                .filter { it.chance >= 1f || Random.nextFloat() < it.chance }
                .map { it.item to it.amount }
    }
}

/**
 * Every item the frontier can hold. Prices, weapon damage, food value and
 * effects all live here so the shop, combat and inventory stay data-driven.
 * Sell price is derived in [com.emberfall.game.item.ShopCatalog].
 */
enum class ItemId(
    val displayName: String,
    val category: ItemCategory,
    val rarity: Rarity,
    val maxStack: Int,
    /** Gold price in the shop. Sell price is half of this (minimum 1). */
    val buyPrice: Int,
    val color: Int,
    val description: String,
    /** Extra diamonds required on top of the gold price. */
    val diamondPrice: Int = 0,
    val damage: Float = 0f,
    val heal: Float = 0f,
    val mana: Float = 0f,
    val food: Float = 0f,
    /** Fractional attack bonus while active (0.25f = +25%). */
    val attackBoost: Float = 0f,
    /** How long the attack bonus lasts, in seconds. */
    val attackBoostSeconds: Float = 0f,
    /** Health per second while the regen effect is active. */
    val regenPerSecond: Float = 0f,
    /** Duration of the regen effect in seconds. */
    val regenSeconds: Float = 0f,
    val style: WeaponStyle = WeaponStyle.MELEE,
    val pierces: Boolean = false,
) {
    WOOD("Wood", ItemCategory.MATERIAL, Rarity.COMMON, 50, 5, 0xFF8A6A43.toInt(),
        "Crafting material"),
    STONE("Stone", ItemCategory.MATERIAL, Rarity.COMMON, 50, 8, 0xFF8C8C94.toInt(),
        "Crafting material"),
    FIBER("Fiber", ItemCategory.MATERIAL, Rarity.COMMON, 50, 6, 0xFF9BB56A.toInt(),
        "Crafting material"),
    IRON("Iron Ore", ItemCategory.MATERIAL, Rarity.UNCOMMON, 50, 40, 0xFFC9A27A.toInt(),
        "Rare crafting material"),
    HIDE("Hide", ItemCategory.MATERIAL, Rarity.COMMON, 30, 12, 0xFFA9794F.toInt(),
        "Crafting material"),
    MAGIC_STONE("Magic Stone", ItemCategory.MATERIAL, Rarity.RARE, 20, 120, 0xFF7A6BD9.toInt(),
        "High-tier crafting", diamondPrice = 1),

    FRUIT("Wild Fruit", ItemCategory.CONSUMABLE, Rarity.COMMON, 20, 8, 0xFFD95F6A.toInt(),
        "+12 HP, +18 Hunger", heal = 12f, food = 18f),
    RAW_MEAT("Raw Meat", ItemCategory.CONSUMABLE, Rarity.COMMON, 20, 12, 0xFFC26262.toInt(),
        "+6 HP, +20 Hunger", heal = 6f, food = 20f),
    COOKED_MEAT("Cooked Meat", ItemCategory.CONSUMABLE, Rarity.COMMON, 20, 22, 0xFFB5793F.toInt(),
        "+20 HP, +40 Hunger", heal = 20f, food = 40f),
    ELIXIR("Elixir", ItemCategory.CONSUMABLE, Rarity.UNCOMMON, 10, 60, 0xFF5FD3C4.toInt(),
        "+30 HP, +50 Mana", heal = 30f, mana = 50f),
    MANA_POTION("Mana Potion", ItemCategory.CONSUMABLE, Rarity.COMMON, 10, 35, 0xFF6B84E8.toInt(),
        "+45 Mana", mana = 45f),
    POWER_POTION("Power Potion", ItemCategory.CONSUMABLE, Rarity.RARE, 10, 110, 0xFFE28B4D.toInt(),
        "+25% attack power for 60 sec", attackBoost = 0.25f, attackBoostSeconds = 60f),
    REGEN_POTION("Regen Potion", ItemCategory.CONSUMABLE, Rarity.RARE, 10, 100, 0xFF69C879.toInt(),
        "+5 HP/sec for 15 sec", regenPerSecond = 5f, regenSeconds = 15f),

    WOODEN_SWORD("Wooden Sword", ItemCategory.WEAPON, Rarity.COMMON, 1, 40, 0xFF9A7446.toInt(),
        "A simple blade", damage = 8f),
    IRON_SWORD("Iron Sword", ItemCategory.WEAPON, Rarity.UNCOMMON, 1, 150, 0xFFB9C3D0.toInt(),
        "Forged for the frontier", damage = 14f),
    CROSS_SWORD("Cross Sword", ItemCategory.WEAPON, Rarity.RARE, 1, 280, 0xFFD9DCE8.toInt(),
        "Sweeping cross-cut", damage = 22f, style = WeaponStyle.ARC),
    RING_BLADE("Ring Blade", ItemCategory.WEAPON, Rarity.RARE, 1, 260, 0xFFC9A227.toInt(),
        "Spins through everything nearby", damage = 18f, style = WeaponStyle.RING),
    BOW("Hunting Bow", ItemCategory.WEAPON, Rarity.UNCOMMON, 1, 130, 0xFF8A6238.toInt(),
        "Fires a piercing arrow", damage = 16f, style = WeaponStyle.PROJECTILE),
    GUN("Flintlock Gun", ItemCategory.WEAPON, Rarity.EPIC, 1, 500, 0xFF6E7A86.toInt(),
        "Heavy shot that punches through", diamondPrice = 4, damage = 26f,
        style = WeaponStyle.PROJECTILE, pierces = true);

    val isUsable: Boolean
        get() = category == ItemCategory.CONSUMABLE ||
                category == ItemCategory.VALUABLE ||
                category == ItemCategory.WEAPON

    val iconLetter: String
        get() = displayName.first().toString()
}