//! SilentExec PC Executor, Arabic/English Fuzzy Indexer, Inference & LAN Bridge Library

pub mod agent_engine;
pub mod bridge;
pub mod config;
pub mod executor;
pub mod fast_router;
pub mod indexer;
pub mod inference;
pub mod mcp_bridge;
pub mod memory;
pub mod model_manager;
pub mod planner;
pub mod process_helper;
pub mod runtime_manager;
pub mod skills;
pub mod ui_win32;
pub mod updater;

pub use agent_engine::{
    AgentTurnDecision, GooseAcpRunner, GooseStatus, ToolCallingAgent, ToolLoopStep,
};
pub use bridge::{start_bridge_server, BridgeConfig, ExecuteRequest};
pub use config::{AppConfig, McpServerConfig};
pub use executor::{action_type_to_str, ExecutionResult, PcExecutor};
pub use fast_router::FastRuleRouter;
pub use indexer::{
    compute_fuzzy_score, normalize_arabic_english, FileIndexer, IndexedItem, ScoredMatch,
};
pub use inference::{
    build_chatml_prompt, extract_action_json, format_prompt, get_effective_system_prompt,
    AiBackend, LlamaBackend, DEFAULT_SYSTEM_PROMPT,
};
pub use mcp_bridge::{
    JsonRpcError, JsonRpcRequest, JsonRpcResponse, McpClient, McpTool, McpToolCallResult,
};
pub use memory::{
    delete_memory, get_relevant_memories_prompt, list_memories, save_memory, MemoryItem,
    MemoryStore,
};
pub use model_manager::{
    DownloadProgress, DownloadStatus, ModelDownloadInfo, ModelManager, ModelReleaseInfo,
};
pub use planner::{
    parse_plan, resolve_clause, split_compound_prompt, PlanEngine, PlanExecutionSummary, StepEvent,
};
pub use process_helper::hidden_command;
pub use runtime_manager::{
    check_health_http, ensure_llama_server_running, parse_health_url, run_setup,
    select_llama_asset, select_llama_asset_for_platform, ReleaseAsset, RuntimeManager, SetupResult,
};
pub use skills::{BuiltInSkills, SkillDefinition, SkillStore};
pub use ui_win32::{
    map_result_to_ui_outcome, run_command_bar, string_to_utf16, utf16_to_string, UiActionOutcome,
};
pub use updater::{SelfUpdater, UpdateInfo};

#[cfg(test)]
mod tests {
    use super::*;
    use silent_exec_core::{
        ActionCommand, ActionType, ExecutionMode, MultiStepPlan, Platform, SafetyGate,
    };
    use std::fs::{self, File};
    use std::io::Write;
    use std::path::PathBuf;

    struct TempDirGuard {
        path: PathBuf,
    }

    impl TempDirGuard {
        fn new(name: &str) -> Self {
            let unique = format!(
                "silent_exec_test_{}_{}",
                name,
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap()
                    .as_nanos()
            );
            let path = std::env::temp_dir().join(unique);
            fs::create_dir_all(&path).expect("Failed to create test temp dir");
            Self { path }
        }
    }

