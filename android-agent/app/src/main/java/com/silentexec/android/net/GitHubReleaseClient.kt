package com.silentexec.android.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

data class ModelReleaseItem(
    val tag: String,
    val filename: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val checksumsUrl: String?
) {
    val sizeMb: Double
        get() = sizeBytes / (1024.0 * 1024.0)
}

data class AppReleaseItem(
    val tag: String,
    val versionName: String,
    val apkFilename: String,
    val apkDownloadUrl: String,
    val checksumsUrl: String?
)

class GitHubReleaseClient(
    val repo: String = "tahadeab-ai/silent-exec"
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchReleasesJson(): Result<String> = runCatching {
        val endpoint = "https://api.github.com/repos/$repo/releases"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "SilentExec-Android")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("GitHub Releases API returned HTTP $code")
        }

        connection.inputStream.bufferedReader().use { it.readText() }
    }

    fun parseModelReleases(jsonText: String): List<ModelReleaseItem> {
        val root = json.parseToJsonElement(jsonText).jsonArray
        val models = mutableListOf<ModelReleaseItem>()

        for (el in root) {
            val relObj = el.jsonObject
            val tag = relObj["tag_name"]?.jsonPrimitive?.content ?: continue
            if (!tag.contains("-model")) continue

            val assets = relObj["assets"]?.jsonArray ?: continue
            var ggufFilename: String? = null
            var ggufSize = 0L
            var ggufUrl: String? = null
            var checksumsUrl: String? = null

            for (assetEl in assets) {
                val assetObj = assetEl.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content ?: continue
                val downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: continue
                val size = assetObj["size"]?.jsonPrimitive?.longOrNull ?: 0L

                if (name.endsWith(".gguf", ignoreCase = true)) {
                    ggufFilename = name
                    ggufSize = size
                    ggufUrl = downloadUrl
                } else if (name.equals("SHA256SUMS.txt", ignoreCase = true)) {
                    checksumsUrl = downloadUrl
                }
            }

            if (ggufFilename != null && ggufUrl != null) {
                models.add(
                    ModelReleaseItem(
                        tag = tag,
                        filename = ggufFilename,
                        sizeBytes = ggufSize,
                        downloadUrl = ggufUrl,
                        checksumsUrl = checksumsUrl
                    )
                )
            }
        }

        return models
    }

    fun parseLatestAppRelease(jsonText: String): AppReleaseItem? {
        val root = json.parseToJsonElement(jsonText).jsonArray

        for (el in root) {
            val relObj = el.jsonObject
            val tag = relObj["tag_name"]?.jsonPrimitive?.content ?: continue
            if (!tag.contains("-android")) continue

            val assets = relObj["assets"]?.jsonArray ?: continue
            var apkFilename: String? = null
            var apkUrl: String? = null
            var checksumsUrl: String? = null

            for (assetEl in assets) {
                val assetObj = assetEl.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content ?: continue
                val downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: continue

                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkFilename = name
                    apkUrl = downloadUrl
                } else if (name.equals("SHA256SUMS.txt", ignoreCase = true)) {
                    checksumsUrl = downloadUrl
                }
            }

            if (apkFilename != null && apkUrl != null) {
                val cleanVersion = tag
                    .removePrefix("v")
                    .removeSuffix("-android")
                    .trim()

                return AppReleaseItem(
                    tag = tag,
                    versionName = cleanVersion,
                    apkFilename = apkFilename,
                    apkDownloadUrl = apkUrl,
                    checksumsUrl = checksumsUrl
                )
            }
        }

        return null
    }

    companion object {
        fun parseSemVer(versionStr: String): Triple<Int, Int, Int> {
            val clean = versionStr.trim().removePrefix("v").substringBefore("-")
            val parts = clean.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return Triple(major, minor, patch)
        }

        fun compareVersions(v1: String, v2: String): Int {
            val (maj1, min1, pat1) = parseSemVer(v1)
            val (maj2, min2, pat2) = parseSemVer(v2)

            return when {
                maj1 != maj2 -> maj1.compareTo(maj2)
                min1 != min2 -> min1.compareTo(min2)
                else -> pat1.compareTo(pat2)
            }
        }

        fun isUpdateAvailable(currentVersion: String, latestReleaseVersion: String): Boolean {
            return compareVersions(currentVersion, latestReleaseVersion) < 0
        }
    }
}
