package com.emberfall.game.save

import android.content.Context
import android.util.Log
import com.emberfall.game.core.GraphicsQuality
import com.emberfall.game.core.GraphicsSettings
import com.emberfall.game.core.GameMode
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
    val stage: Int = 1,
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
    val gold: Int = 0,
    val diamonds: Int = 0,
    val campfireX: Float = 0f,
    val campfireY: Float = 0f,
    val defeatedBosses: Set<String> = emptySet(),
    val openedChests: Set<Int> = emptySet(),
    val terrainChanges: List<Triple<Int, Int, Int>> = emptyList(),
    val equippedWeapon: String? = null,
    val meleeWeapon: String? = null,
    val rangedWeapon: String? = null,
    val weaponLevels: Map<String, Int> = emptyMap(),
    val weaponStars: Map<String, Int> = emptyMap(),
    val graphics: GraphicsSettings = GraphicsSettings(),
    val hackBoostsEnabled: Boolean = false,
    val hackSnapshot: HackSnapshot? = null,
)

/** Resources held before temporary hack boosts were enabled. */
data class HackSnapshot(
    val inventory: List<Pair<ItemId, Int>>,
    val gold: Int,
    val diamonds: Int,
    val weaponLevels: Map<String, Int>,
    val weaponStars: Map<String, Int>,
    val equippedWeapon: String?,
    val meleeWeapon: String?,
    val rangedWeapon: String?,
)

/**
 * Versioned JSON save. Version 1 files (position/time only) still load: every
 * newer field falls back to a sensible default.
 */
class SaveManager(private val context: Context, private val mode: GameMode = GameMode.NORMAL) {

    private fun getFile(slot: Int = 1): File {
        val baseName = if (mode == GameMode.HACK) "emberfall_hack_save" else "emberfall_save"
        if (slot <= 1) {
            val legacy = File(context.filesDir, "$baseName.json")
            if (legacy.exists()) return legacy
            return File(context.filesDir, "${baseName}_1.json")
        }
        return File(context.filesDir, "${baseName}_$slot.json")
    }

