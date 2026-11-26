//! Multi-Step Problem-Solving ReAct Loop for SilentExec Desktop (`v3.1.0`).
//!
//! Implements Observe -> Think -> Act -> Verify (up to 8 turns) with:
//! - Sub-Goal Progress Checklist (`[DONE]` / `[CURRENT]`)
//! - Repeat-Action Ban (blocks identical repeated calls and halts cleanly after 2 recovery turns)
//! - Built-In Pro Tools (`builtin_web_fetch`, `read_file`, `write_file`, `search_files`,
//!   `launch_app`, `open_folder`, `open_url`, `set_volume`, `copy_to_clipboard`, `memory_save`, `reply`)
//! - Real MCP tool discovery and execution

use crate::agent_engine::{AgentTurnDecision, GooseAcpRunner, ToolCallingAgent, ToolLoopStep};
use crate::config::AppConfig;
use crate::executor::{action_type_to_str, ExecutionResult, PcExecutor};
use crate::memory::get_relevant_memories_prompt;
use crate::process_helper::hidden_command;
use serde_json::json;
use silent_exec_core::{ActionCommand, ActionType, Platform, SafetyDecision, SafetyGate};
use std::collections::HashSet;

#[derive(Debug, Clone)]
struct StepRecord {
    action: String,
    target: String,
    succeeded: bool,
}

pub struct PcAgentLoop;

impl PcAgentLoop {
    /// Splits a compound user goal into a lightweight sub-goal checklist (up to 6 items).
    pub fn parse_sub_goals(goal: &str) -> Vec<String> {
        let mut parts: Vec<String> = Vec::new();
        let mut current = String::new();
        let lower = goal.to_lowercase();
        let chars: Vec<char> = goal.chars().collect();
        let lower_chars: Vec<char> = lower.chars().collect();
        let mut i = 0;

        while i < chars.len() {
            let rest_lower: String = lower_chars[i..].iter().collect();
            let sep_len = if rest_lower.starts_with(" then ") {
                Some(6)
            } else if rest_lower.starts_with(" and ") {
                Some(5)
            } else if rest_lower.starts_with(" ثم ") {
                Some(4)
            } else if rest_lower.starts_with(" و ") {
                Some(3)
            } else if chars[i] == ',' || chars[i] == '،' {
                Some(1)
            } else {
                None
            };

            if let Some(len) = sep_len {
                let trimmed = current.trim().to_string();
                if !trimmed.is_empty() {
                    parts.push(trimmed);
                }
                current.clear();
                i += len;
                continue;
            }
            current.push(chars[i]);
            i += 1;
        }

        let tail = current.trim().to_string();
        if !tail.is_empty() {
            parts.push(tail);
        }

        if parts.is_empty() {
            vec![goal.trim().to_string()]
        } else {
            parts.into_iter().take(6).collect()
        }
    }

    pub fn normalize_target(target: Option<&str>) -> String {
        target
            .unwrap_or("")
            .trim()
            .to_lowercase()
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ")
    }

    /// Executes the built-in `web_fetch` tool via hidden `curl` (no visible console window).
    pub fn execute_web_fetch(query_or_url: &str) -> Result<String, String> {
        let trimmed = query_or_url.trim();
        if trimmed.is_empty() {
            return Err("web_fetch target cannot be empty".to_string());
        }

        let url = if trimmed.starts_with("http://") || trimmed.starts_with("https://") {
            trimmed.to_string()
        } else {
            let encoded: String = trimmed
                .bytes()
                .map(|b| match b {
                    b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                        (b as char).to_string()
                    }
                    b' ' => "+".to_string(),
                    other => format!("%{:02X}", other),
                })
                .collect();
            format!("https://duckduckgo.com/html/?q={}", encoded)
        };

