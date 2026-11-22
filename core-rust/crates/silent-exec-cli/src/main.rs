use serde::Serialize;
use silent_exec_core::{ActionCommand, SafetyDecision, SafetyGate};
use std::env;
use std::io::{self, BufRead, Write};

#[derive(Debug, Serialize)]
struct EvalResult {
    valid: bool,
    decision: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    reason: Option<String>,
}

fn process_eval_stdin() {
    let stdin = io::stdin();
    let stdout = io::stdout();
    let mut handle = stdout.lock();

    for line_result in stdin.lock().lines() {
        let line = match line_result {
            Ok(l) => l,
            Err(e) => {
                let err_out = EvalResult {
                    valid: false,
                    decision: "parse_error",
                    reason: Some(format!("IO read error: {e}")),
                };
                let _ = writeln!(
                    handle,
                    "{}",
                    serde_json::to_string(&err_out).unwrap_or_default()
                );
                continue;
            }
        };

        let trimmed = line.trim().trim_start_matches('\u{feff}');
        if trimmed.is_empty() {
            continue;
        }

        let eval_res = match ActionCommand::from_json(trimmed) {
            Err(err_msg) => EvalResult {
                valid: false,
                decision: "parse_error",
                reason: Some(err_msg),
            },
            Ok(cmd) => match SafetyGate::evaluate(&cmd) {
                SafetyDecision::AllowSilent => EvalResult {
                    valid: true,
                    decision: "allow_silent",
                    reason: None,
                },
                SafetyDecision::RequireConfirm { reason } => EvalResult {
                    valid: true,
                    decision: "require_confirm",
                    reason: Some(reason),
                },
                SafetyDecision::Block { reason } => EvalResult {
                    valid: true,
                    decision: "block",
                    reason: Some(reason),
                },
            },
        };

        if let Ok(serialized) = serde_json::to_string(&eval_res) {
            let _ = writeln!(handle, "{serialized}");
        }
    }
    let _ = handle.flush();
}

fn main() {
    let args: Vec<String> = env::args().collect();

    if args.iter().any(|arg| arg == "--eval-stdin") {
        process_eval_stdin();
    } else {
        println!("SilentExec CLI");
        println!("Usage: silent-exec-cli --eval-stdin");
        println!("Reads ActionCommand JSON lines from stdin and outputs evaluation results.");
    }
}
