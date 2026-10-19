package com.emberfall.game

import com.emberfall.game.core.Spell
import com.emberfall.game.item.ItemCategory
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.Recipes
import com.emberfall.game.item.WeaponStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {

    @Test
    fun everyRecipeHasCostsAndARealResult() {
        assertTrue(Recipes.ALL.isNotEmpty())
        for (recipe in Recipes.ALL) {
            assertTrue("${recipe.result} has no costs", recipe.costs.isNotEmpty())
            for ((id, count) in recipe.costs) {
                assertTrue("bad cost for ${recipe.result}", count > 0)
                assertTrue(id != recipe.result)
            }
        }
    }

    @Test
    fun allAttackStylesAreCraftable() {
        val styles = Recipes.ALL
            .map { it.result }
            .filter { it.category == ItemCategory.WEAPON }
            .map { it.style }
            .toSet()
        assertTrue(styles.contains(WeaponStyle.MELEE))
        assertTrue(styles.contains(WeaponStyle.ARC))
        assertTrue(styles.contains(WeaponStyle.RING))
        assertTrue(styles.contains(WeaponStyle.PROJECTILE))
    }

    @Test
    fun spellsCostManaAndDealDamage() {
        for (spell in Spell.values()) {
            assertTrue(spell.manaCost > 0f)
            assertTrue(spell.damage > 0f)
            assertTrue(spell.displayName.isNotBlank())
        }
    }

    @Test
    fun fruitAndMeatRestoreHealth() {
        assertTrue(ItemId.FRUIT.heal > 0f)
        assertTrue(ItemId.RAW_MEAT.heal > 0f)
        assertTrue(ItemId.COOKED_MEAT.heal > ItemId.RAW_MEAT.heal)
        assertTrue(ItemId.FRUIT.food > 0f)
    }

    @Test
    fun gunPiercesAndBowDoesNot() {
        assertEquals(WeaponStyle.PROJECTILE, ItemId.BOW.style)
        assertEquals(WeaponStyle.PROJECTILE, ItemId.GUN.style)
        assertTrue(ItemId.GUN.pierces)
        assertTrue(!ItemId.BOW.pierces)
    }
}
