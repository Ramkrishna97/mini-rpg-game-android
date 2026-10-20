package com.emberfall.game.item

/** Data-only prices; visual menus and transaction code share this catalog. */
data class ShopOffer(val item: ItemId, val goldPrice: Int = 0, val diamondPrice: Int = 0)

object ShopCatalog {
    val offers = listOf(
        ShopOffer(ItemId.FRUIT, goldPrice = 8),
        ShopOffer(ItemId.COOKED_MEAT, goldPrice = 22),
        ShopOffer(ItemId.RAW_MEAT, goldPrice = 12),
        ShopOffer(ItemId.ELIXIR, goldPrice = 40),
        ShopOffer(ItemId.MANA_POTION, goldPrice = 35),
        ShopOffer(ItemId.POWER_POTION, goldPrice = 65),
        ShopOffer(ItemId.REGEN_POTION, goldPrice = 55),
        ShopOffer(ItemId.WOOD, goldPrice = 5),
        ShopOffer(ItemId.STONE, goldPrice = 8),
        ShopOffer(ItemId.FIBER, goldPrice = 6),
        ShopOffer(ItemId.IRON, goldPrice = 24),
        ShopOffer(ItemId.HIDE, goldPrice = 14),
        ShopOffer(ItemId.WOODEN_SWORD, goldPrice = 80),
        ShopOffer(ItemId.IRON_SWORD, goldPrice = 220),
        ShopOffer(ItemId.CROSS_SWORD, goldPrice = 360),
        ShopOffer(ItemId.RING_BLADE, goldPrice = 340),
        ShopOffer(ItemId.BOW, goldPrice = 260),
        ShopOffer(ItemId.MAGIC_STONE, goldPrice = 90, diamondPrice = 1),
        ShopOffer(ItemId.GUN, goldPrice = 560, diamondPrice = 4),
    )

    fun sellGold(item: ItemId): Int =
        (offers.firstOrNull { it.item == item }?.goldPrice ?: 12).coerceAtLeast(2) / 2
}