    @Synchronized
    fun load(slot: Int = 1): SaveState? = try {
        val file = getFile(slot)
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

                val terrainChanges = mutableListOf<Triple<Int, Int, Int>>()
                json.optJSONArray("terrainChanges")?.let { tiles ->
                    for (i in 0 until tiles.length()) {
                        val tile = tiles.optJSONArray(i) ?: continue
                        if (tile.length() == 3) terrainChanges.add(
                            Triple(tile.optInt(0), tile.optInt(1), tile.optInt(2)))
                    }
                }

                SaveState(
                    seed = json.getInt("seed"),
                    x = json.optDouble("x", 0.0).toFloat(),
                    y = json.optDouble("y", 0.0).toFloat(),
                    minutes = json.optDouble("minutes", 450.0).toFloat(),
                    day = json.optInt("day", 1).coerceAtLeast(1),
                    stage = json.optInt("stage", 1).coerceAtLeast(1),
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
                    gold = json.optInt("gold", 0).coerceAtLeast(0),
                    diamonds = json.optInt("diamonds", 0).coerceAtLeast(0),
                    campfireX = json.optDouble("campfireX", 0.0).toFloat(),
                    campfireY = json.optDouble("campfireY", 0.0).toFloat(),
                    defeatedBosses = json.optJSONArray("defeatedBosses").toStringSet(),
                    openedChests = json.optJSONArray("openedChests").toIntSet(),
                    terrainChanges = terrainChanges,
                    equippedWeapon = json.optString("equippedWeapon").takeIf { it.isNotBlank() },
                    meleeWeapon = json.optString("meleeWeapon").takeIf { it.isNotBlank() },
                    rangedWeapon = json.optString("rangedWeapon").takeIf { it.isNotBlank() },
                    weaponLevels = json.optJSONObject("weaponLevels").toIntMap(),
                    weaponStars = json.optJSONObject("weaponStars").toIntMap(),
                    graphics = json.optJSONObject("graphics").toGraphics(),
                    hackBoostsEnabled = if (mode == GameMode.HACK) json.optBoolean("hackBoostsEnabled", false) else false,
                    hackSnapshot = if (mode == GameMode.HACK) json.optJSONObject("hackSnapshot").toHackSnapshot() else null,
                )
            }
        }
    } catch (t: Throwable) {
        Log.e("EmberfallSave", "load failed", t)
        null
    }

    @Synchronized
    fun save(state: SaveState, slot: Int = 1) {
        val file = getFile(slot)
        try {
            val inventoryJson = JSONArray()
            for ((id, count) in state.inventory) {
                inventoryJson.put(JSONObject().put("id", id.name).put("count", count))
            }
            val bossesJson = JSONArray()
            for (id in state.defeatedBosses) bossesJson.put(id)
            val chestsJson = JSONArray()
            for (id in state.openedChests) chestsJson.put(id)
            val terrainJson = JSONArray()
            for ((tx, ty, type) in state.terrainChanges) {
                terrainJson.put(JSONArray().put(tx).put(ty).put(type))
            }

            val json = JSONObject()
                .put("version", 2)
                .put("seed", state.seed)
                .put("x", state.x.toDouble())
                .put("y", state.y.toDouble())
                .put("minutes", state.minutes.toDouble())
                .put("day", state.day)
                .put("stage", state.stage)
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
                .put("gold", state.gold)
                .put("diamonds", state.diamonds)
                .put("campfireX", state.campfireX.toDouble())
                .put("campfireY", state.campfireY.toDouble())
                .put("defeatedBosses", bossesJson)
                .put("openedChests", chestsJson)
                .put("terrainChanges", terrainJson)
                .put("equippedWeapon", state.equippedWeapon ?: "")
                .put("meleeWeapon", state.meleeWeapon ?: "")
                .put("rangedWeapon", state.rangedWeapon ?: "")
                .put("weaponLevels", JSONObject(state.weaponLevels))
                .put("weaponStars", JSONObject(state.weaponStars))
                .put(
                    "graphics",
                    JSONObject()
                        .put("quality", state.graphics.quality.name)
                        .put("smoothTextures", state.graphics.smoothTextures)
                        .put("antiAliasing", state.graphics.antiAliasing)
                        .put("particles", state.graphics.particles)
                        .put("music", state.graphics.music)
                        .put("sound", state.graphics.sound)
                        .put("vibration", state.graphics.vibration)
                        .put("resolutionScale", state.graphics.resolutionScale)
                        .put("targetFps", state.graphics.targetFps),
                )

            if (mode == GameMode.HACK) {
                json.put("hackBoostsEnabled", state.hackBoostsEnabled)
                json.put("hackSnapshot", state.hackSnapshot?.let { snapshot ->
                    val items = JSONArray()
                    for ((id, count) in snapshot.inventory)
                        items.put(JSONObject().put("id", id.name).put("count", count))
                    JSONObject()
                        .put("inventory", items)
                        .put("gold", snapshot.gold)
                        .put("diamonds", snapshot.diamonds)
                        .put("weaponLevels", JSONObject(snapshot.weaponLevels))
                        .put("weaponStars", JSONObject(snapshot.weaponStars))
                        .put("equippedWeapon", snapshot.equippedWeapon ?: "")
                        .put("meleeWeapon", snapshot.meleeWeapon ?: "")
                        .put("rangedWeapon", snapshot.rangedWeapon ?: "")
                } ?: JSONObject.NULL)
            }

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

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        if (this == null) return emptyMap()
        val result = mutableMapOf<String, Int>()
        val keys = keys()
        while (keys.hasNext()) { val key = keys.next(); result[key] = optInt(key, 1) }
        return result
    }

    private fun JSONObject?.toHackSnapshot(): HackSnapshot? {
        if (this == null) return null
        val items = mutableListOf<Pair<ItemId, Int>>()
        val array = optJSONArray("inventory")
        if (array != null) for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val id = runCatching { ItemId.valueOf(entry.optString("id")) }.getOrNull() ?: continue
            val count = entry.optInt("count", 0)
            if (count > 0) items.add(id to count)
        }
        return HackSnapshot(items, optInt("gold", 0), optInt("diamonds", 0),
            optJSONObject("weaponLevels").toIntMap(), optJSONObject("weaponStars").toIntMap(),
            optString("equippedWeapon").takeIf { it.isNotBlank() },
            optString("meleeWeapon").takeIf { it.isNotBlank() },
            optString("rangedWeapon").takeIf { it.isNotBlank() })
    }

    private fun JSONObject?.toGraphics(): GraphicsSettings {
        val settings = GraphicsSettings()
        if (this == null) return settings
        settings.quality = runCatching {
            GraphicsQuality.valueOf(optString("quality", GraphicsQuality.HIGH.name))
        }.getOrDefault(GraphicsQuality.HIGH)
        settings.smoothTextures = optBoolean("smoothTextures", true)
        settings.antiAliasing = optBoolean("antiAliasing", true)
        settings.particles = optBoolean("particles", true)
        settings.music = optBoolean("music", true)
        settings.sound = optBoolean("sound", false)
        settings.vibration = optBoolean("vibration", true)
        settings.resolutionScale = optDouble("resolutionScale", 1.0).toFloat().coerceIn(0.5f, 2f)
        settings.targetFps = optInt("targetFps", 60).coerceIn(30, 120)
        return settings
    }
}
