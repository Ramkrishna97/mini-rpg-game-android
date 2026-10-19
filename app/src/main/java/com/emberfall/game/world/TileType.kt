package com.emberfall.game.world

/**
 * Every tile the world can contain. [solid] drives collision; the renderer
 * decides how each one looks.
 */
enum class TileType(val id: Int, val solid: Boolean, val label: String) {
    GRASS(0, false, "Grass"),
    FOREST_FLOOR(1, false, "Forest floor"),
    FLOWERS(2, false, "Flowers"),
    DIRT(3, false, "Dirt"),
    SAND(4, false, "Sand"),
    WATER(5, true, "Water"),
    DEEP_WATER(6, true, "Deep water"),
    TREE(7, true, "Tree"),
    ROCK(8, true, "Rock"),
    RUIN_FLOOR(9, false, "Ruins"),
    RUIN_WALL(10, true, "Ruin wall"),
    FOREST_TREE(11, true, "Forest tree");

    companion object {
        private val ALL = values()
        fun fromId(id: Int): TileType = ALL[id]
    }
}
