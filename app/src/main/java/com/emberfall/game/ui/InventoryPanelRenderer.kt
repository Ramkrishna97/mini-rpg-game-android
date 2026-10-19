package com.emberfall.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.Context
import com.emberfall.game.core.Game
import com.emberfall.game.item.ItemCategory
import com.emberfall.game.item.Recipes

/** Inventory and crafting overlay. Simulation is paused while it is open. */
class InventoryPanelRenderer(context: Context) {

    private val art = UiArt(context)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = art.font
    }
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        game: Game,
        width: Float,
        height: Float,
        density: Float,
        crafting: Boolean,
        craftScroll: Int,
    ) {
        paint.color = 0xB3000000.toInt()
        canvas.drawRect(0f, 0f, width, height, paint)

        val panel = UiLayout.inventoryPanel(width, height, density)
        art.panel(canvas, panel, density)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 15f * density
        canvas.drawText(
            if (crafting) "CRAFTING" else "INVENTORY",
            panel.left + 18f * density,
            panel.top + 30f * density,
            textPaint,
        )

        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "LV ${game.player.stats.level}  ${game.player.stats.xp}/${game.player.stats.xpToNext()} XP",
            panel.right - 124f * density,
            panel.top + 30f * density,
            textPaint,
        )

        val button = UiLayout.craftButton(width, height, density)
        paint.color = 0xFF2A352C.toInt()
        canvas.drawRoundRect(button, 6f * density, 6f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = 0x88E8A64C.toInt()
        canvas.drawRoundRect(button, 6f * density, 6f * density, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = 0xFFE8A64C.toInt()
        textPaint.textSize = 9f * density
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            if (crafting) "BACK" else "CRAFT",
            button.centerX(),
            button.centerY() + 3.5f * density,
            textPaint,
        )

        if (crafting) {
            drawRecipes(canvas, game, width, height, density, panel, craftScroll)
        } else {
            drawGrid(canvas, game, width, height, density, panel)
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        game: Game,
        width: Float,
        height: Float,
        density: Float,
        panel: RectF,
    ) {
        val equipped = game.activeWeapon()
        for (index in 0 until UiLayout.INVENTORY_COLUMNS * UiLayout.INVENTORY_ROWS) {
            val cell = UiLayout.inventoryCell(index, width, height, density)
            paint.color = 0xFF232D25.toInt()
            canvas.drawRoundRect(cell, 5f * density, 5f * density, paint)

            val stack = game.inventory.slot(index)
            if (stack == null) continue

            val inset = 7f * density
            rect.set(cell.left + inset, cell.top + inset, cell.right - inset, cell.top + cell.height() * 0.68f)
            paint.color = 0xFF17221D.toInt()
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint)
            art.item(canvas, stack.id, cell.centerX(), cell.top + cell.height() * .36f,
                minOf(rect.width(), rect.height(), 36f * density))

            textPaint.color = 0xFFE8E8DE.toInt()
            textPaint.textSize = 7.5f * density
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                shorten(stack.id.displayName, 13),
                cell.left + 5f * density,
                cell.bottom - 5f * density,
                textPaint,
            )

            if (stack.count > 1) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = 0xFFFFE08A.toInt()
                textPaint.textSize = 9f * density
                canvas.drawText(
                    "x${stack.count}",
                    cell.right - 5f * density,
                    cell.bottom - 5f * density,
                    textPaint,
                )
            }

            if (stack.id.category == ItemCategory.WEAPON) {
                textPaint.textAlign = Paint.Align.LEFT; textPaint.color = 0xFFFFD08A.toInt(); textPaint.textSize = 7f*density
                canvas.drawText("${"*".repeat(game.weaponStars[stack.id] ?: 1)}  Lv.${game.weaponLevels[stack.id] ?: 1}", cell.left+5f*density, cell.top+10f*density, textPaint)
            }
            if (stack.id == equipped) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = 0xFFE8A64C.toInt()
                canvas.drawRoundRect(cell, 5f * density, 5f * density, paint)
                paint.style = Paint.Style.FILL
            }
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        val hint = "Tap a consumable to use it  /  tap a weapon to equip  /  tap outside to close"
        canvas.drawText(hint, panel.left + 18f * density, panel.bottom - 10f * density, textPaint)
    }

    private fun drawRecipes(
        canvas: Canvas,
        game: Game,
        width: Float,
        height: Float,
        density: Float,
        panel: RectF,
        scroll: Int,
    ) {
        val end = minOf(Recipes.ALL.size, scroll + UiLayout.craftRowsPerPage(width, height, density))
        for (index in scroll until end) {
            val recipe = Recipes.ALL[index]
            val row = UiLayout.craftRow(index, width, height, density, scroll)
            if (row.bottom > panel.bottom - 28f * density) break

            val affordable = recipe.costs.all { (id, count) -> game.inventory.count(id) >= count }
            paint.color = if (affordable) 0x332F6B3A else 0x33231C1C
            canvas.drawRoundRect(row, 5f * density, 5f * density, paint)
            art.item(canvas, recipe.result, row.left + 16f*density, row.centerY(), 25f*density)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = if (affordable) 0xFFE8E8DE.toInt() else 0xFF8A8A82.toInt()
            textPaint.textSize = 9.5f * density
            val styleLabel = if (recipe.result.category == ItemCategory.WEAPON) {
                "  dmg ${recipe.result.damage.toInt()} ${recipe.result.style.name.lowercase()}"
            } else {
                ""
            }
            canvas.drawText(
                recipe.result.displayName + styleLabel,
                row.left + 34f * density,
                row.centerY() + 3.5f * density,
                textPaint,
            )

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 8f * density
            val costText = recipe.costs.joinToString("  ") { (id, count) ->
                val have = game.inventory.count(id)
                "${id.displayName.take(4)} $have/$count"
            }
            textPaint.color = if (affordable) 0xFFA9D6A0.toInt() else 0xFFD68A82.toInt()
            canvas.drawText(costText, row.right - 10f * density, row.centerY() + 3f * density, textPaint)
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFF90A090.toInt()
        textPaint.textSize = 8.5f * density
        canvas.drawText(
            "Use INTERACT to gather wood, stone and fruit; craft here",
            panel.left + 18f * density,
            panel.bottom - 10f * density,
            textPaint,
        )
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = 0xFFFFD08A.toInt()
        canvas.drawText("${if (end < Recipes.ALL.size) "NEXT" else "FIRST"}  ${scroll+1}-$end/${Recipes.ALL.size}",
            panel.right-17f*density, panel.bottom-10f*density, textPaint)
    }

    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}
