//! SilentExec PC Executor & Arabic/English Fuzzy Indexer Library

pub mod executor;
pub mod indexer;
pub mod model_manager;

pub use executor::{action_type_to_str, ExecutionResult, PcExecutor};
pub use indexer::{
    compute_fuzzy_score, normalize_arabic_english, FileIndexer, IndexedItem, ScoredMatch,
};
pub use model_manager::{ModelDownloadInfo, ModelManager};

#[cfg(test)]
mod tests {
    use super::*;
    use silent_exec_core::{ActionCommand, ActionType, Platform};
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
}
