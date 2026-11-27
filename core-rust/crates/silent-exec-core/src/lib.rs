pub mod pii;

use serde::{Deserialize, Serialize};

/// The 20 cross-platform tool actions supported by SilentExec.
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
    // GUI Agent Actions
    UiClick,
    UiType,
    UiScroll,
    UiBack,
    UiHome,
    // ReAct Loop Termination
    Done,
    // SilentExec Content Generation & Agent Tools
    Reply,
    ReadFile,
    WriteFile,
    MemorySave,
    BatchActions,
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
            | Self::OpenUrl
            | Self::UiClick
            | Self::UiType
            | Self::UiScroll
            | Self::UiBack
            | Self::UiHome
            | Self::Done
            | Self::Reply
            | Self::ReadFile
            | Self::MemorySave
            | Self::BatchActions => SafetyTier::Tier1Silent,

            Self::DeleteFile
            | Self::MoveFile
            | Self::SystemPower
            | Self::ToggleSetting
            | Self::SendMessage
            | Self::WriteFile => SafetyTier::Tier2Confirm,
        }
    }
}

/// Target execution platform.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Platform {
    Pc,
    Android,
    #[default]
    Any,
}

impl<'de> Deserialize<'de> for Platform {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let s = match String::deserialize(deserializer) {
            Ok(val) => val,
            Err(_) => return Ok(Platform::Any),
        };
        match s.to_lowercase().trim() {
            "pc" => Ok(Platform::Pc),
            "android" => Ok(Platform::Android),
            _ => Ok(Platform::Any), // Lenient: unknown platforms (e.g. YouTube, Windows) safely map to Platform::Any
        }
    }
}

#[must_use]
pub fn default_platform() -> Platform {
    Platform::Any
}

#[must_use]
pub fn default_confidence() -> f64 {
    0.85
}

/// Strict ActionCommand contract matching `schemas/action_schema.json`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ActionCommand {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub thought: Option<String>,
    pub action: ActionType,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub target: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub folder: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub args: Option<serde_json::Value>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub actions: Option<Vec<ActionCommand>>,
    #[serde(default = "default_platform")]
    pub platform: Platform,
    #[serde(default = "default_confidence")]
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

    /// Parses an `ActionCommand` from raw LLM JSON output:
    /// strips unknown keys (such as `"index": 10`) safely, handles duplicate keys via
    /// `serde_json::Value` object map, and validates strictly through `SafetyGate::evaluate`.
    pub fn from_llm_json(raw: &str) -> Result<Self, String> {
        let value: serde_json::Value = serde_json::from_str(raw).map_err(|e| e.to_string())?;
        let obj = value
            .as_object()
            .ok_or_else(|| "Expected JSON object for ActionCommand".to_string())?;

        const KNOWN_KEYS: [&str; 8] = [
            "thought",
            "action",
            "target",
            "folder",
            "args",
            "actions",
            "platform",
            "confidence",
        ];

        let mut filtered = serde_json::Map::new();
        for (k, v) in obj {
            if KNOWN_KEYS.contains(&k.as_str()) {
                filtered.insert(k.clone(), v.clone());
            }
        }

        let cmd: Self = serde_json::from_value(serde_json::Value::Object(filtered))
            .map_err(|e| e.to_string())?;
        if !(0.0..=1.0).contains(&cmd.confidence) {
            return Err(format!(
                "Confidence score {} is out of valid range [0.0, 1.0]",
                cmd.confidence
            ));
        }
        if let SafetyDecision::Block { reason } = SafetyGate::evaluate(&cmd) {
            return Err(reason);
        }
        Ok(cmd)
    }

    /// Serializes the `ActionCommand` to a canonical JSON string.
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }
}

/// Multi-step execution plan containing ordered `ActionCommand` steps (up to 6 steps).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MultiStepPlan {
    pub steps: Vec<ActionCommand>,
}

impl MultiStepPlan {
    pub const MAX_STEPS: usize = 6;