        let output = hidden_command("curl")
            .args([
                "-sSfL",
                "--max-time",
                "12",
                "-A",
                "SilentExec/3.1 (Desktop)",
                &url,
            ])
            .output()
            .map_err(|e| format!("Failed to run curl for web_fetch: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "web_fetch failed for '{}': {}",
                url,
                String::from_utf8_lossy(&output.stderr).trim()
            ));
        }

        let html = String::from_utf8_lossy(&output.stdout);
        let stripped = strip_html_to_text(&html);
        let truncated: String = stripped.chars().take(4000).collect();
        if truncated.is_empty() {
            Ok(format!("Fetched {} (empty text body)", url))
        } else {
            Ok(truncated)
        }
    }

    /// Runs the full ReAct loop for the configured backend (Local GGUF / Ollama / OpenAI / Goose).
    pub fn run(
        goal: &str,
        cfg: &AppConfig,
        executor: &PcExecutor,
        dry_run: bool,
        max_steps: usize,
    ) -> Result<(Vec<ToolLoopStep>, String, Option<ActionCommand>), String> {
        if cfg.backend == "goose_acp" {
            let goose_bin = GooseAcpRunner::find_goose_binary().ok_or_else(|| {
                "Goose CLI is not installed. Click 'Install Goose ACP' in Settings.".to_string()
            })?;
            let out = GooseAcpRunner::run_task(
                &goose_bin,
                goal,
                Some(&cfg.goose_provider),
                Some(&cfg.goose_model),
            )?;
            let step = ToolLoopStep {
                step_index: 1,
                thought: Some("Executed via Goose ACP".to_string()),
                action_label: "goose_acp".to_string(),
                details: out.clone(),
                stage: "executed".to_string(),
            };
            return Ok((vec![step], out, None));
        }

        let (endpoint_base, api_key, model_name) = match cfg.backend.as_str() {
            "ollama" => (
                cfg.ollama_url.clone(),
                String::new(),
                cfg.ollama_model.clone(),
            ),
            "openai_compat" => (
                cfg.openai_api_base.clone(),
                cfg.openai_api_key.clone(),
                cfg.openai_api_model.clone(),
            ),
            _ => (
                "http://127.0.0.1:8080".to_string(),
                String::new(),
                "local-gguf".to_string(),
            ),
        };

        let mut discovered = Vec::new();
        for srv in cfg.mcp_servers.iter().filter(|s| s.enabled) {
            let mut client = crate::mcp_bridge::McpClient::new(srv.clone());
            if client.start().is_ok() && client.initialize().is_ok() {
                if let Ok(tools) = client.list_tools() {
                    discovered.push((srv.clone(), tools));
                }
            }
        }

        let mut tool_refs: Vec<(&str, &crate::mcp_bridge::McpTool)> = Vec::new();
        for (srv, tools) in &discovered {
            for t in tools {
                tool_refs.push((srv.name.as_str(), t));
            }
        }

        let tools_schema = ToolCallingAgent::build_tools_schema(&tool_refs);
        let sub_goals = Self::parse_sub_goals(goal);
        let mut completed_sub_goals = 0usize;
        let memory_ctx = get_relevant_memories_prompt(goal, 5);
        let system_prompt = build_pro_system_prompt(&memory_ctx);

        let mut messages = vec![
            json!({ "role": "system", "content": system_prompt }),
            json!({
                "role": "user",
                "content": format_user_turn_prompt(goal, &sub_goals, completed_sub_goals, None)
            }),
        ];

        let mut steps = Vec::new();
        let mut history: Vec<StepRecord> = Vec::new();
        let mut seen_nav: HashSet<(String, String)> = HashSet::new();
        let mut consecutive_recovery_turns = 0usize;
        let limit = max_steps.clamp(1, 8);

        for step_idx in 1..=limit {
            let raw = ToolCallingAgent::run_chat_completion(
                &endpoint_base,
                &api_key,
                &model_name,
                &messages,
                &tools_schema,
            )?;
            let decision = ToolCallingAgent::parse_response(&raw, &tool_refs)?;

            match decision {
                AgentTurnDecision::FinalReply(reply) => {
                    steps.push(ToolLoopStep {
                        step_index: step_idx,
                        thought: None,
                        action_label: "reply".to_string(),
                        details: reply.clone(),
                        stage: "executed".to_string(),
                    });
                    return Ok((steps, reply, None));
                }
                AgentTurnDecision::McpCall {
                    server_name,
                    tool_name,
                    arguments,
                    thought,
                } => {
                    if server_name == "builtin" && tool_name == "web_fetch" {
                        let target = arguments
                            .get("target")
                            .or_else(|| arguments.get("url"))
                            .or_else(|| arguments.get("query"))
                            .and_then(|v| v.as_str())
                            .unwrap_or("");
                        let norm = Self::normalize_target(Some(target));
                        if seen_nav.contains(&("web_fetch".to_string(), norm.clone())) {
                            consecutive_recovery_turns += 1;
                            if consecutive_recovery_turns >= 2 {
                                return Ok((
                                    steps,
                                    "Stopped cleanly: repeated web_fetch blocked twice."
                                        .to_string(),
                                    None,
                                ));
                            }
                            messages.push(json!({
                                "role": "user",
                                "content": "BLOCKED REPEAT: You already fetched that URL/query. Use the observation already in context and call reply or done."
                            }));
                            continue;
                        }
                        seen_nav.insert(("web_fetch".to_string(), norm));
                        let fetched = Self::execute_web_fetch(target);
                        let (ok, out_text) = match fetched {
                            Ok(txt) => (true, txt),
                            Err(err) => (false, err),
                        };
                        steps.push(ToolLoopStep {
                            step_index: step_idx,
                            thought,
                            action_label: "web_fetch".to_string(),
                            details: out_text.clone(),
                            stage: if ok {
                                "executed".to_string()
                            } else {
                                "failed".to_string()
                            },
                        });
                        if ok {
                            consecutive_recovery_turns = 0;
                            if completed_sub_goals < sub_goals.len() {
                                completed_sub_goals += 1;
                            }
                        }
                        messages.push(json!({
                            "role": "assistant",
                            "content": format!("Called web_fetch({})", target)
                        }));
                        messages.push(json!({
                            "role": "user",
                            "content": format!(
                                "Web fetch result (ok={}):\n{}\n\n{}",
                                ok,
                                out_text,
                                format_user_turn_prompt(goal, &sub_goals, completed_sub_goals, None)
                            )
                        }));
                        continue;
                    }

                    let srv = discovered
                        .iter()
                        .map(|(s, _)| s)
                        .find(|s| s.name == server_name)
                        .ok_or_else(|| format!("MCP server '{}' not found", server_name))?;
                    let tool_out = ToolCallingAgent::execute_mcp_tool(
                        srv,
                        &tool_name,
                        arguments.clone(),
                        dry_run,
                    )?;
                    consecutive_recovery_turns = 0;
                    if completed_sub_goals < sub_goals.len() {
                        completed_sub_goals += 1;
                    }
                    steps.push(ToolLoopStep {
                        step_index: step_idx,
                        thought,
                        action_label: format!("mcp:{}/{}", server_name, tool_name),
                        details: tool_out.clone(),
                        stage: "executed".to_string(),
                    });
                    messages.push(json!({
                        "role": "assistant",
                        "content": format!("Called MCP tool {}/{}", server_name, tool_name)
                    }));
                    messages.push(json!({
                        "role": "user",
                        "content": format!("Tool observation:\n{}\n\nContinue or finish with action='reply' or 'done'.", tool_out)
                    }));
                }
                AgentTurnDecision::Action(mut cmd) => {
                    if cmd.platform == Platform::Any {
                        cmd.platform = Platform::Pc;
                    }
                    let action_name = action_type_to_str(cmd.action).to_string();

                    if cmd.action == ActionType::Done || cmd.action == ActionType::Reply {
                        let summary = cmd
                            .target
                            .clone()
                            .or_else(|| cmd.thought.clone())
                            .unwrap_or_else(|| "Task completed".to_string());
                        steps.push(ToolLoopStep {
                            step_index: step_idx,
                            thought: cmd.thought.clone(),
                            action_label: action_name,
                            details: summary.clone(),
                            stage: "executed".to_string(),
                        });
                        return Ok((steps, summary, None));
                    }

                    // Repeat-Action Ban
                    let norm_target = Self::normalize_target(cmd.target.as_deref());
                    let is_nav = matches!(
                        cmd.action,
                        ActionType::LaunchApp | ActionType::OpenFolder | ActionType::OpenUrl
                    );
                    let repeated_nav =
                        is_nav && seen_nav.contains(&(action_name.clone(), norm_target.clone()));
                    let repeated_failed = history.last().is_some_and(|prev| {
                        !prev.succeeded && prev.action == action_name && prev.target == norm_target
                    });

                    if repeated_nav || repeated_failed {
                        consecutive_recovery_turns += 1;
                        if consecutive_recovery_turns >= 2 {
                            let msg = format!(
                                "Stopped cleanly: repeated '{}' action blocked after 2 recovery turns.",
                                action_name
                            );
                            steps.push(ToolLoopStep {
                                step_index: step_idx,
                                thought: cmd.thought.clone(),
                                action_label: action_name,
                                details: msg.clone(),
                                stage: "executed".to_string(),
                            });
                            return Ok((steps, msg, None));
                        }

                        let directive = format!(
                            "BLOCKED REPEAT: Do NOT call {}({}) again. Choose a different tool/action or finish with reply/done.",
                            action_name,
                            cmd.target.as_deref().unwrap_or("")
                        );
                        steps.push(ToolLoopStep {
                            step_index: step_idx,
                            thought: cmd.thought.clone(),
                            action_label: action_name,
                            details: directive.clone(),
                            stage: "blocked".to_string(),
                        });
                        messages.push(json!({
                            "role": "user",
                            "content": format_user_turn_prompt(goal, &sub_goals, completed_sub_goals, Some(&directive))
                        }));
                        continue;
                    }

                    match SafetyGate::evaluate(&cmd) {
                        SafetyDecision::Block { reason } => {
                            steps.push(ToolLoopStep {
                                step_index: step_idx,
                                thought: cmd.thought.clone(),
                                action_label: action_name,
                                details: reason.clone(),
                                stage: "blocked".to_string(),
                            });
                            return Err(format!("Blocked by SafetyGate: {}", reason));
                        }
                        SafetyDecision::RequireConfirm { reason } if !dry_run => {
                            steps.push(ToolLoopStep {
                                step_index: step_idx,
                                thought: cmd.thought.clone(),
                                action_label: action_name,
                                details: reason.clone(),
                                stage: "awaiting_confirm".to_string(),
                            });
                            return Ok((steps, format!("Confirm: {}", reason), Some(cmd)));
                        }
                        _ => {}
                    }

                    let exec_res = executor.execute(&cmd, dry_run);
                    let (ok, msg) = match exec_res {
                        ExecutionResult::Success { details, .. } => (true, details),
                        ExecutionResult::DryRun { message, .. } => (true, message),
                        ExecutionResult::Error { message, .. } => (false, message),
                        ExecutionResult::Blocked { reason, .. } => (false, reason),
                        ExecutionResult::ConfirmationRequired { reason, .. } => (false, reason),
                    };

                    if is_nav && ok {
                        seen_nav.insert((action_name.clone(), norm_target.clone()));
                    }
                    history.push(StepRecord {
                        action: action_name.clone(),
                        target: norm_target,
                        succeeded: ok,
                    });

                    if ok {
                        consecutive_recovery_turns = 0;
                        if completed_sub_goals < sub_goals.len() {
                            completed_sub_goals += 1;
                        }
                    }

                    steps.push(ToolLoopStep {
                        step_index: step_idx,
                        thought: cmd.thought.clone(),
                        action_label: action_name.clone(),
                        details: msg.clone(),
                        stage: if ok {
                            "executed".to_string()
                        } else {
                            "failed".to_string()
                        },
                    });

                    messages.push(json!({
                        "role": "assistant",
                        "content": serde_json::to_string(&cmd).unwrap_or_default()
                    }));
                    messages.push(json!({
                        "role": "user",
                        "content": format!(
                            "Action {} result (ok={}): {}\n\n{}",
                            action_name,
                            ok,
                            msg,
                            format_user_turn_prompt(goal, &sub_goals, completed_sub_goals, None)
                        )
                    }));
                }
            }
        }

        Ok((steps, format!("Completed {} steps", limit), None))
    }
}

