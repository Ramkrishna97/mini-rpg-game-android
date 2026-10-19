package com.emberfall.game.input

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe bridge between the UI thread (touch events) and the game thread.
 */
class InputState {

    @Volatile var moveX = 0f
    @Volatile var moveY = 0f

    @Volatile var run = false

    @Volatile var joystickActive = false
    @Volatile var joystickBaseX = 0f
    @Volatile var joystickBaseY = 0f
    @Volatile var joystickKnobX = 0f
    @Volatile var joystickKnobY = 0f

    @Volatile var attackDown = false

    @Volatile var inventoryOpen = false
    @Volatile var craftingOpen = false
    @Volatile var settingsOpen = false
    @Volatile var spellPickerOpen = false
    @Volatile var mapOpen = false

    private val attackRequested = AtomicBoolean(false)
    private val interactRequested = AtomicBoolean(false)
    private val commands = ConcurrentLinkedQueue<Int>()

    fun requestAttack() {
        attackRequested.set(true)
    }

    fun consumeAttackRequest(): Boolean = attackRequested.getAndSet(false)

    fun requestInteract() {
        interactRequested.set(true)
    }

    fun consumeInteractRequest(): Boolean = interactRequested.getAndSet(false)

    fun pushCommand(code: Int) {
        commands.add(code)
    }

    fun pollCommand(): Int? = commands.poll()

    fun closePanels() {
        inventoryOpen = false
        craftingOpen = false
        settingsOpen = false
        spellPickerOpen = false
        mapOpen = false
    }
}