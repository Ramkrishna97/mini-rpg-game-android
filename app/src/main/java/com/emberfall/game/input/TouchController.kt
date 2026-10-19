package com.emberfall.game.input

import android.view.MotionEvent
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
) {

    private var joystickPointerId = -1
    private var attackPointerId = -1

    fun handle(event: MotionEvent, width: Int, height: Int): Boolean {
        if (width == 0 || height == 0) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    if (input.inventoryOpen || input.settingsOpen ||
                        input.spellPickerOpen || input.mapOpen
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
                        input.mapOpen = false
                        return true
                    }

                    input.spellPickerOpen -> {
                        val card = UiLayout.spellCardAt(
                            x, y, width.toFloat(), height.toFloat(), density,
                        )
                        if (card >= 0) {
                            input.pushCommand(UiCommand.CAST_BASE + card)
                        }
                        input.spellPickerOpen = false
                        return true
                    }

                    input.inventoryOpen -> {
                        handleInventoryTap(x, y, width, height)
                        return true
                    }

                    input.settingsOpen -> {
                        handleSettingsTap(x, y, width, height)
                        return true
                    }
                }

                when {
                    UiLayout.minimapContains(x, y, width, height, density) -> {
                        input.mapOpen = true
                        resetJoystick()
                        joystickPointerId = -1
                    }

                    isOnMagic(x, y, width, height) -> {
                        input.spellPickerOpen = true
                        resetJoystick()
                        joystickPointerId = -1
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

                    isOnInteract(x, y, width, height) -> input.requestInteract()

                    joystickPointerId == -1 && x < width * 0.60f -> {
                        joystickPointerId = pointerId
                        beginJoystick(x, y, width, height)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (input.inventoryOpen || input.settingsOpen ||
                    input.spellPickerOpen || input.mapOpen
                ) {
                    return true
                }
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

            MotionEvent.ACTION_POINTER_UP -> releasePointer(event.getPointerId(event.actionIndex))

            MotionEvent.ACTION_UP -> releasePointer(event.getPointerId(event.actionIndex))

            MotionEvent.ACTION_CANCEL -> {
                joystickPointerId = -1
                attackPointerId = -1
                resetJoystick()
                input.attackDown = false
            }
        }
        return true
    }

    private fun handleInventoryTap(x: Float, y: Float, width: Int, height: Int) {
        if (input.craftingOpen) {
            val row = UiLayout.craftRowAt(
                x, y, width.toFloat(), height.toFloat(), density, Recipes.ALL.size,
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
            return
        }

        val slot = UiLayout.inventorySlotAt(x, y, width.toFloat(), height.toFloat(), density)
        when {
            slot >= 0 -> input.pushCommand(UiCommand.USE_SLOT_BASE + slot)
            slot == -2 -> {
                input.inventoryOpen = false
                input.craftingOpen = false
            }
        }
    }

    private fun handleSettingsTap(x: Float, y: Float, width: Int, height: Int) {
        val row = UiLayout.settingsRowAt(x, y, width.toFloat(), height.toFloat(), density)
        when (row) {
            UiLayout.SETTINGS_LOW,
            UiLayout.SETTINGS_MEDIUM,
            UiLayout.SETTINGS_HIGH -> input.pushCommand(UiCommand.QUALITY_BASE + row)

            UiLayout.SETTINGS_SMOOTH -> input.pushCommand(UiCommand.TOGGLE_SMOOTH)
            UiLayout.SETTINGS_ANTIALIAS -> input.pushCommand(UiCommand.TOGGLE_ANTIALIAS)
            UiLayout.SETTINGS_PARTICLES -> input.pushCommand(UiCommand.TOGGLE_PARTICLES)
            UiLayout.SETTINGS_CLOSE -> input.settingsOpen = false
            -1 -> input.settingsOpen = false
        }
    }

    private fun releasePointer(pointerId: Int) {
        if (pointerId == joystickPointerId) {
            joystickPointerId = -1
            resetJoystick()
        }
        if (pointerId == attackPointerId) {
            attackPointerId = -1
            input.attackDown = false
        }
    }

    private fun isOnAttack(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.attackRadius(density) * 1.28f
        return hypot(
            x - UiLayout.attackX(width, density),
            y - UiLayout.attackY(height, density),
        ) <= radius
    }

    private fun isOnMagic(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.magicRadius(density) * 1.3f
        return hypot(
            x - UiLayout.magicX(width, density),
            y - UiLayout.magicY(height, density),
        ) <= radius
    }

    private fun isOnInteract(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.interactRadius(density) * 1.3f
        return hypot(
            x - UiLayout.interactX(width, density),
            y - UiLayout.interactY(height, density),
        ) <= radius
    }

    private fun isOnBag(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.bagRadius(density) * 1.25f
        return hypot(
            x - UiLayout.bagX(width, density),
            y - UiLayout.bagY(height, density),
        ) <= radius
    }

    private fun isOnGear(x: Float, y: Float, width: Int, height: Int): Boolean {
        val radius = UiLayout.gearRadius(density) * 1.5f
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
