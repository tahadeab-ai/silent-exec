//! SilentExec PC Executor, Arabic/English Fuzzy Indexer, Inference & LAN Bridge Library

pub mod bridge;
pub mod executor;
pub mod indexer;
pub mod inference;
pub mod model_manager;

pub use bridge::{start_bridge_server, BridgeConfig, ExecuteRequest};
pub use executor::{action_type_to_str, ExecutionResult, PcExecutor};
pub use indexer::{
    compute_fuzzy_score, normalize_arabic_english, FileIndexer, IndexedItem, ScoredMatch,
};
pub use inference::{
    build_chatml_prompt, extract_action_json, format_prompt, LlamaBackend, DEFAULT_SYSTEM_PROMPT,
    EMBEDDED_ACTION_GBNF,
};
pub use model_manager::{ModelDownloadInfo, ModelManager};

#[cfg(test)]
mod tests {
    use super::*;
    use silent_exec_core::{ActionCommand, ActionType, Platform, SafetyGate};
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
}
