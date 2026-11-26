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
    val checksumsUrl: String?,
    val description: String? = null
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

    fun parseModelReleases(jsonText: String, includeCurated: Boolean = false): List<ModelReleaseItem> {
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

        if (includeCurated) {
            for (curated in CURATED_EDGE_MODELS) {
                if (models.none { it.filename.equals(curated.filename, ignoreCase = true) }) {
                    models.add(curated)
                }
            }
        }

        return models
    }

    fun getAllCatalogModels(jsonText: String? = null): List<ModelReleaseItem> {
        return getAllCatalogModels(jsonText, resolveHuggingFace = false)
    }

    fun getAllCatalogModels(
        jsonText: String? = null,
        resolveHuggingFace: Boolean
    ): List<ModelReleaseItem> {
        val releases = if (!jsonText.isNullOrBlank()) {
            parseModelReleases(jsonText, includeCurated = false)
        } else emptyList()
        val result = releases.toMutableList()

        val dynamicLfm3 = if (resolveHuggingFace) {
            fetchHuggingFaceQ4KM(
                repoId = "LiquidAI/LFM3-1.7B-GGUF",
                description = "LiquidAI/LFM3-1.7B (Fast Edge Agent)"
            ).getOrNull()
        } else null

        val curatedList = if (dynamicLfm3 != null) {
            CURATED_EDGE_MODELS.map { item ->
                if (item.downloadUrl.contains("LiquidAI/LFM3-1.7B-GGUF")) dynamicLfm3 else item
            }
        } else {
            CURATED_EDGE_MODELS
        }

        for (curated in curatedList) {
            if (result.none { it.filename.equals(curated.filename, ignoreCase = true) }) {
                result.add(curated)
            }
        }
        return result
    }

    fun fetchHuggingFaceQ4KM(
        repoId: String,
        description: String? = null
    ): Result<ModelReleaseItem> = runCatching {
        val endpoint = "https://huggingface.co/api/models/$repoId/tree/main"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "SilentExec-Android")
        connection.setRequestProperty("Accept", "application/json")

        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Hugging Face API returned HTTP $code for $repoId")
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        parseHuggingFaceTreeQ4KM(repoId, body, description)
            ?: throw IllegalStateException("No *Q4_K_M*.gguf file found in $repoId")
    }

    fun parseHuggingFaceTreeQ4KM(
        repoId: String,
        jsonText: String,
        description: String? = null
    ): ModelReleaseItem? {
        val root = json.parseToJsonElement(jsonText).jsonArray
        for (el in root) {
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: continue
            if (type != "file") continue
            val path = obj["path"]?.jsonPrimitive?.content ?: continue
            if (path.endsWith(".gguf", ignoreCase = true) && path.contains("Q4_K_M", ignoreCase = true)) {
                val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                val filename = path.substringAfterLast('/')
                return ModelReleaseItem(
                    tag = "curated-edge",
                    filename = filename,
                    sizeBytes = size,
                    downloadUrl = "https://huggingface.co/$repoId/resolve/main/$path?download=true",
                    checksumsUrl = null,
                    description = description ?: repoId
                )
            }
        }
        return null
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
        val CURATED_EDGE_MODELS = listOf(
            ModelReleaseItem(
                tag = "curated-edge",
                filename = "Qwen3.5-2B-Q4_K_M.gguf",
                sizeBytes = 1_374_389_534L, // 1.28 GB
                downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
                checksumsUrl = null,
                description = "unsloth/Qwen3.5-2B (Recommended Default • Apache-2.0 • Non-Thinking • 201 Languages incl. Arabic)"
            ),
            ModelReleaseItem(
                tag = "curated-edge",
                filename = "LFM3-VL-2B-Q4_K_M.gguf",
                sizeBytes = 1_450_000_000L,
                downloadUrl = "https://huggingface.co/LiquidAI/LFM3-VL-2B-GGUF/resolve/main/LFM3-VL-2B-Q4_K_M.gguf?download=true",
                checksumsUrl = null,
                description = "LiquidAI/LFM3-VL-2B (Vision-Heavy Preset • XML Tool-Call Mode)"
            ),
            ModelReleaseItem(
                tag = "curated-edge",
                filename = "FunctionGemma-270M-Q4_K_M.gguf",
                sizeBytes = 230_000_000L,
                downloadUrl = "https://huggingface.co/unsloth/functiongemma-270m-it-GGUF/resolve/main/functiongemma-270m-it-Q4_K_M.gguf?download=true",
                checksumsUrl = null,
                description = "FunctionGemma-270M (<250 MB • Fast Intent Router Slot)"
            ),
            ModelReleaseItem(
                tag = "curated-edge",
                filename = "LFM3-1.7B-Q4_K_M.gguf",
                sizeBytes = 1_099_627_808L,
                downloadUrl = "https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf?download=true",
                checksumsUrl = null,
                description = "LiquidAI/LFM3-1.7B (Experimental / Thinking Mode • XML Tool-Call)"
            ),
            ModelReleaseItem(
                tag = "curated-edge",
                filename = "Qwen3-1.7B-Q4_K_M.gguf",
                sizeBytes = 1_107_409_472L,
                downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true",
                checksumsUrl = null,
                description = "unsloth/Qwen3-1.7B-GGUF (Strong Arabic & Multi-Step Reasoning)"
            )
        )

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
