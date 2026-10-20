package com.emberfall.game.content

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

sealed interface ContentUpdateResult {
    data object Disabled : ContentUpdateResult
    data object Current : ContentUpdateResult
    data class Applied(val version: String) : ContentUpdateResult
    data class Incompatible(val requiredAppVersion: Long) : ContentUpdateResult
    data class Failed(val reason: String) : ContentUpdateResult
}

/**
 * Downloads a complete content pack into a staging directory. Only a fully
 * downloaded, size-checked and SHA-256-checked pack can become active.
 */
class ContentUpdater private constructor(private val context: Context) {
    private val repository = ContentRepository.from(context)

    fun checkForUpdatesAsync(onComplete: (ContentUpdateResult) -> Unit = {}): Thread? {
        val manifestUrl = configuredManifestUrl().trim()
        if (manifestUrl.isBlank()) {
            onComplete(ContentUpdateResult.Disabled)
            return null
        }
        return Thread({ onComplete(checkForUpdates(manifestUrl)) }, "EmberfallContentUpdate").apply { start() }
    }

    fun checkForUpdates(manifestUrl: String): ContentUpdateResult {
        return try {
        val manifestUri = checkedHttpsUri(manifestUrl)
        val rawManifest = downloadBytes(manifestUri.toURL(), ContentManifestParser.MAX_MANIFEST_BYTES.toLong())
        val rawManifestText = rawManifest.toString(Charsets.UTF_8)
        val manifest = ContentManifestParser.parse(rawManifestText)
        if (manifest.minimumAppVersion > installedVersionCode()) {
            ContentUpdateResult.Incompatible(manifest.minimumAppVersion)
        } else {
            val key = "pack-${sha256(rawManifest).take(32)}"
            val finalPack = repository.packDirectory(key)
            if (isComplete(finalPack, manifest)) {
                repository.activate(finalPack, manifest)
                ContentUpdateResult.Current
            } else {
                val stage = File(repository.packsDirectory(), "$key-${UUID.randomUUID()}.staging")
                check(stage.mkdirs()) { "Could not create content staging directory" }
                try {
                    for (asset in manifest.assets) {
                        val target = safeChild(stage, asset.path)
                        target.parentFile?.mkdirs()
                        downloadAsset(manifestUri.resolve(asset.path).toURL(), target, asset)
                    }
                    repository.writeAtomically(File(stage, "manifest.json"), rawManifest)
                    check(stage.renameTo(finalPack)) { "Could not finalize content pack" }
                    repository.activate(finalPack, manifest)
                    ContentUpdateResult.Applied(manifest.contentVersion)
                } finally {
                    if (stage.exists()) stage.deleteRecursively()
                }
            }
        }
    } catch (error: Throwable) {
        Log.w(TAG, "Content update was not applied", error)
        ContentUpdateResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun isComplete(directory: File, manifest: ContentManifest): Boolean = runCatching {
        if (!directory.isDirectory) return@runCatching false
        manifest.assets.all { asset ->
            val file = safeChild(directory, asset.path)
            file.length() == asset.bytes && repository.sha256(file) == asset.sha256
        }
    }.getOrDefault(false)

    private fun downloadAsset(url: URL, target: File, asset: ContentAsset) {
        val connection = openConnection(url)
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var written = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    written += count
                    require(written <= asset.bytes) { "Download exceeds declared size for ${asset.id}" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
                output.fd.sync()
                require(written == asset.bytes) { "Unexpected size for ${asset.id}" }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == asset.sha256) { "Checksum mismatch for ${asset.id}" }
            }
        }
        connection.disconnect()
    }

    private fun downloadBytes(url: URL, maximumBytes: Long): ByteArray {
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= maximumBytes) { "Download is too large" }
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL): HttpURLConnection {
        require(url.protocol == "https") { "Content downloads must use HTTPS" }
        return (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, image/*, application/octet-stream")
            connect()
            require(responseCode in 200..299) { "HTTP $responseCode for $url" }
        }
    }

    private fun checkedHttpsUri(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Content manifest URL must be HTTPS" }
        return uri
    }

    private fun safeChild(root: File, relativePath: String): File {
        require(ContentManifestParser.isSafeRelativePath(relativePath)) { "Unsafe content path" }
        val file = File(root, relativePath).canonicalFile
        require(file.toPath().startsWith(root.canonicalFile.toPath())) { "Unsafe content path" }
        return file
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    @Suppress("DEPRECATION")
    private fun configuredManifestUrl(): String = context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString(CONTENT_MANIFEST_URL)
        .orEmpty()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "EmberfallContent"
        private const val CONTENT_MANIFEST_URL = "com.emberfall.game.CONTENT_MANIFEST_URL"

        fun from(context: Context): ContentUpdater = ContentUpdater(context.applicationContext)
    }
}
