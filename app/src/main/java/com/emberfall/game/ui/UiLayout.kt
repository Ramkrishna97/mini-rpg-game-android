package com.emberfall.game.ui

import android.graphics.RectF
import com.emberfall.game.util.clamp
import kotlin.math.min

/**
 * Single source of truth for every touch target and panel rectangle. The input
 * controller and the renderers both call these functions, so hit boxes and
 * visuals can never drift apart.
 */
object UiLayout {

    // ---- Floating joystick -------------------------------------------------

    fun joystickRadius(density: Float): Float = 62f * density

    fun joystickHomeX(density: Float): Float = 96f * density

    fun joystickHomeY(height: Int, density: Float): Float = height - 112f * density

    fun clampBaseX(x: Float, width: Int, radius: Float, density: Float): Float =
        clamp(x, radius + 8f * density, width - radius - 8f * density)

    fun clampBaseY(y: Float, height: Int, radius: Float, density: Float): Float =
        clamp(y, radius + 8f * density, height - radius - 8f * density)

    // ---- Action buttons ----------------------------------------------------

    fun attackRadius(density: Float): Float = 46f * density

    fun attackX(width: Int, density: Float): Float = width - 88f * density

    fun attackY(height: Int, density: Float): Float = height - 100f * density

    fun interactRadius(density: Float): Float = 30f * density

    fun interactX(width: Int, density: Float): Float = width - 170f * density

    fun interactY(height: Int, density: Float): Float = height - 118f * density

    fun bagRadius(density: Float): Float = 27f * density

    fun bagX(width: Int, density: Float): Float = width - 88f * density

    fun bagY(height: Int, density: Float): Float = height - 198f * density

    fun gearRadius(density: Float): Float = 19f * density

    fun gearX(width: Int, density: Float): Float = width - 33f * density

    fun gearY(height: Int, density: Float): Float = (14f + 122f + 26f) * density

    // ---- Minimap -----------------------------------------------------------

    fun minimapSize(density: Float): Float = 122f * density

    fun minimapLeft(width: Int, density: Float): Float = width - 14f * density - minimapSize(density)

    fun minimapTop(density: Float): Float = 14f * density

    // ---- Inventory panel ---------------------------------------------------

    const val INVENTORY_COLUMNS = 6
    const val INVENTORY_ROWS = 3

    fun inventoryPanel(width: Float, height: Float, density: Float): RectF {
        val panelWidth = min(width * 0.64f, 660f * density)
        val panelHeight = min(height * 0.76f, 400f * density)
        val left = (width - panelWidth) / 2f
        val top = (height - panelHeight) / 2f
        return RectF(left, top, left + panelWidth, top + panelHeight)
    }

    fun inventoryCell(
        index: Int,
        width: Float,
        height: Float,
        density: Float,
    ): RectF {
        val panel = inventoryPanel(width, height, density)
        val padding = 16f * density
        val gap = 4f * density
        val gridLeft = panel.left + padding
        val gridTop = panel.top + 54f * density
        val gridWidth = panel.width() - padding * 2f
        val gridHeight = panel.height() - 86f * density
        val cellWidth = (gridWidth - gap * (INVENTORY_COLUMNS - 1)) / INVENTORY_COLUMNS
        val cellHeight = (gridHeight - gap * (INVENTORY_ROWS - 1)) / INVENTORY_ROWS
        val column = index % INVENTORY_COLUMNS
        val row = index / INVENTORY_COLUMNS
        val left = gridLeft + column * (cellWidth + gap)
        val top = gridTop + row * (cellHeight + gap)
        return RectF(left, top, left + cellWidth, top + cellHeight)
    }

    /** -2 = outside panel, -1 = panel but empty space, >= 0 = slot index. */
    fun inventorySlotAt(x: Float, y: Float, width: Float, height: Float, density: Float): Int {
        val panel = inventoryPanel(width, height, density)
        if (!panel.contains(x, y)) return -2
        for (index in 0 until INVENTORY_COLUMNS * INVENTORY_ROWS) {
            if (inventoryCell(index, width, height, density).contains(x, y)) return index
        }
        return -1
    }

    // ---- Settings panel ----------------------------------------------------

    const val SETTINGS_LOW = 0
    const val SETTINGS_MEDIUM = 1
    const val SETTINGS_HIGH = 2
    const val SETTINGS_SMOOTH = 3
    const val SETTINGS_ANTIALIAS = 4
    const val SETTINGS_PARTICLES = 5
    const val SETTINGS_MUSIC = 6
    const val SETTINGS_SOUND = 7
    const val SETTINGS_VIBRATION = 8
    const val SETTINGS_RESOLUTION = 9
    const val SETTINGS_FPS = 10
    const val SETTINGS_SAVE = 11
    const val SETTINGS_LOAD = 12
    const val SETTINGS_CLOSE = 13

    fun settingsPanel(width: Float, height: Float, density: Float): RectF {
        val panelWidth = min(width * 0.70f, 520f * density)
        val panelHeight = min(height * 0.98f, 560f * density)
        val left = (width - panelWidth) / 2f
        val top = (height - panelHeight) / 2f
        return RectF(left, top, left + panelWidth, top + panelHeight)
    }

    fun settingsRow(index: Int, scroll: Int, width: Float, height: Float, density: Float): RectF {
        val panel = settingsPanel(width, height, density)
        val padding = 18f * density
        val rowHeight = 22f * density
        val gap = 2f * density
        val top = panel.top + 38f * density + (index - scroll) * (rowHeight + gap)
        return RectF(panel.left + padding, top, panel.right - padding, top + rowHeight)
    }

