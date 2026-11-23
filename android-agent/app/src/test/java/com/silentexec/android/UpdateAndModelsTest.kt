package com.silentexec.android

import com.silentexec.android.core.ActionType
import com.silentexec.android.core.ExecutionMode
import com.silentexec.android.core.FastRuleRouter
import com.silentexec.android.core.InferenceSource
import com.silentexec.android.net.GitHubReleaseClient
import com.silentexec.android.net.ModelDownloader
import com.silentexec.android.net.ModelReleaseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateAndModelsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleReleasesJson = """
    [
      {
        "tag_name": "v0.3.0-android",
        "name": "SilentExec Android v0.3.0-android",
        "assets": [
          {
            "name": "silent-exec-android.apk",
            "size": 15728640,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.3.0-android/silent-exec-android.apk"
          },
          {
            "name": "SHA256SUMS.txt",
            "size": 128,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.3.0-android/SHA256SUMS.txt"
          }
        ]
      },
      {
        "tag_name": "v0.2.0-model",
        "name": "SilentExec Model v0.2.0-model",
        "assets": [
          {
            "name": "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf",
            "size": 397443072,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.2.0-model/Qwen2.5-0.5B-Instruct.Q4_K_M.gguf"
          },
          {
            "name": "SHA256SUMS.txt",
            "size": 128,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.2.0-model/SHA256SUMS.txt"
          }
        ]
      },
      {
        "tag_name": "v0.1.0-model",
        "name": "SilentExec Model v0.1.0-model",
        "assets": [
          {
            "name": "Qwen2.5-0.5B-Instruct-v1.gguf",
            "size": 350000000,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.1.0-model/Qwen2.5-0.5B-Instruct-v1.gguf"
          }
        ]
      },
      {
        "tag_name": "v0.2.0-pc",
        "name": "SilentExec PC Agent v0.2.0-pc",
        "assets": [
          {
            "name": "silent-exec-pc.exe",
            "size": 8388608,
            "browser_download_url": "https://github.com/tahadeab-ai/silent-exec/releases/download/v0.2.0-pc/silent-exec-pc.exe"
          }
        ]
      }
    ]
    """.trimIndent()

    @Test
    fun test01_parseModelReleasesExtractsGgufAndChecksums() {
        val client = GitHubReleaseClient()
        val models = client.parseModelReleases(sampleReleasesJson)

        assertEquals(2, models.size)

        val first = models[0]
        assertEquals("v0.2.0-model", first.tag)
        assertEquals("Qwen2.5-0.5B-Instruct.Q4_K_M.gguf", first.filename)
        assertEquals(397443072L, first.sizeBytes)
        assertTrue(first.downloadUrl.contains("v0.2.0-model"))
        assertNotNull(first.checksumsUrl)

        val second = models[1]
        assertEquals("v0.1.0-model", second.tag)
        assertEquals("Qwen2.5-0.5B-Instruct-v1.gguf", second.filename)
        assertNull(second.checksumsUrl)
    }

    @Test
    fun test02_parseModelReleasesIgnoresNonModelReleases() {
        val client = GitHubReleaseClient()
        val models = client.parseModelReleases(sampleReleasesJson)

        for (model in models) {
            assertTrue(model.tag.contains("-model"))
            assertFalse(model.tag.contains("-pc"))
            assertFalse(model.tag.contains("-android"))
        }
    }

    @Test
    fun test03_parseLatestAppReleaseExtractsApk() {
        val client = GitHubReleaseClient()
        val release = client.parseLatestAppRelease(sampleReleasesJson)

        assertNotNull(release)
        assertEquals("v0.3.0-android", release!!.tag)
        assertEquals("0.3.0", release.versionName)
        assertEquals("silent-exec-android.apk", release.apkFilename)
        assertTrue(release.apkDownloadUrl.endsWith("silent-exec-android.apk"))
        assertNotNull(release.checksumsUrl)
    }

    @Test
    fun test04_semVerParsing() {
        val (maj1, min1, pat1) = GitHubReleaseClient.parseSemVer("0.3.0")
        assertEquals(0, maj1)
        assertEquals(3, min1)
        assertEquals(0, pat1)

        val (maj2, min2, pat2) = GitHubReleaseClient.parseSemVer("v1.2.4-android")
        assertEquals(1, maj2)
        assertEquals(2, min2)
        assertEquals(4, pat2)
    }

    @Test
    fun test05_semVerComparison() {
        assertTrue(GitHubReleaseClient.compareVersions("0.2.0", "0.3.0") < 0)
        assertTrue(GitHubReleaseClient.compareVersions("0.3.0", "0.2.0") > 0)
        assertEquals(0, GitHubReleaseClient.compareVersions("0.3.0", "0.3.0"))
        assertTrue(GitHubReleaseClient.compareVersions("0.3.0", "0.3.1") < 0)
        assertTrue(GitHubReleaseClient.compareVersions("1.0.0", "0.9.9") > 0)
    }

    @Test
    fun test06_isUpdateAvailable() {
        assertTrue(GitHubReleaseClient.isUpdateAvailable("0.2.0", "0.3.0"))
        assertFalse(GitHubReleaseClient.isUpdateAvailable("0.3.0", "0.3.0"))
        assertFalse(GitHubReleaseClient.isUpdateAvailable("0.3.1", "0.3.0"))
    }

    @Test
    fun test07_appSettingsModeEnumTransitions() {
        assertEquals(ExecutionMode.SILENT, ExecutionMode.fromString("silent"))
        assertEquals(ExecutionMode.SILENT, ExecutionMode.fromString("SILENT"))
        assertEquals(ExecutionMode.INTERACTIVE, ExecutionMode.fromString("interactive"))
        assertEquals(ExecutionMode.DRY_RUN, ExecutionMode.fromString("dry_run"))
        assertEquals(ExecutionMode.SILENT, ExecutionMode.fromString("nonexistent_mode"))

        assertEquals(InferenceSource.OFFLINE, InferenceSource.fromString("offline"))
        assertEquals(InferenceSource.LAN_BRIDGE, InferenceSource.fromString("lan_bridge"))
        assertEquals(InferenceSource.OFFLINE, InferenceSource.fromString("unknown_engine"))
    }

    @Test
    fun test08_fastRuleRouterDeterministicAndroid() {
        val cmdBackAr = FastRuleRouter.route("ارجع")
        assertNotNull(cmdBackAr)
        assertEquals(ActionType.UI_BACK, cmdBackAr!!.action)

        val cmdBackEn = FastRuleRouter.route("back")
        assertNotNull(cmdBackEn)
        assertEquals(ActionType.UI_BACK, cmdBackEn!!.action)

        val cmdHome = FastRuleRouter.route("الشاشة الرئيسية")
        assertNotNull(cmdHome)
        assertEquals(ActionType.UI_HOME, cmdHome!!.action)

        val cmdScroll = FastRuleRouter.route("انزل")
        assertNotNull(cmdScroll)
        assertEquals(ActionType.UI_SCROLL, cmdScroll!!.action)
        assertEquals("down", cmdScroll.target)

        val cmdVolAr = FastRuleRouter.route("ارفع الصوت 80")
        assertNotNull(cmdVolAr)
        assertEquals(ActionType.SET_VOLUME, cmdVolAr!!.action)
        assertEquals("80", cmdVolAr.target)

        val cmdVolEn = FastRuleRouter.route("set volume to 60")
        assertNotNull(cmdVolEn)
        assertEquals(ActionType.SET_VOLUME, cmdVolEn!!.action)
        assertEquals("60", cmdVolEn.target)

        val cmdUrl = FastRuleRouter.route("open https://github.com")
        assertNotNull(cmdUrl)
        assertEquals(ActionType.OPEN_URL, cmdUrl!!.action)
        assertEquals("https://github.com", cmdUrl.target)

        val naturalComplex = FastRuleRouter.route("ابحث لي عن ملف تقرير التخرج")
        assertNull(naturalComplex)
    }

    @Test
    fun test09_modelReleaseItemSizeMbCalculation() {
        val item = ModelReleaseItem(
            tag = "v0.2.0-model",
            filename = "Qwen2.5-0.5B-Instruct.Q4_K_M.gguf",
            sizeBytes = 397443072L,
            downloadUrl = "https://example.com/model.gguf",
            checksumsUrl = null
        )

        assertEquals(379.03, item.sizeMb, 0.1)
    }

    @Test
    fun test10_modelDownloaderDeleteAndList() {
        val modelsDir = tempFolder.newFolder("models")
        val downloader = ModelDownloader(modelsDir)

        val f1 = File(modelsDir, "model1.gguf").apply { writeText("dummy gguf weights") }
        val f2 = File(modelsDir, "model2.gguf").apply { writeText("dummy gguf weights 2") }
        File(modelsDir, "ignore.txt").apply { writeText("not gguf") }

        val downloaded = downloader.listDownloadedModels()
        assertEquals(2, downloaded.size)

        assertTrue(downloader.isModelDownloaded("model1.gguf"))
        assertTrue(downloader.deleteModel("model1.gguf"))
        assertFalse(downloader.isModelDownloaded("model1.gguf"))
        assertEquals(1, downloader.listDownloadedModels().size)
    }
}
