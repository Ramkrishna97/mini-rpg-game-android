package com.emberfall.game.render

import android.graphics.Bitmap
import com.emberfall.game.world.TileType
import com.emberfall.game.world.World

/** Eight-neighbor blob masks; diagonal bits only count when both adjacent sides match. */
object IsoAutotile {
    const val N = 1; const val NE = 2; const val E = 4; const val SE = 8
    const val S = 16; const val SW = 32; const val W = 64; const val NW = 128

    val masks: IntArray = (0..255).filter { canonical(it) == it }.toIntArray()
    private val indexes = IntArray(256) { masks.indexOf(it) }

    fun surface(type: TileType): TileType = when (type) {
        TileType.TREE, TileType.ROCK, TileType.FLOWERS -> TileType.GRASS
        TileType.FOREST_TREE -> TileType.FOREST_FLOOR
        TileType.RUIN_WALL -> TileType.RUIN_FLOOR
        else -> type
    }

    fun canonical(mask: Int): Int {
        var result = mask and (N or E or S or W)
        if ((result and (N or E)) == (N or E)) result = result or (mask and NE)
        if ((result and (S or E)) == (S or E)) result = result or (mask and SE)
        if ((result and (S or W)) == (S or W)) result = result or (mask and SW)
        if ((result and (N or W)) == (N or W)) result = result or (mask and NW)
        return result
    }

    fun variant(mask: Int): Int = indexes[canonical(mask)]

    private val reusableList = ArrayList<Pair<TileType, Int>>(4)
    private val distinctTargets = arrayOf(TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS)

    fun transitions(world: World, x: Int, y: Int, base: TileType): List<Pair<TileType, Int>> {
        reusableList.clear()
        val n0 = surface(world.tileAt(x, y - 1))
        val n1 = surface(world.tileAt(x + 1, y))
        val n2 = surface(world.tileAt(x, y + 1))
        val n3 = surface(world.tileAt(x - 1, y))

        var count = 0
        if (n0 != base) { distinctTargets[0] = n0; count = 1 }
        if (n1 != base && (count == 0 || n1 != distinctTargets[0])) { distinctTargets[count++] = n1 }
        if (n2 != base && (count == 0 || (n2 != distinctTargets[0] && n2 != distinctTargets[1]))) { distinctTargets[count++] = n2 }
        if (n3 != base && (count == 0 || (n3 != distinctTargets[0] && n3 != distinctTargets[1] && n3 != distinctTargets[2]))) { distinctTargets[count++] = n3 }

        for (i in 0 until count) {
            val target = distinctTargets[i]
            var mask = 0
            if (n0 == target) mask = mask or N
            if (n1 == target) mask = mask or E
            if (n2 == target) mask = mask or S
            if (n3 == target) mask = mask or W
            if (surface(world.tileAt(x + 1, y - 1)) == target) mask = mask or NE
            if (surface(world.tileAt(x + 1, y + 1)) == target) mask = mask or SE
            if (surface(world.tileAt(x - 1, y + 1)) == target) mask = mask or SW
            if (surface(world.tileAt(x - 1, y - 1)) == target) mask = mask or NW
            val valid = canonical(mask)
            if (valid != 0) reusableList.add(target to valid)
        }
        return reusableList
    }

    fun overlay(source: Bitmap, mask: Int): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val sx = (x + .5f) / source.width * 2f - 1f
            val sy = (y + .5f) / source.height * 2f - 1f
            val u = (sx + sy + 1f) * .5f
            val v = (sy - sx + 1f) * .5f
            if (u < 0f || v < 0f || u > 1f || v > 1f) continue
            val jag = ((x * 17 + y * 31) % 7) / 90f
            val edge = .15f + jag
            val alpha = when {
                (mask and N != 0 && v < edge) || (mask and E != 0 && u > 1f-edge) ||
                    (mask and S != 0 && v > 1f-edge) || (mask and W != 0 && u < edge) -> 215
                (mask and NE != 0 && u > .72f && v < .28f) ||
                    (mask and SE != 0 && u > .72f && v > .72f) ||
                    (mask and SW != 0 && u < .28f && v > .72f) ||
                    (mask and NW != 0 && u < .28f && v < .28f) -> 140
                else -> 0
            }
            if (alpha > 0) result.setPixel(x, y, (source.getPixel(x, y) and 0xFFFFFF) or (alpha shl 24))
        }
        return result
    }
}
