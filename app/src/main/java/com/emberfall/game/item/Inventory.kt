package com.emberfall.game.item

import kotlin.math.max
import kotlin.math.min

data class ItemStack(val id: ItemId, var count: Int)

/**
 * Fixed-slot inventory with stacking. Pure Kotlin so it can be unit tested
 * without an emulator.
 */
class Inventory(val capacity: Int = 18) {

    private val slots = arrayOfNulls<ItemStack>(capacity)

    /** Returns the amount that did not fit. */
    fun add(id: ItemId, amount: Int): Int {
        if (amount <= 0) return 0
        var remaining = amount

        for (stack in slots) {
            if (stack == null || stack.id != id || stack.count >= id.maxStack) continue
            val moved = min(id.maxStack - stack.count, remaining)
            stack.count += moved
            remaining -= moved
            if (remaining == 0) return 0
        }

        for (index in slots.indices) {
            if (slots[index] != null) continue
            val moved = min(id.maxStack, remaining)
            slots[index] = ItemStack(id, moved)
            remaining -= moved
            if (remaining == 0) return 0
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
        if (slots.any { it != null && it.id == id && it.count < id.maxStack }) return true
        return slots.any { it == null }
    }

    fun totalItems(): Int = slots.sumOf { it?.count ?: 0 }

    companion object {
        const val DEFAULT_CAPACITY = 18
    }
}
