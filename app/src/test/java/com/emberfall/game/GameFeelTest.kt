package com.emberfall.game

import com.emberfall.game.core.Config
import com.emberfall.game.entity.Boss
import com.emberfall.game.entity.Particle
import com.emberfall.game.entity.Projectile
import com.emberfall.game.item.ItemId
import com.emberfall.game.item.Recipes
import com.emberfall.game.world.Bosses
import com.emberfall.game.world.WorldPoints
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class GameFeelTest {
    @Test fun checkpointsRemainOutsideBossArenasAndSafeZones() {
        for (boss in Bosses.ALL) for (camp in WorldPoints.campfires) {
            val distance = hypot((boss.tileX-camp.tileX).toFloat(), (boss.tileY-camp.tileY).toFloat())
            // Include openSpotNear's six-tile relocation allowance.
            assertTrue("${boss.id} overlaps camp $camp", distance > boss.arenaRadiusTiles + 145f/Config.TILE + 6f)
        }
    }
    @Test fun bossHealthTriplesAndLevelChangesPreserveDamageRatio() {
        for (def in Bosses.ALL) {
            val boss = Boss(def)
            assertEquals(def.maxHealth*3f,boss.maxHp,.001f)
            boss.health = boss.maxHp*.5f
            boss.setLevel(3)
            assertEquals(def.maxHealth*3f*1.44f,boss.maxHp,.01f)
            assertEquals(.5f,boss.health/boss.maxHp,.001f)
            boss.resetFight()
            assertEquals(boss.maxHp,boss.health,0f)
        }
    }
    @Test fun rareMaterialsHaveIncreasingConversionCosts() {
        val chain = listOf(ItemId.STONE,ItemId.IRON,ItemId.MAGIC_STONE)
        var previous = 0
        for (item in chain) {
            val cost = Recipes.ALL.single { it.result == item }.costs.sumOf { it.second }
            assertTrue(cost > previous); previous = cost
        }
    }
    @Test fun footstepsExpireInThreeHundredMilliseconds() {
        val particle = Particle(0f,0f,10f,0f,.3f,0,3f,0f,shrink=true)
        assertTrue(particle.update(.15f))
        assertEquals(.5f,particle.life/particle.maxLife,.001f)
        assertFalse(particle.update(.151f))
    }
    @Test fun hostileProjectileMovesSlowlyAndExpires() {
        val shot = Projectile(0f,0f,125f,0f,12f,0,life=4f,hostile=true)
        shot.update(1f)
        assertEquals(125f,shot.x,0f); assertTrue(shot.hostile); assertFalse(shot.dead)
        shot.update(3f); assertTrue(shot.dead)
    }
}
