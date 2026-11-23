//! SilentExec PC Executor, Arabic/English Fuzzy Indexer, Inference & LAN Bridge Library

pub mod bridge;
pub mod config;
pub mod executor;
pub mod fast_router;
pub mod indexer;
pub mod inference;
pub mod model_manager;
pub mod planner;
pub mod runtime_manager;
pub mod ui_win32;
pub mod updater;

pub use bridge::{start_bridge_server, BridgeConfig, ExecuteRequest};
pub use config::AppConfig;
pub use executor::{action_type_to_str, ExecutionResult, PcExecutor};
pub use fast_router::FastRuleRouter;
pub use indexer::{
    compute_fuzzy_score, normalize_arabic_english, FileIndexer, IndexedItem, ScoredMatch,
};
pub use inference::{
    build_chatml_prompt, extract_action_json, format_prompt, LlamaBackend, DEFAULT_SYSTEM_PROMPT,
    EMBEDDED_ACTION_GBNF,
};
pub use model_manager::{ModelDownloadInfo, ModelManager, ModelReleaseInfo};
pub use planner::{parse_plan, split_compound_prompt, PlanEngine, PlanExecutionSummary, StepEvent};
pub use runtime_manager::{
    check_health_http, ensure_llama_server_running, parse_health_url, run_setup,
    select_llama_asset, select_llama_asset_for_platform, ReleaseAsset, RuntimeManager, SetupResult,
};
pub use ui_win32::{
    map_result_to_ui_outcome, run_command_bar, string_to_utf16, utf16_to_string, UiActionOutcome,
};
pub use updater::{SelfUpdater, UpdateInfo};

#[cfg(test)]
mod tests {
    use super::*;
    use silent_exec_core::{ActionCommand, ActionType, MultiStepPlan, Platform, SafetyGate};
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

        let check = ModelManager::assert_not_in_git_repo(&inside_models);
        assert!(check.is_err(), "Must reject directories inside a Git repo");
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

        let res = RuntimeManager::assert_not_in_git_repo(&sub_dir);
        assert!(res.is_err());
        assert!(res.unwrap_err().contains("located inside a Git repository"));
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
                action: ActionType::UiClick,
                target: Some("submit_button".to_string()),
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                action: ActionType::UiType,
                target: Some("search_bar".to_string()),
                folder: None,
                args: Some(serde_json::json!({"text": "hello"})),
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                action: ActionType::UiScroll,
                target: None,
                folder: None,
                args: Some(serde_json::json!({"direction": "down"})),
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
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
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                platform: Platform::Pc,
                confidence: 0.95,
            },
            ActionCommand {
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
}
