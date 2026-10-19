package com.emberfall.game.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossArenaTest {

    @Test
    fun everyArenaIsFlatWalkableRuinFloor() {
        val world = World(12345)
        for (boss in Bosses.ALL) {
            val radius = boss.arenaRadiusTiles.toInt()
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    if (dx * dx + dy * dy > radius * radius) continue
                    val tx = boss.tileX + dx
                    val ty = boss.tileY + dy
                    assertEquals(
                        "${boss.id} arena tile ($dx,$dy)",
                        TileType.RUIN_FLOOR,
                        world.tileAt(tx, ty),
                    )
                    assertFalse(world.isSolid(tx, ty))
                }
            }
        }
    }

    @Test
    fun arenaBiomeIsReportedAsRuins() {
        val world = World(777)
        for (boss in Bosses.ALL) {
            assertEquals(Biome.RUINS, world.biomeAt(boss.tileX, boss.tileY))
        }
    }

    @Test
    fun bossesAreSpreadFarApart() {
        val minimumDistance = 40f
        for (i in Bosses.ALL.indices) {
            for (j in i + 1 until Bosses.ALL.size) {
                val a = Bosses.ALL[i]
                val b = Bosses.ALL[j]
                val dx = (a.tileX - b.tileX).toFloat()
                val dy = (a.tileY - b.tileY).toFloat()
                val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                assertTrue("bosses $i and $j overlap", distance > minimumDistance)
            }
        }
    }

    @Test
    fun bossRewardsScaleWithTier() {
        val ordered = Bosses.ALL.sortedBy { it.maxHealth }
        for (i in 1 until ordered.size) {
            assertTrue(ordered[i].xpReward > ordered[i - 1].xpReward)
            assertTrue(ordered[i].healthReward >= ordered[i - 1].healthReward)
            assertTrue(ordered[i].loot.isNotEmpty())
        }
    }
}
