package com.emberfall.game.input

import android.view.MotionEvent
import com.emberfall.game.core.Game
import com.emberfall.game.item.ShopCatalog
import com.emberfall.game.item.Recipes
import com.emberfall.game.ui.UiLayout
import com.emberfall.game.util.clamp
import kotlin.math.hypot

/**
 * Turns raw multi-touch events into game input.
 *
 *  - Left ~60% of the screen is a floating joystick.
 *  - Right side: attack, magic, interact, inventory and settings.
 *  - Minimap doubles as a button for the full world map.
 *  - Panels pause the simulation and capture input.
 */
class TouchController(
    private val input: InputState,
    private val density: Float,
    private val inventoryCapacity: Int,
    private val game: Game,
) {

    private var joystickPointerId = -1
    private var attackPointerId = -1
    private var rangedPointerId = -1
    private var lastSettingsY = Float.NaN
    private var resolutionTap = 0
    private var fpsTap = 0
    private var inventoryPointerId = -1
    private var inventoryStartY = Float.NaN

    fun handle(event: MotionEvent, width: Int, height: Int): Boolean {
        if (width == 0 || height == 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    if (input.settingsOpen ||
                        input.spellPickerOpen || input.weaponPickerOpen || input.mapOpen ||
                        input.optionsOpen || input.shopOpen || input.campMenuOpen
                    ) {
                        return true
                    }
                }
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)

                when {
                    input.mapOpen -> {
                        handleMapTap(x, y, width, height)
                        return true
                    }

                    input.optionsOpen -> {
                        handleOptionsTap(x, y, width, height)
                        return true
                    }

                    input.shopOpen -> {
                        handleShopTap(x, y, width, height, pointerId)
                        return true
                    }

                    input.spellPickerOpen -> {
                        val card = UiLayout.spellCardAt(
                            x, y, width.toFloat(), height.toFloat(), density,
                        )
                        if (card >= 0) input.pushCommand(UiCommand.CAST_BASE + card)
                        else if (UiLayout.inventoryPanel(width.toFloat(), height.toFloat(), density).contains(x, y)) input.pushCommand(UiCommand.UPGRADE_MAGIC)
                        input.spellPickerOpen = false
                        return true
                    }

                    input.weaponPickerOpen -> {
                        val row = UiLayout.weaponRowAt(x, y, width.toFloat(), height.toFloat(), density)
                        val weapon = com.emberfall.game.item.ItemId.values().filter { it.category == com.emberfall.game.item.ItemCategory.WEAPON }.getOrNull(row)
                        if (weapon != null) input.pushCommand(UiCommand.EQUIP_WEAPON_BASE + weapon.ordinal)
                        input.weaponPickerOpen = false
                        return true
                    }

                    input.inventoryOpen -> {
                        if (inventoryPointerId == -1) {
                            inventoryPointerId = pointerId
                            inventoryStartY = y
                        }
                        return true
                    }

                    input.settingsOpen -> {
                        lastSettingsY = y
                        handleSettingsTap(x, y, width, height)
                        return true
                    }
                }

                when {
                    input.campMenuOpen -> {
                        val owned = com.emberfall.game.item.ItemId.values().filter { it.category == com.emberfall.game.item.ItemCategory.WEAPON }
                        val panel = UiLayout.workbenchPanel(width.toFloat(), height.toFloat(), density)
                        val close = UiLayout.campCloseButton(width.toFloat(), height.toFloat(), density)
                        if (close.contains(x, y) || !panel.contains(x, y)) { input.campMenuOpen = false; return true }
                        val spellButton = UiLayout.campSpellButton(width.toFloat(), height.toFloat(), density)
                        if (spellButton.contains(x, y)) { input.campMenuOpen = false; input.spellPickerOpen = true; return true }
                        val craftButton = UiLayout.campCraftButton(width.toFloat(), height.toFloat(), density)
                        if (craftButton.contains(x, y)) { input.campMenuOpen = false; input.inventoryOpen = true; input.craftingOpen = true; input.craftScroll = 0; return true }
                        val row = UiLayout.workbenchRowAt(x, y, width.toFloat(), height.toFloat(), density)
                        if (row >= 0) {
                            val weapon = owned.getOrNull(row)
                            if (weapon != null && weapon.category == com.emberfall.game.item.ItemCategory.WEAPON) {
                                input.pushCommand(UiCommand.EQUIP_WEAPON_BASE + weapon.ordinal)
                            }
                        } else {
                            when {
                                UiLayout.workbenchActionButton(0, width.toFloat(), height.toFloat(), density).contains(x, y) -> input.pushCommand(UiCommand.UPGRADE_WEAPON)
                                UiLayout.workbenchActionButton(1, width.toFloat(), height.toFloat(), density).contains(x, y) -> input.pushCommand(UiCommand.COMBINE_WEAPON)
                                UiLayout.workbenchActionButton(2, width.toFloat(), height.toFloat(), density).contains(x, y) -> input.pushCommand(UiCommand.SELL_WEAPON)
                            }
                        }
                        return true
                    }
                    UiLayout.minimapContains(x, y, width, height, density) -> {
                        input.mapOpen = true
                        input.selectedCampfireId = -1
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnSpellSelect(x, y, width, height) -> {
                        input.spellPickerOpen = true
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnMagic(x, y, width, height) -> {
                        input.requestMagic()
                    }

                    rangedPointerId == -1 && isOnRanged(x, y, width, height) -> {
                        rangedPointerId = pointerId
                        input.rangedDown = true
                        input.requestRanged()
                    }

                    attackPointerId == -1 && isOnAttack(x, y, width, height) -> {
                        attackPointerId = pointerId
                        input.attackDown = true
                        input.requestAttack()
                    }

                    isOnBag(x, y, width, height) -> {
                        input.inventoryOpen = true
                        input.craftingOpen = false
                        input.settingsOpen = false
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnGear(x, y, width, height) -> {
                        input.settingsOpen = true
                        input.inventoryOpen = false
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnOptions(x, y) -> {
                        input.optionsOpen = true
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnInteract(x, y, width, height) -> input.requestInteract()

                    joystickPointerId == -1 && x < width * 0.60f -> {
                        joystickPointerId = pointerId
                        beginJoystick(x, y, width, height)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (input.settingsOpen) {
                    val pointer = event.actionIndex
                    val y = event.getY(pointer)
                    if (!lastSettingsY.isNaN()) {
                        val delta = lastSettingsY - y
                        if (kotlin.math.abs(delta) > 3f*density) {
                            // Settings uses tabs; swipes must not change hidden options.
                            lastSettingsY = y
                        }
                    }
                    return true
                }
                if (input.inventoryOpen) {
                    return true
                }
                if (input.shopOpen) {
                    if (input.shopHoldActive) {
                        val pointerIndex = event.findPointerIndex(input.shopHoldPointerId)
                        if (pointerIndex >= 0) {
                            val px = event.getX(pointerIndex)
                            val py = event.getY(pointerIndex)
                            val row = UiLayout.marketRow(input.shopHoldIndex, input.shopScroll, width.toFloat(), height.toFloat(), density)
                            val button = UiLayout.marketActionButton(row, input.shopHoldIsBuy, density)
                            val pad = 14f * density
                            if (px < button.left - pad || px > button.right + pad || py < button.top - pad || py > button.bottom + pad) {
                                input.stopShopHold()
                            }
                        } else {
                            input.stopShopHold()
                        }
                    }
                    return true
                }
                if (input.spellPickerOpen || input.weaponPickerOpen || input.mapOpen ||
                    input.optionsOpen || input.campMenuOpen) return true
                val joystickIndex = event.findPointerIndex(joystickPointerId)
                if (joystickIndex >= 0) {
                    moveJoystick(
                        event.getX(joystickIndex),
                        event.getY(joystickIndex),
                        width,
                        height,
                    )
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                finishInventoryTouch(event, width, height)
                releasePointer(event.getPointerId(event.actionIndex))
            }

            MotionEvent.ACTION_UP -> {
                finishInventoryTouch(event, width, height)
                releasePointer(event.getPointerId(event.actionIndex))
            }

            MotionEvent.ACTION_CANCEL -> {
                joystickPointerId = -1
                attackPointerId = -1
                rangedPointerId = -1
                resetJoystick()
                input.attackDown = false
                input.rangedDown = false
                inventoryPointerId = -1
                inventoryStartY = Float.NaN
                input.stopShopHold()
            }
        }
        return true
    }

    private fun handleInventoryTap(x: Float, y: Float, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (UiLayout.panelPageButton(false, w, h, density).contains(x, y)) {
            scrollInventory(forward = false, width, height)
            return
        }
        if (UiLayout.panelPageButton(true, w, h, density).contains(x, y)) {
            scrollInventory(forward = true, width, height)
            return
        }
        if (input.craftingOpen) {
            val row = UiLayout.craftRowAt(
                x, y, width.toFloat(), height.toFloat(), density, Recipes.ALL.size, input.craftScroll,
            )
            if (row >= 0) {
                input.pushCommand(UiCommand.CRAFT_BASE + row)
            } else if (UiLayout.craftButton(width.toFloat(), height.toFloat(), density).contains(x, y)) {
                input.craftingOpen = false
            } else {
                val panel = UiLayout.inventoryPanel(width.toFloat(), height.toFloat(), density)
                if (!panel.contains(x, y)) {
                    input.inventoryOpen = false
                    input.craftingOpen = false
                }
            }
            return
        }

        if (UiLayout.craftButton(width.toFloat(), height.toFloat(), density).contains(x, y)) {
            input.craftingOpen = true
            input.craftScroll = 0
            return
        }

        val slot = UiLayout.inventorySlotAt(x, y, width.toFloat(), height.toFloat(), density)
        when {
            slot >= 0 -> input.pushCommand(UiCommand.USE_SLOT_BASE + slot + input.inventoryScroll)
            slot == -2 -> {
                input.inventoryOpen = false
                input.craftingOpen = false
            }
        }
    }

    private fun finishInventoryTouch(event: MotionEvent, width: Int, height: Int) {
        val index = event.actionIndex
        if (event.getPointerId(index) != inventoryPointerId) return
        val delta = event.getY(index) - inventoryStartY
        if (kotlin.math.abs(delta) >= 32f * density) {
            scrollInventory(forward = delta < 0f, width, height)
        } else if (input.inventoryOpen) {
            handleInventoryTap(event.getX(index), event.getY(index), width, height)
        }
        inventoryPointerId = -1
        inventoryStartY = Float.NaN
    }

    private fun scrollInventory(forward: Boolean, width: Int, height: Int) {
        if (input.craftingOpen) {
            val pageSize = UiLayout.craftRowsPerPage(width.toFloat(), height.toFloat(), density)
            val lastPage = ((Recipes.ALL.size - 1) / pageSize) * pageSize
            input.craftScroll = (input.craftScroll + if (forward) pageSize else -pageSize).coerceIn(0, lastPage)
        } else {
            val pageSize = UiLayout.INVENTORY_COLUMNS * UiLayout.INVENTORY_ROWS
            val lastPage = ((inventoryCapacity - 1) / pageSize) * pageSize
            input.inventoryScroll = (input.inventoryScroll + if (forward) pageSize else -pageSize).coerceIn(0, lastPage)
        }
    }

    private fun handleMapTap(x: Float, y: Float, width: Int, height: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val selected = input.selectedCampfireId
        if (selected >= 0 && UiLayout.teleportButton(w, h, density).contains(x, y)) {
            input.pushCommand(UiCommand.TELEPORT_BASE + selected)
            input.mapOpen = false
            input.selectedCampfireId = -1
            return
        }
        val playerTx = game.world.tileX(game.player.x)
        val playerTy = game.world.tileY(game.player.y)
        val camp = game.campfires.minByOrNull { fire ->
            val point = UiLayout.regionMapPoint(game.world.tileX(fire.x), game.world.tileY(fire.y),
                playerTx, playerTy, w, h, density)
            hypot(x - point.x, y - point.y)
        }
        if (camp != null) {
            val point = UiLayout.regionMapPoint(game.world.tileX(camp.x), game.world.tileY(camp.y),
                playerTx, playerTy, w, h, density)
            if (hypot(x - point.x, y - point.y) <= 18f * density) {
                input.selectedCampfireId = camp.index
                return
            }
        }
        input.selectedCampfireId = -1
        if (!UiLayout.regionMapRect(w, h, density).contains(x, y)) input.mapOpen = false
    }

    private fun handleOptionsTap(x: Float, y: Float, width: Int, height: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val panel = UiLayout.optionsPanel(w, h, density)
        if (!panel.contains(x, y) || UiLayout.optionsClose(w, h, density).contains(x, y)) {
            input.optionsOpen = false
            return
        }
        val rows = if (game.isHackMode) 4 else 3
        val choice = (0 until rows * 2).firstOrNull { UiLayout.optionsCell(it, w, h, density, rows).contains(x, y) } ?: return
        input.optionsOpen = false
        when (choice) {
            0 -> { input.inventoryOpen = true; input.craftingOpen = false }
            1 -> input.weaponPickerOpen = true
            2 -> input.campMenuOpen = true
            3 -> { input.shopOpen = true; input.shopScroll = 0 }
            4 -> { input.inventoryOpen = true; input.craftingOpen = true; input.craftScroll = 0 }
            5 -> input.settingsOpen = true
            6 -> input.pushCommand(UiCommand.HACK_TOGGLE_BOOSTS)
            7 -> input.pushCommand(UiCommand.HACK_SUMMON_DRAGON)
        }
    }

    private fun handleShopTap(x: Float, y: Float, width: Int, height: Int, pointerId: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val panel = UiLayout.inventoryPanel(w, h, density)
        if (!panel.contains(x, y)) {
            input.shopOpen = false
            input.stopShopHold()
            return
        }
        val pageSize = UiLayout.marketRowsPerPage(w, h, density)
        val lastPage = ((ShopCatalog.offers.size - 1) / pageSize) * pageSize
        if (UiLayout.panelPageButton(false, w, h, density).contains(x, y)) {
            input.shopScroll = (input.shopScroll - pageSize).coerceAtLeast(0)
            input.stopShopHold()
            return
        }
        if (UiLayout.panelPageButton(true, w, h, density).contains(x, y)) {
            input.shopScroll = (input.shopScroll + pageSize).coerceAtMost(lastPage)
            input.stopShopHold()
            return
        }
        for (index in input.shopScroll until minOf(ShopCatalog.offers.size, input.shopScroll + pageSize)) {
            val row = UiLayout.marketRow(index, input.shopScroll, w, h, density)
            if (UiLayout.marketActionButton(row, true, density).contains(x, y)) {
                input.pushCommand(UiCommand.BUY_ITEM_BASE + index)
                input.startShopHold(pointerId, index, isBuy = true)
                return
            }
            if (UiLayout.marketActionButton(row, false, density).contains(x, y)) {
                input.pushCommand(UiCommand.SELL_ITEM_BASE + index)
                input.startShopHold(pointerId, index, isBuy = false)
                return
            }
        }
        input.stopShopHold()
    }

    private fun handleSettingsTap(x: Float, y: Float, width: Int, height: Int) {
        val panel = UiLayout.settingsPanel(width.toFloat(), height.toFloat(), density)
        for (tab in 0..2) if (UiLayout.settingsTab(tab,width.toFloat(),height.toFloat(),density).contains(x,y)) {
            input.settingsScroll = tab; return
        }
        val row = UiLayout.settingsRowAt(x, y, input.settingsScroll, width.toFloat(), height.toFloat(), density)
        when (row) {
            UiLayout.SETTINGS_LOW,
            UiLayout.SETTINGS_MEDIUM,
            UiLayout.SETTINGS_HIGH -> input.pushCommand(UiCommand.QUALITY_BASE + row)

            UiLayout.SETTINGS_SMOOTH -> input.pushCommand(UiCommand.TOGGLE_SMOOTH)
            UiLayout.SETTINGS_ANTIALIAS -> input.pushCommand(UiCommand.TOGGLE_ANTIALIAS)
            UiLayout.SETTINGS_PARTICLES -> input.pushCommand(UiCommand.TOGGLE_PARTICLES)
            UiLayout.SETTINGS_MUSIC -> input.pushCommand(UiCommand.TOGGLE_MUSIC)
            UiLayout.SETTINGS_SOUND -> input.pushCommand(UiCommand.TOGGLE_SOUND)
            UiLayout.SETTINGS_VIBRATION -> input.pushCommand(UiCommand.TOGGLE_VIBRATION)
            UiLayout.SETTINGS_RESOLUTION -> { resolutionTap = (resolutionTap + 1) % 5; input.pushCommand(UiCommand.RESOLUTION_BASE + resolutionTap) }
            UiLayout.SETTINGS_FPS -> { fpsTap = (fpsTap + 1) % 4; input.pushCommand(UiCommand.FPS_BASE + fpsTap) }
            UiLayout.SETTINGS_NEW_GAME -> input.pushCommand(UiCommand.NEW_GAME)
            UiLayout.SETTINGS_SAVE -> input.pushCommand(UiCommand.SAVE_SLOT_1)
            UiLayout.SETTINGS_SAVE_2 -> input.pushCommand(UiCommand.SAVE_SLOT_2)
            UiLayout.SETTINGS_SAVE_3 -> input.pushCommand(UiCommand.SAVE_SLOT_3)
            UiLayout.SETTINGS_LOAD -> input.pushCommand(UiCommand.LOAD_SLOT_1)
            UiLayout.SETTINGS_LOAD_2 -> input.pushCommand(UiCommand.LOAD_SLOT_2)
            UiLayout.SETTINGS_LOAD_3 -> input.pushCommand(UiCommand.LOAD_SLOT_3)
            UiLayout.SETTINGS_CLOSE -> input.settingsOpen = false
            -1 -> if (!panel.contains(x, y)) input.settingsOpen = false
        }
    }

    private fun releasePointer(pointerId: Int) {
        if (pointerId == input.shopHoldPointerId) {
            input.stopShopHold()
        }
        if (pointerId == joystickPointerId) {
            joystickPointerId = -1
            resetJoystick()
        }
        if (pointerId == attackPointerId) {
            attackPointerId = -1
            input.attackDown = false
        }
        if (pointerId == rangedPointerId) {
            rangedPointerId = -1
            input.rangedDown = false
        }
    }

    private fun isOnRanged(x: Float, y: Float, width: Int, height: Int): Boolean =
        hypot(x - UiLayout.rangedX(width, density), y - UiLayout.rangedY(height, density)) <=
            UiLayout.rangedRadius(density) * 1.1f

    private fun isOnOptions(x: Float, y: Float): Boolean = UiLayout.optionsButton(density).contains(x, y)

    private fun isOnAttack(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.attackRadius(density) * 1.08f
        return hypot(
            x - UiLayout.attackX(width, density),
            y - UiLayout.attackY(height, density),
        ) <= radius
    }

    private fun isOnMagic(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.magicRadius(density) * 1.1f
        return hypot(
            x - UiLayout.magicX(width, density),
            y - UiLayout.magicY(height, density),
        ) <= radius
    }

    private fun isOnSpellSelect(x: Float, y: Float, width: Int, height: Int): Boolean {
        return hypot(x - UiLayout.spellSelectX(width, density), y - UiLayout.spellSelectY(height, density)) <= UiLayout.spellSelectRadius(density) * 1.1f
    }

    private fun isOnInteract(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.interactRadius(density) * 1.08f
        return hypot(
            x - UiLayout.interactX(width, density),
            y - UiLayout.interactY(height, density),
        ) <= radius
    }

    private fun isOnBag(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.bagRadius(density) * 1.12f
        return hypot(
            x - UiLayout.bagX(width, density),
            y - UiLayout.bagY(height, density),
        ) <= radius
    }

    private fun isOnGear(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.gearRadius(density) * 1.15f
        return hypot(
            x - UiLayout.gearX(width, density),
            y - UiLayout.gearY(height, density),
        ) <= radius
    }

    private fun beginJoystick(x: Float, y: Float, width: Int, height: Int) {
        val radius = UiLayout.joystickRadius(density)
        val baseX = UiLayout.clampBaseX(x, width, radius, density)
        val baseY = UiLayout.clampBaseY(y, height, radius, density)

        input.joystickActive = true
        input.joystickBaseX = baseX
        input.joystickBaseY = baseY
        input.joystickKnobX = baseX
        input.joystickKnobY = baseY
        input.moveX = 0f
        input.moveY = 0f
        input.run = false
    }

    private fun moveJoystick(x: Float, y: Float, width: Int, height: Int) {
        val radius = UiLayout.joystickRadius(density)
        val baseX = input.joystickBaseX
        val baseY = input.joystickBaseY
        val dx = x - baseX
        val dy = y - baseY
        val distance = hypot(dx, dy)

        val magnitude = clamp(distance / (radius * 0.62f), 0f, 1f)
        if (magnitude < 0.15f) {
            input.moveX = 0f
            input.moveY = 0f
            input.run = false
        } else {
            input.moveX = dx / distance * magnitude
            input.moveY = dy / distance * magnitude
            input.run = magnitude > 0.94f
        }

        val knobDistance = minOf(distance, radius * 0.72f)
        if (distance > 0.0001f) {
            input.joystickKnobX = baseX + dx / distance * knobDistance
            input.joystickKnobY = baseY + dy / distance * knobDistance
        } else {
            input.joystickKnobX = baseX
            input.joystickKnobY = baseY
        }

        input.joystickBaseX = UiLayout.clampBaseX(baseX, width, radius, density)
        input.joystickBaseY = UiLayout.clampBaseY(baseY, height, radius, density)
    }

    private fun resetJoystick() {
        input.joystickActive = false
        input.moveX = 0f
        input.moveY = 0f
        input.run = false
    }
}
