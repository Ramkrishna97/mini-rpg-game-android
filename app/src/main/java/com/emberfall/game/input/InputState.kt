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
    @Volatile var rangedDown = false

    @Volatile var inventoryOpen = false
    @Volatile var craftingOpen = false
    @Volatile var craftScroll = 0
    @Volatile var inventoryScroll = 0
    @Volatile var settingsOpen = false
    @Volatile var settingsScroll = 0
    @Volatile var spellPickerOpen = false
    @Volatile var mapOpen = false
    @Volatile var optionsOpen = false
    @Volatile var shopOpen = false
    @Volatile var shopScroll = 0
    @Volatile var selectedCampfireId = -1
    @Volatile var campMenuOpen = false
    @Volatile var weaponPickerOpen = false
    @Volatile var musicEnabled = true
    @Volatile var soundEnabled = false

    val bossSoundRequested = AtomicBoolean(false)

    private val attackRequested = AtomicBoolean(false)
    private val rangedRequested = AtomicBoolean(false)
    private val interactRequested = AtomicBoolean(false)
    private val magicRequested = AtomicBoolean(false)
    private val commands = ConcurrentLinkedQueue<Int>()

    fun requestAttack() {
        attackRequested.set(true)
    }

    fun consumeAttackRequest(): Boolean = attackRequested.getAndSet(false)

    fun requestRanged() { rangedRequested.set(true) }
    fun consumeRangedRequest(): Boolean = rangedRequested.getAndSet(false)

    fun requestInteract() {
        interactRequested.set(true)
    }

    fun consumeInteractRequest(): Boolean = interactRequested.getAndSet(false)

    fun requestMagic() { magicRequested.set(true) }
    fun consumeMagicRequest(): Boolean = magicRequested.getAndSet(false)

    fun pushAudioState(music: Boolean, sound: Boolean) { musicEnabled = music; soundEnabled = sound }

    fun pushCommand(code: Int) {
        commands.add(code)
    }

    fun pollCommand(): Int? = commands.poll()

    fun closePanels() {
        attackDown = false
        rangedDown = false
        inventoryOpen = false
        craftingOpen = false
        craftScroll = 0
        inventoryScroll = 0
        settingsOpen = false
        settingsScroll = 0
        spellPickerOpen = false
        mapOpen = false
        optionsOpen = false
        shopOpen = false
        shopScroll = 0
        selectedCampfireId = -1
        campMenuOpen = false
        weaponPickerOpen = false
    }
}
