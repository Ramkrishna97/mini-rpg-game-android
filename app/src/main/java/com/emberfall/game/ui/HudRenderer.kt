package com.emberfall.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.emberfall.game.core.Game
import com.emberfall.game.entity.Boss
import com.emberfall.game.input.InputState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space HUD: vitals, minimap, controls, boss bar and overlays.
 * Everything is drawn after the world so it never inherits the camera.
 */
class HudRenderer(context: Context) {

    private val density = context.resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = dp(11f)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = dp(8.5f)
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = dp(20f)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private val minimap = MinimapRenderer()
    private val inventoryPanel = InventoryPanelRenderer()
    private val settingsPanel = SettingsPanelRenderer()
    private val spellPicker = SpellPickerRenderer()
    private val worldMap = WorldMapRenderer()

    private var cachedMinute = -1
    private var cachedTime = "07:30"

    fun update(dt: Float, game: Game) {
        minimap.update(dt, game)
    }

    fun draw(canvas: Canvas, game: Game, input: InputState, width: Float, height: Float) {
        drawHurtFlash(canvas, game, width, height)

        if (input.mapOpen) {
            worldMap.draw(canvas, game, width, height, density)
            return
        }

        if (input.inventoryOpen) {
            inventoryPanel.draw(canvas, game, width, height, density, input.craftingOpen)
            return
        }

        if (input.spellPickerOpen) {
            spellPicker.draw(canvas, game, width, height, density)
            return
        }
        if (input.settingsOpen) {
            settingsPanel.draw(canvas, game, width, height, density)
            return
        }

        drawVitals(canvas, game)
        minimap.draw(canvas, game, width, height, density)
        drawFps(canvas, game, width)
        drawBossBar(canvas, game, width)
        drawJoystick(canvas, input, width, height)
        drawButtons(canvas, game, input, width, height)
        drawPrompt(canvas, game, width, height)
        drawBanner(canvas, game, width, height)
        drawToast(canvas, game, width, height)
        drawHint(canvas, game, width, height)

        if (game.deathTimer > 0f) drawDeath(canvas, game, width, height)
    }

    private fun drawVitals(canvas: Canvas, game: Game) {
        val left = dp(14f)
        val top = dp(14f)
        val width = dp(250f)
        val height = dp(116f)

        fillPaint.color = 0x8C0B120F.toInt()
        rect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)