    /// Creates a new `MultiStepPlan`.
    #[must_use]
    pub fn new(steps: Vec<ActionCommand>) -> Self {
        Self { steps }
    }

    /// Parses and strictly validates a `MultiStepPlan` from a JSON string.
    pub fn from_json(json_str: &str) -> Result<Self, String> {
        let plan: Self = serde_json::from_str(json_str).map_err(|e| e.to_string())?;
        if plan.steps.is_empty() {
            return Err("MultiStepPlan steps array cannot be empty".to_string());
        }
        if plan.steps.len() > Self::MAX_STEPS {
            return Err(format!(
                "MultiStepPlan exceeds maximum step limit of {} (found {})",
                Self::MAX_STEPS,
                plan.steps.len()
            ));
        }
        for (i, step) in plan.steps.iter().enumerate() {
            if !(0.0..=1.0).contains(&step.confidence) {
                return Err(format!(
                    "Step {} confidence score {} is out of valid range [0.0, 1.0]",
                    i + 1,
                    step.confidence
                ));
            }
        }
        Ok(plan)
    }

    /// Parses either a `MultiStepPlan` JSON or a single `ActionCommand` JSON
    /// into a `MultiStepPlan`.
    pub fn from_any_json(json_str: &str) -> Result<Self, String> {
        let trimmed = json_str.trim();
        if trimmed.contains("\"steps\"") {
            if let Ok(plan) = Self::from_json(trimmed) {
                return Ok(plan);
            }
        }
        // Fall back to single ActionCommand
        let cmd = ActionCommand::from_json(trimmed)?;
        Ok(Self { steps: vec![cmd] })
    }

    /// Leniently parses either a `MultiStepPlan` JSON or a single `ActionCommand` JSON
    /// from LLM output, stripping unknown keys on each step and validating via `SafetyGate`.
    pub fn from_any_llm_json(json_str: &str) -> Result<Self, String> {
        let trimmed = json_str.trim();
        if let Ok(value) = serde_json::from_str::<serde_json::Value>(trimmed) {
            if let Some(steps_arr) = value.get("steps").and_then(|v| v.as_array()) {
                if steps_arr.is_empty() {
                    return Err("MultiStepPlan steps array cannot be empty".to_string());
                }
                if steps_arr.len() > Self::MAX_STEPS {
                    return Err(format!(
                        "MultiStepPlan exceeds maximum step limit of {} (found {})",
                        Self::MAX_STEPS,
                        steps_arr.len()
                    ));
                }
                let mut steps = Vec::with_capacity(steps_arr.len());
                for step_val in steps_arr {
                    let step_str = serde_json::to_string(step_val).map_err(|e| e.to_string())?;
                    steps.push(ActionCommand::from_llm_json(&step_str)?);
                }
                return Ok(Self { steps });
            }
        }
        let cmd = ActionCommand::from_llm_json(trimmed)?;
        Ok(Self { steps: vec![cmd] })
    }

    /// Serializes the `MultiStepPlan` to a canonical JSON string.
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }
}

/// Execution mode controlling safety and confirmation behavior.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ExecutionMode {
    #[default]
    Silent,
    Interactive,
    DryRun,
}

impl ExecutionMode {
    #[must_use]
    pub fn from_str_loose(s: &str) -> Self {
        match s.to_lowercase().trim() {
            "interactive" => Self::Interactive,
            "dry_run" | "dryrun" | "dry-run" => Self::DryRun,
            _ => Self::Silent,
        }
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

    /// Bounded capability manifest: blocked high-risk MCP/OS verbs.
    const BOUNDED_BLOCKED_VERBS: &'static [&'static str] =
        &["format_disk", "rm_rf", "registry_wipe", "wipe_device"];

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

