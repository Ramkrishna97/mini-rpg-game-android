package com.emberfall.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatsTest {

    @Test
    fun xpBelowThresholdDoesNotLevel() {
        val stats = PlayerStats()
        assertEquals(0, stats.addXp(10))
        assertEquals(1, stats.level)
        assertEquals(10, stats.xp)
    }

    @Test
    fun levelingRaisesMaxHealthAndMana() {
        val stats = PlayerStats()
        val beforeHealth = stats.maxHealth
        val beforeMana = stats.maxMana
        val levels = stats.addXp(200)
        assertTrue("should gain at least one level", levels >= 1)
        assertTrue(stats.maxHealth > beforeHealth)
        assertTrue(stats.maxMana > beforeMana)
        assertEquals(1 + levels, stats.level)
    }

    @Test
    fun bossRewardIsPermanentAndHeals() {
        val stats = PlayerStats()
        stats.damage(40f)
        stats.grantBossReward(10f, 5f)
        assertEquals(110f, stats.maxHealth, 0.001f)
        assertEquals(55f, stats.maxMana, 0.001f)
        assertEquals(stats.maxHealth, stats.health, 0.001f)
        assertEquals(stats.maxMana, stats.mana, 0.001f)
    }

    @Test
    fun hungerDrainsAndStarvesSlowly() {
        val stats = PlayerStats()
        repeat(600) { stats.update(0.1f) }
        assertTrue("hunger should drop", stats.hunger < 100f)
        assertTrue("player should still be alive after 60s", stats.health > 0f)
    }

    @Test
    fun manaRegenerates() {
        val stats = PlayerStats()
        stats.restoreMana(-30f)
        val before = stats.mana
        repeat(60) { stats.update(0.1f) }
        assertTrue(stats.mana > before)
    }
}
