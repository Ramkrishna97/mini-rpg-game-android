package com.emberfall.game.core

/**
 * Health, mana, hunger, XP and leveling. All growth is additive so a save file
 * only needs the level, XP and bonus values to reconstruct the character.
 */
class PlayerStats {

    companion object {
        /** Base damage when no weapon is equipped. */
        const val UNARMED_DAMAGE = 10f

        /** Attack bonus from Power Potion (+25%). */
        const val POWER_BOOST = 0.25f

        /** Stat gains per level, from the balance chart. */
        const val HEALTH_PER_LEVEL = 8f
        const val MANA_PER_LEVEL = 4f
        const val LEVEL_HEAL = 25f
        const val LEVEL_MANA_RESTORE = 12f
    }

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

    /** Seconds of Power Potion attack bonus remaining. */
    var powerBoostTimer = 0f
        private set

    /** Seconds of Regen Potion effect remaining. */
    var regenTimer = 0f
        private set

    /** Health per second from the active Regen Potion. */
    var regenPerSecond = 0f
        private set

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
            maxHealth += HEALTH_PER_LEVEL
            maxMana += MANA_PER_LEVEL
            health = (health + LEVEL_HEAL).coerceAtMost(maxHealth)
            mana = (mana + LEVEL_MANA_RESTORE).coerceAtMost(maxMana)
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

    /**
     * Starts timed effects from a consumed item. Call this after the item's
     * instant heal, mana and food have been applied.
     */
    fun applyPotionEffect(attackBoostSeconds: Float, regenPerSec: Float, regenDuration: Float) {
        if (attackBoostSeconds > 0f) powerBoostTimer = attackBoostSeconds
        if (regenDuration > 0f) {
            regenPerSecond = regenPerSec
            regenTimer = regenDuration
        }
    }

    /** Multiplier applied to weapon damage while Power Potion is active. */
    fun attackMultiplier(): Float = if (powerBoostTimer > 0f) 1f + POWER_BOOST else 1f

    fun update(dt: Float) {
        hunger = (hunger - 0.20f * dt).coerceIn(0f, 100f)
        if (hunger <= 0f) {
            health = (health - 1.1f * dt).coerceAtLeast(0f)
        } else if (hunger > 40f && health < maxHealth) {
            health = (health + 0.35f * dt).coerceAtMost(maxHealth)
        }
        if (regenTimer > 0f) {
            regenTimer = (regenTimer - dt).coerceAtLeast(0f)
            heal(regenPerSecond * dt)
            if (regenTimer == 0f) regenPerSecond = 0f
        }
        if (powerBoostTimer > 0f) powerBoostTimer = (powerBoostTimer - dt).coerceAtLeast(0f)
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