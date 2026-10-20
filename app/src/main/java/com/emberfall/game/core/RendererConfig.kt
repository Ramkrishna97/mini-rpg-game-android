package com.emberfall.game.core

/**
 * Renderer selection.
 *
 * useVulkanRenderer = true  -> Vulkan path (step 1: debug markers only)
 * useVulkanRenderer = false -> Canvas path (full art, ground-truth reference)
 *
 * Default is false until step 2 brings the tile and sprite art across. If
 * Vulkan fails to initialise at runtime, GameView falls back to Canvas on its own.
 */
object RendererConfig {
    @Volatile
    var useVulkanRenderer: Boolean = true
}
