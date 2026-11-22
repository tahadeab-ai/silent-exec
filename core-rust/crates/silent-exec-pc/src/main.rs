//! SilentExec PC CLI: Safe execution, fuzzy search, and model download.

use silent_exec_core::ActionCommand;
use silent_exec_pc::{FileIndexer, ModelManager, PcExecutor};
use std::io::{self, Read};
use std::path::PathBuf;
use std::process::ExitCode;

fn print_usage() {
    eprintln!("SilentExec PC Execution Agent & Model Manager");
    eprintln!();
    eprintln!("Usage:");
    eprintln!(
        "  silent-exec-pc exec [--json <JSON> | --stdin] [--dry-run] [--confirm] [--roots <R1,R2>]"
    );
    eprintln!("  silent-exec-pc search <QUERY> [--dirs-only] [--roots <R1,R2>]");
    eprintln!("  silent-exec-pc download-model --tag <TAG> [--cache-dir <DIR>]");
    eprintln!("  silent-exec-pc status");
    eprintln!();
    eprintln!("Options:");
    eprintln!(
        "  --dry-run   Simulate execution and print resolved paths without spawning OS windows"
    );
    eprintln!("  --confirm   Explicitly authorize Tier-2 destructive or system commands");
    eprintln!("  --tag       GitHub Release tag to download model from (e.g. v0.2.0-model)");
}

fn parse_roots(arg: Option<&str>) -> Vec<PathBuf> {
    match arg {
        Some(s) => s.split(',').map(|p| PathBuf::from(p.trim())).collect(),
        None => FileIndexer::default_user_roots(),
    }
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 2 {
        print_usage();
        return ExitCode::from(1);
    }

    match args[1].as_str() {
        "exec" => {
            let mut json_str = None;
            let mut use_stdin = false;
            let mut dry_run = false;
            let mut confirm = false;
            let mut roots_arg = None;

            let mut idx = 2;
            while idx < args.len() {
                match args[idx].as_str() {
                    "--json" if idx + 1 < args.len() => {
                        json_str = Some(args[idx + 1].clone());
                        idx += 1;
                    }
                    "--stdin" => use_stdin = true,
                    "--dry-run" => dry_run = true,
                    "--confirm" => confirm = true,
                    "--roots" if idx + 1 < args.len() => {
                        roots_arg = Some(args[idx + 1].as_str());
                        idx += 1;
                    }
                    _ => {}
                }
                idx += 1;
            }

            let raw_json = if let Some(s) = json_str {
                s
            } else if use_stdin {
                let mut buf = String::new();
                if let Err(e) = io::stdin().read_to_string(&mut buf) {
                    eprintln!("Error reading stdin: {}", e);
                    return ExitCode::from(1);
                }
                buf
            } else {
                eprintln!("Error: specify either --json <JSON> or --stdin");
                return ExitCode::from(1);
            };

            let cmd: ActionCommand = match serde_json::from_str(&raw_json) {
                Ok(c) => c,
                Err(e) => {
                    eprintln!("JSON parse error against ActionCommand schema: {}", e);
                    return ExitCode::from(1);
                }
            };

            let roots = parse_roots(roots_arg);
            let mut indexer = FileIndexer::new(roots);
            let _ = indexer.build_index();

            let executor = PcExecutor::new(indexer, dry_run);
            let result = executor.execute(&cmd, confirm);

            println!("{}", serde_json::to_string_pretty(&result).unwrap());
            ExitCode::SUCCESS
        }
        "search" => {
            if args.len() < 3 {
                eprintln!("Usage: silent-exec-pc search <QUERY> [--dirs-only] [--roots <R1,R2>]");
                return ExitCode::from(1);
            }
            let query = &args[2];
            let dirs_only = args.iter().any(|a| a == "--dirs-only");
            let roots_arg = args
                .iter()
                .position(|a| a == "--roots")
                .and_then(|i| args.get(i + 1).map(|s| s.as_str()));

            let roots = parse_roots(roots_arg);
            let mut indexer = FileIndexer::new(roots);
            if let Err(e) = indexer.build_index() {
                eprintln!("Indexing error: {}", e);
            }

            let matches = indexer.search(query, dirs_only);
            println!("Found {} matches for query '{}':", matches.len(), query);
            for (i, m) in matches.iter().take(10).enumerate() {
                println!(
                    "  {:2}. [score: {:4}] {} ({})",
                    i + 1,
                    m.score,
                    m.name,
                    m.path.display()
                );
            }
            ExitCode::SUCCESS
        }
        "download-model" => {
            let mut tag = "v0.1.0-model".to_string();
            let mut custom_cache = None;

            let mut idx = 2;
            while idx < args.len() {
                match args[idx].as_str() {
                    "--tag" if idx + 1 < args.len() => {
                        tag = args[idx + 1].clone();
                        idx += 1;
                    }
                    "--cache-dir" if idx + 1 < args.len() => {
                        custom_cache = Some(PathBuf::from(args[idx + 1].clone()));
                        idx += 1;
                    }
                    _ => {}
                }
                idx += 1;
            }

            let manager = match ModelManager::new(custom_cache) {
                Ok(m) => m,
                Err(e) => {
                    eprintln!("ModelManager initialization error: {}", e);
                    return ExitCode::from(1);
                }
            };

            println!("Model Cache Directory: {}", manager.cache_dir().display());
            match manager.download_model(&tag) {
                Ok(info) => {
                    println!("\nModel Ready:");
                    println!("{}", serde_json::to_string_pretty(&info).unwrap());
                    ExitCode::SUCCESS
                }
                Err(e) => {
                    eprintln!("Failed to download model for tag '{}': {}", tag, e);
                    ExitCode::from(1)
                }
            }
        }
        "status" => {
            let roots = FileIndexer::default_user_roots();
            let cache_dir = ModelManager::default_cache_dir();
            println!("=== SilentExec PC Status ===");
            println!("Default Roots: {:?}", roots);
            println!("Model Cache:   {}", cache_dir.display());
            ExitCode::SUCCESS
        }
        _ => {
            print_usage();
            ExitCode::from(1)
        }
    }
}
