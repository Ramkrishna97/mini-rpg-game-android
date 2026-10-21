package com.emberfall.game.item

import kotlin.math.max
import kotlin.math.min

data class ItemStack(val id: ItemId, var count: Int)

/**
 * Fixed-slot inventory with stacking. Pure Kotlin so it can be unit tested
 * without an emulator.
 */
class Inventory(val capacity: Int = DEFAULT_CAPACITY) {

    private val slots = arrayOfNulls<ItemStack>(capacity)

    /** Returns the amount that did not fit. */
    fun add(id: ItemId, amount: Int): Int {
        if (amount <= 0) return 0
        // A type has one shared limit, even when it occupies several slots.
        var remaining = amount
        val allowed = min(amount, (MAX_PER_ITEM - count(id)).coerceAtLeast(0))
        if (allowed == 0) return amount
        var toStore = allowed

        for (stack in slots) {
            if (stack == null || stack.id != id || stack.count >= id.maxStack) continue
            val moved = min(id.maxStack - stack.count, toStore)
            stack.count += moved
            toStore -= moved
            remaining -= moved
            if (toStore == 0) return remaining
        }

        for (index in slots.indices) {
            if (slots[index] != null) continue
            val moved = min(id.maxStack, toStore)
            slots[index] = ItemStack(id, moved)
            toStore -= moved
            remaining -= moved
            if (toStore == 0) return remaining
        }
        return remaining
    }

    /** Returns how many were actually removed. */
    fun remove(id: ItemId, amount: Int): Int {
        if (amount <= 0) return 0
        var remaining = amount
        for (index in slots.indices) {
            val stack = slots[index] ?: continue
            if (stack.id != id) continue
            val taken = min(stack.count, remaining)
            stack.count -= taken
            remaining -= taken
            if (stack.count == 0) slots[index] = null
            if (remaining == 0) break
        }
        return amount - remaining
    }

    fun removeAt(index: Int, amount: Int = 1): ItemStack? {
        val stack = slots.getOrNull(index) ?: return null
        val taken = min(stack.count, amount)
        stack.count -= taken
        if (stack.count <= 0) slots[index] = null
        return if (taken > 0) ItemStack(stack.id, taken) else null
    }

    fun count(id: ItemId): Int = slots.sumOf { if (it != null && it.id == id) it.count else 0 }

    fun slot(index: Int): ItemStack? = slots.getOrNull(index)

    fun firstSlot(id: ItemId): Int = slots.indexOfFirst { it != null && it.id == id }

    fun isEmpty(): Boolean = slots.all { it == null }

    fun isFull(): Boolean = slots.none { it == null }

    fun bestWeapon(): ItemId? {
        var best: ItemId? = null
        for (stack in slots) {
            val id = stack?.id ?: continue
            if (id.category != ItemCategory.WEAPON) continue
            if (best == null || id.damage > best.damage) best = id
        }
        return best
    }

    fun allStacks(): List<ItemStack?> = slots.toList()

    fun clearSlot(index: Int) {
        slots[index] = null
    }

    fun clear() {
        for (index in slots.indices) slots[index] = null
    }

    fun hasRoomFor(id: ItemId): Boolean {
        if (count(id) >= MAX_PER_ITEM) return false
        if (slots.any { it != null && it.id == id && it.count < id.maxStack }) return true
        return slots.any { it == null }
    }

    /** Checks a whole harvest or chest reward before consuming its source. */
    fun canFit(rewards: List<Pair<ItemId, Int>>): Boolean {
        var freeSlots = slots.count { it == null }
        for ((id, amount) in rewards.groupBy { it.first }.mapValues { entry -> entry.value.sumOf { it.second } }) {
            if (count(id).toLong() + amount > MAX_PER_ITEM) return false
            val existingRoom = slots.sumOf { stack ->
                if (stack?.id == id) id.maxStack - stack.count else 0
            }
            val remaining = (amount - existingRoom).coerceAtLeast(0)
            val neededSlots = (remaining + id.maxStack - 1) / id.maxStack
            freeSlots -= neededSlots
            if (freeSlots < 0) return false
        }
        return true
    }

    fun canFitAfterRemoving(id: ItemId, amount: Int, rewards: List<Pair<ItemId, Int>>): Boolean {
        val preview = Inventory(capacity)
        for (index in slots.indices) {
            val stack = slots[index] ?: continue
            preview.slots[index] = ItemStack(stack.id, stack.count)
        }
        return preview.remove(id, amount) == amount && preview.canFit(rewards)
    }

    fun totalItems(): Int = slots.sumOf { it?.count ?: 0 }

    companion object {
        const val DEFAULT_CAPACITY = 18
        const val MAX_PER_ITEM = 999
    }
}

/**
 * Backpack tiers from the balance chart. Each upgrade costs gold and materials
 * and grows capacity. Upgrading creates a new [Inventory] and copies stacks over.
 */
enum class PackTier(
    val capacity: Int,
    val goldCost: Int,
    val materialCost: List<Pair<ItemId, Int>>,
) {
    LEATHER_POUCH(18, 0, emptyList()),
    ADVENTURER_BACKPACK(24, 100, listOf(ItemId.HIDE to 10, ItemId.FIBER to 10)),
    EXPLORER_PACK(30, 250, listOf(ItemId.HIDE to 15, ItemId.IRON to 5)),
    VAULT_PACK(36, 500, listOf(ItemId.HIDE to 20, ItemId.MAGIC_STONE to 2));

    /** The tier you upgrade to next, or null if already at the top. */
    fun next(): PackTier? = values().getOrNull(ordinal + 1)
}
