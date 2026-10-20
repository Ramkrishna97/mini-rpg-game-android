package com.emberfall.game.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * The small, deliberately data-only contract for a downloadable content pack.
 *
 * A pack can replace art and other declared data files, but it cannot introduce
 * executable code. This keeps the game updateable without bypassing Android or
 * Google Play's application-update model.
 */
data class ContentManifest(
    val schemaVersion: Int,
    val contentVersion: String,
    val minimumAppVersion: Long,
    val assets: List<ContentAsset>,
)

data class ContentAsset(
    val id: String,
    val path: String,
    val sha256: String,
    val bytes: Long,
)

/** IDs used by the renderer. Keep these stable even when pack filenames change. */
object ContentAssetIds {
    const val TILESET = "tileset"

    fun sprite(name: String): String = "sprite.$name"
}

/**
 * Parses and validates manifests before any files are downloaded. Paths are
 * intentionally relative, normalized paths so a remote manifest can never
 * escape the content-pack directory.
 */
object ContentManifestParser {
    const val SCHEMA_VERSION = 1
    const val MAX_MANIFEST_BYTES = 512 * 1024
    const val MAX_ASSET_BYTES = 64L * 1024L * 1024L
    const val MAX_PACK_BYTES = 200L * 1024L * 1024L

    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val pathSegmentPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun parse(raw: String): ContentManifest {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
            "Content manifest is too large"
        }
        val objectRoot = JSONObject(raw)
        val schemaVersion = objectRoot.requiredInt("schemaVersion")
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported content schema $schemaVersion"
        }
        val contentVersion = objectRoot.requiredString("contentVersion")
        require(contentVersion.length <= 96 && idPattern.matches(contentVersion)) {
            "Invalid content version"
        }
        val minAppVersion = objectRoot.requiredLong("minimumAppVersion")
        require(minAppVersion > 0L) { "minimumAppVersion must be positive" }
        val array = objectRoot.requiredArray("assets")
        require(array.length() > 0) { "Content manifest has no assets" }

        val seenIds = HashSet<String>()
        val seenPaths = HashSet<String>()
        var totalBytes = 0L
        val assets = ArrayList<ContentAsset>(array.length())
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.requiredString("id")
            val path = item.requiredString("path")
            val sha256 = item.requiredString("sha256").lowercase()
            val bytes = item.requiredLong("bytes")

            require(idPattern.matches(id) && seenIds.add(id)) { "Invalid or duplicate asset id '$id'" }
            require(isSafeRelativePath(path) && seenPaths.add(path)) { "Invalid or duplicate asset path '$path'" }
            require(hashPattern.matches(sha256)) { "Invalid SHA-256 for '$id'" }
            require(bytes in 1..MAX_ASSET_BYTES) { "Invalid size for '$id'" }
            totalBytes = Math.addExact(totalBytes, bytes)
            require(totalBytes <= MAX_PACK_BYTES) { "Content pack is too large" }
            assets += ContentAsset(id, path, sha256, bytes)
        }
        return ContentManifest(schemaVersion, contentVersion, minAppVersion, assets)
    }

    fun isSafeRelativePath(path: String): Boolean =
        path.length in 1..255 &&
            !path.startsWith('/') &&
            !path.contains('\\') &&
            path.split('/').all { pathSegmentPattern.matches(it) }

    private fun JSONObject.requiredString(name: String): String {
        require(has(name) && !isNull(name)) { "Missing '$name'" }
        return getString(name)
    }

    private fun JSONObject.requiredInt(name: String): Int =
        require(has(name)) { "Missing '$name'" }.let { getInt(name) }

    private fun JSONObject.requiredLong(name: String): Long =
        require(has(name)) { "Missing '$name'" }.let { getLong(name) }

    private fun JSONObject.requiredArray(name: String): JSONArray =
        require(has(name)) { "Missing '$name'" }.let { getJSONArray(name) }
}
