package com.emberfall.game.render

/** World-space item in the isometric painter pass. */
data class Renderable(val gridDepth: Float, val isoX: Float, val draw: () -> Unit)