    fun settingsRowAt(x: Float, y: Float, scroll: Int, width: Float, height: Float, density: Float): Int {
        val panel = settingsPanel(width, height, density)
        if (!panel.contains(x, y)) return -1
        for (index in 0..SETTINGS_CLOSE) {
            if (settingsRow(index, scroll, width, height, density).contains(x, y)) return index
        }
        return -1
    }

    // ---- Magic button + spell picker --------------------------------------

    fun magicRadius(density: Float): Float = 36f * density

    fun magicX(width: Int, density: Float): Float = width - 170f * density

    fun magicY(height: Int, density: Float): Float = height - 198f * density

    fun spellSelectX(width: Int, density: Float): Float = width - 248f * density
    fun spellSelectY(height: Int, density: Float): Float = height - 198f * density
    fun spellSelectRadius(density: Float): Float = 24f * density

    fun weaponSelectX(width: Int, density: Float): Float = attackX(width, density)
    fun weaponSelectY(height: Int, density: Float): Float = attackY(height, density) - 34f * density
    fun weaponSelectRadius(density: Float): Float = 16f * density

    fun weaponRow(index: Int, width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        val rowHeight = (panel.height() - 105f * density) / 6f
        val top = panel.top + 48f * density + index * rowHeight
        return RectF(panel.left + 20f * density, top, panel.right - 20f * density, top + rowHeight - 3f * density)
    }

    fun weaponRowAt(x: Float, y: Float, width: Float, height: Float, density: Float): Int =
        (0 until 6).firstOrNull { weaponRow(it, width, height, density).contains(x, y) } ?: -1

    fun spellPanel(width: Float, height: Float, density: Float): RectF {
        val panelWidth = min(width * 0.56f, 520f * density)
        val panelHeight = min(height * 0.82f, 400f * density)
        val left = (width - panelWidth) / 2f
        val top = (height - panelHeight) / 2f
        return RectF(left, top, left + panelWidth, top + panelHeight)
    }

    fun spellCard(index: Int, width: Float, height: Float, density: Float): RectF {
        val panel = spellPanel(width, height, density)
        val padding = 18f * density
        val gap = 12f * density
        val cardW = (panel.width() - padding * 2f - gap) / 2f
        val cardH = (panel.height() - 58f * density - padding - gap) / 2f
        val col = index % 2
        val row = index / 2
        val left = panel.left + padding + col * (cardW + gap)
        val top = panel.top + 48f * density + row * (cardH + gap)
        return RectF(left, top, left + cardW, top + cardH)
    }

    fun spellCardAt(x: Float, y: Float, width: Float, height: Float, density: Float): Int {
        for (index in 0 until 4) {
            if (spellCard(index, width, height, density).contains(x, y)) return index
        }
        return -1
    }

    fun campCloseButton(width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        return RectF(panel.right-dp(78f,density), panel.top+dp(10f,density), panel.right-dp(12f,density), panel.top+dp(40f,density))
    }

    fun campSpellButton(width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        return RectF(panel.left+dp(16f,density), panel.top+dp(10f,density), panel.left+dp(100f,density), panel.top+dp(40f,density))
    }

    fun campCraftButton(width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        return RectF(panel.centerX()-42f*density, panel.top+10f*density,
            panel.centerX()+42f*density, panel.top+40f*density)
    }

    private fun dp(value: Float, density: Float) = value * density

    // ---- Full map ----------------------------------------------------------

    fun minimapContains(x: Float, y: Float, width: Int, height: Int, density: Float): Boolean {
        val size = minimapSize(density)
        val left = minimapLeft(width, density)
        val top = minimapTop(density)
        return x >= left && x <= left + size && y >= top && y <= top + size
    }

    // ---- Crafting rows -----------------------------------------------------

    fun craftButton(width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        return RectF(
            panel.right - 108f * density,
            panel.top + 12f * density,
            panel.right - 18f * density,
            panel.top + 38f * density,
        )
    }

    fun craftRow(index: Int, width: Float, height: Float, density: Float, scroll: Int = 0): RectF {
        val panel = inventoryPanel(width, height, density)
        val padding = 18f * density
        val rowHeight = 30f * density
        val gap = 4f * density
        val top = panel.top + 58f * density + (index-scroll) * (rowHeight + gap)
        return RectF(panel.left + padding, top, panel.right - padding, top + rowHeight)
    }

    fun craftRowsPerPage(width: Float, height: Float, density: Float): Int =
        ((inventoryPanel(width, height, density).height() / density - 86f) / 34f).toInt().coerceAtLeast(1)

    fun craftPageButton(width: Float, height: Float, density: Float): RectF {
        val panel = inventoryPanel(width, height, density)
        return RectF(panel.right-95f*density,panel.bottom-27f*density,
            panel.right-14f*density,panel.bottom-3f*density)
    }

    fun craftRowAt(x: Float, y: Float, width: Float, height: Float, density: Float, count: Int, scroll: Int = 0): Int {
        val panel = inventoryPanel(width, height, density)
        for (index in scroll until min(count,scroll+craftRowsPerPage(width,height,density))) {
            val row = craftRow(index, width, height, density, scroll)
            if (row.bottom <= panel.bottom-28f*density && row.contains(x, y)) return index
        }
        return -1
    }
}
