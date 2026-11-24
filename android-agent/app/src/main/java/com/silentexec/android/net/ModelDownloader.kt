package com.silentexec.android.net

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class DownloadStatus {
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    ERROR
}

data class DownloadState(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedMBps: Double,
    val etaSeconds: Long,
    val status: DownloadStatus
)

/**
 * In-App Model Downloader for SilentExec Android.
 * Streams GGUF weights and SHA256 checksums from GitHub Releases outside the APK into filesDir/models/.
 * Supports real HTTP byte-range continuation (Range: bytes=X-) with Pause/Resume/Cancel.
 */
class ModelDownloader(val modelsDir: File) {

    constructor(context: Context) : this(File(context.filesDir, "models"))

    companion object {
        const val DEFAULT_REPO = "tahadeab-ai/silent-exec"
        const val DEFAULT_TAG = "v0.1.0-model"
        const val DEFAULT_MODEL_FILENAME = "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf"
        const val BASE_RELEASE_URL = "https://github.com/tahadeab-ai/silent-exec/releases/download"
    }

    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    fun pause(id: String) {
        pauseFlags[id]?.set(true)
    }

    fun resume(id: String) {
        pauseFlags[id]?.set(false)
        cancelFlags[id]?.set(false)
    }

    fun cancel(id: String) {
        cancelFlags[id]?.set(true)
    }

    fun isPaused(id: String): Boolean = pauseFlags[id]?.get() == true
    fun isCancelled(id: String): Boolean = cancelFlags[id]?.get() == true

    /**
     * Resolves the target model file handle.
     */
    fun getModelFile(filename: String = DEFAULT_MODEL_FILENAME): File {
        return File(modelsDir, filename)
    }

    /**
     * Checks if the model file already exists on device.
     */
    fun isModelDownloaded(filename: String = DEFAULT_MODEL_FILENAME): Boolean {
        val file = getModelFile(filename)
        return file.exists() && file.length() > 0
    }

