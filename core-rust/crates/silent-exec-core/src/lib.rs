use serde::{Deserialize, Serialize};

/// The 15 cross-platform tool actions supported by SilentExec.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ActionType {
    // PC & Shared Tools
    OpenFolder,
    OpenFile,
    LaunchApp,
    SearchFiles,
    SetVolume,
    SetBrightness,
    CreateNote,
    CopyToClipboard,
    DeleteFile,
    MoveFile,
    SystemPower,
    // Android-Specific Tools
    OpenAndroidApp,
    ToggleSetting,
    SendMessage,
    OpenUrl,
}

impl ActionType {
    /// Returns the safety tier for this action.
    #[must_use]
    pub fn tier(&self) -> SafetyTier {
        match self {
            Self::OpenFolder
            | Self::OpenFile
            | Self::LaunchApp
            | Self::SearchFiles
            | Self::SetVolume
            | Self::SetBrightness
            | Self::CreateNote
            | Self::CopyToClipboard
            | Self::OpenAndroidApp
            | Self::OpenUrl => SafetyTier::Tier1Silent,

            Self::DeleteFile
            | Self::MoveFile
            | Self::SystemPower
            | Self::ToggleSetting
            | Self::SendMessage => SafetyTier::Tier2Confirm,
        }
    }
}

/// Target execution platform.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Platform {
    Pc,
    Android,
    Any,
}

/// Strict ActionCommand contract matching `schemas/action_schema.json`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ActionCommand {
    pub action: ActionType,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub target: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub folder: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub args: Option<serde_json::Value>,
    pub platform: Platform,
    pub confidence: f64,
}

impl ActionCommand {
    /// Parses and strictly validates an `ActionCommand` from a JSON string.
    pub fn from_json(json_str: &str) -> Result<Self, String> {
        let cmd: Self = serde_json::from_str(json_str).map_err(|e| e.to_string())?;
        if !(0.0..=1.0).contains(&cmd.confidence) {
            return Err(format!(
                "Confidence score {} is out of valid range [0.0, 1.0]",
                cmd.confidence
            ));
        }
        Ok(cmd)
    }

    /// Serializes the `ActionCommand` to a canonical JSON string.
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }
}

/// Operational safety tier classification.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SafetyTier {
    Tier1Silent,
    Tier2Confirm,
}

/// The deterministic decision rendered by the SafetyGate.
#[derive(Debug, Clone, PartialEq)]
pub enum SafetyDecision {
    AllowSilent,
    RequireConfirm { reason: String },
    Block { reason: String },
}

/// Safety Gate that deterministically evaluates commands against safety policies.
pub struct SafetyGate;

impl SafetyGate {
    /// Confidence threshold below which Tier 1 actions are downgraded to require confirmation.
    pub const CONFIDENCE_THRESHOLD: f64 = 0.65;

