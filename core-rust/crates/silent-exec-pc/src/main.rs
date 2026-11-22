//! SilentExec PC CLI: Safe execution, fuzzy search, model download, LAN bridge, and quick-bar mode.

use silent_exec_core::ActionCommand;
use silent_exec_pc::{
    BridgeConfig, ExecutionResult, FileIndexer, LlamaBackend, ModelManager, PcExecutor,
};
use std::fs;
use std::io::{self, BufRead, Read};
use std::path::PathBuf;
use std::process::ExitCode;

fn print_usage() {
    eprintln!("SilentExec PC Execution Agent, LAN Bridge & Model Manager");
    eprintln!();
    eprintln!("Usage:");
    eprintln!(
        "  silent-exec-pc run \"<PROMPT>\" [--dry-run] [--confirm] [--server-url <URL>] [--llama-bin <PATH>] [--model <PATH>] [--grammar <PATH>] [--roots <R1,R2>]"
    );
    eprintln!(
        "  silent-exec-pc quick [--dry-run] [--confirm] [--server-url <URL>] [--llama-bin <PATH>] [--model <PATH>] [--roots <R1,R2>]"
    );
    eprintln!(
        "  silent-exec-pc serve [--host <HOST>] [--port <PORT>] [--token <SECRET>] [--dry-run] [--server-url <URL>] [--roots <R1,R2>]"
    );
    eprintln!(
        "  silent-exec-pc exec [--json <JSON> | --stdin] [--dry-run] [--confirm] [--roots <R1,R2>]"
    );
    eprintln!("  silent-exec-pc search <QUERY> [--dirs-only] [--roots <R1,R2>]");
    eprintln!("  silent-exec-pc download-model [--tag <TAG>] [--cache-dir <DIR>]");
    eprintln!("  silent-exec-pc status");
    eprintln!();
    eprintln!("Options:");
    eprintln!("  --dry-run       Simulate execution without spawning OS windows");
    eprintln!("  --confirm       Authorize Tier-2 destructive or system commands");
    eprintln!("  --host          Host IP address to bind LAN bridge (default: 0.0.0.0)");
    eprintln!("  --port          Port for LAN bridge (default: 8765)");
    eprintln!("  --token         Shared Bearer secret token for LAN bridge authentication");
    eprintln!("  --server-url    llama-server endpoint URL (default: http://127.0.0.1:8080)");
    eprintln!("  --llama-bin     Path to llama-cli executable");
    eprintln!("  --model         Path to .gguf model file");
    eprintln!("  --grammar       Path to custom GBNF grammar file");
    eprintln!("  --roots         Comma-separated search roots for indexing");
    eprintln!("  --tag           GitHub Release tag to download model from (e.g. v0.2.0-model)");
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
        "run" => {
            if args.len() < 3 {
                eprintln!("Usage: silent-exec-pc run \"<PROMPT>\" [--dry-run] [--confirm] [--server-url <URL>] [...]");
                return ExitCode::from(1);
            }
            let user_prompt = &args[2];
            let mut dry_run = false;
            let mut confirm = false;
            let mut server_url = None;
            let mut llama_bin = None;
            let mut model_path = None;
            let mut grammar_path = None;
            let mut roots_arg = None;

            let mut idx = 3;
            while idx < args.len() {
                match args[idx].as_str() {
                    "--dry-run" => dry_run = true,
                    "--confirm" => confirm = true,
                    "--server-url" if idx + 1 < args.len() => {
                        server_url = Some(args[idx + 1].clone());
                        idx += 1;
                    }
                    "--llama-bin" if idx + 1 < args.len() => {
                        llama_bin = Some(PathBuf::from(&args[idx + 1]));
                        idx += 1;
                    }
                    "--model" if idx + 1 < args.len() => {
                        model_path = Some(PathBuf::from(&args[idx + 1]));
                        idx += 1;
                    }
                    "--grammar" if idx + 1 < args.len() => {
                        grammar_path = Some(PathBuf::from(&args[idx + 1]));
                        idx += 1;
                    }
                    "--roots" if idx + 1 < args.len() => {
                        roots_arg = Some(args[idx + 1].as_str());
                        idx += 1;
                    }
                    _ => {}
                }
                idx += 1;
            }

            let backend = if let Some(bin) = llama_bin {
                let model = model_path.unwrap_or_else(|| {
                    ModelManager::default_cache_dir().join("Qwen2.5-1.5B-Instruct.Q4_K_M.gguf")
                });
                LlamaBackend::new_cli(bin, model, grammar_path)
            } else if let Some(model) = model_path {
                let bin = PathBuf::from("llama-cli");
                LlamaBackend::new_cli(bin, model, grammar_path)
            } else {
                let custom_grammar = grammar_path
                    .as_ref()
                    .and_then(|p| fs::read_to_string(p).ok());
                let url = server_url.or_else(|| std::env::var("SILENT_EXEC_SERVER_URL").ok());
                LlamaBackend::new_server(url.as_deref(), custom_grammar)
            };

            let cmd = match backend.infer(user_prompt) {
                Ok(c) => c,
                Err(e) => {
                    eprintln!("Inference error: {}", e);
                    return ExitCode::from(1);
                }
            };

            println!(
                "Inferred ActionCommand:\n{}",
                serde_json::to_string_pretty(&cmd).unwrap()
            );

            let roots = parse_roots(roots_arg);
            let mut indexer = FileIndexer::new(roots);
            let _ = indexer.build_index();

            let executor = PcExecutor::new(indexer, dry_run);
            let result = executor.execute(&cmd, confirm);

            println!(
                "\nExecution Result:\n{}",
                serde_json::to_string_pretty(&result).unwrap()
            );
            ExitCode::SUCCESS
        }
        "quick" => {
            let mut dry_run = false;
            let mut confirm = false;
            let mut server_url = None;
            let mut llama_bin = None;
            let mut model_path = None;
            let mut roots_arg = None;

            let mut idx = 2;
            while idx < args.len() {
                match args[idx].as_str() {
                    "--dry-run" => dry_run = true,
                    "--confirm" => confirm = true,
                    "--server-url" if idx + 1 < args.len() => {
                        server_url = Some(args[idx + 1].clone());
                        idx += 1;
                    }
                    "--llama-bin" if idx + 1 < args.len() => {
                        llama_bin = Some(PathBuf::from(&args[idx + 1]));
                        idx += 1;
                    }
                    "--model" if idx + 1 < args.len() => {
                        model_path = Some(PathBuf::from(&args[idx + 1]));
                        idx += 1;
                    }
                    "--roots" if idx + 1 < args.len() => {
                        roots_arg = Some(args[idx + 1].as_str());
                        idx += 1;
                    }
                    _ => {}
                }
                idx += 1;
            }

            let backend = if let Some(bin) = llama_bin {
                let model = model_path.unwrap_or_else(|| {
                    ModelManager::default_cache_dir().join("Qwen2.5-1.5B-Instruct.Q4_K_M.gguf")
                });
                LlamaBackend::new_cli(bin, model, None)
            } else if let Some(model) = model_path {
                let bin = PathBuf::from("llama-cli");
                LlamaBackend::new_cli(bin, model, None)
            } else {
                let url = server_url.or_else(|| std::env::var("SILENT_EXEC_SERVER_URL").ok());
                LlamaBackend::new_server(url.as_deref(), None)
            };

            let roots = parse_roots(roots_arg);
            let mut indexer = FileIndexer::new(roots);
            let _ = indexer.build_index();
            let executor = PcExecutor::new(indexer, dry_run);

            let stdin = io::stdin();
            for line_res in stdin.lock().lines() {
                let line = match line_res {
                    Ok(l) => l,
                    Err(_) => break,
                };
                let trimmed = line.trim();
                if trimmed.is_empty() {
                    continue;
                }
                if trimmed.eq_ignore_ascii_case("exit") || trimmed.eq_ignore_ascii_case("quit") {
                    break;
                }

                let cmd = match backend.infer(trimmed) {
                    Ok(c) => c,
                    Err(e) => {
                        println!("! inference failed: {}", e);
                        continue;
                    }
                };

                let result = executor.execute(&cmd, confirm);
                match result {
                    ExecutionResult::Success { action, .. }
                    | ExecutionResult::DryRun { action, .. } => {
                        println!("✓ {}", action);
                    }
                    ExecutionResult::ConfirmationRequired { reason, .. } => {
                        println!("? confirm required: {}", reason);
                    }
                    ExecutionResult::Blocked { reason, .. } => {
                        println!("✗ blocked: {}", reason);
                    }
                    ExecutionResult::Error { message, .. } => {
                        println!("! error: {}", message);
                    }
                }
            }
            ExitCode::SUCCESS
        }
        "serve" => {
            let mut host = "0.0.0.0".to_string();
            let mut port = 8765u16;
            let mut token = "silent-exec-secret".to_string();
            let mut dry_run = false;
            let mut server_url = None;
            let mut roots_arg = None;

            let mut idx = 2;
            while idx < args.len() {
                match args[idx].as_str() {
                    "--host" if idx + 1 < args.len() => {
                        host = args[idx + 1].clone();
                        idx += 1;
                    }
                    "--port" if idx + 1 < args.len() => {
                        if let Ok(p) = args[idx + 1].parse::<u16>() {
                            port = p;
                        }
                        idx += 1;
                    }
                    "--token" if idx + 1 < args.len() => {
                        token = args[idx + 1].clone();
                        idx += 1;
                    }
                    "--dry-run" => dry_run = true,
                    "--server-url" if idx + 1 < args.len() => {
                        server_url = Some(args[idx + 1].clone());
                        idx += 1;
                    }
                    "--roots" if idx + 1 < args.len() => {
                        roots_arg = Some(args[idx + 1].as_str());
                        idx += 1;
                    }
                    _ => {}
                }
                idx += 1;
            }

            let roots = parse_roots(roots_arg);
            let config = BridgeConfig {
                host,
                port,
                token,
                dry_run,
                server_url,
                roots,
            };

            println!(
                "Starting SilentExec LAN Bridge on {}:{} (dry_run: {})...",
                config.host, config.port, config.dry_run
            );
            let (addr, handle) = match silent_exec_pc::start_bridge_server(config, None) {
                Ok(res) => res,
                Err(e) => {
                    eprintln!("Failed to start LAN bridge: {}", e);
                    return ExitCode::from(1);
                }
            };
            println!("LAN Bridge listening at http://{}", addr);
            let _ = handle.join();
            ExitCode::SUCCESS
        }
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
            let mut tag = "v0.2.0-model".to_string();
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
