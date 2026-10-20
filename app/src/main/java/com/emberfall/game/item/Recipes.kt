package com.emberfall.game.item

data class Recipe(
    val result: ItemId,
    val costs: List<Pair<ItemId, Int>>,
)

object Recipes {
    val ALL: List<Recipe> = listOf(
        Recipe(
            ItemId.WOODEN_SWORD,
            listOf(ItemId.WOOD to 4, ItemId.FIBER to 2),
        ),
        Recipe(
            ItemId.IRON_SWORD,
            listOf(ItemId.IRON to 6, ItemId.WOOD to 3),
        ),
        Recipe(
            ItemId.BOW,
            listOf(ItemId.WOOD to 6, ItemId.FIBER to 4),
        ),
        Recipe(
            ItemId.CROSS_SWORD,
            listOf(ItemId.IRON to 8, ItemId.WOOD to 4, ItemId.STONE to 2),
        ),
        Recipe(
            ItemId.RING_BLADE,
            listOf(ItemId.IRON to 10, ItemId.STONE to 6, ItemId.MAGIC_STONE to 2),
        ),
        Recipe(
            ItemId.GUN,
            listOf(ItemId.IRON to 12, ItemId.STONE to 8, ItemId.MAGIC_STONE to 3),
        ),
        Recipe(
            ItemId.COOKED_MEAT,
            listOf(ItemId.RAW_MEAT to 1, ItemId.WOOD to 1),
        ),
        Recipe(ItemId.ELIXIR, listOf(ItemId.MAGIC_STONE to 1, ItemId.FRUIT to 2)),
        Recipe(ItemId.MANA_POTION, listOf(ItemId.MAGIC_STONE to 1, ItemId.FIBER to 2)),
        Recipe(ItemId.POWER_POTION, listOf(ItemId.MAGIC_STONE to 1, ItemId.RAW_MEAT to 1)),
        Recipe(ItemId.REGEN_POTION, listOf(ItemId.FRUIT to 2, ItemId.FIBER to 2)),
    )
}
