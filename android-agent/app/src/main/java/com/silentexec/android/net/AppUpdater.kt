package com.silentexec.android.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.silentexec.android.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseItem) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
}

class AppUpdater(
    private val context: Context,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient()
) {

    private val updatesDir: File = File(context.cacheDir, "updates").apply {
        if (!exists()) mkdirs()
    }

    fun checkForUpdate(): Result<UpdateCheckResult> = runCatching {
        val jsonText = releaseClient.fetchReleasesJson().getOrThrow()
        val latest = releaseClient.parseLatestAppRelease(jsonText)
            ?: return@runCatching UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)

        val currentVersion = BuildConfig.VERSION_NAME
        if (GitHubReleaseClient.isUpdateAvailable(currentVersion, latest.versionName)) {
            UpdateCheckResult.UpdateAvailable(latest)
        } else {
            UpdateCheckResult.UpToDate(currentVersion)
        }
    }

    fun downloadAndInstallUpdate(
        release: AppReleaseItem,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = runCatching {
        val targetApk = File(updatesDir, release.apkFilename)
        val partFile = File(updatesDir, "${release.apkFilename}.part")

        // 1. Checksums if available
        var expectedHash: String? = null
        if (release.checksumsUrl != null) {
            runCatching {
                val text = fetchString(release.checksumsUrl)
                expectedHash = parseSha256(text, release.apkFilename)
            }
        }

        // 2. Download into .part
        val conn = URL(release.apkDownloadUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true

        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Failed to download APK: HTTP ${conn.responseCode} from ${release.apkDownloadUrl}")
        }

        val totalBytes = conn.contentLengthLong
        var downloadedBytes = 0L

        conn.inputStream.use { input ->
            FileOutputStream(partFile).use { output ->
                val buffer = ByteArray(32768)
                var read: Int
                var lastPercent = -1

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read

                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }

        // 3. Verify SHA256 if expected
        if (expectedHash != null) {
            val actualHash = computeSha256(partFile)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                partFile.delete()
                throw IllegalStateException("APK SHA256 mismatch: expected $expectedHash, got $actualHash")
            }
        }

        // 4. Rename to target
        if (targetApk.exists()) targetApk.delete()
        if (!partFile.renameTo(targetApk)) {
            throw IllegalStateException("Failed to rename ${partFile.name} to ${targetApk.name}")
        }

        onProgress(100)
        installApk(targetApk)
        targetApk
    }

    fun installApk(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchString(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseSha256(checksumsText: String, targetFilename: String): String? {
        for (line in checksumsText.lines()) {
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

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