    impl Drop for TempDirGuard {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.path);
        }
    }

    #[test]
    fn test_01_arabic_alef_normalization() {
        assert_eq!(normalize_arabic_english("أحمد"), "احمد");
        assert_eq!(normalize_arabic_english("إدارة"), "اداره");
        assert_eq!(normalize_arabic_english("آثار"), "اثار");
        assert_eq!(normalize_arabic_english("ٱبن"), "ابن");
    }

    #[test]
    fn test_02_arabic_teh_marbuta_and_alif_maqsura() {
        assert_eq!(normalize_arabic_english("مكتبة"), "مكتبه");
        assert_eq!(normalize_arabic_english("مستشفى"), "مستشفي");
        assert_eq!(normalize_arabic_english("مسؤول"), "مسوول");
        assert_eq!(normalize_arabic_english("رئيس"), "رييس");
    }

    #[test]
    fn test_03_arabic_diacritics_removal() {
        // Fatha, Damma, Kasra, Shadda, Sukun
        let with_tashkeel = "مَشْرُوعُ التَّخَرُّجِ";
        assert_eq!(normalize_arabic_english(with_tashkeel), "مشروع التخرج");
    }

    #[test]
    fn test_04_english_and_punctuation_normalization() {
        assert_eq!(
            normalize_arabic_english("Final_Report-2026.pdf"),
            "final report 2026.pdf"
        );
        assert_eq!(normalize_arabic_english("   QUICK   NOTE   "), "quick note");
    }

    #[test]
    fn test_05_fuzzy_score_ranking() {
        let exact = compute_fuzzy_score("تقرير التخرج", "تقرير التخرج");
        let prefix = compute_fuzzy_score("تقرير", "تقرير التخرج النهائي");
        let substring = compute_fuzzy_score("التخرج", "مشروع التخرج النهائي");
        let partial = compute_fuzzy_score("تقرير", "مستند الحسابات");

        assert_eq!(exact, 1000);
        assert!(exact > prefix);
        assert!(prefix > substring);
        assert!(substring > partial);
    }

    #[test]
    fn test_06_temp_directory_indexing_and_depth() {
        let guard = TempDirGuard::new("depth_test");
        let base = &guard.path;

        let level1 = base.join("level1");
        let level2 = level1.join("level2");
        let level3 = level2.join("level3");
        fs::create_dir_all(&level3).unwrap();

        File::create(base.join("root_file.txt")).unwrap();
        File::create(level1.join("file1.txt")).unwrap();
        File::create(level2.join("file2.txt")).unwrap();
        File::create(level3.join("file3.txt")).unwrap();

        // Index with max depth 1
        let mut indexer = FileIndexer::new(vec![base.clone()]).with_depth(1);
        let count = indexer.build_index().unwrap();

        assert!(count >= 4);
    }

    #[test]
    fn test_07_indexer_ignore_list() {
        let guard = TempDirGuard::new("ignore_test");
        let base = &guard.path;

        let git_dir = base.join(".git");
        let node_modules = base.join("node_modules");
        let valid_dir = base.join("docs");

        fs::create_dir_all(&git_dir).unwrap();
        fs::create_dir_all(&node_modules).unwrap();
        fs::create_dir_all(&valid_dir).unwrap();

        File::create(git_dir.join("HEAD")).unwrap();
        File::create(node_modules.join("package.json")).unwrap();
        File::create(valid_dir.join("guide.pdf")).unwrap();

        let mut indexer = FileIndexer::new(vec![base.clone()]);
        indexer.build_index().unwrap();

        let found_git = indexer.search("HEAD", false);
        assert!(found_git.is_empty(), ".git contents must be ignored");

        let found_npm = indexer.search("package.json", false);
        assert!(found_npm.is_empty(), "node_modules must be ignored");

        let found_doc = indexer.search("guide.pdf", false);
        assert_eq!(found_doc.len(), 1, "Valid docs must be indexed");
    }

    #[test]
    fn test_08_arabic_file_resolution() {
        let guard = TempDirGuard::new("arabic_file");
        let base = &guard.path;

        let target_file = base.join("تقرير_المشروع_النهائي.pdf");
        File::create(&target_file).unwrap();

        let mut indexer = FileIndexer::new(vec![base.clone()]);
        indexer.build_index().unwrap();

        // Query without underscores and with different Alef form
        let best_match = indexer.find_best("تقرير المشروع النهائي", false);
        assert!(best_match.is_some());
        assert_eq!(
            best_match.unwrap().file_name().unwrap(),
            "تقرير_المشروع_النهائي.pdf"
        );
    }

    #[test]
    fn test_09_folder_fuzzy_resolution() {
        let guard = TempDirGuard::new("folder_resolve");
        let base = &guard.path;

        let projects_dir = base.join("Projects");
        fs::create_dir_all(&projects_dir).unwrap();

        let mut indexer = FileIndexer::new(vec![base.clone()]);
        indexer.build_index().unwrap();

        let resolved = indexer.find_best("Projects", true);
        assert!(resolved.is_some());
        assert_eq!(resolved.unwrap(), projects_dir);
    }

    #[test]
    fn test_10_safety_gate_blocking() {
        let guard = TempDirGuard::new("safety_block");
        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer, true);

        let cmd = ActionCommand {
            thought: None,
            action: ActionType::OpenFile,
            target: Some("cmd.exe && calc".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        let result = executor.execute(&cmd, false);
        match result {
            ExecutionResult::Blocked { reason, action } => {
                assert_eq!(action, "open_file");
                assert!(reason.to_lowercase().contains("injection"));
            }
            other => panic!("Expected Blocked result, got: {:?}", other),
        }
    }

    #[test]
    fn test_11_tier_2_confirmation_gating() {
        let guard = TempDirGuard::new("confirm_gating");
        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer, true);

        let cmd = ActionCommand {
            thought: None,
            action: ActionType::DeleteFile,
            target: Some("test.log".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        // Without confirmation flag
        let result = executor.execute(&cmd, false);
        match result {
            ExecutionResult::ConfirmationRequired { action, prompt, .. } => {
                assert_eq!(action, "delete_file");
                assert!(prompt.contains("Confirm"));
            }
            other => panic!("Expected ConfirmationRequired, got: {:?}", other),
        }
    }

    #[test]
    fn test_12_tier_2_confirmation_allowed_with_flag() {
        let guard = TempDirGuard::new("confirm_allowed");
        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer, true);

        let cmd = ActionCommand {
            thought: None,
            action: ActionType::DeleteFile,
            target: Some("test.log".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        // With explicit confirmation flag in dry-run
        let result = executor.execute(&cmd, true);
        match result {
            ExecutionResult::DryRun { action, .. } => {
                assert_eq!(action, "delete_file");
            }
            other => panic!("Expected DryRun result, got: {:?}", other),
        }
    }

    #[test]
    fn test_13_dry_run_open_folder() {
        let guard = TempDirGuard::new("dry_open_folder");
        let target_dir = guard.path.join("Downloads");
        fs::create_dir_all(&target_dir).unwrap();

        let mut indexer = FileIndexer::new(vec![guard.path.clone()]);
        indexer.build_index().unwrap();

        let executor = PcExecutor::new(indexer, true);
        let cmd = ActionCommand {
            thought: None,
            action: ActionType::OpenFolder,
            target: Some("Downloads".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        let result = executor.execute(&cmd, false);
        match result {
            ExecutionResult::DryRun {
                action,
                target_resolved,
                command_line,
                ..
            } => {
                assert_eq!(action, "open_folder");
                assert!(target_resolved.is_some());
                assert!(command_line.contains("Downloads"));
            }
            other => panic!("Expected DryRun result, got: {:?}", other),
        }
    }

    #[test]
    fn test_14_dry_run_create_note() {
        let guard = TempDirGuard::new("dry_create_note");
        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer, true).with_notes_dir(guard.path.join("MyNotes"));

        let cmd = ActionCommand {
            thought: None,
            action: ActionType::CreateNote,
            target: Some("Meeting Agenda".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        let result = executor.execute(&cmd, false);
        match result {
            ExecutionResult::DryRun {
                action,
                target_resolved,
                ..
            } => {
                assert_eq!(action, "create_note");
                assert!(target_resolved.unwrap().contains("Meeting Agenda.txt"));
            }
            other => panic!("Expected DryRun result, got: {:?}", other),
        }
    }

    #[test]
    fn test_15_model_manager_checksum_verification() {
        let guard = TempDirGuard::new("checksum_test");
        let test_file = guard.path.join("sample_model.bin");

        let sample_data = b"SilentExec Model Weights Verification Test Data 2026";
        {
            let mut f = File::create(&test_file).unwrap();
            f.write_all(sample_data).unwrap();
        }

        let computed_hash = ModelManager::compute_file_sha256(&test_file).unwrap();
        assert!(!computed_hash.is_empty());

        // Verification with correct hash
        assert!(ModelManager::verify_checksum(&test_file, &computed_hash));

        // Verification with corrupted hash fails
        assert!(!ModelManager::verify_checksum(
            &test_file,
            "deadbeef1234567890"
        ));
    }

    #[test]
    fn test_16_model_manager_git_repo_guard() {
        let guard = TempDirGuard::new("git_guard");
        let git_dir = guard.path.join(".git");
        let inside_models = guard.path.join("models");
        fs::create_dir_all(&git_dir).unwrap();
        fs::create_dir_all(&inside_models).unwrap();

        // Without schemas/action_schema.json or core-rust/Cargo.toml, it's just a generic git repo (e.g. user home),
        // so it must pass!
        let generic_check = ModelManager::assert_not_in_git_repo(&inside_models);
        assert!(
            generic_check.is_ok(),
            "Generic git directory outside silent-exec source repository should not block cache"
        );

        // Under actual source repository markers (containing schemas/action_schema.json):
        let schemas_dir = guard.path.join("schemas");
        fs::create_dir_all(&schemas_dir).unwrap();
        fs::write(schemas_dir.join("action_schema.json"), "{}").unwrap();

        let check = ModelManager::assert_not_in_git_repo(&inside_models);
        assert!(
            check.is_err(),
            "Must reject directories inside SilentExec source repo"
        );
    }

    #[test]
    fn test_17_chatml_prompt_english() {
        let prompt = format_prompt("open folder Downloads");
        assert!(prompt.contains("<|im_start|>system"));
        assert!(prompt.contains(DEFAULT_SYSTEM_PROMPT));
        assert!(prompt.contains("<|im_start|>user\nopen folder Downloads<|im_end|>"));
        assert!(prompt.ends_with("<|im_start|>assistant\n"));
    }

    #[test]
    fn test_18_chatml_prompt_msa_arabic() {
        let prompt = format_prompt("افتح مستند المبيعات");
        assert!(prompt.contains("<|im_start|>user\nافتح مستند المبيعات<|im_end|>"));
        assert!(prompt.ends_with("<|im_start|>assistant\n"));
    }

    #[test]
    fn test_19_chatml_prompt_yemeni_arabic() {
        let prompt = format_prompt("ادخل مجلد المشاريع وريني الملفات");
        assert!(prompt.contains("<|im_start|>user\nادخل مجلد المشاريع وريني الملفات<|im_end|>"));
        assert!(prompt.ends_with("<|im_start|>assistant\n"));
    }

    #[test]
    fn test_20_extract_clean_json() {
        let raw =
            r#"{"action":"open_folder","target":"Downloads","platform":"pc","confidence":0.95}"#;
        let extracted = extract_action_json(raw).unwrap();
        assert_eq!(extracted, raw);
    }

    #[test]
    fn test_21_extract_json_with_code_fences() {
        let raw = "```json\n{\n  \"action\": \"open_file\",\n  \"target\": \"report.pdf\",\n  \"platform\": \"pc\",\n  \"confidence\": 0.90\n}\n```";
        let extracted = extract_action_json(raw).unwrap();
        let cmd = ActionCommand::from_json(&extracted).unwrap();
        assert_eq!(cmd.action, ActionType::OpenFile);
        assert_eq!(cmd.target.as_deref(), Some("report.pdf"));
    }

    #[test]
    fn test_22_extract_json_with_chatml_eos() {
        let raw = "{\"action\":\"search_files\",\"target\":\"grades\",\"platform\":\"pc\",\"confidence\":0.88}<|im_end|>";
        let extracted = extract_action_json(raw).unwrap();
        let cmd = ActionCommand::from_json(&extracted).unwrap();
        assert_eq!(cmd.action, ActionType::SearchFiles);
    }

    #[test]
    fn test_23_extract_json_rejects_conversational_chatter() {
        // English conversational chatter
        let english_chatter = "Sure! Here is your command:\n{\"action\":\"open_folder\",\"target\":\"Downloads\",\"platform\":\"pc\",\"confidence\":0.95}";
        assert!(extract_action_json(english_chatter).is_err());

        // Arabic conversational chatter
        let arabic_chatter = "حاضر، سأقوم بفتح المجلد:\n{\"action\":\"open_folder\",\"target\":\"Downloads\",\"platform\":\"pc\",\"confidence\":0.95}";
        assert!(extract_action_json(arabic_chatter).is_err());

        // Trailing chatter
        let trailing_chatter = "{\"action\":\"open_folder\",\"target\":\"Downloads\",\"platform\":\"pc\",\"confidence\":0.95}\nHope this helps!";
        assert!(extract_action_json(trailing_chatter).is_err());
    }

    #[test]
    fn test_24_extract_json_rejects_empty_or_non_object() {
        assert!(extract_action_json("").is_err());
        assert!(extract_action_json("   ").is_err());
        assert!(extract_action_json("[]").is_err());
        assert!(extract_action_json("Just chit chat with no json").is_err());
    }

    #[test]
    fn test_25_end_to_end_inference_to_execution_dry_run() {
        let guard = TempDirGuard::new("e2e_dry_run");
        let target_dir = guard.path.join("Projects");
        fs::create_dir_all(&target_dir).unwrap();

        // 1. Simulate raw model completion output with GBNF formatting
        let raw_model_output =
            r#"{"action":"open_folder","target":"Projects","platform":"pc","confidence":0.95}"#;

        // 2. Strict JSON extraction
        let clean_json = extract_action_json(raw_model_output).unwrap();

        // 3. Schema parsing
        let cmd = ActionCommand::from_json(&clean_json).unwrap();

        // 4. SafetyGate validation
        let safety_decision = SafetyGate::evaluate(&cmd);
        assert_eq!(
            safety_decision,
            silent_exec_core::SafetyDecision::AllowSilent
        );

        // 5. Indexing & Execution
        let mut indexer = FileIndexer::new(vec![guard.path.clone()]);
        indexer.build_index().unwrap();

        let executor = PcExecutor::new(indexer, true);
        let result = executor.execute(&cmd, false);

        match result {
            ExecutionResult::DryRun {
                action,
                target_resolved,
                ..
            } => {
                assert_eq!(action, "open_folder");
                assert_eq!(target_resolved.unwrap(), target_dir.to_string_lossy());
            }
            other => panic!("Expected DryRun result, got: {:?}", other),
        }
    }

    #[test]
    fn test_26_end_to_end_inference_blocks_injection() {
        let guard = TempDirGuard::new("e2e_block");

        // 1. Injected prompt generated malicious command
        let raw_injected_output = r#"{"action":"open_file","target":"report.pdf | rm -rf /","platform":"pc","confidence":0.90}"#;

        let clean_json = extract_action_json(raw_injected_output).unwrap();
        let cmd = ActionCommand::from_json(&clean_json).unwrap();

        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer, true);

        // SafetyGate inside executor blocks execution
        let result = executor.execute(&cmd, false);
        match result {
            ExecutionResult::Blocked { reason, action } => {
                assert_eq!(action, "open_file");
                assert!(
                    reason.to_lowercase().contains("pipe")
                        || reason.to_lowercase().contains("injection")
                );
            }
            other => panic!("Expected Blocked result, got: {:?}", other),
        }
    }

    fn send_http_request(
        addr: std::net::SocketAddr,
        method: &str,
        path: &str,
        auth_header: Option<&str>,
        body: Option<&str>,
    ) -> (u16, String) {
        use std::io::{BufRead, BufReader, Read, Write};
        use std::net::TcpStream;

        let mut stream = TcpStream::connect(addr).expect("Failed to connect to bridge");
        let body_str = body.unwrap_or("");
        let content_len = body_str.len();

        let mut req = format!(
            "{} {} HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: {}\r\nConnection: close\r\n",
            method, path, content_len
        );
        if let Some(auth) = auth_header {
            req.push_str(&format!("Authorization: {}\r\n", auth));
        }
        if !body_str.is_empty() {
            req.push_str("Content-Type: application/json\r\n");
        }
        req.push_str("\r\n");
        req.push_str(body_str);

        stream.write_all(req.as_bytes()).unwrap();
        stream.flush().unwrap();

        let mut reader = BufReader::new(stream);
        let mut status_line = String::new();
        reader.read_line(&mut status_line).unwrap();

        let status_code: u16 = status_line
            .split_whitespace()
            .nth(1)
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);

        loop {
            let mut line = String::new();
            if reader.read_line(&mut line).is_err() || line.trim().is_empty() {
                break;
            }
        }

        let mut resp_body = String::new();
        let _ = reader.read_to_string(&mut resp_body);

        (status_code, resp_body)
    }

    #[test]
    fn test_27_bridge_health_endpoint() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "test-secret-123".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let (code, body) = send_http_request(addr, "GET", "/health", None, None);
        assert_eq!(code, 200);
        assert!(body.contains(r#""status":"ok""#));
        assert!(body.contains(r#""version":"0.1.0""#));

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_28_bridge_unauthorized_missing_token() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "test-secret-123".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let (code, body) = send_http_request(addr, "POST", "/v1/execute", None, Some("{}"));
        assert_eq!(code, 401);
        assert!(body.contains("unauthorized"));

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_29_bridge_unauthorized_wrong_token() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "test-secret-123".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let (code, body) = send_http_request(
            addr,
            "POST",
            "/v1/execute",
            Some("Bearer wrong-token-xyz"),
            Some("{}"),
        );
        assert_eq!(code, 401);
        assert!(body.contains("unauthorized"));

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_30_bridge_execute_dry_run_allow_silent() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let guard = TempDirGuard::new("bridge_dry_run");
        let target_dir = guard.path.join("Downloads");
        fs::create_dir_all(&target_dir).unwrap();

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "secret-token".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![guard.path.clone()],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let req_json = r#"{
            "json_command": {
                "action": "open_folder",
                "target": "Downloads",
                "platform": "pc",
                "confidence": 0.95
            },
            "dry_run": true
        }"#;

        let (code, body) = send_http_request(
            addr,
            "POST",
            "/v1/execute",
            Some("Bearer secret-token"),
            Some(req_json),
        );

        assert_eq!(code, 200);
        let res: ExecutionResult = serde_json::from_str(&body).unwrap();
        match res {
            ExecutionResult::DryRun {
                action,
                target_resolved,
                ..
            } => {
                assert_eq!(action, "open_folder");
                assert!(target_resolved.is_some());
            }
            other => panic!("Expected DryRun result, got: {:?}", other),
        }

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_31_bridge_execute_safety_gate_blocking() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "secret-token".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let req_json = r#"{
            "json_command": {
                "action": "open_file",
                "target": "calc.exe && malicious_script",
                "platform": "pc",
                "confidence": 0.95
            },
            "dry_run": true
        }"#;

        let (code, body) = send_http_request(
            addr,
            "POST",
            "/v1/execute",
            Some("Bearer secret-token"),
            Some(req_json),
        );

        assert_eq!(code, 200);
        let res: ExecutionResult = serde_json::from_str(&body).unwrap();
        match res {
            ExecutionResult::Blocked { action, reason } => {
                assert_eq!(action, "open_file");
                assert!(reason.to_lowercase().contains("injection"));
            }
            other => panic!("Expected Blocked result, got: {:?}", other),
        }

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_32_bridge_execute_tier_2_confirmation_gating() {
        use std::sync::atomic::{AtomicBool, Ordering};
        use std::sync::Arc;

        let shutdown = Arc::new(AtomicBool::new(false));
        let config = BridgeConfig {
            host: "127.0.0.1".to_string(),
            port: 0,
            token: "secret-token".to_string(),
            dry_run: true,
            server_url: None,
            roots: vec![],
        };

        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone())).unwrap();

        let req_json = r#"{
            "json_command": {
                "action": "delete_file",
                "target": "sensitive.log",
                "platform": "pc",
                "confidence": 0.95
            },
            "confirm": false,
            "dry_run": true
        }"#;

        let (code, body) = send_http_request(
            addr,
            "POST",
            "/v1/execute",
            Some("Bearer secret-token"),
            Some(req_json),
        );

        assert_eq!(code, 200);
        let res: ExecutionResult = serde_json::from_str(&body).unwrap();
        match res {
            ExecutionResult::ConfirmationRequired { action, prompt, .. } => {
                assert_eq!(action, "delete_file");
                assert!(prompt.contains("Confirm"));
            }
            other => panic!("Expected ConfirmationRequired, got: {:?}", other),
        }

        shutdown.store(true, Ordering::Relaxed);
        let _ = handle.join();
    }

    #[test]
    fn test_33_asset_selection_avx2_preferred() {
        let assets = vec![
            ReleaseAsset {
                name: "llama-b4120-bin-win-vulkan-x64.zip".to_string(),
                browser_download_url: "https://example.com/vulkan.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-win-cuda-cu12.4-x64.zip".to_string(),
                browser_download_url: "https://example.com/cuda.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-win-avx2-x64.zip".to_string(),
                browser_download_url: "https://example.com/avx2.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-win-cpu-x64.zip".to_string(),
                browser_download_url: "https://example.com/cpu.zip".to_string(),
            },
        ];

        let selected = select_llama_asset_for_platform(&assets, "windows", "x86_64").unwrap();
        assert_eq!(selected.name, "llama-b4120-bin-win-avx2-x64.zip");
        assert_eq!(
            selected.browser_download_url,
            "https://example.com/avx2.zip"
        );
    }

    #[test]
    fn test_34_asset_selection_cpu_fallback() {
        let assets = vec![
            ReleaseAsset {
                name: "llama-b4000-bin-win-vulkan-x64.zip".to_string(),
                browser_download_url: "https://example.com/vulkan.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4000-bin-win-cpu-x64.zip".to_string(),
                browser_download_url: "https://example.com/cpu.zip".to_string(),
            },
        ];

        let selected = select_llama_asset_for_platform(&assets, "windows", "x86_64").unwrap();
        assert_eq!(selected.name, "llama-b4000-bin-win-cpu-x64.zip");
    }

    #[test]
    fn test_35_asset_selection_rejects_gpu_and_non_windows() {
        let assets = vec![
            ReleaseAsset {
                name: "llama-b4120-bin-win-cuda-cu12.4-x64.zip".to_string(),
                browser_download_url: "https://example.com/cuda.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-win-vulkan-x64.zip".to_string(),
                browser_download_url: "https://example.com/vulkan.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-ubuntu-x64.zip".to_string(),
                browser_download_url: "https://example.com/ubuntu.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-b4120-bin-macos-arm64.zip".to_string(),
                browser_download_url: "https://example.com/macos.zip".to_string(),
            },
        ];

        let selected = select_llama_asset_for_platform(&assets, "windows", "x86_64");
        assert!(selected.is_none());
    }

    #[test]
    fn test_36_asset_selection_alternative_names() {
        let assets = vec![
            ReleaseAsset {
                name: "win-cpu-x64.zip".to_string(),
                browser_download_url: "https://example.com/win-cpu.zip".to_string(),
            },
            ReleaseAsset {
                name: "llama-server-win-avx2-x64.zip".to_string(),
                browser_download_url: "https://example.com/llama-server-avx2.zip".to_string(),
            },
        ];

        let selected = select_llama_asset_for_platform(&assets, "windows", "x86_64").unwrap();
        assert_eq!(selected.name, "llama-server-win-avx2-x64.zip");
    }

    #[test]
    fn test_37_runtime_cache_directory_resolution() {
        let temp_guard = TempDirGuard::new("runtime_res");
        let custom_dir = temp_guard.path.join("custom_runtime");

        std::env::set_var("SILENT_EXEC_RUNTIME_DIR", &custom_dir);
        let resolved = RuntimeManager::default_runtime_dir();
        assert_eq!(resolved, custom_dir);
        std::env::remove_var("SILENT_EXEC_RUNTIME_DIR");

        let default_dir = RuntimeManager::default_runtime_dir();
        assert!(!default_dir.as_os_str().is_empty());
    }

    #[test]
    fn test_38_runtime_git_repo_pollution_guard() {
        let temp_guard = TempDirGuard::new("runtime_git_guard");
        let git_dir = temp_guard.path.join(".git");
        fs::create_dir_all(&git_dir).unwrap();

        let sub_dir = temp_guard.path.join("runtime");
        fs::create_dir_all(&sub_dir).unwrap();

        // Under generic git repo (e.g. user home), passes!
        let generic_res = RuntimeManager::assert_not_in_git_repo(&sub_dir);
        assert!(generic_res.is_ok());

        // Under source repo with core-rust/Cargo.toml, blocked!
        let core_rust = temp_guard.path.join("core-rust");
        fs::create_dir_all(&core_rust).unwrap();
        fs::write(core_rust.join("Cargo.toml"), "[workspace]").unwrap();

        let res = RuntimeManager::assert_not_in_git_repo(&sub_dir);
        assert!(res.is_err());
        assert!(res
            .unwrap_err()
            .contains("located inside the SilentExec source repository"));
    }

    #[test]
    fn test_39_health_check_url_parsing() {
        assert_eq!(
            parse_health_url("http://127.0.0.1:8080", 8080),
            "http://127.0.0.1:8080/health"
        );
        assert_eq!(
            parse_health_url("http://127.0.0.1:8080/", 8080),
            "http://127.0.0.1:8080/health"
        );
        assert_eq!(
            parse_health_url("http://127.0.0.1:8080/completion", 8080),
            "http://127.0.0.1:8080/health"
        );
        assert_eq!(
            parse_health_url("127.0.0.1:8080", 8080),
            "http://127.0.0.1:8080/health"
        );
        assert_eq!(
            parse_health_url("127.0.0.1", 8080),
            "http://127.0.0.1:8080/health"
        );
        assert_eq!(
            parse_health_url("http://localhost:9000/v1/models", 8080),
            "http://localhost:9000/health"
        );
        assert_eq!(
            parse_health_url("https://example.com:8443/chat", 8080),
            "https://example.com:8443/health"
        );
    }

    #[test]
    fn test_40_managed_server_reuses_healthy() {
        use std::io::Read;
        use std::net::TcpListener;

        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();

        let server_thread = std::thread::spawn(move || {
            if let Ok((mut stream, _)) = listener.accept() {
                let mut buf = [0u8; 1024];
                let _ = stream.read(&mut buf);
                let response = "HTTP/1.1 200 OK\r\nContent-Length: 15\r\nConnection: close\r\n\r\n{\"status\":\"ok\"}";
                let _ = stream.write_all(response.as_bytes());
            }
        });

        let dummy_model = PathBuf::from("non_existent_model.gguf");
        let dummy_runtime = PathBuf::from("non_existent_runtime");

        let res = ensure_llama_server_running(&dummy_model, &dummy_runtime, port);
        assert!(res.is_ok());
        assert_eq!(res.unwrap(), format!("http://127.0.0.1:{}", port));

        let _ = server_thread.join();
    }

    #[test]
    fn test_41_utf16_arabic_unicode_roundtrip() {
        let arabic_samples = [
            "افتح ملف تقرير المشروع",
            "احذف الملف القديم",
            "شغل المتصفح وابحث عن الذكاء الاصطناعي",
            "مَشْرُوعُ التَّخَرُّجِ النهائي",
            "افتح مجلد التنزيلات والوثائق",
            "مشتريات_رمضان_2026.xlsx",
        ];

        for sample in &arabic_samples {
            let utf16 = string_to_utf16(sample);
            let recovered = utf16_to_string(&utf16);
            assert_eq!(
                &recovered, *sample,
                "Failed roundtrip for Arabic text: {}",
                sample
            );
        }
    }

    #[test]
    fn test_42_utf16_english_mixed_roundtrip() {
        let mixed_samples = [
            "open C:\\Users\\Administrator\\Desktop\\report.pdf",
            "delete_file \"sensitive.log\" --confirm",
            "Alt+Space Floating Command Bar (Arabic: شريط الأوامر)",
            "✓ open_file: C:/Notes/quick_note.txt",
        ];

        for sample in &mixed_samples {
            let utf16 = string_to_utf16(sample);
            let recovered = utf16_to_string(&utf16);
            assert_eq!(
                &recovered, *sample,
                "Failed roundtrip for mixed text: {}",
                sample
            );
        }
    }

    #[test]
    fn test_43_ui_outcome_mapping_allow_silent() {
        let res_success = ExecutionResult::Success {
            action: "open_file".to_string(),
            details: "opened note.txt".to_string(),
            target_resolved: Some("note.txt".to_string()),
        };
        let outcome = map_result_to_ui_outcome(&res_success);
        assert_eq!(
            outcome,
            UiActionOutcome::SilentSuccess {
                action: "open_file".to_string()
            }
        );

        let res_dry_run = ExecutionResult::DryRun {
            action: "create_note".to_string(),
            target_resolved: Some("note.txt".to_string()),
            command_line: "cmd /c".to_string(),
            message: "Would write note to Desktop".to_string(),
        };
        let outcome_dry = map_result_to_ui_outcome(&res_dry_run);
        assert_eq!(
            outcome_dry,
            UiActionOutcome::SilentSuccess {
                action: "create_note".to_string()
            }
        );
    }

    #[test]
    fn test_44_ui_outcome_mapping_confirmation_required() {
        let res_confirm = ExecutionResult::ConfirmationRequired {
            action: "delete_file".to_string(),
            prompt: "Confirm delete_file on report.docx".to_string(),
            confidence: 0.95,
            reason: "Tier-2 destructive action".to_string(),
        };
        let outcome = map_result_to_ui_outcome(&res_confirm);
        assert_eq!(
            outcome,
            UiActionOutcome::RequireConfirmation {
                action: "delete_file".to_string(),
                prompt: "Confirm delete_file on report.docx".to_string()
            }
        );
    }

    #[test]
    fn test_45_ui_outcome_mapping_blocked() {
        let res_blocked = ExecutionResult::Blocked {
            action: "system_reboot".to_string(),
            reason: "Critical system power action blocked by security policy".to_string(),
        };
        let outcome = map_result_to_ui_outcome(&res_blocked);
        assert_eq!(
            outcome,
            UiActionOutcome::Blocked {
                reason: "Critical system power action blocked by security policy".to_string()
            }
        );
    }

    #[test]
    fn test_46_bar_state_hotkey_toggle_transitions() {
        use crate::ui_win32::BarState;

        let mut bar = BarState::new();
        assert!(!bar.is_visible);

        // Pressing Alt+Space toggles visibility to true
        let vis1 = bar.handle_hotkey_toggle();
        assert!(vis1);
        assert!(bar.is_visible);

        // Pressing Alt+Space again toggles visibility to false
        let vis2 = bar.handle_hotkey_toggle();
        assert!(!vis2);
        assert!(!bar.is_visible);
    }

    #[test]
    fn test_47_bar_state_submit_and_escape() {
        use crate::ui_win32::BarState;

        let mut bar = BarState::new();
        bar.handle_hotkey_toggle();
        assert!(bar.is_visible);

        // Escape dismisses the bar
        bar.handle_escape();
        assert!(!bar.is_visible);
        assert!(bar.current_input.is_empty());

        // Show again and submit empty string: should return None
        bar.handle_hotkey_toggle();
        let empty_sub = bar.handle_submit("    ".to_string());
        assert!(empty_sub.is_none());
        assert!(!bar.is_visible);

        // Show and submit command: should return trimmed prompt and hide
        bar.handle_hotkey_toggle();
        let prompt_sub = bar.handle_submit("  افتح ملف التقرير  ".to_string());
        assert_eq!(prompt_sub, Some("افتح ملف التقرير".to_string()));
        assert!(!bar.is_visible);
    }

    #[test]
    fn test_48_fast_router_yemeni_and_msa_back_and_home() {
        use crate::fast_router::FastRuleRouter;

        // Back commands
        for cmd_str in ["ارجع", "ارجع للخلف", "رجوع", "باك", "ارجع ورا", "سوي باك"]
        {
            let res = FastRuleRouter::route(cmd_str).expect("Should route back command");
            assert_eq!(res.action, ActionType::UiBack);
            assert_eq!(res.confidence, 1.0);
        }

        // Home commands
        for cmd_str in [
            "الشاشة الرئيسية",
            "سطح المكتب",
            "الهوم",
            "روح للشاشة الرئيسية",
            "ارجع لسطح المكتب",
        ] {
            let res = FastRuleRouter::route(cmd_str).expect("Should route home command");
            assert_eq!(res.action, ActionType::UiHome);
            assert_eq!(res.confidence, 1.0);
        }
    }

    #[test]
    fn test_49_fast_router_english_back_and_home() {
        use crate::fast_router::FastRuleRouter;

        for cmd_str in ["back", "go back", "navigate back", "return"] {
            let res = FastRuleRouter::route(cmd_str).expect("Should route english back");
            assert_eq!(res.action, ActionType::UiBack);
            assert_eq!(res.confidence, 1.0);
        }

        for cmd_str in ["desktop", "home", "home screen", "go home", "show desktop"] {
            let res = FastRuleRouter::route(cmd_str).expect("Should route english home");
            assert_eq!(res.action, ActionType::UiHome);
            assert_eq!(res.confidence, 1.0);
        }
    }

    #[test]
    fn test_50_fast_router_scroll_directions() {
        use crate::fast_router::FastRuleRouter;

        let down_res = FastRuleRouter::route("انزل لتحت").expect("Should route down");
        assert_eq!(down_res.action, ActionType::UiScroll);
        assert_eq!(
            down_res
                .args
                .as_ref()
                .and_then(|a| a.get("direction"))
                .and_then(|d| d.as_str()),
            Some("down")
        );

        let up_res = FastRuleRouter::route("scroll up").expect("Should route up");
        assert_eq!(up_res.action, ActionType::UiScroll);
        assert_eq!(
            up_res
                .args
                .as_ref()
                .and_then(|a| a.get("direction"))
                .and_then(|d| d.as_str()),
            Some("up")
        );

        let left_res = FastRuleRouter::route("swipe left").expect("Should route left");
        assert_eq!(left_res.action, ActionType::UiScroll);
        assert_eq!(
            left_res
                .args
                .as_ref()
                .and_then(|a| a.get("direction"))
                .and_then(|d| d.as_str()),
            Some("left")
        );

        let right_res = FastRuleRouter::route("مرر لليمين").expect("Should route right");
        assert_eq!(right_res.action, ActionType::UiScroll);
        assert_eq!(
            right_res
                .args
                .as_ref()
                .and_then(|a| a.get("direction"))
                .and_then(|d| d.as_str()),
            Some("right")
        );
    }

    #[test]
    fn test_51_fast_router_set_volume() {
        use crate::fast_router::FastRuleRouter;

        let vol80 = FastRuleRouter::route("ارفع الصوت 80").expect("Should route volume 80");
        assert_eq!(vol80.action, ActionType::SetVolume);
        assert_eq!(
            vol80
                .args
                .as_ref()
                .and_then(|a| a.get("level"))
                .and_then(|l| l.as_i64()),
            Some(80)
        );

        let vol50 = FastRuleRouter::route("set volume to 50").expect("Should route volume 50");
        assert_eq!(vol50.action, ActionType::SetVolume);
        assert_eq!(
            vol50
                .args
                .as_ref()
                .and_then(|a| a.get("level"))
                .and_then(|l| l.as_i64()),
            Some(50)
        );
    }

    #[test]
    fn test_52_fast_router_open_folder() {
        use crate::fast_router::FastRuleRouter;

        let dl = FastRuleRouter::route("افتح التنزيلات").expect("Should route open downloads");
        assert_eq!(dl.action, ActionType::OpenFolder);
        assert_eq!(dl.target.as_deref(), Some("Downloads"));

        let docs = FastRuleRouter::route("مجلد المستندات").expect("Should route documents");
        assert_eq!(docs.action, ActionType::OpenFolder);
        assert_eq!(docs.target.as_deref(), Some("Documents"));

        let pics = FastRuleRouter::route("open pictures").expect("Should route pictures");
        assert_eq!(pics.action, ActionType::OpenFolder);
        assert_eq!(pics.target.as_deref(), Some("Pictures"));
    }

    #[test]
    fn test_53_fast_router_fallback_to_none_for_complex() {
        use crate::fast_router::FastRuleRouter;

        // Complex queries that must fall back to LLM inference
        assert!(FastRuleRouter::route("ابحث عن تقرير المبيعات في التنزيلات").is_none());
        assert!(FastRuleRouter::route("click the green submit button on the left").is_none());
        assert!(FastRuleRouter::route("اكتب مرحبا في شريط البحث").is_none());
        assert!(FastRuleRouter::route("").is_none());
    }

    #[test]
    fn test_54_config_persistence_roundtrip() {
        use crate::config::AppConfig;

        let temp_dir = TempDirGuard::new("config_test");
        let cfg_path = temp_dir.path.join("config.json");

        let mut config = AppConfig::default();
        assert_eq!(config.language, "ar");
        assert_eq!(config.mode, "silent");
        assert!(config.fast_router_enabled);

        // Update fields
        config
            .update(
                Some("en"),
                Some("dry_run"),
                Some("v0.3.0-model"),
                Some(false),
            )
            .expect("Valid update");
        assert_eq!(config.language, "en");
        assert_eq!(config.mode, "dry_run");
        assert_eq!(config.active_model_tag.as_deref(), Some("v0.3.0-model"));
        assert!(!config.fast_router_enabled);

        // Save and reload
        config.save_to(&cfg_path).expect("Should save config");
        let loaded = AppConfig::load_from(&cfg_path).expect("Should load config");
        assert_eq!(config, loaded);

        // Invalid validation tests
        assert!(config.update(Some("fr"), None, None, None).is_err());
        assert!(config
            .update(None, Some("unsafe_mode"), None, None)
            .is_err());
    }

    #[test]
    fn test_55_updater_version_comparison() {
        use crate::updater::SelfUpdater;

        assert_eq!(SelfUpdater::parse_version("v0.3.0-pc"), (0, 3, 0));
        assert_eq!(SelfUpdater::parse_version("0.3.0"), (0, 3, 0));
        assert_eq!(SelfUpdater::parse_version("v1.2.3"), (1, 2, 3));

        // Newer version comparisons
        assert!(SelfUpdater::is_newer("0.3.0", "v0.3.1-pc"));
        assert!(SelfUpdater::is_newer("0.3.0", "v0.4.0-pc"));
        assert!(SelfUpdater::is_newer("0.3.0", "v1.0.0-pc"));

        // Same or older
        assert!(!SelfUpdater::is_newer("0.3.0", "v0.3.0-pc"));
        assert!(!SelfUpdater::is_newer("0.3.0", "v0.2.0-pc"));
        assert!(!SelfUpdater::is_newer("0.3.0", "v0.1.0-pc"));
    }

    #[test]
    fn test_56_cache_prompt_payload_verification() {
        use crate::inference::{LlamaBackend, DEFAULT_SYSTEM_PROMPT};

        let prompt = "test query";
        let grammar = "root ::= \"test\"";
        let payload = LlamaBackend::build_completion_payload(prompt, grammar);

        assert_eq!(
            payload.get("cache_prompt").and_then(|v| v.as_bool()),
            Some(true)
        );
        assert_eq!(
            payload.get("temperature").and_then(|v| v.as_f64()),
            Some(0.0)
        );
        assert_eq!(payload.get("prompt").and_then(|v| v.as_str()), Some(prompt));

        // Invariant: System prompt must be 100% static (no dynamic timestamps or dates)
        assert!(!DEFAULT_SYSTEM_PROMPT.contains("202"));
        assert!(!DEFAULT_SYSTEM_PROMPT.contains("time"));
        assert!(!DEFAULT_SYSTEM_PROMPT.contains("date"));
    }

    #[test]
    fn test_57_dry_run_gui_actions() {
        let temp = TempDirGuard::new("executor_gui_dry");
        let indexer = FileIndexer::new(vec![temp.path.clone()]);
        let executor = PcExecutor::new(indexer, true); // dry_run = true

        let gui_cmds = [
            ActionCommand {
                thought: None,
                action: ActionType::UiClick,
                target: Some("submit_button".to_string()),
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiType,
                target: Some("search_bar".to_string()),
                folder: None,
                args: Some(serde_json::json!({"text": "hello"})),
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiScroll,
                target: None,
                folder: None,
                args: Some(serde_json::json!({"direction": "down"})),
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::UiHome,
                target: None,
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
        ];

        for cmd in &gui_cmds {
            let res = executor.execute(cmd, false);
            match res {
                ExecutionResult::DryRun {
                    action, message, ..
                } => {
                    assert_eq!(action, action_type_to_str(cmd.action));
                    assert!(message.contains("Would"));
                }
                other => panic!("Expected DryRun for {:?}, got {:?}", cmd.action, other),
            }
        }
    }

    #[test]
    fn test_58_live_gui_actions_execution() {
        let temp = TempDirGuard::new("executor_gui_live");
        let indexer = FileIndexer::new(vec![temp.path.clone()]);
        let executor = PcExecutor::new(indexer, false); // live mode

        let cmd_home = ActionCommand {
            thought: None,
            action: ActionType::UiHome,
            target: None,
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.95,
        };

        let res = executor.execute(&cmd_home, false);
        match res {
            ExecutionResult::Success {
                action, details, ..
            } => {
                assert_eq!(action, "ui_home");
                assert!(
                    details.contains("home")
                        || details.contains("desktop")
                        || details.contains("via")
                );
            }
            other => panic!("Expected Success for live UiHome, got {:?}", other),
        }
    }

    #[test]
    fn test_59_fast_router_compound_command_guard() {
        use crate::fast_router::FastRuleRouter;
        use std::time::Instant;

        // Multi-step compound commands must return None / bypass fast rules
        let compound_ar = "افتح واتساب وابحث عن أحمد وارسل له مرحبا";
        assert!(FastRuleRouter::is_compound_command(compound_ar));
        assert!(FastRuleRouter::isCompoundCommand(compound_ar));
        assert!(FastRuleRouter::route(compound_ar).is_none());

        let compound_en = "open chrome and search for rust";
        assert!(FastRuleRouter::is_compound_command(compound_en));
        assert!(FastRuleRouter::isCompoundCommand(compound_en));
        assert!(FastRuleRouter::route(compound_en).is_none());

        // Simple commands must match in <15ms
        let start_back = Instant::now();
        let cmd_back = FastRuleRouter::route("ارجع").expect("Should match 'ارجع'");
        let elapsed_back = start_back.elapsed();
        assert_eq!(cmd_back.action, ActionType::UiBack);
        assert!(
            elapsed_back.as_millis() < 15,
            "Expected <15ms, took {:?}",
            elapsed_back
        );

        let start_dl = Instant::now();
        let cmd_dl = FastRuleRouter::route("افتح التنزيلات").expect("Should match 'افتح التنزيلات'");
        let elapsed_dl = start_dl.elapsed();
        assert_eq!(cmd_dl.action, ActionType::OpenFolder);
        assert_eq!(cmd_dl.target.as_deref(), Some("Downloads"));
        assert!(
            elapsed_dl.as_millis() < 15,
            "Expected <15ms, took {:?}",
            elapsed_dl
        );
    }

    #[test]
    fn test_60_compound_prompt_splitter() {
        use crate::planner::split_compound_prompt;

        let ar_prompt = "افتح واتساب وابحث عن أحمد وارسل له مرحبا";
        let ar_clauses = split_compound_prompt(ar_prompt);
        assert_eq!(
            ar_clauses,
            vec!["افتح واتساب", "ابحث عن أحمد", "ارسل له مرحبا"]
        );

        let en_prompt = "open chrome and search for rust";
        let en_clauses = split_compound_prompt(en_prompt);
        assert_eq!(en_clauses, vec!["open chrome", "search for rust"]);

        let then_prompt = "افتح مجلد التنزيلات ثم احذف temp.txt";
        let then_clauses = split_compound_prompt(then_prompt);
        assert_eq!(then_clauses, vec!["افتح مجلد التنزيلات", "احذف temp.txt"]);

        let single = split_compound_prompt("ارجع");
        assert_eq!(single, vec!["ارجع"]);
    }

    #[test]
    fn test_61_plan_engine_sequential_execution_and_events() {
        use crate::planner::{parse_plan, PlanEngine, StepEvent};
        use silent_exec_core::ExecutionMode;

        let temp = TempDirGuard::new("plan_engine_test");
        let indexer = FileIndexer::new(vec![temp.path.clone()]);
        let executor = PcExecutor::new(indexer, true); // dry run

        // 1. Parse multi-step JSON plan
        let plan_json = r#"{
            "steps": [
                {"action": "open_folder", "target": "Downloads", "platform": "pc", "confidence": 0.95},
                {"action": "ui_click", "target": "search bar", "platform": "pc", "confidence": 0.92},
                {"action": "done", "platform": "pc", "confidence": 1.0}
            ]
        }"#;
        let plan = parse_plan(plan_json).expect("Parse plan JSON");
        assert_eq!(plan.steps.len(), 3);

        // 2. Execute plan in DryRun mode and track events
        let mut events = Vec::new();
        let summary = PlanEngine::execute_plan(&executor, &plan, ExecutionMode::DryRun, |ev| {
            events.push(ev);
        })
        .expect("Execution succeed");

        assert!(summary.completed);
        assert_eq!(summary.total_steps, 3);
        assert_eq!(events[0], StepEvent::Understanding);
        assert_eq!(events[1], StepEvent::Planned(3));
        assert!(matches!(events[2], StepEvent::Running(1, 3, _)));
        assert!(matches!(events[3], StepEvent::Running(2, 3, _)));
        assert!(matches!(events[4], StepEvent::Running(3, 3, _)));
        assert_eq!(events[5], StepEvent::Done);

        // 3. Test blocked step in plan stops execution
        let dangerous_plan = MultiStepPlan::new(vec![
            ActionCommand {
                thought: None,
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                thought: None,
                action: ActionType::OpenFile,
                target: Some("../../etc/shadow".to_string()),
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
        ]);

        let mut dangerous_events = Vec::new();
        let dangerous_summary =
            PlanEngine::execute_plan(&executor, &dangerous_plan, ExecutionMode::Silent, |ev| {
                dangerous_events.push(ev);
            })
            .expect("Plan returns blocked summary");

        assert!(!dangerous_summary.completed);
        assert!(dangerous_events.contains(&StepEvent::Failed));
    }

    #[test]
    fn test_62_resumable_part_file_sha256_verification_and_rename() {
        let guard = TempDirGuard::new("resumable_test");
        let part_file = guard.path.join("model.bin.part");
        let final_file = guard.path.join("model.bin");

        let content = b"resumable download content block 1234567890";
        fs::write(&part_file, content).unwrap();

        let expected_hash = ModelManager::compute_file_sha256(&part_file).unwrap();

        let computed = ModelManager::compute_file_sha256(&part_file).unwrap();
        assert_eq!(expected_hash, computed);

        // Rename logic
        fs::rename(&part_file, &final_file).unwrap();
        assert!(final_file.exists());
        assert!(!part_file.exists());
        assert!(ModelManager::verify_checksum(&final_file, &expected_hash));
    }

    #[test]
    fn test_63_deterministic_web_search_resolution() {
        let cmd1 = resolve_clause("ابحث عن كود رست").expect("Must resolve Arabic search");
        assert_eq!(cmd1.action, ActionType::OpenUrl);
        assert!(cmd1
            .target
            .as_ref()
            .unwrap()
            .starts_with("https://www.google.com/search?q="));

        let cmd2 = resolve_clause("search for rust async").expect("Must resolve English search");
        assert_eq!(cmd2.action, ActionType::OpenUrl);
        assert!(cmd2
            .target
            .as_ref()
            .unwrap()
            .contains("search?q=rust+async"));
    }

    #[test]
    fn test_64_deterministic_verified_app_aliases() {
        let cmd_chrome = resolve_clause("افتح المتصفح").expect("Must resolve browser alias");
        assert_eq!(cmd_chrome.action, ActionType::LaunchApp);
        assert_eq!(cmd_chrome.target.as_deref(), Some("chrome"));

        let cmd_whatsapp = resolve_clause("شغل واتساب").expect("Must resolve whatsapp alias");
        assert_eq!(cmd_whatsapp.action, ActionType::LaunchApp);
        assert_eq!(cmd_whatsapp.target.as_deref(), Some("whatsapp"));

        let cmd_calc = resolve_clause("افتح الحاسبة").expect("Must resolve calc alias");
        assert_eq!(cmd_calc.action, ActionType::LaunchApp);
        assert_eq!(cmd_calc.target.as_deref(), Some("calc"));

        let cmd_notepad = resolve_clause("شغل المفكرة").expect("Must resolve notepad alias");
        assert_eq!(cmd_notepad.action, ActionType::LaunchApp);
        assert_eq!(cmd_notepad.target.as_deref(), Some("notepad"));

        let cmd_code = resolve_clause("افتح الكود").expect("Must resolve vscode alias");
        assert_eq!(cmd_code.action, ActionType::LaunchApp);
        assert_eq!(cmd_code.target.as_deref(), Some("code"));
    }

    #[test]
    fn test_65_rejects_fake_launch_app_on_unknown_clauses() {
        // Unknown raw Arabic sentence without app keywords must return None, NOT fake launch_app
        let unknown1 = resolve_clause("اعمل تحليل للمبيعات وارسلها للمدير");
        assert!(unknown1.is_none());

        let unknown2 = resolve_clause("لخص لي الاجتماع السابق");
        assert!(unknown2.is_none());
    }

    #[test]
    fn test_66_ollama_payload_structure() {
        let payload = AiBackend::build_ollama_payload("qwen2.5:1.5b", "افتح ملف تقرير");
        assert_eq!(payload["model"], "qwen2.5:1.5b");
        assert_eq!(payload["format"], "json");
        assert_eq!(payload["stream"], false);
        assert_eq!(payload["messages"][0]["role"], "system");
        assert_eq!(payload["messages"][1]["role"], "user");
        assert_eq!(payload["messages"][1]["content"], "افتح ملف تقرير");
    }

    #[test]
    fn test_67_openai_compat_payload_structure() {
        let payload = AiBackend::build_openai_compat_payload("gpt-4o-mini", "open report.pdf");
        assert_eq!(payload["model"], "gpt-4o-mini");
        assert_eq!(payload["response_format"]["type"], "json_object");
        assert_eq!(payload["messages"][0]["role"], "system");
        assert_eq!(payload["messages"][1]["role"], "user");
        assert_eq!(payload["messages"][1]["content"], "open report.pdf");
    }

    #[test]
    fn test_68_mcp_json_rpc_message_framing() {
        let req = JsonRpcRequest::new("tools/list", None);
        assert_eq!(req.jsonrpc, "2.0");
        assert_eq!(req.method, "tools/list");
        assert!(req.id > 0);

        let serialized = serde_json::to_string(&req).unwrap();
        assert!(serialized.contains("\"jsonrpc\":\"2.0\""));
        assert!(serialized.contains("\"method\":\"tools/list\""));

        let resp_json = r#"{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"test_tool","description":"A test tool"}]}}"#;
        let resp: JsonRpcResponse = serde_json::from_str(resp_json).unwrap();
        assert_eq!(resp.id, Some(1));
        assert!(resp.error.is_none());
        assert!(resp.result.is_some());
    }

    #[test]
    fn test_69_mcp_safety_gate_blocking_dangerous_tool() {
        let mut client = McpClient::new(McpServerConfig {
            name: "test_mcp".to_string(),
            command: "echo".to_string(),
            args: vec![],
            enabled: true,
        });

        // Calling dangerous shell injection target via MCP tool
        let res = client
            .call_tool("cmd; rm -rf /", None, ExecutionMode::Silent)
            .unwrap();
        assert!(matches!(res, ExecutionResult::Blocked { .. }));
    }

    #[test]
    fn test_70_mcp_dry_run_simulation() {
        let mut client = McpClient::new(McpServerConfig {
            name: "calculator".to_string(),
            command: "calc".to_string(),
            args: vec![],
            enabled: true,
        });

        let res = client
            .call_tool(
                "add",
                Some(serde_json::json!({"a": 2, "b": 3})),
                ExecutionMode::DryRun,
            )
            .unwrap();

        assert!(matches!(res, ExecutionResult::DryRun { .. }));
    }

    #[test]
    fn test_71_config_ai_backend_and_mcp_persistence() {
        let guard = TempDirGuard::new("config_ai_test");
        let config_file = guard.path.join("config.json");

        let mut cfg = AppConfig::default();
        cfg.backend = "ollama".to_string();
        cfg.ollama_model = "qwen2.5:3b".to_string();
        cfg.mcp_servers.push(McpServerConfig {
            name: "weather".to_string(),
            command: "node".to_string(),
            args: vec!["weather_server.js".to_string()],
            enabled: true,
        });

        cfg.save_to(&config_file).unwrap();

        let loaded = AppConfig::load_from(&config_file).unwrap();
        assert_eq!(loaded.backend, "ollama");
        assert_eq!(loaded.ollama_model, "qwen2.5:3b");
        assert_eq!(loaded.mcp_servers.len(), 1);
        assert_eq!(loaded.mcp_servers[0].name, "weather");
    }

    #[test]
    fn test_72_memory_store_crud_and_relevance() {
        let guard = TempDirGuard::new("memory_test");
        let mem_file = guard.path.join("memory.json");

        let mut store = MemoryStore::load_from(&mem_file).unwrap();
        assert!(store.list().is_empty());

        let m1 = store.save_item(
            "preferred_language",
            "User strictly speaks Arabic and English",
            Some("preference"),
        );
        assert_eq!(m1.key, "preferred_language");
        assert_eq!(store.list().len(), 1);

        // Update existing
        let m1_updated = store.save_item(
            "preferred_language",
            "User strictly speaks Yemeni Arabic",
            Some("preference"),
        );
        assert_eq!(m1_updated.content, "User strictly speaks Yemeni Arabic");
        assert_eq!(store.list().len(), 1);

        // Save second item
        let m2 = store.save_item("projects_dir", "D:\\Projects", Some("fact"));
        assert_eq!(m2.key, "projects_dir");
        assert_eq!(store.list().len(), 2);

        store.save_to(&mem_file).unwrap();
        let reloaded = MemoryStore::load_from(&mem_file).unwrap();
        assert_eq!(reloaded.list().len(), 2);

        // Relevance
        let relevant = reloaded.get_relevant("language", 2);
        assert!(!relevant.is_empty());
        assert_eq!(relevant[0].key, "preferred_language");

        // Delete
        let mut store = reloaded;
        assert!(store.delete_item("projects_dir"));
        assert_eq!(store.list().len(), 1);
    }

    #[test]
    fn test_73_curated_community_models_catalog() {
        let guard = TempDirGuard::new("models_test");
        let curated = ModelManager::curated_community_models(&guard.path, Some("v0.2.0-model"));
        assert!(curated.len() >= 4);

        let liquid = curated.iter().find(|m| m.tag == "liquidai-lfm3-1.7b");
        assert!(
            liquid.is_some(),
            "LiquidAI model must exist in curated catalog"
        );
        assert!(liquid.unwrap().download_url.is_some());

        let qwen = curated.iter().find(|m| m.tag == "qwen3-1.7b-gguf");
        assert!(
            qwen.is_some(),
            "Qwen3 GGUF model must exist in curated catalog"
        );

        let v02 = curated.iter().find(|m| m.tag == "v0.2.0-model");
        assert!(v02.is_some(), "v0.2.0-model must exist in curated catalog");
        assert!(v02.unwrap().is_active);

        let v01 = curated.iter().find(|m| m.tag == "v0.1.0-model");
        assert!(v01.is_some(), "v0.1.0-model must exist in curated catalog");
    }

    #[test]
    fn test_74_hermes_tools_execution_and_dry_run() {
        let guard = TempDirGuard::new("hermes_tools_test");
        let indexer = FileIndexer::new(vec![guard.path.clone()]);
        let executor = PcExecutor::new(indexer.clone(), false).with_notes_dir(guard.path.clone());
        let dry_executor = PcExecutor::new(indexer, true).with_notes_dir(guard.path.clone());

        // 1. Reply tool (DryRun & Live)
        let reply_cmd = ActionCommand {
            thought: None,
            action: ActionType::Reply,
            target: Some("Answer: 42".to_string()),
            folder: None,
            args: Some(serde_json::json!({"text": "Answer: 42"})),
            platform: Platform::Any,
            confidence: 0.95,
        };
        let dry_reply = dry_executor.execute(&reply_cmd, false);
        assert!(matches!(dry_reply, ExecutionResult::DryRun { .. }));
        let live_reply = executor.execute(&reply_cmd, false);
        match live_reply {
            ExecutionResult::Success { details, .. } => assert_eq!(details, "Answer: 42"),
            other => panic!("Expected Success for reply, got {:?}", other),
        }

        // 2. WriteFile tool (Tier-2 Confirmation required if false, executes when true)
        let test_file = guard.path.join("output.txt");
        let write_cmd = ActionCommand {
            thought: None,
            action: ActionType::WriteFile,
            target: Some(test_file.to_string_lossy().to_string()),
            folder: None,
            args: Some(serde_json::json!({"content": "Hello from agent"})),
            platform: Platform::Pc,
            confidence: 0.90,
        };
        let unconfirmed_write = executor.execute(&write_cmd, false);
        assert!(matches!(
            unconfirmed_write,
            ExecutionResult::ConfirmationRequired { .. }
        ));
        let live_write = executor.execute(&write_cmd, true);
        assert!(matches!(live_write, ExecutionResult::Success { .. }));
        assert_eq!(fs::read_to_string(&test_file).unwrap(), "Hello from agent");

        // 3. ReadFile tool (Live)
        let read_cmd = ActionCommand {
            thought: None,
            action: ActionType::ReadFile,
            target: Some(test_file.to_string_lossy().to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 0.92,
        };
        let live_read = executor.execute(&read_cmd, false);
        match live_read {
            ExecutionResult::Success { details, .. } => assert_eq!(details, "Hello from agent"),
            other => panic!("Expected Success for read_file, got {:?}", other),
        }

        // 4. MemorySave tool (Live)
        let mem_cmd = ActionCommand {
            thought: None,
            action: ActionType::MemorySave,
            target: Some("user_theme".to_string()),
            folder: None,
            args: Some(serde_json::json!({
                "key": "user_theme",
                "content": "dark_mode",
                "category": "preference"
            })),
            platform: Platform::Any,
            confidence: 0.98,
        };
        let live_mem = executor.execute(&mem_cmd, false);
        assert!(matches!(live_mem, ExecutionResult::Success { .. }));
    }

    #[test]
    fn test_75_built_in_skills_youtube_and_web_search() {
        let ar_steps = BuiltInSkills::resolve_with_store("افتح اليوتيوب و ابحث عن nlp", None)
            .expect("Should resolve Arabic YouTube search skill");
        assert_eq!(ar_steps.len(), 1);
        assert_eq!(ar_steps[0].action, ActionType::OpenUrl);
        assert_eq!(
            ar_steps[0].target.as_deref(),
            Some("https://www.youtube.com/results?search_query=nlp")
        );

        let en_steps = BuiltInSkills::resolve_with_store("open YouTube and search for nlp", None)
            .expect("Should resolve English YouTube search skill");
        assert_eq!(en_steps.len(), 1);
        assert_eq!(en_steps[0].action, ActionType::OpenUrl);
        assert_eq!(
            en_steps[0].target.as_deref(),
            Some("https://www.youtube.com/results?search_query=nlp")
        );

        let web_steps = BuiltInSkills::resolve_with_store("افتح المتصفح وابحث عن rust lang", None)
            .expect("Should resolve Arabic web search skill");
        assert_eq!(web_steps[0].action, ActionType::OpenUrl);
        assert_eq!(
            web_steps[0].target.as_deref(),
            Some("https://www.google.com/search?q=rust+lang")
        );
    }

    #[test]
    fn test_76_custom_skill_store_persistence() {
        let guard = TempDirGuard::new("skills_test");
        let skills_file = guard.path.join("skills.json");
        let mut store = SkillStore::load_from(&skills_file).unwrap();

        let step1 = ActionCommand {
            thought: None,
            action: ActionType::SetVolume,
            target: None,
            folder: None,
            args: Some(serde_json::json!({ "level": 30 })),
            platform: Platform::Pc,
            confidence: 1.0,
        };
        let step2 = ActionCommand {
            thought: None,
            action: ActionType::OpenUrl,
            target: Some("https://github.com".to_string()),
            folder: None,
            args: None,
            platform: Platform::Pc,
            confidence: 1.0,
        };

        store
            .save_skill(
                "وضع الدراسة",
                vec!["Work Mode".to_string()],
                vec![step1, step2],
                "Lowers volume and opens workspace",
            )
            .unwrap();

        let reloaded = SkillStore::load_from(&skills_file).unwrap();
        let resolved = BuiltInSkills::resolve_with_store("Work Mode", Some(&reloaded))
            .expect("Should resolve custom skill by trigger");
        assert_eq!(resolved.len(), 2);
        assert_eq!(resolved[0].action, ActionType::SetVolume);
        assert_eq!(resolved[1].action, ActionType::OpenUrl);
    }

    #[test]
    fn test_77_huggingface_tree_json_parsing_lfm3() {
        let guard = TempDirGuard::new("hf_tree_test");
        let sample_hf_tree = r#"[
            {"type":"file","size":3466626336,"path":"LFM3-1.7B-BF16.gguf"},
            {"type":"file","size":1099627808,"path":"LFM3-1.7B-Q4_K_M.gguf"},
            {"type":"file","size":1258663200,"path":"LFM3-1.7B-Q5_K_M.gguf"}
        ]"#;

        let parsed = ModelManager::parse_huggingface_tree_q4km(
            "LiquidAI/LFM3-1.7B-GGUF",
            sample_hf_tree,
            "liquidai-lfm3-1.7b",
            "LiquidAI/LFM3-1.7B (Fast Edge Agent)",
            &guard.path,
            None,
        )
        .expect("Should parse Q4_K_M entry from HF tree JSON");

        assert_eq!(parsed.filename, "LFM3-1.7B-Q4_K_M.gguf");
        assert_eq!(parsed.size_bytes, 1_099_627_808);
        assert_eq!(
            parsed.download_url.as_deref(),
            Some("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf?download=true")
        );
    }

    #[test]
    fn test_78_compound_intent_guard_does_not_hijack_multistep_prompts() {
        let multi_ar = "افتح اليوتيوب و ابحث عن nlp و افتح اول فيديو و اضغط لايك";
        assert!(BuiltInSkills::resolve_with_store(multi_ar, None).is_none());

        let multi_en = "open youtube and search for nlp and open first video and click like";
        assert!(BuiltInSkills::resolve_with_store(multi_en, None).is_none());

        let single = BuiltInSkills::resolve_with_store("افتح اليوتيوب و ابحث عن nlp", None);
        assert!(single.is_some());
    }

    #[test]
    fn test_79_action_command_deserializes_thought_field() {
        let json = r##"{"thought":"Open first video result","action":"ui_click","target":"#0","platform":"pc","confidence":0.91}"##;
        let cmd =
            ActionCommand::from_json(json).expect("Should deserialize ActionCommand with thought");
        assert_eq!(cmd.thought.as_deref(), Some("Open first video result"));
        assert_eq!(cmd.action, ActionType::UiClick);
        assert_eq!(cmd.target.as_deref(), Some("#0"));
    }

    #[test]
    fn test_80_tool_calling_agent_and_mcp_schema() {
        let mcp_tool = McpTool {
            name: "read_file".to_string(),
            description: Some("Read file contents".to_string()),
            input_schema: Some(serde_json::json!({
                "type": "object",
                "properties": { "path": { "type": "string" } }
            })),
        };
        let lookup = vec![("filesystem", &mcp_tool)];
        let schema = ToolCallingAgent::build_tools_schema(&lookup);
        let arr = schema.as_array().expect("tools should be JSON array");
        assert_eq!(arr.len(), 2);
        assert_eq!(arr[1]["function"]["name"], "mcp__filesystem__read_file");

        let resp_pc = r#"{
            "choices": [{
                "message": {
                    "role": "assistant",
                    "tool_calls": [{
                        "id": "call_1",
                        "type": "function",
                        "function": {
                            "name": "execute_pc_action",
                            "arguments": "{\"thought\":\"Open Downloads\",\"action\":\"open_folder\",\"target\":\"Downloads\",\"platform\":\"pc\",\"confidence\":0.96}"
                        }
                    }]
                }
            }]
        }"#;
        match ToolCallingAgent::parse_response(resp_pc, &lookup).expect("should parse tool call") {
            AgentTurnDecision::Action(cmd) => {
                assert_eq!(cmd.action, ActionType::OpenFolder);
                assert_eq!(cmd.target.as_deref(), Some("Downloads"));
            }
            other => panic!("Expected Action, got {:?}", other),
        }

        let resp_mcp = r#"{
            "choices": [{
                "message": {
                    "role": "assistant",
                    "tool_calls": [{
                        "id": "call_2",
                        "type": "function",
                        "function": {
                            "name": "mcp__filesystem__read_file",
                            "arguments": "{\"path\":\"README.md\"}"
                        }
                    }]
                }
            }]
        }"#;
        match ToolCallingAgent::parse_response(resp_mcp, &lookup).expect("should parse mcp call") {
            AgentTurnDecision::McpCall {
                server_name,
                tool_name,
                arguments,
                ..
            } => {
                assert_eq!(server_name, "filesystem");
                assert_eq!(tool_name, "read_file");
                assert_eq!(arguments["path"], "README.md");
            }
            other => panic!("Expected McpCall, got {:?}", other),
        }
    }
}
