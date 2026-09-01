package com.vexo.models

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "VexoModelStore"
private const val MARKER_NAME = ".complete"
private const val CONNECT_TIMEOUT_MILLIS = 15_000
private const val READ_TIMEOUT_MILLIS = 30_000
private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * Strips the archive's single top-level directory from [entryName] and rejects anything that would
 * escape the pack directory. Returns null for entries that should be skipped.
 *
 * Pure so the traversal rules are unit testable; [ModelStore] still performs a canonical-path check
 * when writing, as defence in depth.
 */
internal fun packRelativePath(entryName: String): String? {
    val normalised = entryName.replace('\\', '/')
    val relative = normalised.substringAfter('/', missingDelimiterValue = "")
    if (relative.isEmpty()) return null

    val segments = relative.split('/').filter { it.isNotEmpty() && it != "." }
    if (segments.isEmpty() || segments.any { it == ".." }) return null
    return segments.joinToString("/")
}

/**
 * A model VEXO fetches on demand rather than shipping in the APK. Three of these exist: the neural
 * voice, the wake-word spotter, and the speaker embedding extractor.
 */
sealed interface RemoteModel {
    /** Directory name under `filesDir/models`, and the cache key for an installed model. */
    val id: String
    val url: String

    /** Exact download size, used to resume and to reject a truncated transfer. */
    val bytes: Long

    /** Files that must exist for the model to count as installed, relative to its directory. */
    val required: List<String>

    /** A `.tar.bz2` whose single top-level directory is dropped so files land in the pack root. */
    data class Archive(
        override val id: String,
        override val url: String,
        override val bytes: Long,
        override val required: List<String>,
    ) : RemoteModel

    /** A bare file — speaker embedding extractors ship as a single `.onnx`, with no archive. */
    data class SingleFile(
        override val id: String,
        override val url: String,
        override val bytes: Long,
        val fileName: String,
    ) : RemoteModel {
        override val required: List<String> get() = listOf(fileName)
    }
}

/**
 * Owns the on-disk models under `filesDir/models/<id>`.
 *
 * Downloads are resumable by HTTP range request, because VEXO's process can be reclaimed at any
 * moment: each attempt continues where the last stopped instead of restarting. Installation is
 * atomic — content lands in a staging directory and is only swapped into place once complete, so a
 * kill mid-install can never leave a partial model that [isInstalled] would accept.
 */
class ModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "models")

    /**
     * Serialises installs. Two callers wanting the same model would otherwise both write the same
     * `.part` file and race over the staging directory — observed when the wake service was started
     * twice and both attempts downloaded the spotter at once.
     */
    private val installLock = Mutex()

    fun dir(model: RemoteModel): File = File(root, model.id)

    fun isInstalled(model: RemoteModel): Boolean {
        val directory = dir(model)
        if (!File(directory, MARKER_NAME).exists()) return false
        return model.required.all { File(directory, it).exists() }
    }

    /**
     * Downloads and installs [model] unless it is already present, returning its directory.
     * Blocking, so it runs on IO. Safe to call repeatedly and from several coroutines at once.
     */
    suspend fun install(
        model: RemoteModel,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        installLock.withLock {
            val target = dir(model)
            // Re-check inside the lock: another caller may have finished while we waited.
            if (isInstalled(model)) return@withLock target
            installLocked(model, target, onProgress)
        }
    }

    private fun installLocked(
        model: RemoteModel,
        target: File,
        onProgress: (Float) -> Unit,
    ): File {
        root.mkdirs()
        val download = File(root, "${model.id}.part")

        val downloadStartedAt = System.nanoTime()
        download(model, download, onProgress)
        Log.i(
            TAG,
            "${model.id}: downloaded in ${(System.nanoTime() - downloadStartedAt) / 1_000_000}ms " +
                "(${download.length()}/${model.bytes} bytes)",
        )
        if (download.length() != model.bytes) {
            download.delete()
            throw IOException(
                "${model.id}: size mismatch, got ${download.length()} expected ${model.bytes}"
            )
        }

        val staging = File(root, "${model.id}.staging")
        staging.deleteRecursively()
        staging.mkdirs()

        val installStartedAt = System.nanoTime()
        when (model) {
            is RemoteModel.Archive -> {
                val entries = extract(download, staging)
                Log.i(
                    TAG,
                    "${model.id}: extracted $entries entries in " +
                        "${(System.nanoTime() - installStartedAt) / 1_000_000}ms",
                )
            }

            is RemoteModel.SingleFile -> {
                if (!download.renameTo(File(staging, model.fileName))) {
                    download.copyTo(File(staging, model.fileName), overwrite = true)
                }
            }
        }

        val missing = model.required.filterNot { File(staging, it).exists() }
        if (missing.isNotEmpty()) {
            staging.deleteRecursively()
            throw IOException("${model.id}: install is missing $missing")
        }

        target.deleteRecursively()
        if (!staging.renameTo(target)) {
            staging.deleteRecursively()
            throw IOException("${model.id}: could not move staged model into $target")
        }
        File(target, MARKER_NAME).writeText(model.id)
        download.delete()

        Log.i(TAG, "${model.id}: installed into ${target.absolutePath}")
        return target
    }

    /**
     * Fetches [model] into [destination], resuming from whatever is already on disk. A server that
     * ignores the range header and replies 200 restarts the transfer rather than corrupting it.
     */
    private fun download(model: RemoteModel, destination: File, onProgress: (Float) -> Unit) {
        var have = if (destination.isFile) destination.length() else 0L
        if (have >= model.bytes) return

        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }

        try {
            val status = connection.responseCode
            val resuming = status == HttpURLConnection.HTTP_PARTIAL
            if (status != HttpURLConnection.HTTP_OK && !resuming) {
                throw IOException("${model.id}: download failed with HTTP $status")
            }
            if (have > 0 && !resuming) {
                Log.w(TAG, "${model.id}: server ignored range request, restarting")
                have = 0L
            }

            FileOutputStream(destination, resuming).use { sink ->
                connection.inputStream.use { source ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var written = have
                    var lastDecile = -1
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        val decile = (written * 10 / model.bytes).toInt()
                        if (decile != lastDecile) {
                            lastDecile = decile
                            onProgress(written.toFloat() / model.bytes)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Returns the number of files written. */
    private fun extract(archive: File, destination: File): Int {
        // Named to avoid shadowing this class's `root` (the models directory).
        val boundary = destination.canonicalFile
        var entries = 0

        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))
        ).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val relative = packRelativePath(entry.name) ?: continue

                val out = File(destination, relative)
                if (!out.canonicalPath.startsWith(boundary.path + File.separator)) {
                    throw IOException("Refusing archive entry outside the pack: ${entry.name}")
                }

                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { sink -> tar.copyTo(sink, COPY_BUFFER_BYTES) }
                entries++
            }
        }
        return entries
    }

    /**
     * These models total well over 100 MiB, which is not something to put on someone's cellular
     * data without asking, so they are only fetched on an unmetered network.
     */
    fun isUnmetered(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.activeNetwork
            ?.let { manager.getNetworkCapabilities(it) }
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
