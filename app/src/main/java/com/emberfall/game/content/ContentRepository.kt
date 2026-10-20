package com.emberfall.game.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Resolves a stable asset ID to an active downloadable file, falling back to a
 * bundled APK asset when no valid content pack provides it. Activation changes
 * one in-memory map only after a complete pack has been verified.
 */
class ContentRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val contentRoot = File(appContext.filesDir, "emberfall-content")
    private val packsRoot = File(contentRoot, "packs")
    private val activePointer = File(contentRoot, "active-pack.json")

    @Volatile
    private var activeAssets: Map<String, File> = loadActiveAssets()

    fun open(assetId: String, bundledPath: String): InputStream {
        val downloaded = activeAssets[assetId]
        if (downloaded != null && downloaded.isFile) {
            return FileInputStream(downloaded)
        }
        return appContext.assets.open(bundledPath)
    }

    fun openBundled(bundledPath: String): InputStream = appContext.assets.open(bundledPath)

    /**
     * Decodes an optional replacement image. A bad external image is never
     * allowed to break rendering: the APK asset immediately becomes the source.
     */
    fun bitmap(
        assetId: String,
        bundledPath: String,
        isValid: (Bitmap) -> Boolean = { true },
    ): Bitmap? {
        val downloaded = activeAssets[assetId]
        if (downloaded != null) {
            val candidate = runCatching {
                FileInputStream(downloaded).use(BitmapFactory::decodeStream)
            }.getOrNull()
            if (candidate != null && isValid(candidate)) return candidate
            candidate?.recycle()
        }
        return openBundled(bundledPath).use(BitmapFactory::decodeStream)
    }

    /** Called by [ContentUpdater] only after every file in a pack has passed SHA-256 verification. */
    @Synchronized
    internal fun activate(packDirectory: File, manifest: ContentManifest) {
        require(packDirectory.isDirectory) { "Content pack directory is missing" }
        val resolved = resolveAssets(packDirectory, manifest)
        contentRoot.mkdirs()
        val pointer = "{\"pack\":\"${packDirectory.name}\"}"
        writeAtomically(activePointer, pointer.toByteArray(Charsets.UTF_8))
        activeAssets = resolved
    }

    internal fun packDirectory(key: String): File = File(packsRoot, key)
    internal fun packsDirectory(): File = packsRoot.apply { mkdirs() }

    private fun loadActiveAssets(): Map<String, File> = runCatching {
        if (!activePointer.isFile) return@runCatching emptyMap()
        val root = org.json.JSONObject(activePointer.readText(Charsets.UTF_8))
        val packName = root.getString("pack")
        require(Regex("pack-[a-f0-9]{32}").matches(packName)) { "Invalid active content pack" }
        val pack = File(packsRoot, packName)
        val manifestFile = File(pack, "manifest.json")
        val manifest = ContentManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
        resolveAssets(pack, manifest)
    }.getOrDefault(emptyMap())

    private fun resolveAssets(pack: File, manifest: ContentManifest): Map<String, File> {
        val packPath = pack.canonicalFile.toPath()
        return manifest.assets.associate { asset ->
            val file = File(pack, asset.path).canonicalFile
            require(file.toPath().startsWith(packPath) && file.isFile) { "Missing ${asset.id}" }
            require(sha256(file) == asset.sha256) { "Checksum mismatch for ${asset.id}" }
            asset.id to file
        }
    }

    internal fun sha256(file: File): String =
        FileInputStream(file).use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

    internal fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        check(temporary.renameTo(target)) { "Could not activate ${target.name}" }
    }

    companion object {
        @Volatile private var instance: ContentRepository? = null

        fun from(context: Context): ContentRepository = instance ?: synchronized(this) {
            instance ?: ContentRepository(context).also { instance = it }
        }
    }
}
