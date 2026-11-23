package com.silentexec.android.net

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-App Model Downloader for SilentExec Android.
 * Streams GGUF weights and SHA256 checksums from GitHub Releases outside the APK into filesDir/models/.
 * Guarantees cryptographic integrity by downloading into a `.part` file, verifying SHA-256,
 * and atomically renaming to the target filename.
 */
class ModelDownloader(val modelsDir: File) {

    constructor(context: Context) : this(File(context.filesDir, "models"))

    companion object {
        const val DEFAULT_REPO = "tahadeab-ai/silent-exec"
        const val DEFAULT_TAG = "v0.1.0-model"
        const val DEFAULT_MODEL_FILENAME = "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf"
        const val BASE_RELEASE_URL = "https://github.com/tahadeab-ai/silent-exec/releases/download"
    }

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

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
     * Downloads and verifies the model synchronously, reporting progress from 0..100.
     * Should be called from a background thread or coroutine.
     */
    fun downloadModelBlocking(
        tag: String = DEFAULT_TAG,
        filename: String = DEFAULT_MODEL_FILENAME,
        onProgress: (Int) -> Unit = {}
    ): Result<File> {
        val targetFile = getModelFile(filename)
        val partFile = File(modelsDir, "$filename.part")

        return runCatching {
            val releaseUrl = "$BASE_RELEASE_URL/$tag"

            // 1. Fetch and parse SHA256SUMS.txt
            val checksumsUrl = "$releaseUrl/SHA256SUMS.txt"
            val checksumsText = fetchString(checksumsUrl)
            val expectedHash = parseExpectedSha256(checksumsText, filename)
                ?: throw IllegalStateException("Filename '$filename' not found in release checksums at $checksumsUrl")

            // 2. Check if already exists and matches hash
            if (targetFile.exists() && verifySha256(targetFile, expectedHash)) {
                onProgress(100)
                return@runCatching targetFile
            }

            // 3. Stream download into .part file
            val fileUrl = "$releaseUrl/$filename"
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Failed to download model: HTTP $responseCode from $fileUrl")
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(32768)
                    var read: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            // 4. Verify SHA-256 of downloaded .part file
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

            onProgress(100)
            targetFile
        }
    }

    fun downloadModelItem(
        item: ModelReleaseItem,
        onProgress: (Int) -> Unit = {}
    ): Result<File> {
        val targetFile = getModelFile(item.filename)
        val partFile = File(modelsDir, "${item.filename}.part")

        return runCatching {
            val expectedHash: String? = item.checksumsUrl?.let { cUrl ->
                runCatching {
                    val content = fetchString(cUrl)
                    parseExpectedSha256(content, item.filename)
                }.getOrNull()
            }

            if (expectedHash != null && targetFile.exists() && verifySha256(targetFile, expectedHash)) {
                onProgress(100)
                return@runCatching targetFile
            }

            val conn = URL(item.downloadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true

            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download model: HTTP ${conn.responseCode} from ${item.downloadUrl}")
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: item.sizeBytes
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(32768)
                    var read: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            if (expectedHash != null && !verifySha256(partFile, expectedHash)) {
                val computed = computeSha256(partFile)
                partFile.delete()
                throw IllegalStateException("SHA256 mismatch for ${item.filename}: expected $expectedHash, got $computed")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename ${partFile.name} to ${targetFile.name}")
            }

            onProgress(100)
            targetFile
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
