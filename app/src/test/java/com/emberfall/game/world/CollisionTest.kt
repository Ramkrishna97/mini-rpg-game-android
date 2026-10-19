package com.emberfall.game.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionTest {

    private class FakeQuery(private val solid: Set<Pair<Int, Int>>) : TileQuery {
        override fun isSolid(tx: Int, ty: Int): Boolean = solid.contains(tx to ty)
    }

    @Test
    fun circleTouchingRectEdgeCollides() {
        // Rect 32x32 at origin, circle to the left with radius 10.
        assertTrue(Collision.circleIntersectsRect(-9f, 16f, 10f, 0f, 0f, 32f, 32f))
        assertFalse(Collision.circleIntersectsRect(-11f, 16f, 10f, 0f, 0f, 32f, 32f))
    }

    @Test
    fun circleAtRectCornerUsesRoundedDistance() {
        // Diagonal distance to the corner must be compared, not a box overlap.
        assertFalse(Collision.circleIntersectsRect(-8f, -8f, 10f, 0f, 0f, 32f, 32f))
        assertTrue(Collision.circleIntersectsRect(-3f, -3f, 10f, 0f, 0f, 32f, 32f))
    }

    @Test
    fun movementIntoWallIsStopped() {
        val world = FakeQuery(setOf(1 to 0))
        val result = Collision.MoveResult()
        // Player at tile 0 centre, wall tile starts at x = 32.
        Collision.moveAndSlide(world, 16f, 16f, 20f, 0f, 10f, result)
        assertEquals(16f, result.x, 0.001f)
    }

    @Test
    fun movementIntoOpenSpaceIsApplied() {
        val world = FakeQuery(emptySet())
        val result = Collision.MoveResult()
        Collision.moveAndSlide(world, 16f, 16f, 5f, -4f, 10f, result)
        assertEquals(21f, result.x, 0.001f)
        assertEquals(12f, result.y, 0.001f)
    }

    @Test
    fun diagonalMovementSlidesAlongWall() {
        val world = FakeQuery(setOf(1 to 0))
        val result = Collision.MoveResult()
        // Pushing right and down against a wall: X is blocked, Y still moves.
        Collision.moveAndSlide(world, 16f, 16f, 12f, 6f, 10f, result)
        assertEquals(16f, result.x, 0.001f)
        assertEquals(22f, result.y, 0.001f)
    }
}
