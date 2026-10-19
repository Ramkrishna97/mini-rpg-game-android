package com.emberfall.game.core

/**
 * Health, mana, hunger, XP and leveling. All growth is additive so a save file
 * only needs the level, XP and bonus values to reconstruct the character.
 */
class PlayerStats {

    var level = 1
    var xp = 0

    var maxHealth = 100f
    var health = 100f

    var maxMana = 50f
    var mana = 50f

    var hunger = 100f

    /** Permanent bonuses from boss kills. */
    var bonusHealth = 0f
    var bonusMana = 0f

    fun xpToNext(): Int = 55 + (level - 1) * 45

    fun xpProgress(): Float = (xp.toFloat() / xpToNext()).coerceIn(0f, 1f)

    /** Returns how many levels were gained. */
    fun addXp(amount: Int): Int {
        if (amount <= 0) return 0
        xp += amount
        var gained = 0
        while (xp >= xpToNext()) {
            xp -= xpToNext()
            level += 1
            gained += 1
            maxHealth += 8f
            maxMana += 4f
            health = (health + 25f).coerceAtMost(maxHealth)
            mana = (mana + 12f).coerceAtMost(maxMana)
        }
        return gained
    }

    fun grantBossReward(healthBonus: Float, manaBonus: Float) {
        bonusHealth += healthBonus
        bonusMana += manaBonus
        maxHealth += healthBonus
        maxMana += manaBonus
        health = maxHealth
        mana = maxMana
    }

    fun update(dt: Float) {
        hunger = (hunger - 0.20f * dt).coerceIn(0f, 100f)
        if (hunger <= 0f) {
            health = (health - 1.1f * dt).coerceAtLeast(0f)
        } else if (hunger > 40f && health < maxHealth) {
            health = (health + 0.35f * dt).coerceAtMost(maxHealth)
        }
        if (mana < maxMana) mana = (mana + 1.1f * dt).coerceAtMost(maxMana)
    }

    fun damage(amount: Float) {
        health = (health - amount).coerceAtLeast(0f)
    }

    fun heal(amount: Float) {
        health = (health + amount).coerceAtMost(maxHealth)
    }

    fun restoreMana(amount: Float) {
        mana = (mana + amount).coerceAtMost(maxMana)
    }

    fun feed(amount: Float) {
        hunger = (hunger + amount).coerceAtMost(100f)
    }
}