fn build_pro_system_prompt(memory_ctx: &str) -> String {
    let base = "You are SilentExec Desktop, a problem-solving Windows AI assistant and ReAct OS agent. \
        You can answer questions, write code, draft documents, and explain errors using action='reply' with rich Markdown in 'target'. \
        You also have built-in tools via execute_pc_action (read_file, write_file, search_files, launch_app, open_folder, open_url, set_volume, copy_to_clipboard, memory_save) and builtin_web_fetch (for web research/search) plus any enabled MCP tools. \
        Reason briefly in 'thought', never repeat an already-executed launch/open action, and emit 'reply' or 'done' when the goal is complete.";
    if memory_ctx.trim().is_empty() {
        base.to_string()
    } else {
        format!("{}\n\n{}", base, memory_ctx.trim())
    }
}

fn format_user_turn_prompt(
    goal: &str,
    sub_goals: &[String],
    completed: usize,
    recovery_directive: Option<&str>,
) -> String {
    let mut out = format!("Goal: {}", goal.trim());
    if sub_goals.len() > 1 {
        let mut lines = Vec::new();
        for (idx, item) in sub_goals.iter().enumerate() {
            let tag = if idx < completed {
                " [DONE]"
            } else if idx == completed {
                " [CURRENT]"
            } else {
                ""
            };
            lines.push(format!("{}. {}{}", idx + 1, item, tag));
        }
        out.push_str(&format!("\n\nProgress Checklist:\n{}", lines.join("\n")));
    }
    if let Some(dir) = recovery_directive {
        out.push_str(&format!("\n\n{}", dir));
    }
    out
}