    fun listDownloadedModels(): List<File> {
        return modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("gguf", ignoreCase = true) && file.length() > 0
        }?.toList() ?: emptyList()
    }

    fun deleteModel(filename: String): Boolean {
        val file = getModelFile(filename)
        return if (file.exists()) file.delete() else false
    }

    /**
     * Computes the SHA-256 hex digest of a given file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses the SHA256SUMS.txt format to extract the expected hash for a given filename.
     */
    fun parseExpectedSha256(checksumsContent: String, targetFilename: String): String? {
        for (line in checksumsContent.lines()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val hash = parts[0]
                val name = parts[1].trimStart('*')
                if (name.equals(targetFilename, ignoreCase = true) || name.endsWith("/$targetFilename")) {
                    return hash.lowercase()
                }
            }
        }
        return null
    }

    /**
     * Verifies that the file matches the expected SHA-256 digest.
     */
    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val actual = computeSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    /**
     * Downloads and verifies the model synchronously, supporting HTTP byte-range continuation (Range: bytes=X-).
     * Reports detailed DownloadState and supports Pause/Resume/Cancel via thread-safe flags.
     */
    fun downloadModelBlocking(
        tag: String = DEFAULT_TAG,
        filename: String = DEFAULT_MODEL_FILENAME,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = downloadModelWithState(tag, filename) { state ->
        if (state.totalBytes > 0) {
            val percent = ((state.downloadedBytes * 100) / state.totalBytes).toInt().coerceIn(0, 100)
            onProgress(percent)
        }
    }

    fun downloadModelWithState(
        tag: String = DEFAULT_TAG,
        filename: String = DEFAULT_MODEL_FILENAME,
        onStateUpdate: (DownloadState) -> Unit = {}
    ): Result<File> {
        val targetFile = getModelFile(filename)
        val partFile = File(modelsDir, "$filename.part")
        val downloadId = filename

        val pauseFlag = pauseFlags.computeIfAbsent(downloadId) { AtomicBoolean(false) }
        val cancelFlag = cancelFlags.computeIfAbsent(downloadId) { AtomicBoolean(false) }
        pauseFlag.set(false)
        cancelFlag.set(false)

        return runCatching {
            val releaseUrl = "$BASE_RELEASE_URL/$tag"

            // 1. Fetch and parse SHA256SUMS.txt
            val checksumsUrl = "$releaseUrl/SHA256SUMS.txt"
            val checksumsText = fetchString(checksumsUrl)
            val expectedHash = parseExpectedSha256(checksumsText, filename)
                ?: throw IllegalStateException("Filename '$filename' not found in release checksums at $checksumsUrl")

            // 2. Check if already exists and matches hash
            if (targetFile.exists() && verifySha256(targetFile, expectedHash)) {
                val size = targetFile.length()
                onStateUpdate(DownloadState(size, size, 0.0, 0, DownloadStatus.COMPLETED))
                return@runCatching targetFile
            }

            // 3. Resumable range continuation
            val fileUrl = "$releaseUrl/$filename"
            downloadResumableFile(fileUrl, partFile, expectedHash, downloadId, pauseFlag, cancelFlag, onStateUpdate)

            // 4. Verify SHA-256 of downloaded .part file
            onStateUpdate(DownloadState(partFile.length(), partFile.length(), 0.0, 0, DownloadStatus.VERIFYING))
            if (!verifySha256(partFile, expectedHash)) {
                val computed = computeSha256(partFile)
                partFile.delete()
                throw IllegalStateException("SHA256 verification failed for $filename: expected $expectedHash, got $computed")
            }

            // 5. Atomically rename .part to target filename
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename ${partFile.name} to ${targetFile.name}")
            }

            val finalSize = targetFile.length()
            onStateUpdate(DownloadState(finalSize, finalSize, 0.0, 0, DownloadStatus.COMPLETED))
            targetFile
        }
    }

    fun downloadModelItem(
        item: ModelReleaseItem,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = downloadModelItemWithState(item) { state ->
        if (state.totalBytes > 0) {
            val percent = ((state.downloadedBytes * 100) / state.totalBytes).toInt().coerceIn(0, 100)
            onProgress(percent)
        }
    }

    fun downloadModelItemWithState(
        item: ModelReleaseItem,
        onStateUpdate: (DownloadState) -> Unit = {}
    ): Result<File> {
        val targetFile = getModelFile(item.filename)
        val partFile = File(modelsDir, "${item.filename}.part")
        val downloadId = item.filename

        val pauseFlag = pauseFlags.computeIfAbsent(downloadId) { AtomicBoolean(false) }
        val cancelFlag = cancelFlags.computeIfAbsent(downloadId) { AtomicBoolean(false) }
        pauseFlag.set(false)
        cancelFlag.set(false)

        return runCatching {
            val expectedHash: String? = item.checksumsUrl?.let { cUrl ->
                runCatching {
                    val content = fetchString(cUrl)
                    parseExpectedSha256(content, item.filename)
                }.getOrNull()
            }

            if (expectedHash != null && targetFile.exists() && verifySha256(targetFile, expectedHash)) {
                val size = targetFile.length()
                onStateUpdate(DownloadState(size, size, 0.0, 0, DownloadStatus.COMPLETED))
                return@runCatching targetFile
            }

            downloadResumableFile(item.downloadUrl, partFile, expectedHash, downloadId, pauseFlag, cancelFlag, onStateUpdate)

            if (expectedHash != null) {
                onStateUpdate(DownloadState(partFile.length(), partFile.length(), 0.0, 0, DownloadStatus.VERIFYING))
                if (!verifySha256(partFile, expectedHash)) {
                    val computed = computeSha256(partFile)
                    partFile.delete()
                    throw IllegalStateException("SHA256 mismatch for ${item.filename}: expected $expectedHash, got $computed")
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename ${partFile.name} to ${targetFile.name}")
            }

            val finalSize = targetFile.length()
            onStateUpdate(DownloadState(finalSize, finalSize, 0.0, 0, DownloadStatus.COMPLETED))
            targetFile
        }
    }

    private fun downloadResumableFile(
        fileUrl: String,
        partFile: File,
        expectedHash: String?,
        downloadId: String,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
        onStateUpdate: (DownloadState) -> Unit
    ) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val url = URL(fileUrl)
        var connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true

        if (existingBytes > 0) {
            connection.setRequestProperty("Range", "bytes=$existingBytes-")
        }

        var responseCode = connection.responseCode
        // If server redirects with Range, some servers lose Range on redirect; handle redirect explicitly
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
            val redirectUrl = connection.getHeaderField("Location")
            connection.disconnect()
            if (!redirectUrl.isNullOrEmpty()) {
                connection = URL(redirectUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingBytes-")
                }
                responseCode = connection.responseCode
            }
        }

        val isPartial = (responseCode == HttpURLConnection.HTTP_PARTIAL) // 206
        val appendMode = isPartial && existingBytes > 0

        if (responseCode !in 200..299) {
            throw IllegalStateException("Failed to download model: HTTP $responseCode from $fileUrl")
        }

        val contentLength = connection.contentLengthLong
        val totalBytes = when {
            isPartial -> existingBytes + contentLength
            contentLength > 0 -> contentLength
            else -> existingBytes
        }

        var downloadedBytes = if (appendMode) existingBytes else 0L
        if (!appendMode && existingBytes > 0) {
            partFile.delete()
        }

        val startTime = System.currentTimeMillis()
        var lastCalcTime = startTime
        var lastCalcBytes = downloadedBytes

        val output = RandomAccessFile(partFile, "rw")
        if (appendMode) {
            output.seek(existingBytes)
        } else {
            output.setLength(0)
        }

        connection.inputStream.use { input ->
            output.use { raf ->
                val buffer = ByteArray(65536)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (cancelFlag.get()) {
                        onStateUpdate(DownloadState(downloadedBytes, totalBytes, 0.0, 0, DownloadStatus.ERROR))
                        throw IllegalStateException("Download cancelled by user")
                    }
                    if (pauseFlag.get()) {
                        onStateUpdate(DownloadState(downloadedBytes, totalBytes, 0.0, 0, DownloadStatus.PAUSED))
                        throw IllegalStateException("Download paused by user")
                    }

                    raf.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastCalcTime
                    if (timeDiff >= 500) {
                        val bytesDiff = downloadedBytes - lastCalcBytes
                        val speedBytesPerSec = (bytesDiff * 1000.0) / timeDiff
                        val speedMBps = speedBytesPerSec / (1024.0 * 1024.0)
                        val remainingBytes = totalBytes - downloadedBytes
                        val etaSeconds = if (speedBytesPerSec > 1024.0 && remainingBytes > 0) {
                            (remainingBytes / speedBytesPerSec).toLong()
                        } else {
                            0L
                        }

                        lastCalcTime = now
                        lastCalcBytes = downloadedBytes

                        onStateUpdate(
                            DownloadState(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedMBps = speedMBps,
                                etaSeconds = etaSeconds,
                                status = DownloadStatus.DOWNLOADING
                            )
                        )
                    }
                }
            }
        }
    }

    private fun fetchString(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Failed to fetch $urlString: HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
