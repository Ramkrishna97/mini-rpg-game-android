package com.emberfall.game.core

enum class GraphicsQuality { LOW, MEDIUM, HIGH }

/**
 * Player-facing graphics options. They are deliberately cheap to apply: the
 * renderer reads them every frame, so no restart is needed.
 */
class GraphicsSettings {
    var quality = GraphicsQuality.MEDIUM
    var smoothTextures = true
    var antiAliasing = true
    var particles = true

    fun particleScale(): Float = when (quality) {
        GraphicsQuality.LOW -> 0.35f
        GraphicsQuality.MEDIUM -> 1f
        GraphicsQuality.HIGH -> 1.7f
    }

    fun label(): String = quality.name
}