        ringPaint.color = 0x33FFFFFF
        ringPaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), ringPaint)

        val stats = game.player.stats

        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = dp(12f)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("LV ${stats.level}", left + dp(12f), top + dp(20f), textPaint)

        smallPaint.color = 0xFFF0F0E6.toInt()
        smallPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "DAY ${game.day}  ${timeLabel(game.minutes)}",
            left + width - dp(12f),
            top + dp(20f),
            smallPaint,
        )

        smallPaint.color = 0xFFA9BCA9.toInt()
        smallPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(game.biomeLabel, left + dp(12f), top + dp(36f), smallPaint)

        val barLeft = left + dp(34f)
        val barRight = left + width - dp(12f)
        drawBar(canvas, barLeft, top + dp(46f), barRight, dp(9f), stats.health / stats.maxHealth, 0xFFD9534F.toInt(), "HP")
        drawBar(canvas, barLeft, top + dp(60f), barRight, dp(9f), stats.mana / stats.maxMana, 0xFF4A90D9.toInt(), "MP")
        drawBar(canvas, barLeft, top + dp(74f), barRight, dp(9f), stats.hunger / 100f, 0xFFD9A441.toInt(), "HU")
        drawBar(canvas, barLeft, top + dp(90f), barRight, dp(4f), stats.xpProgress(), 0xFF9B7BD9.toInt(), "XP")

        // Numeric health readout keeps the bar readable at a glance.
        smallPaint.color = 0xFFE8E8DE.toInt()
        smallPaint.textAlign = Paint.Align.RIGHT
        smallPaint.textSize = dp(7.5f)
        canvas.drawText(
            "${stats.health.toInt()}/${stats.maxHealth.toInt()}",
            left + width - dp(12f),
            top + dp(44f),
            smallPaint,
        )
        smallPaint.textSize = dp(8.5f)
    }

    private fun drawBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        fraction: Float,
        color: Int,
        label: String,
    ) {
        smallPaint.color = 0xFF90A090.toInt()
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = dp(7.5f)
        canvas.drawText(label, left - dp(22f), top + height, smallPaint)

        fillPaint.color = 0x66000000
        rect.set(left, top, right, top + height)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, fillPaint)

        fillPaint.color = color
        rect.set(left, top, left + (right - left) * fraction.coerceIn(0f, 1f), top + height)
        canvas.drawRoundRect(rect, height / 2f, height / 2f, fillPaint)
    }

    private fun drawFps(canvas: Canvas, game: Game, width: Float) {
        smallPaint.color = 0x88FFFFFF.toInt()
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = dp(8.5f)
        canvas.drawText(
            String.format(Locale.US, "%d FPS", game.fps.toInt()),
            dp(14f),
            dp(144f),
            smallPaint,
        )
    }

    private fun drawBossBar(canvas: Canvas, game: Game, width: Float) {
        val boss: Boss = game.activeBoss ?: return
        if (!boss.engaged || boss.defeated) return

        val barWidth = width * 0.40f
        val left = (width - barWidth) / 2f
        val top = dp(14f)

        textPaint.color = 0xFFFFD08A.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(11f)
        canvas.drawText(boss.displayName.uppercase(Locale.US), width / 2f, top + dp(11f), textPaint)

        fillPaint.color = 0x99000000.toInt()
        rect.set(left, top + dp(17f), left + barWidth, top + dp(29f))
        canvas.drawRoundRect(rect, dp(6f), dp(6f), fillPaint)

        fillPaint.color = 0xFFE0483C.toInt()
        val fraction = (boss.health / boss.maxHp).coerceIn(0f, 1f)
        rect.set(left, top + dp(17f), left + barWidth * fraction, top + dp(29f))
        canvas.drawRoundRect(rect, dp(6f), dp(6f), fillPaint)

        smallPaint.color = 0xCCFFB4A8.toInt()
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = dp(8f)
        canvas.drawText(
            "SEALED — DEFEAT TO ESCAPE",
            width / 2f,
            top + dp(40f),
            smallPaint,
        )
    }

    private fun drawJoystick(canvas: Canvas, input: InputState, width: Float, height: Float) {
        val radius = UiLayout.joystickRadius(density)

        if (!input.joystickActive) {
            val homeX = UiLayout.joystickHomeX(density)
            val homeY = UiLayout.joystickHomeY(height.toInt(), density)
            ringPaint.color = 0x22FFFFFF
            ringPaint.strokeWidth = dp(2f)
            canvas.drawCircle(homeX, homeY, radius * 0.8f, ringPaint)
            fillPaint.color = 0x18FFFFFF
            canvas.drawCircle(homeX, homeY, radius * 0.16f, fillPaint)
            return
        }

        fillPaint.color = 0x1FFFFFFF
        canvas.drawCircle(input.joystickBaseX, input.joystickBaseY, radius, fillPaint)
        ringPaint.color = 0x66FFFFFF
        canvas.drawCircle(input.joystickBaseX, input.joystickBaseY, radius, ringPaint)
        ringPaint.color = 0x33FFFFFF
        canvas.drawCircle(input.joystickBaseX, input.joystickBaseY, radius * 0.62f, ringPaint)

        fillPaint.color = 0xCCF0F0E6.toInt()
        canvas.drawCircle(input.joystickKnobX, input.joystickKnobY, dp(23f), fillPaint)
        ringPaint.color = 0x88FFFFFF.toInt()
        canvas.drawCircle(input.joystickKnobX, input.joystickKnobY, dp(23f), ringPaint)
    }

    private fun drawButtons(
        canvas: Canvas,
        game: Game,
        input: InputState,
        width: Float,
        height: Float,
    ) {
        // Attack.
        val attackX = UiLayout.attackX(width.toInt(), density)
        val attackY = UiLayout.attackY(height.toInt(), density)
        val attackRadius = UiLayout.attackRadius(density)
        val pressed = input.attackDown
        fillPaint.color = if (pressed) 0x66E8A64C else 0x4D0B120F
        canvas.drawCircle(attackX, attackY, attackRadius, fillPaint)
        ringPaint.color = if (pressed) 0xFFE8A64C.toInt() else 0x88FFFFFF.toInt()
        ringPaint.strokeWidth = dp(2.5f)
        canvas.drawCircle(attackX, attackY, attackRadius, ringPaint)
        ringPaint.strokeWidth = dp(2f)
        paintSword(canvas, attackX, attackY, if (pressed) 0xFF2A1A08.toInt() else 0xE6F0F0E6.toInt())

        // Interact.
        val interactX = UiLayout.interactX(width.toInt(), density)
        val interactY = UiLayout.interactY(height.toInt(), density)
        val interactRadius = UiLayout.interactRadius(density)
        val hasTarget = game.interactionPrompt() != null
        fillPaint.color = 0x440B120F
        canvas.drawCircle(interactX, interactY, interactRadius, fillPaint)
        ringPaint.color = if (hasTarget) 0xFFE8A64C.toInt() else 0x55FFFFFF
        canvas.drawCircle(interactX, interactY, interactRadius, ringPaint)
        textPaint.color = if (hasTarget) 0xFFE8A64C.toInt() else 0x88FFFFFF.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(15f)
        canvas.drawText("E", interactX, interactY + dp(5.5f), textPaint)

        // Inventory bag.
        val bagX = UiLayout.bagX(width.toInt(), density)
        val bagY = UiLayout.bagY(height.toInt(), density)
        val bagRadius = UiLayout.bagRadius(density)
        fillPaint.color = 0x4D0B120F
        canvas.drawCircle(bagX, bagY, bagRadius, fillPaint)
        ringPaint.color = 0x66FFFFFF
        canvas.drawCircle(bagX, bagY, bagRadius, ringPaint)
        paintBag(canvas, bagX, bagY, 0xE6F0F0E6.toInt())

        // Settings gear.
        val gearX = UiLayout.gearX(width.toInt(), density)
        val gearY = UiLayout.gearY(height.toInt(), density)
        val gearRadius = UiLayout.gearRadius(density)
        fillPaint.color = 0x3D0B120F
        canvas.drawCircle(gearX, gearY, gearRadius, fillPaint)
        ringPaint.color = 0x55FFFFFF
        canvas.drawCircle(gearX, gearY, gearRadius, ringPaint)
        paintGear(canvas, gearX, gearY, gearRadius * 0.55f, 0xCCF0F0E6.toInt())

        // Magic button (opens the four-spell picker).
        val magicX = UiLayout.magicX(width.toInt(), density)
        val magicY = UiLayout.magicY(height.toInt(), density)
        val magicRadius = UiLayout.magicRadius(density)
        val spell = game.lastSpell
        fillPaint.color = 0x4D0B120F
        canvas.drawCircle(magicX, magicY, magicRadius, fillPaint)
        ringPaint.color = spell.color
        ringPaint.strokeWidth = dp(2.5f)
        canvas.drawCircle(magicX, magicY, magicRadius, ringPaint)
        textPaint.color = spell.color
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(8f)
        canvas.drawText(spell.shortName, magicX, magicY + dp(2.5f), textPaint)
    }

    private fun drawPrompt(canvas: Canvas, game: Game, width: Float, height: Float) {
        val prompt = game.interactionPrompt() ?: return

        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = dp(9f)
        val text = "TAP E  —  $prompt"
        val textWidth = smallPaint.measureText(text)
        val padding = dp(12f)
        val left = (width - textWidth) / 2f - padding
        val right = (width + textWidth) / 2f + padding
        val top = height - dp(96f)
        val bottom = top + dp(24f)

        fillPaint.color = 0xCC0B120F.toInt()
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fillPaint)
        ringPaint.color = 0x44E8A64C
        ringPaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, dp(12f), dp(12f), ringPaint)

        smallPaint.color = 0xFFFFD08A.toInt()
        canvas.drawText(text, left + padding, top + dp(16f), smallPaint)
    }

    private fun drawBanner(canvas: Canvas, game: Game, width: Float, height: Float) {
        if (game.bossBannerTimer <= 0f) return
        val alpha = (min(1f, game.bossBannerTimer) * 255).toInt().coerceIn(0, 255)
        bigPaint.color = Color.argb(alpha, 255, 214, 140)
        canvas.drawText(game.bossBannerText, width / 2f, height * 0.30f, bigPaint)
    }

    private fun drawToast(canvas: Canvas, game: Game, width: Float, height: Float) {
        if (game.toastTimer <= 0f) return
        val alpha = (min(1f, game.toastTimer) * 220).toInt().coerceIn(0, 255)
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = dp(9.5f)
        smallPaint.color = Color.argb(alpha, 240, 240, 230)
        canvas.drawText(game.toastText, width / 2f, height - dp(56f), smallPaint)
    }

    private fun drawHint(canvas: Canvas, game: Game, width: Float, height: Float) {
        if (game.hintTimer <= 0f) return
        if (game.activeBoss?.engaged == true) return
        val alpha = (min(1f, game.hintTimer) * 190f).toInt().coerceIn(0, 255)
        smallPaint.color = Color.argb(alpha, 240, 240, 230)
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = dp(9f)
        canvas.drawText(
            "DRAG LEFT TO MOVE  /  SWORD ATTACKS  /  E INTERACTS",
            width / 2f,
            height * 0.155f,
            smallPaint,
        )
    }

    private fun drawHurtFlash(canvas: Canvas, game: Game, width: Float, height: Float) {
        if (game.player.hurtFlash <= 0f) return
        val alpha = (game.player.hurtFlash / 0.45f * 70f).toInt().coerceIn(0, 70)
        fillPaint.color = Color.argb(alpha, 180, 20, 20)
        canvas.drawRect(0f, 0f, width, height, fillPaint)
    }

    private fun drawDeath(canvas: Canvas, game: Game, width: Float, height: Float) {
        val progress = 1f - (game.deathTimer / 1.8f).coerceIn(0f, 1f)
        fillPaint.color = Color.argb((140 + 80 * progress).toInt(), 20, 4, 4)
        canvas.drawRect(0f, 0f, width, height, fillPaint)

        bigPaint.color = 0xFFE0483C.toInt()
        bigPaint.textSize = dp(30f)
        canvas.drawText("YOU DIED", width / 2f, height * 0.44f, bigPaint)

        smallPaint.color = 0xCCE8D8D0.toInt()
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = dp(10f)
        canvas.drawText(
            "RETURNING TO THE LAST CAMPFIRE",
            width / 2f,
            height * 0.52f,
            smallPaint,
        )
        bigPaint.textSize = dp(20f)
    }

    private fun paintSword(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        canvas.save()
        canvas.rotate(-45f, cx, cy)

        ringPaint.color = color
        ringPaint.strokeWidth = dp(3f)
        ringPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy - dp(18f), cx, cy + dp(4f), ringPaint)
        canvas.drawLine(cx - dp(8f), cy + dp(4f), cx + dp(8f), cy + dp(4f), ringPaint)
        canvas.drawLine(cx, cy + dp(4f), cx, cy + dp(13f), ringPaint)
        fillPaint.color = color
        canvas.drawCircle(cx, cy + dp(15f), dp(2.2f), fillPaint)

        canvas.restore()
    }

    private fun paintBag(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        ringPaint.color = color
        ringPaint.strokeWidth = dp(2.4f)
        rect.set(cx - dp(10f), cy - dp(4f), cx + dp(10f), cy + dp(10f))
        canvas.drawRoundRect(rect, dp(3f), dp(3f), ringPaint)
        canvas.drawLine(cx - dp(6f), cy - dp(4f), cx - dp(6f), cy - dp(9f), ringPaint)
        canvas.drawLine(cx + dp(6f), cy - dp(4f), cx + dp(6f), cy - dp(9f), ringPaint)
        canvas.drawLine(cx - dp(6f), cy - dp(9f), cx + dp(6f), cy - dp(9f), ringPaint)
    }

    private fun paintGear(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        ringPaint.color = color
        ringPaint.strokeWidth = dp(2.2f)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        for (i in 0 until 6) {
            val angle = i * Math.PI.toFloat() / 3f
            canvas.drawLine(
                cx + cos(angle) * radius * 0.7f,
                cy + sin(angle) * radius * 0.7f,
                cx + cos(angle) * radius * 1.35f,
                cy + sin(angle) * radius * 1.35f,
                ringPaint,
            )
        }
    }

    private fun timeLabel(minutes: Float): String {
        val total = minutes.toInt()
        if (total != cachedMinute) {
            cachedMinute = total
            cachedTime = String.format(
                Locale.US,
                "%02d:%02d",
                (total / 60) % 24,
                total % 60,
            )
        }
        return cachedTime
    }

    private fun dp(value: Float): Float = value * density
}