        // 3.5. Validate nested `actions` for batch_actions
        if let Some(actions) = &cmd.actions {
            if actions.len() > 4 {
                return SafetyDecision::Block {
                    reason: format!(
                        "batch_actions exceeds maximum of 4 sub-actions (found {})",
                        actions.len()
                    ),
                };
            }
            for (idx, sub) in actions.iter().enumerate() {
                match Self::evaluate(sub) {
                    SafetyDecision::Block { reason } => {
                        return SafetyDecision::Block {
                            reason: format!("Batch sub-action {} blocked: {}", idx + 1, reason),
                        };
                    }
                    SafetyDecision::RequireConfirm { reason } => {
                        return SafetyDecision::RequireConfirm {
                            reason: format!(
                                "Batch sub-action {} requires confirmation: {}",
                                idx + 1,
                                reason
                            ),
                        };
                    }
                    SafetyDecision::AllowSilent => {}
                }
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

    /// Evaluates a `MultiStepPlan` sequentially.
    ///
    /// Validates every step in order:
    /// - If any step evaluates to `Block`, the entire plan is blocked.
    /// - If any step evaluates to `RequireConfirm` (and none are `Block`), the plan requires confirmation.
    /// - Otherwise, returns `AllowSilent`.
    #[must_use]
    pub fn evaluate_plan(plan: &MultiStepPlan) -> SafetyDecision {
        let mut first_confirm: Option<String> = None;

        for (idx, step) in plan.steps.iter().enumerate() {
            match Self::evaluate(step) {
                SafetyDecision::Block { reason } => {
                    return SafetyDecision::Block {
                        reason: format!("Plan step {} blocked: {}", idx + 1, reason),
                    };
                }
                SafetyDecision::RequireConfirm { reason } => {
                    if first_confirm.is_none() {
                        first_confirm = Some(format!(
                            "Plan step {} requires confirmation: {}",
                            idx + 1,
                            reason
                        ));
                    }
                }
                SafetyDecision::AllowSilent => {}
            }
        }

        if let Some(reason) = first_confirm {
            SafetyDecision::RequireConfirm { reason }
        } else {
            SafetyDecision::AllowSilent
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

        // Bounded capability manifest verb check
        let lower_val = value.to_ascii_lowercase();
        for &verb in Self::BOUNDED_BLOCKED_VERBS {
            if lower_val.contains(verb) {
                return Some(format!(
                    "Bounded capability policy blocked high-risk verb '{verb}' in {field_name}: '{value}'"
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
    fn test_05_missing_platform_defaults_to_any_and_lenient_unknown() {
        // Missing platform defaults to Platform::Any
        let json_missing_platform = r#"{
            "action": "open_file",
            "target": "report.pdf",
            "confidence": 0.90
        }"#;
        let cmd = ActionCommand::from_json(json_missing_platform)
            .expect("Missing platform should default to Platform::Any");
        assert_eq!(cmd.platform, Platform::Any);

        // Unknown platform "YouTube" maps safely to Platform::Any
        let json_youtube = r#"{
            "action": "open_url",
            "target": "https://youtube.com",
            "platform": "YouTube",
            "confidence": 0.95
        }"#;
        let cmd_yt = ActionCommand::from_json(json_youtube)
            .expect("Lenient platform deserialization must succeed for YouTube");
        assert_eq!(cmd_yt.platform, Platform::Any);

        // Platform "Windows" maps safely to Platform::Any
        let json_windows = r#"{
            "action": "launch_app",
            "target": "Notepad",
            "platform": "Windows",
            "confidence": 0.90
        }"#;
        let cmd_win = ActionCommand::from_json(json_windows)
            .expect("Lenient platform deserialization must succeed for Windows");
        assert_eq!(cmd_win.platform, Platform::Any);

        // Missing confidence defaults to 0.85
        let json_missing_conf = r#"{
            "action": "launch_app",
            "target": "Calculator"
        }"#;
        let cmd_default = ActionCommand::from_json(json_missing_conf)
            .expect("Missing confidence should default to 0.85");
        assert_eq!(cmd_default.platform, Platform::Any);
        assert!((cmd_default.confidence - 0.85).abs() < 1e-6);
    }

    #[test]
    fn test_06_path_traversal_target_blocked() {
        let cmd = ActionCommand {
            thought: None,
            action: ActionType::OpenFile,
            target: Some("../../secret.env".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::OpenFolder,
            target: Some("safe.txt".to_string()),
            folder: Some("../../../Windows".to_string()),
            args: None,
            actions: None,
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
                thought: None,
                action: ActionType::OpenFile,
                target: Some(path.to_string()),
                folder: None,
                args: None,
                actions: None,
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
                thought: None,
                action: ActionType::OpenFile,
                target: Some(path.to_string()),
                folder: None,
                args: None,
                actions: None,
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
            thought: None,
            action: ActionType::LaunchApp,
            target: Some("calc && notepad".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::LaunchApp,
            target: Some("echo test | sh".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::OpenFile,
            target: Some("$(cat /etc/passwd)".to_string()),
            folder: None,
            args: None,
            actions: None,
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
                thought: None,
                action: ActionType::OpenFile,
                target: Some(meta_payload.to_string()),
                folder: None,
                args: None,
                actions: None,
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
            thought: None,
            action: ActionType::DeleteFile,
            target: Some("notes.txt".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::MoveFile,
            target: Some("file.txt".to_string()),
            folder: Some("archive".to_string()),
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::SystemPower,
            target: Some("reboot".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::SendMessage,
            actions: None,
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
            thought: None,
            action: ActionType::ToggleSetting,
            target: Some("wifi".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            actions: None,
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
            thought: None,
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            actions: None,
            platform: Platform::Pc,
            confidence: 0.65, // Exactly threshold
        };
        assert_eq!(SafetyGate::evaluate(&cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_18_out_of_bounds_confidence_blocked() {
        let cmd = ActionCommand {
            thought: None,
            action: ActionType::OpenFile,
            target: Some("document.pdf".to_string()),
            folder: None,
            args: None,
            actions: None,
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

    #[test]
    fn test_19_gui_actions_wire_format_and_tier1_silent() {
        let test_cases = [
            (
                r#"{"action":"ui_click","target":"Submit","platform":"pc","confidence":0.95}"#,
                ActionType::UiClick,
            ),
            (
                r#"{"action":"ui_type","target":"username","args":{"text":"admin"},"platform":"pc","confidence":0.90}"#,
                ActionType::UiType,
            ),
            (
                r#"{"action":"ui_scroll","args":{"direction":"down"},"platform":"android","confidence":0.85}"#,
                ActionType::UiScroll,
            ),
            (
                r#"{"action":"ui_back","platform":"android","confidence":0.99}"#,
                ActionType::UiBack,
            ),
            (
                r#"{"action":"ui_home","platform":"pc","confidence":0.92}"#,
                ActionType::UiHome,
            ),
        ];

        for (json_str, expected_type) in test_cases {
            let cmd = ActionCommand::from_json(json_str)
                .unwrap_or_else(|e| panic!("Failed to parse {}: {}", json_str, e));
            assert_eq!(cmd.action, expected_type);
            assert_eq!(cmd.action.tier(), SafetyTier::Tier1Silent);
            assert_eq!(
                SafetyGate::evaluate(&cmd),
                SafetyDecision::AllowSilent,
                "Action {:?} should evaluate to AllowSilent",
                expected_type
            );
        }
    }

    #[test]
    fn test_20_gui_actions_arabic_ui_click_and_ui_type() {
        // ui_click with Arabic target
        let click_json = r#"{
            "action": "ui_click",
            "target": "زر ارسال",
            "platform": "android",
            "confidence": 0.95
        }"#;
        let click_cmd =
            ActionCommand::from_json(click_json).expect("Failed to parse Arabic ui_click");
        assert_eq!(click_cmd.action, ActionType::UiClick);
        assert_eq!(click_cmd.target.as_deref(), Some("زر ارسال"));
        assert_eq!(
            SafetyGate::evaluate(&click_cmd),
            SafetyDecision::AllowSilent
        );

        // ui_type with Arabic target and Arabic text argument
        let type_json = r#"{
            "action": "ui_type",
            "target": "شريط البحث",
            "args": {"text": "مرحبا بكم في التطبيق"},
            "platform": "pc",
            "confidence": 0.92
        }"#;
        let type_cmd = ActionCommand::from_json(type_json).expect("Failed to parse Arabic ui_type");
        assert_eq!(type_cmd.action, ActionType::UiType);
        assert_eq!(type_cmd.target.as_deref(), Some("شريط البحث"));
        assert_eq!(
            type_cmd
                .args
                .as_ref()
                .and_then(|a| a.get("text"))
                .and_then(|t| t.as_str()),
            Some("مرحبا بكم في التطبيق")
        );
        assert_eq!(SafetyGate::evaluate(&type_cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_21_injection_blocking_inside_ui_type_target() {
        let dangerous_targets = [
            "search_box; rm -rf /",
            "input && whoami",
            "field | cat /etc/passwd",
            "$(reboot)",
            "`id`",
            "../../secret_input",
            "C:\\Windows\\System32\\calc.exe",
        ];

        for target in dangerous_targets {
            let cmd = ActionCommand {
                thought: None,
                action: ActionType::UiType,
                actions: None,
                target: Some(target.to_string()),
                folder: None,
                args: Some(serde_json::json!({"text": "sample"})),
                platform: Platform::Pc,
                confidence: 0.95,
            };
            match SafetyGate::evaluate(&cmd) {
                SafetyDecision::Block { reason } => {
                    assert!(
                        reason.contains("injection")
                            || reason.contains("traversal")
                            || reason.contains("protected"),
                        "Expected security block reason for target '{}', got '{}'",
                        target,
                        reason
                    );
                }
                other => panic!(
                    "Expected Block for dangerous target '{}', got {:?}",
                    target, other
                ),
            }
        }
    }

    #[test]
    fn test_22_injection_blocking_inside_ui_type_args() {
        let dangerous_args = [
            serde_json::json!({"text": "echo hello; rm -rf /"}),
            serde_json::json!({"text": "cmd | evil"}),
            serde_json::json!({"text": "$(cat /etc/shadow)"}),
            serde_json::json!({"text": "`whoami`"}),
            serde_json::json!({"text": "../../traversal"}),
            serde_json::json!({"nested": {"field": "test && reboot"}}),
            serde_json::json!({"dir; ls": "value"}),
        ];

        for args in dangerous_args {
            let cmd = ActionCommand {
                thought: None,
                action: ActionType::UiType,
                target: Some("search_input".to_string()),
                folder: None,
                args: Some(args.clone()),
                actions: None,
                platform: Platform::Android,
                confidence: 0.95,
            };
            match SafetyGate::evaluate(&cmd) {
                SafetyDecision::Block { reason } => {
                    assert!(
                        reason.contains("injection")
                            || reason.contains("traversal")
                            || reason.contains("metacharacter"),
                        "Expected injection block reason for args '{:?}', got '{}'",
                        args,
                        reason
                    );
                }
                other => panic!(
                    "Expected Block for dangerous args '{:?}', got {:?}",
                    args, other
                ),
            }
        }
    }

    #[test]
    fn test_23_multi_step_plan_all_silent() {
        let plan = MultiStepPlan::new(vec![
            ActionCommand {
                thought: None,
                action: ActionType::UiClick,
                target: Some("search_bar".to_string()),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiType,
                actions: None,
                target: Some("search_bar".to_string()),
                folder: None,
                args: Some(serde_json::json!({"text": "hello world"})),
                platform: Platform::Pc,
                confidence: 0.92,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiScroll,
                actions: None,
                target: None,
                folder: None,
                args: Some(serde_json::json!({"direction": "down"})),
                platform: Platform::Pc,
                confidence: 0.88,
            },
        ]);

        let decision = SafetyGate::evaluate_plan(&plan);
        assert_eq!(decision, SafetyDecision::AllowSilent);

        // Test serialization and deserialization
        let plan_json = plan.to_json().expect("Failed to serialize MultiStepPlan");
        let parsed_plan =
            MultiStepPlan::from_json(&plan_json).expect("Failed to deserialize MultiStepPlan");
        assert_eq!(plan, parsed_plan);
    }

    #[test]
    fn test_24_multi_step_plan_blocked_step() {
        let plan = MultiStepPlan::new(vec![
            ActionCommand {
                thought: None,
                action: ActionType::UiClick,
                target: Some("button_login".to_string()),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiType,
                actions: None,
                target: Some("password_field".to_string()),
                folder: None,
                args: Some(serde_json::json!({"text": "pass && rm -rf /"})),
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiClick,
                target: Some("button_submit".to_string()),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
        ]);

        match SafetyGate::evaluate_plan(&plan) {
            SafetyDecision::Block { reason } => {
                assert!(
                    reason.contains("step 2 blocked"),
                    "Expected step 2 block notice, got '{}'",
                    reason
                );
                assert!(reason.contains("Shell injection"));
            }
            other => panic!(
                "Expected Block for plan with dangerous step, got {:?}",
                other
            ),
        }
    }

    #[test]
    fn test_25_multi_step_plan_require_confirm() {
        let plan = MultiStepPlan::new(vec![
            ActionCommand {
                thought: None,
                action: ActionType::UiClick,
                target: Some("open_dialog".to_string()),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::DeleteFile,
                target: Some("obsolete_cache.tmp".to_string()),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 0.99,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Android,
                confidence: 0.90,
            },
        ]);

        match SafetyGate::evaluate_plan(&plan) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(
                    reason.contains("step 2 requires confirmation"),
                    "Expected step 2 require confirm notice, got '{}'",
                    reason
                );
                assert!(reason.contains("Tier 2"));
            }
            other => panic!(
                "Expected RequireConfirm for plan with Tier-2 step, got {:?}",
                other
            ),
        }
    }

    #[test]
    fn test_26_done_action_tier_and_safety() {
        let done_cmd = ActionCommand {
            thought: None,
            action: ActionType::Done,
            target: None,
            folder: None,
            args: None,
            actions: None,
            platform: Platform::Any,
            confidence: 1.0,
        };

        assert_eq!(done_cmd.action.tier(), SafetyTier::Tier1Silent);
        let decision = SafetyGate::evaluate(&done_cmd);
        assert_eq!(decision, SafetyDecision::AllowSilent);

        let json = done_cmd.to_json().expect("Serialize done");
        let parsed = ActionCommand::from_json(&json).expect("Deserialize done");
        assert_eq!(parsed.action, ActionType::Done);
    }

    #[test]
    fn test_27_multi_step_plan_step_bounds_and_from_any() {
        // Empty steps rejected
        assert!(MultiStepPlan::from_json(r#"{"steps":[]}"#).is_err());

        // > 6 steps rejected
        let seven_steps = (0..7)
            .map(|_| ActionCommand {
                thought: None,
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Any,
                confidence: 0.9,
            })
            .collect();
        let plan_7 = MultiStepPlan::new(seven_steps);
        let json_7 = serde_json::to_string(&plan_7).unwrap();
        assert!(MultiStepPlan::from_json(&json_7).is_err());

        // from_any_json with single ActionCommand wraps in MultiStepPlan
        let single_cmd_json = r#"{"action":"ui_back","platform":"android","confidence":0.95}"#;
        let plan_single =
            MultiStepPlan::from_any_json(single_cmd_json).expect("Parse single as plan");
        assert_eq!(plan_single.steps.len(), 1);
        assert_eq!(plan_single.steps[0].action, ActionType::UiBack);

        // ExecutionMode parsing
        assert_eq!(
            ExecutionMode::from_str_loose("silent"),
            ExecutionMode::Silent
        );
        assert_eq!(
            ExecutionMode::from_str_loose("INTERACTIVE"),
            ExecutionMode::Interactive
        );
        assert_eq!(
            ExecutionMode::from_str_loose("dry_run"),
            ExecutionMode::DryRun
        );
    }

    #[test]
    fn test_28_hermes_content_generation_tools_and_safety_tiers() {
        // 1. Reply tool (Tier 1 Silent)
        let reply_json = r#"{
            "action": "reply",
            "target": "Here is the summary of your meeting notes.",
            "args": {"text": "Here is the summary of your meeting notes."},
            "platform": "any",
            "confidence": 0.95
        }"#;
        let reply_cmd = ActionCommand::from_json(reply_json).expect("Parse reply action");
        assert_eq!(reply_cmd.action, ActionType::Reply);
        assert_eq!(reply_cmd.action.tier(), SafetyTier::Tier1Silent);
        assert_eq!(
            SafetyGate::evaluate(&reply_cmd),
            SafetyDecision::AllowSilent
        );

        // 2. ReadFile tool (Tier 1 Silent)
        let read_json = r#"{
            "action": "read_file",
            "target": "Documents/notes.txt",
            "platform": "pc",
            "confidence": 0.90
        }"#;
        let read_cmd = ActionCommand::from_json(read_json).expect("Parse read_file action");
        assert_eq!(read_cmd.action, ActionType::ReadFile);
        assert_eq!(read_cmd.action.tier(), SafetyTier::Tier1Silent);
        assert_eq!(SafetyGate::evaluate(&read_cmd), SafetyDecision::AllowSilent);

        // 3. WriteFile tool (Tier 2 Confirm)
        let write_json = r#"{
            "action": "write_file",
            "target": "Documents/output.py",
            "args": {"content": "print('hello world')"},
            "platform": "pc",
            "confidence": 0.92
        }"#;
        let write_cmd = ActionCommand::from_json(write_json).expect("Parse write_file action");
        assert_eq!(write_cmd.action, ActionType::WriteFile);
        assert_eq!(write_cmd.action.tier(), SafetyTier::Tier2Confirm);
        match SafetyGate::evaluate(&write_cmd) {
            SafetyDecision::RequireConfirm { reason } => {
                assert!(reason.contains("WriteFile"));
            }
            other => panic!("Expected RequireConfirm for write_file, got {:?}", other),
        }

        // 4. MemorySave tool (Tier 1 Silent)
        let mem_json = r#"{
            "action": "memory_save",
            "target": "preferred_theme",
            "args": {"key": "preferred_theme", "content": "dark", "category": "preference"},
            "platform": "any",
            "confidence": 0.99
        }"#;
        let mem_cmd = ActionCommand::from_json(mem_json).expect("Parse memory_save action");
        assert_eq!(mem_cmd.action, ActionType::MemorySave);
        assert_eq!(mem_cmd.action.tier(), SafetyTier::Tier1Silent);
        assert_eq!(SafetyGate::evaluate(&mem_cmd), SafetyDecision::AllowSilent);
    }

    #[test]
    fn test_29_lfm3_lenient_llm_json_with_duplicate_and_unknown_keys() {
        let raw_lfm3_output = r#"{"action":"open_url","platform":"android","confidence":0.88,"thought":"navigate to YouTube","target":"ابحث عن nlp","platform":"android","index":10}"#;

        // Strict schema parser still rejects unknown keys
        assert!(ActionCommand::from_json(raw_lfm3_output).is_err());

        // Lenient LLM parser strips unknown keys, handles duplicate keys, and passes SafetyGate
        let cmd = ActionCommand::from_llm_json(raw_lfm3_output)
            .expect("from_llm_json should parse LFM3-1.7B output cleanly");
        assert_eq!(cmd.action, ActionType::OpenUrl);
        assert_eq!(cmd.platform, Platform::Android);
        assert!((cmd.confidence - 0.88).abs() < 1e-6);
        assert_eq!(cmd.thought.as_deref(), Some("navigate to YouTube"));
        assert_eq!(cmd.target.as_deref(), Some("ابحث عن nlp"));
    }
}
