package com.emberfall.game.save

import android.content.Context
import android.util.Log
import com.emberfall.game.core.GraphicsQuality
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.item.ItemId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SaveState(
    val seed: Int,
    val x: Float,
    val y: Float,
    val minutes: Float,
    val day: Int,
    val level: Int = 1,
    val xp: Int = 0,
    val maxHealth: Float = 100f,
    val health: Float = 100f,
    val maxMana: Float = 50f,
    val mana: Float = 50f,
    val hunger: Float = 100f,
    val bonusHealth: Float = 0f,
    val bonusMana: Float = 0f,
    val inventory: List<Pair<ItemId, Int>> = emptyList(),
    val campfireX: Float = 0f,
    val campfireY: Float = 0f,
    val defeatedBosses: Set<String> = emptySet(),
    val openedChests: Set<Int> = emptySet(),
    val graphics: GraphicsSettings = GraphicsSettings(),
)

/**
 * Versioned JSON save. Version 1 files (position/time only) still load: every
 * newer field falls back to a sensible default.
 */
class SaveManager(context: Context) {

    private val file = File(context.filesDir, "emberfall_save.json")

    @Synchronized
    fun load(): SaveState? = try {
        if (!file.exists()) {
            null
        } else {
            val json = JSONObject(file.readText())
            val version = json.optInt("version", 1)
            if (version < 1) {
                null
            } else {
                val inventory = mutableListOf<Pair<ItemId, Int>>()
                val inventoryJson = json.optJSONArray("inventory")
                if (inventoryJson != null) {
                    for (i in 0 until inventoryJson.length()) {
                        val entry = inventoryJson.optJSONObject(i) ?: continue
                        val id = runCatching {
                            ItemId.valueOf(entry.optString("id"))
                        }.getOrNull() ?: continue
                        val count = entry.optInt("count", 0)
                        if (count > 0) inventory.add(id to count)
                    }
                }

                SaveState(
                    seed = json.getInt("seed"),
                    x = json.optDouble("x", 0.0).toFloat(),
                    y = json.optDouble("y", 0.0).toFloat(),
                    minutes = json.optDouble("minutes", 450.0).toFloat(),
                    day = json.optInt("day", 1).coerceAtLeast(1),
                    level = json.optInt("level", 1).coerceAtLeast(1),
                    xp = json.optInt("xp", 0).coerceAtLeast(0),
                    maxHealth = json.optDouble("maxHealth", 100.0).toFloat(),
                    health = json.optDouble("health", 100.0).toFloat(),
                    maxMana = json.optDouble("maxMana", 50.0).toFloat(),
                    mana = json.optDouble("mana", 50.0).toFloat(),
                    hunger = json.optDouble("hunger", 100.0).toFloat(),
                    bonusHealth = json.optDouble("bonusHealth", 0.0).toFloat(),
                    bonusMana = json.optDouble("bonusMana", 0.0).toFloat(),
                    inventory = inventory,
                    campfireX = json.optDouble("campfireX", 0.0).toFloat(),
                    campfireY = json.optDouble("campfireY", 0.0).toFloat(),
                    defeatedBosses = json.optJSONArray("defeatedBosses").toStringSet(),
                    openedChests = json.optJSONArray("openedChests").toIntSet(),
                    graphics = json.optJSONObject("graphics").toGraphics(),
                )
            }
        }
    } catch (t: Throwable) {
        Log.e("EmberfallSave", "load failed", t)
        null
    }

    @Synchronized
    fun save(state: SaveState) {
        try {            val inventoryJson = JSONArray()
            for ((id, count) in state.inventory) {
                inventoryJson.put(JSONObject().put("id", id.name).put("count", count))
            }
            val bossesJson = JSONArray()
            for (id in state.defeatedBosses) bossesJson.put(id)
            val chestsJson = JSONArray()
            for (id in state.openedChests) chestsJson.put(id)

            val json = JSONObject()
                .put("version", 2)
                .put("seed", state.seed)
                .put("x", state.x.toDouble())
                .put("y", state.y.toDouble())
                .put("minutes", state.minutes.toDouble())
                .put("day", state.day)
                .put("level", state.level)
                .put("xp", state.xp)
                .put("maxHealth", state.maxHealth.toDouble())
                .put("health", state.health.toDouble())
                .put("maxMana", state.maxMana.toDouble())
                .put("mana", state.mana.toDouble())
                .put("hunger", state.hunger.toDouble())
                .put("bonusHealth", state.bonusHealth.toDouble())
                .put("bonusMana", state.bonusMana.toDouble())
                .put("inventory", inventoryJson)
                .put("campfireX", state.campfireX.toDouble())
                .put("campfireY", state.campfireY.toDouble())
                .put("defeatedBosses", bossesJson)
                .put("openedChests", chestsJson)
                .put(
                    "graphics",
                    JSONObject()
                        .put("quality", state.graphics.quality.name)
                        .put("smoothTextures", state.graphics.smoothTextures)
                        .put("antiAliasing", state.graphics.antiAliasing)
                        .put("particles", state.graphics.particles),
                )

            // Write-then-rename keeps the save intact if the process dies mid-write.
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) {
                file.writeText(json.toString())
                temp.delete()
            }
        } catch (t: Throwable) {
            Log.e("EmberfallSave", "save failed", t)
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until length()) out.add(optString(i))
        return out
    }

    private fun JSONArray?.toIntSet(): Set<Int> {
        if (this == null) return emptySet()
        val out = mutableSetOf<Int>()
        for (i in 0 until length()) out.add(optInt(i))
        return out
    }

    private fun JSONObject?.toGraphics(): GraphicsSettings {
        val settings = GraphicsSettings()
        if (this == null) return settings
        settings.quality = runCatching {
            GraphicsQuality.valueOf(optString("quality", GraphicsQuality.MEDIUM.name))
        }.getOrDefault(GraphicsQuality.MEDIUM)
        settings.smoothTextures = optBoolean("smoothTextures", true)
        settings.antiAliasing = optBoolean("antiAliasing", true)
        settings.particles = optBoolean("particles", true)
        return settings
    }
}