    /// Dangerous shell metacharacters that cause immediate Tier 3 blocking.
    const SHELL_METACHARACTERS: &'static [&'static str] = &["&&", "||", ";", "|", "$(", "`"];

    /// Hard-blocked system paths (normalized lower-case slashes).
    const PROTECTED_SYSTEM_PREFIXES: &'static [&'static str] = &[
        "c:/windows",
        "c:/program files",
        "c:/program files (x86)",
        "/etc",
        "/system",
        "/root",
        "/bin",
        "/sbin",
        "/usr/bin",
        "/usr/sbin",
    ];

    /// Evaluates an `ActionCommand` and renders a deterministic `SafetyDecision`.
    #[must_use]
    pub fn evaluate(cmd: &ActionCommand) -> SafetyDecision {
        // 1. Validate confidence bounds
        if !(0.0..=1.0).contains(&cmd.confidence) {
            return SafetyDecision::Block {
                reason: format!(
                    "Confidence score {} is invalid (must be between 0.0 and 1.0)",
                    cmd.confidence
                ),
            };
        }

        // 2. Check for blocked patterns in `target` and `folder`
        if let Some(target) = &cmd.target {
            if let Some(reason) = Self::detect_blocked_pattern(target, "target") {
                return SafetyDecision::Block { reason };
            }
        }

        if let Some(folder) = &cmd.folder {
            if let Some(reason) = Self::detect_blocked_pattern(folder, "folder") {
                return SafetyDecision::Block { reason };
            }
        }

        // 3. Also check string values inside `args` if present
        if let Some(args) = &cmd.args {
            if let Some(reason) = Self::detect_args_blocked_patterns(args) {
                return SafetyDecision::Block { reason };
            }
        }

        // 4. Evaluate Safety Tier and Confidence
        match cmd.action.tier() {
            SafetyTier::Tier2Confirm => SafetyDecision::RequireConfirm {
                reason: format!(
                    "Action '{:?}' is Tier 2 and requires explicit user confirmation",
                    cmd.action
                ),
            },
            SafetyTier::Tier1Silent => {
                if cmd.confidence < Self::CONFIDENCE_THRESHOLD {
                    SafetyDecision::RequireConfirm {
                        reason: format!(
                            "Confidence {:.2} is below the {:.2} threshold; downgraded to require confirmation",
                            cmd.confidence,
                            Self::CONFIDENCE_THRESHOLD
                        ),
                    }
                } else {
                    SafetyDecision::AllowSilent
                }
            }
        }
    }

    /// Detects path traversal, shell metacharacters, or protected path access.
    fn detect_blocked_pattern(value: &str, field_name: &str) -> Option<String> {
        // Path traversal check
        if value.contains("..") {
            return Some(format!(
                "Path traversal attempt detected in {field_name}: '{value}'"
            ));
        }

        // Shell metacharacter injection check
        for &meta in Self::SHELL_METACHARACTERS {
            if value.contains(meta) {
                return Some(format!(
                    "Shell injection metacharacter '{meta}' detected in {field_name}: '{value}'"
                ));
            }
        }

        // Protected system path check
        let normalized = value.replace('\\', "/").to_lowercase();
        let trimmed = normalized.trim();
        for &prefix in Self::PROTECTED_SYSTEM_PREFIXES {
            if trimmed == prefix
                || trimmed.starts_with(&format!("{prefix}/"))
                || trimmed.starts_with(&format!("{prefix}\\"))
            {
                return Some(format!(
                    "Access to protected system path '{prefix}' blocked in {field_name}: '{value}'"
                ));
            }
        }

        None
    }

    /// Recursively checks JSON args for blocked patterns in string values.
    fn detect_args_blocked_patterns(value: &serde_json::Value) -> Option<String> {
        match value {
            serde_json::Value::String(s) => Self::detect_blocked_pattern(s, "args"),
            serde_json::Value::Array(arr) => {
                for item in arr {
                    if let Some(reason) = Self::detect_args_blocked_patterns(item) {
                        return Some(reason);
                    }
                }
                None
            }
            serde_json::Value::Object(map) => {
                for (k, v) in map {
                    if let Some(reason) = Self::detect_blocked_pattern(k, "args_key") {
                        return Some(reason);
                    }
                    if let Some(reason) = Self::detect_args_blocked_patterns(v) {
                        return Some(reason);
                    }
                }
                None
            }
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_01_valid_english_command_allow_silent() {
        let json = r#"{
            "action": "open_folder",
            "target": "Documents/Projects",
            "platform": "pc",
            "confidence": 0.95
        }"#;
        let cmd = ActionCommand::from_json(json).expect("Failed to parse valid JSON");
        assert_eq!(cmd.action, ActionType::OpenFolder);
        assert_eq!(SafetyGate::evaluate(&cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_02_valid_arabic_command_allow_silent() {
        let json = r#"{
            "action": "create_note",
            "target": "قائمة المهام اليومية",
            "folder": "ملاحظاتي",
            "platform": "any",
            "confidence": 0.88
        }"#;
        let cmd = ActionCommand::from_json(json).expect("Failed to parse valid Arabic JSON");
        assert_eq!(cmd.action, ActionType::CreateNote);
        assert_eq!(SafetyGate::evaluate(&cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_03_unknown_action_rejected() {
        let json = r#"{
            "action": "format_c_drive",
            "target": "C:",
            "platform": "pc",
            "confidence": 0.99
        }"#;
        let result = ActionCommand::from_json(json);
        assert!(
            result.is_err(),
            "Unknown action should fail deserialization"
        );
    }

    #[test]
    fn test_04_extra_fields_rejected_by_deny_unknown_fields() {
        let json = r#"{
            "action": "open_file",
            "target": "report.pdf",
            "platform": "pc",
            "confidence": 0.90,
            "raw_shell_command": "rm -rf /"
        }"#;
        let result = ActionCommand::from_json(json);
        assert!(
            result.is_err(),
            "Extra unknown fields must be rejected by deny_unknown_fields"
        );
    }

    #[test]
    fn test_05_missing_required_platform_rejected() {
        let json = r#"{
            "action": "open_file",
            "target": "report.pdf",
            "confidence": 0.90
        }"#;
        let result = ActionCommand::from_json(json);
        assert!(result.is_err(), "Missing platform field must fail");
    }

    #[test]
    fn test_06_path_traversal_target_blocked() {
        let cmd = ActionCommand {
            action: ActionType::OpenFile,
            target: Some("../../secret.env".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("Path traversal"));
            }
            other => panic!("Expected Block, got {:?}", other),
        }
    }

    #[test]
    fn test_07_path_traversal_folder_blocked() {
        let cmd = ActionCommand {
            action: ActionType::OpenFolder,
            target: Some("safe.txt".to_string()),
            folder: Some("../../../Windows".to_string()),
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("Path traversal"));
            }
            other => panic!("Expected Block, got {:?}", other),
        }
    }

    #[test]
    fn test_08_windows_protected_path_blocked() {
        let paths = [
            r"C:\Windows\System32\cmd.exe",
            "C:/Windows/explorer.exe",
            r"c:\program files\malicious\tool.exe",
            "C:/Program Files (x86)/test",
        ];
        for path in paths {
            let cmd = ActionCommand {
                action: ActionType::OpenFile,
                target: Some(path.to_string()),
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.90,
            };
            match SafetyGate::evaluate(&cmd) {
                SafetyDecision::Block { reason } => {
                    assert!(reason.contains("protected system path"));
                }
                other => panic!("Expected Block for {}, got {:?}", path, other),
            }
        }
    }

    #[test]
    fn test_09_unix_and_android_system_paths_blocked() {
        let paths = [
            "/etc/shadow",
            "/system/bin/su",
            "/root/.ssh/id_rsa",
            "/bin/sh",
            "/sbin/iptables",
            "/usr/bin/curl",
        ];
        for path in paths {
            let cmd = ActionCommand {
                action: ActionType::OpenFile,
                target: Some(path.to_string()),
                folder: None,
                args: None,
                platform: Platform::Any,
                confidence: 0.90,
            };
            match SafetyGate::evaluate(&cmd) {
                SafetyDecision::Block { reason } => {
                    assert!(reason.contains("protected system path"));
                }
                other => panic!("Expected Block for {}, got {:?}", path, other),
            }
        }
    }

    #[test]
    fn test_10_shell_injection_and_operator_blocked() {
        let cmd = ActionCommand {
            action: ActionType::LaunchApp,
            target: Some("calc && notepad".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("Shell injection"));
            }
            other => panic!("Expected Block, got {:?}", other),
        }
    }

    #[test]
    fn test_11_shell_injection_pipe_blocked() {
        let cmd = ActionCommand {
            action: ActionType::LaunchApp,
            target: Some("echo test | sh".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("Shell injection"));
            }
            other => panic!("Expected Block, got {:?}", other),
        }
    }

    #[test]
    fn test_12_shell_injection_subshell_blocked() {
        let cmd = ActionCommand {
            action: ActionType::OpenFile,
            target: Some("$(cat /etc/passwd)".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("Shell injection"));
            }
            other => panic!("Expected Block, got {:?}", other),
        }
    }

    #[test]
    fn test_13_shell_injection_backtick_and_semicolon_blocked() {
        for meta_payload in ["`id`", "file.txt; rm -rf /"] {
            let cmd = ActionCommand {
                action: ActionType::OpenFile,
                target: Some(meta_payload.to_string()),
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            };
            match SafetyGate::evaluate(&cmd) {
                SafetyDecision::Block { reason } => {
                    assert!(reason.contains("Shell injection"));
                }
                other => panic!("Expected Block for {}, got {:?}", meta_payload, other),
            }
        }
    }

    #[test]
    fn test_14_tier_2_delete_and_move_require_confirmation() {
        let delete_cmd = ActionCommand {
            action: ActionType::DeleteFile,
            target: Some("notes.txt".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.99,
        };
        match SafetyGate::evaluate(&delete_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("Tier 2"));
            }
            other => panic!("Expected RequireConfirm, got {:?}", other),
        }

        let move_cmd = ActionCommand {
            action: ActionType::MoveFile,
            target: Some("file.txt".to_string()),
            folder: Some("archive".to_string()),
            args: None,
            platform: Platform::Pc,
            confidence: 0.99,
        };
        match SafetyGate::evaluate(&move_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("Tier 2"));
            }
            other => panic!("Expected RequireConfirm, got {:?}", other),
        }
    }

    #[test]
    fn test_15_tier_2_system_power_and_android_actions_require_confirm() {
        let power_cmd = ActionCommand {
            action: ActionType::SystemPower,
            target: Some("reboot".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&power_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("Tier 2"));
            }
            other => panic!("Expected RequireConfirm, got {:?}", other),
        }

        let send_cmd = ActionCommand {
            action: ActionType::SendMessage,
            target: Some("+123456789".to_string()),
            folder: None,
            args: Some(serde_json::json!({"text": "Hello"})),
            platform: Platform::Android,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&send_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("Tier 2"));
            }
            other => panic!("Expected RequireConfirm, got {:?}", other),
        }

        let toggle_cmd = ActionCommand {
            action: ActionType::ToggleSetting,
            target: Some("wifi".to_string()),
            folder: None,
            args: None,
            platform: Platform::Android,
            confidence: 0.95,
        };
        match SafetyGate::evaluate(&toggle_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("Tier 2"));
            }
            other => panic!("Expected RequireConfirm, got {:?}", other),
        }
    }

    #[test]
    fn test_16_low_confidence_downgrade_to_confirm() {
        let cmd = ActionCommand {
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.64, // Just below 0.65 threshold
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("below the 0.65 threshold"));
            }
            other => panic!("Expected RequireConfirm downgrade, got {:?}", other),
        }
    }

    #[test]
    fn test_17_boundary_confidence_at_threshold_allows_silent() {
        let cmd = ActionCommand {
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.65, // Exactly threshold
        };
        assert_eq!(SafetyGate::evaluate(&cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_18_out_of_bounds_confidence_blocked() {
        let cmd = ActionCommand {
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 1.5,
        };
        match SafetyGate::evaluate(&cmd) {
            SafetyDecision::Block { reason } => {
                assert!(reason.contains("invalid"));
            }
            other => panic!(
                "Expected Block for out-of-bounds confidence, got {:?}",
                other
            ),
        }
    }
}
