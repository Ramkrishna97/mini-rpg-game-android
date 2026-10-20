package com.emberfall.game.item

import com.emberfall.game.core.PlayerStats

/**
 * Weapon upgrade ranks from the v2 balance chart. Each rank adds a fixed damage
 * bonus, up to [MAX_RANK]. The material cost is paid for each rank gained.
 */
object WeaponUpgrades {

    const val MAX_RANK = 4

    data class Upgrade(
        val perRankDamage: Int,
        val costPerRank: List<Pair<ItemId, Int>>,
    )

    val TABLE: Map<ItemId, Upgrade> = mapOf(
        ItemId.WOODEN_SWORD to Upgrade(3, listOf(ItemId.WOOD to 3, ItemId.STONE to 1)),
        ItemId.IRON_SWORD to Upgrade(4, listOf(ItemId.IRON to 2, ItemId.STONE to 2)),
        ItemId.BOW to Upgrade(4, listOf(ItemId.WOOD to 2, ItemId.FIBER to 3)),
        ItemId.RING_BLADE to Upgrade(5, listOf(ItemId.IRON to 3, ItemId.MAGIC_STONE to 1)),
        ItemId.CROSS_SWORD to Upgrade(5, listOf(ItemId.IRON to 4, ItemId.MAGIC_STONE to 1)),
        ItemId.GUN to Upgrade(6, listOf(ItemId.IRON to 4, ItemId.MAGIC_STONE to 2)),
    )

    fun upgradeFor(weapon: ItemId): Upgrade? = TABLE[weapon]

    /** Total Attack Damage = Unarmed Base + Weapon Base + Rank Bonus. */
    fun totalDamage(weapon: ItemId, rank: Int): Float {
        val bonus = upgradeFor(weapon)?.perRankDamage ?: 0
        return PlayerStats.UNARMED_DAMAGE + weapon.damage + rank.coerceIn(0, MAX_RANK) * bonus
    }

    /**
     * Materials needed to go from [currentRank] to the next rank, or null if the
     * weapon is already at max rank or cannot be upgraded.
     */
    fun costForNextRank(weapon: ItemId, currentRank: Int): List<Pair<ItemId, Int>>? {
        if (currentRank >= MAX_RANK) return null
        return upgradeFor(weapon)?.costPerRank
    }
}