package com.emberfall.game.item

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {

    @Test
    fun stacksItemsUpToTheirLimit() {
        val inventory = Inventory(18)
        assertEquals(0, inventory.add(ItemId.WOOD, 120))
        assertEquals(120, inventory.count(ItemId.WOOD))
        // 50 + 50 + 20 across three slots.
        assertEquals(3, inventory.allStacks().count { it?.id == ItemId.WOOD })
    }

    @Test
    fun returnsLeftoverWhenFull() {
        val inventory = Inventory(1)
        val leftover = inventory.add(ItemId.STONE, 60)
        assertEquals(10, leftover)
        assertEquals(50, inventory.count(ItemId.STONE))
    }

    @Test
    fun removesAcrossStacks() {
        val inventory = Inventory(4)
        inventory.add(ItemId.MAGIC_STONE, 25)
        assertEquals(20, inventory.remove(ItemId.MAGIC_STONE, 20))
        assertEquals(5, inventory.count(ItemId.MAGIC_STONE))
    }

    @Test
    fun removeAtEmptiesSlot() {
        val inventory = Inventory(2)
        inventory.add(ItemId.ELIXIR, 1)
        val taken = inventory.removeAt(0)
        assertEquals(ItemId.ELIXIR, taken?.id)
        assertNull(inventory.slot(0))
    }

    @Test
    fun bestWeaponPrefersHighestDamage() {
        val inventory = Inventory(4)
        inventory.add(ItemId.WOODEN_SWORD, 1)
        inventory.add(ItemId.IRON_SWORD, 1)
        inventory.add(ItemId.WOOD, 5)
        assertEquals(ItemId.IRON_SWORD, inventory.bestWeapon())
    }

    @Test
    fun weaponsDoNotStack() {
        val inventory = Inventory(3)
        inventory.add(ItemId.IRON_SWORD, 1)
        inventory.add(ItemId.IRON_SWORD, 1)
        assertEquals(2, inventory.allStacks().count { it?.id == ItemId.IRON_SWORD })
    }

    @Test
    fun reportsFullInventory() {
        val inventory = Inventory(1)
        inventory.add(ItemId.WOODEN_SWORD, 1)
        assertTrue(inventory.isFull())
        assertTrue(!inventory.hasRoomFor(ItemId.WOOD))
    }
}