fn strip_html_to_text(html: &str) -> String {
    let mut out = String::with_capacity(html.len() / 2);
    let mut in_tag = false;
    let mut last_ws = false;
    for ch in html.chars() {
        match ch {
            '<' => in_tag = true,
            '>' => {
                in_tag = false;
                if !last_ws {
                    out.push(' ');
                    last_ws = true;
                }
            }
            c if !in_tag => {
                if c.is_whitespace() {
                    if !last_ws {
                        out.push(' ');
                        last_ws = true;
                    }
                } else {
                    out.push(c);
                    last_ws = false;
                }
            }
            _ => {}
        }
    }
    out.trim().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sub_goal_parsing_arabic_and_english() {
        let goals = PcAgentLoop::parse_sub_goals("Open Chrome and search Rust then summarize");
        assert_eq!(goals.len(), 3);
        assert_eq!(goals[0], "Open Chrome");
        assert_eq!(goals[1], "search Rust");
        assert_eq!(goals[2], "summarize");

        let ar_goals = PcAgentLoop::parse_sub_goals("افتح المجلد ثم اقرأ الملف");
        assert_eq!(ar_goals.len(), 2);
    }

    #[test]
    fn test_strip_html_to_text() {
        let raw =
            "<html><head><title>Test</title></head><body><h1>Hello</h1><p>World</p></body></html>";
        let text = strip_html_to_text(raw);
        assert_eq!(text, "Test Hello World");
    }

    #[test]
    fn test_checklist_prompt_formatting() {
        let sub = vec!["Step A".to_string(), "Step B".to_string()];
        let prompt = format_user_turn_prompt("Do A and B", &sub, 1, Some("BLOCKED REPEAT"));
        assert!(prompt.contains("1. Step A [DONE]"));
        assert!(prompt.contains("2. Step B [CURRENT]"));
        assert!(prompt.contains("BLOCKED REPEAT"));
    }
}
