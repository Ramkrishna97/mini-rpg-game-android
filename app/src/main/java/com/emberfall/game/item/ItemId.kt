package com.emberfall.game.item

enum class ItemCategory { MATERIAL, WEAPON, CONSUMABLE, VALUABLE }

enum class WeaponStyle { MELEE, ARC, RING, PROJECTILE }

/**
 * Every item the frontier can hold. Weapon damage, attack style, food value and
 * restorative effects live on the item so combat and inventory stay data-driven.
 */
enum class ItemId(
    val displayName: String,
    val category: ItemCategory,
    val maxStack: Int,
    val color: Int,
    val description: String,
    val damage: Float = 0f,
    val heal: Float = 0f,
    val mana: Float = 0f,
    val food: Float = 0f,
    val style: WeaponStyle = WeaponStyle.MELEE,
    val pierces: Boolean = false,
) {
    WOOD("Wood", ItemCategory.MATERIAL, 50, 0xFF8A6A43.toInt(), "Crafting material"),
    STONE("Stone", ItemCategory.MATERIAL, 50, 0xFF8C8C94.toInt(), "Crafting material"),
    FIBER("Fiber", ItemCategory.MATERIAL, 50, 0xFF9BB56A.toInt(), "Crafting material"),
    IRON("Iron Ore", ItemCategory.MATERIAL, 50, 0xFFC9A27A.toInt(), "Rare crafting material"),
    HIDE("Hide", ItemCategory.MATERIAL, 30, 0xFFA9794F.toInt(), "Crafting material"),
    MAGIC_STONE("Magic Stone", ItemCategory.VALUABLE, 20, 0xFF7A6BD9.toInt(), "Hums with power", mana = 25f),

    FRUIT("Wild Fruit", ItemCategory.CONSUMABLE, 20, 0xFFD95F6A.toInt(), "Restores health and hunger", heal = 12f, food = 18f),
    RAW_MEAT("Raw Meat", ItemCategory.CONSUMABLE, 20, 0xFFC26262.toInt(), "Restores a little health and hunger", heal = 6f, food = 20f),
    COOKED_MEAT("Cooked Meat", ItemCategory.CONSUMABLE, 20, 0xFFB5793F.toInt(), "Restores health and hunger", heal = 20f, food = 40f),
    ELIXIR("Elixir", ItemCategory.CONSUMABLE, 10, 0xFF5FD3C4.toInt(), "Restores health and mana", heal = 30f, mana = 50f),
    MANA_POTION("Mana Potion", ItemCategory.CONSUMABLE, 10, 0xFF6B84E8.toInt(), "Restores 45 mana", mana = 45f),
    POWER_POTION("Power Potion", ItemCategory.CONSUMABLE, 10, 0xFFE28B4D.toInt(), "Temporarily empowers attacks", food = 0f),
    REGEN_POTION("Regen Potion", ItemCategory.CONSUMABLE, 10, 0xFF69C879.toInt(), "Regenerates health for 15 seconds"),

    WOODEN_SWORD("Wooden Sword", ItemCategory.WEAPON, 1, 0xFF9A7446.toInt(), "A simple blade", damage = 8f),
    IRON_SWORD("Iron Sword", ItemCategory.WEAPON, 1, 0xFFB9C3D0.toInt(), "Forged for the frontier", damage = 14f),
    CROSS_SWORD("Cross Sword", ItemCategory.WEAPON, 1, 0xFFD9DCE8.toInt(), "Sweeping cross-cut", damage = 22f, style = WeaponStyle.ARC),
    RING_BLADE("Ring Blade", ItemCategory.WEAPON, 1, 0xFFC9A227.toInt(), "Spins through everything nearby", damage = 18f, style = WeaponStyle.RING),
    BOW("Hunting Bow", ItemCategory.WEAPON, 1, 0xFF8A6238.toInt(), "Fires a piercing arrow", damage = 16f, style = WeaponStyle.PROJECTILE),
    GUN("Flintlock Gun", ItemCategory.WEAPON, 1, 0xFF6E7A86.toInt(), "Heavy shot that punches through", damage = 26f, style = WeaponStyle.PROJECTILE, pierces = true);

    val isUsable: Boolean
        get() = category == ItemCategory.CONSUMABLE ||
            category == ItemCategory.VALUABLE ||
            category == ItemCategory.WEAPON

    val iconLetter: String
        get() = displayName.first().toString()
}
