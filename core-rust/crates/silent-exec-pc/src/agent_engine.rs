//! Real Agent Engine for SilentExec PC & Desktop:
//! 1. Built-in `goose acp` / `goose` CLI integration (`aaif-goose/goose` releases)
//! 2. Standard OpenAI/Gemini/Ollama/llama-server Tool-Calling Loop (`tools` / `tool_calls`)
//!    with real MCP tool execution and deterministic SafetyGate enforcement.

use crate::config::McpServerConfig;
use crate::executor::{action_type_to_str, ExecutionResult, PcExecutor};
use crate::inference::extract_action_json;
use crate::mcp_bridge::{McpClient, McpTool};
use crate::process_helper::hidden_command;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use silent_exec_core::{
    ActionCommand, ActionType, ExecutionMode, Platform, SafetyDecision, SafetyGate,
};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GooseStatus {
    pub installed: bool,
    pub binary_path: Option<String>,
    pub version: Option<String>,
}

pub struct GooseAcpRunner;

impl GooseAcpRunner {
    pub fn bin_dir() -> PathBuf {
        if let Ok(local_app) = std::env::var("LOCALAPPDATA") {
            if !local_app.trim().is_empty() {
                return PathBuf::from(local_app).join("silent-exec").join("bin");
            }
        }
        if let Ok(home) = std::env::var("HOME") {
            if !home.trim().is_empty() {
                return PathBuf::from(home)
                    .join(".local")
                    .join("share")
                    .join("silent-exec")
                    .join("bin");
            }
        }
        std::env::temp_dir().join("silent-exec-bin")
    }

    pub fn find_goose_binary() -> Option<PathBuf> {
        let dir = Self::bin_dir();
        let candidates = if cfg!(target_os = "windows") {
            vec!["goose.exe", "goose-acp.exe"]
        } else {
            vec!["goose", "goose-acp"]
        };

        for name in &candidates {
            let p = dir.join(name);
            if p.is_file() {
                return Some(p);
            }
        }

        let locator = if cfg!(target_os = "windows") {
            "where.exe"
        } else {
            "which"
        };
        if let Ok(out) = hidden_command(locator).arg("goose").output() {
            if out.status.success() {
                let first = String::from_utf8_lossy(&out.stdout)
                    .lines()
                    .next()
                    .unwrap_or("")
                    .trim()
                    .to_string();
                if !first.is_empty() {
                    return Some(PathBuf::from(first));
                }
            }
        }
        None
    }

    pub fn status() -> GooseStatus {
        match Self::find_goose_binary() {
            Some(path) => {
                let version = hidden_command(&path.to_string_lossy())
                    .arg("--version")
                    .output()
                    .ok()
                    .filter(|o| o.status.success())
                    .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
                    .filter(|s| !s.is_empty());
                GooseStatus {
                    installed: true,
                    binary_path: Some(path.to_string_lossy().to_string()),
                    version,
                }
            }
            None => GooseStatus {
                installed: false,
                binary_path: None,
                version: None,
            },
        }
    }

    pub fn install_from_github() -> Result<PathBuf, String> {
        let api_url = "https://api.github.com/repos/aaif-goose/goose/releases/latest";
        let output = hidden_command("curl")
            .args([
                "-sSfL",
                "-H",
                "User-Agent: silent-exec-desktop",
                "-H",
                "Accept: application/vnd.github.v3+json",
                api_url,
            ])
            .output()
            .map_err(|e| format!("Failed to query GitHub releases: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "GitHub API request failed: {}",
                String::from_utf8_lossy(&output.stderr)
            ));
        }

        let release: Value = serde_json::from_slice(&output.stdout)
            .map_err(|e| format!("Invalid GitHub release JSON: {}", e))?;

        let assets = release
            .get("assets")
            .and_then(|v| v.as_array())
            .ok_or_else(|| "No release assets found in aaif-goose/goose".to_string())?;

        let asset_url = assets
            .iter()
            .filter_map(|a| {
                let name = a.get("name")?.as_str()?;
                let url = a.get("browser_download_url")?.as_str()?;
                if cfg!(target_os = "windows") {
                    if name.contains("x86_64-pc-windows-msvc")
                        && (name.ends_with(".zip") || name.ends_with(".exe"))
                    {
                        return Some((name.to_string(), url.to_string()));
                    }
                } else if name.contains("x86_64-unknown-linux-gnu") && name.ends_with(".tar.gz") {
                    return Some((name.to_string(), url.to_string()));
                }
                None
            })
            .next()
            .ok_or_else(|| {
                "No compatible goose Windows/Linux x86_64 release asset found".to_string()
            })?;

        let bin_dir = Self::bin_dir();
        fs::create_dir_all(&bin_dir)
            .map_err(|e| format!("Failed to create goose bin dir: {}", e))?;

        let archive_path = std::env::temp_dir().join(&asset_url.0);
        let dl = hidden_command("curl")
            .args(["-sSfL", "-o", &archive_path.to_string_lossy(), &asset_url.1])
            .output()
            .map_err(|e| format!("Failed to download goose: {}", e))?;

        if !dl.status.success() {
            return Err(format!(
                "Failed to download goose asset: {}",
                String::from_utf8_lossy(&dl.stderr)
            ));
        }

        if asset_url.0.ends_with(".zip") {
            let ps = format!(
                "Expand-Archive -Path '{}' -DestinationPath '{}' -Force",
                archive_path.to_string_lossy(),
                bin_dir.to_string_lossy()
            );
            let ex = hidden_command("powershell")
                .args(["-NoProfile", "-Command", &ps])
                .output()
                .map_err(|e| format!("Failed to extract goose zip: {}", e))?;
            if !ex.status.success() {
                return Err(format!(
                    "Archive extraction failed: {}",
                    String::from_utf8_lossy(&ex.stderr)
                ));
            }
            let _ = fs::remove_file(&archive_path);
        } else if asset_url.0.ends_with(".exe") {
            let dest = bin_dir.join("goose.exe");
            fs::rename(&archive_path, &dest)
                .map_err(|e| format!("Failed to install goose.exe: {}", e))?;
        }

        Self::find_goose_binary()
            .ok_or_else(|| "Goose binary was not found after extraction".to_string())
    }

    pub fn run_task(
        bin_path: &Path,
        prompt: &str,
        provider: Option<&str>,
        model: Option<&str>,
    ) -> Result<String, String> {
        let mut cmd = hidden_command(&bin_path.to_string_lossy());
        cmd.args(["run", "--text", prompt]);
        if let Some(p) = provider.filter(|s| !s.trim().is_empty()) {
            cmd.env("GOOSE_PROVIDER", p.trim());
        }
        if let Some(m) = model.filter(|s| !s.trim().is_empty()) {
            cmd.env("GOOSE_MODEL", m.trim());
        }

        let output = cmd
            .output()
            .map_err(|e| format!("Failed to launch goose: {}", e))?;

        let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
        let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();

        if !output.status.success() {
            let err_msg = if !stderr.is_empty() { stderr } else { stdout };
            return Err(format!("goose exited with error: {}", err_msg));
        }

        Ok(if !stdout.is_empty() {
            stdout
        } else {
            "Task completed via Goose ACP".to_string()
        })
    }
}

#[derive(Debug, Clone)]
pub enum AgentTurnDecision {
    Action(ActionCommand),
    McpCall {
        server_name: String,
        tool_name: String,
        arguments: Value,
        thought: Option<String>,
    },
    FinalReply(String),
}

#[derive(Debug, Clone)]
pub struct ToolLoopStep {
    pub step_index: usize,
    pub thought: Option<String>,
    pub action_label: String,
    pub details: String,
    pub stage: String,
}

pub struct ToolCallingAgent;

impl ToolCallingAgent {
    pub fn build_tools_schema(mcp_tools: &[(&str, &McpTool)]) -> Value {
        let mut tools = vec![json!({
            "type": "function",
            "function": {
                "name": "execute_pc_action",
                "description": "Execute a single Windows PC action or return final answer with action='done' or 'reply'.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "thought": {
                            "type": "string",
                            "description": "Brief 1-sentence reasoning about the current goal and next step."
                        },
                        "action": {
                            "type": "string",
                            "enum": [
                                "open_folder", "open_file", "launch_app", "search_files",
                                "set_volume", "set_brightness", "create_note", "copy_to_clipboard",
                                "delete_file", "move_file", "system_power", "open_url",
                                "ui_click", "ui_type", "ui_scroll", "read_file",
                                "write_file", "memory_save", "reply", "done"
                            ]
                        },
                        "target": { "type": "string" },
                        "folder": { "type": "string" },
                        "args": { "type": "object" },
                        "platform": { "type": "string", "enum": ["pc", "any"] },
                        "confidence": { "type": "number" }
                    },
                    "required": ["thought", "action", "platform", "confidence"]
                }
            }
        })];

        for (server_name, tool) in mcp_tools {
            let fn_name = format!(
                "mcp__{}__{}",
                sanitize_ident(server_name),
                sanitize_ident(&tool.name)
            );
            let params = tool
                .input_schema
                .clone()
                .unwrap_or_else(|| json!({ "type": "object", "properties": {} }));
            tools.push(json!({
                "type": "function",
                "function": {
                    "name": fn_name,
                    "description": tool.description.clone().unwrap_or_else(|| format!("MCP tool {} on {}", tool.name, server_name)),
                    "parameters": params
                }
            }));
        }

        Value::Array(tools)
    }

    pub fn build_chat_request(model: &str, messages: &[Value], tools: &Value) -> Value {
        json!({
            "model": model,
            "messages": messages,
            "tools": tools,
            "tool_choice": "auto",
            "temperature": 0.0
        })
    }

    pub fn parse_response(
        response_json: &str,
        mcp_lookup: &[(&str, &McpTool)],
    ) -> Result<AgentTurnDecision, String> {
        let root: Value =
            serde_json::from_str(response_json).map_err(|e| format!("Invalid API JSON: {}", e))?;

        let msg = root
            .get("choices")
            .and_then(|c| c.as_array())
            .and_then(|arr| arr.first())
            .and_then(|c| c.get("message"))
            .ok_or_else(|| "Missing choices[0].message in API response".to_string())?;

        if let Some(tool_calls) = msg.get("tool_calls").and_then(|t| t.as_array()) {
            if let Some(first_call) = tool_calls.first() {
                let fn_obj = first_call
                    .get("function")
                    .ok_or_else(|| "Missing function in tool_call".to_string())?;
                let name = fn_obj
                    .get("name")
                    .and_then(|n| n.as_str())
                    .unwrap_or("execute_pc_action");
                let args_val = match fn_obj.get("arguments") {
                    Some(Value::String(s)) => s.clone(),
                    Some(obj @ Value::Object(_)) => obj.to_string(),
                    _ => "{}".to_string(),
                };

                if name == "execute_pc_action" {
                    let cmd = ActionCommand::from_llm_json(&args_val)?;
                    return Ok(AgentTurnDecision::Action(cmd));
                }

                if name.starts_with("mcp__") {
                    for (srv, tool) in mcp_lookup {
                        let expected = format!(
                            "mcp__{}__{}",
                            sanitize_ident(srv),
                            sanitize_ident(&tool.name)
                        );
                        if expected == name {
                            let parsed_args: Value =
                                serde_json::from_str(&args_val).unwrap_or_else(|_| json!({}));
                            let thought = msg
                                .get("content")
                                .and_then(|c| c.as_str())
                                .map(|s| s.to_string());
                            return Ok(AgentTurnDecision::McpCall {
                                server_name: (*srv).to_string(),
                                tool_name: tool.name.clone(),
                                arguments: parsed_args,
                                thought,
                            });
                        }
                    }
                }
            }
        }

        let content = msg
            .get("content")
            .and_then(|c| c.as_str())
            .unwrap_or("")
            .trim();
        if content.is_empty() {
            return Err("Empty model response".to_string());
        }

        if let Ok(json_str) = extract_action_json(content) {
            if let Ok(cmd) = ActionCommand::from_llm_json(&json_str) {
                return Ok(AgentTurnDecision::Action(cmd));
            }
        }

        Ok(AgentTurnDecision::FinalReply(content.to_string()))
    }

    pub fn run_chat_completion(
        endpoint_base: &str,
        api_key: &str,
        model: &str,
        messages: &[Value],
        tools: &Value,
    ) -> Result<String, String> {
        let base = endpoint_base.trim().trim_end_matches('/');
        let url = if base.ends_with("/chat/completions") {
            base.to_string()
        } else if base.ends_with("/v1") {
            format!("{}/chat/completions", base)
        } else {
            format!("{}/v1/chat/completions", base)
        };

        let payload = Self::build_chat_request(model, messages, tools);
        let payload_str =
            serde_json::to_string(&payload).map_err(|e| format!("Serialize error: {}", e))?;

        let mut cmd = hidden_command("curl");
        cmd.args(["-sSf", "-X", "POST", "-H", "Content-Type: application/json"]);
        if !api_key.trim().is_empty() {
            cmd.arg("-H")
                .arg(format!("Authorization: Bearer {}", api_key.trim()));
        }
        cmd.arg("-d").arg(&payload_str).arg(&url);

        let output = cmd
            .output()
            .map_err(|e| format!("Failed to invoke curl: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "LLM endpoint error ({}): {}",
                url,
                String::from_utf8_lossy(&output.stderr).trim()
            ));
        }

        Ok(String::from_utf8_lossy(&output.stdout).into_owned())
    }

    pub fn execute_mcp_tool(
        server: &McpServerConfig,
        tool_name: &str,
        arguments: Value,
        dry_run: bool,
    ) -> Result<String, String> {
        let mut client = McpClient::new(server.clone());
        client.start()?;
        let _ = client.initialize();
        let mode = if dry_run {
            ExecutionMode::DryRun
        } else {
            ExecutionMode::Silent
        };
        let res = client.call_tool(tool_name, Some(arguments), mode)?;
        match res {
            ExecutionResult::Success { details, .. } => Ok(details),
            ExecutionResult::DryRun { message, .. } => Ok(message),
            ExecutionResult::Error { message, .. } => Err(message),
            ExecutionResult::Blocked { reason, .. } => Err(reason),
            ExecutionResult::ConfirmationRequired { reason, .. } => Err(reason),
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub fn run_tool_loop(
        goal: &str,
        endpoint_base: &str,
        api_key: &str,
        model: &str,
        mcp_servers: &[McpServerConfig],
        executor: &PcExecutor,
        dry_run: bool,
        max_steps: usize,
    ) -> Result<(Vec<ToolLoopStep>, String, Option<ActionCommand>), String> {
        // Discover real tools from enabled MCP servers
        let mut discovered: Vec<(McpServerConfig, Vec<McpTool>)> = Vec::new();
        for srv in mcp_servers.iter().filter(|s| s.enabled) {
            let mut client = McpClient::new(srv.clone());
            if client.start().is_ok() && client.initialize().is_ok() {
                if let Ok(tools) = client.list_tools() {
                    discovered.push((srv.clone(), tools));
                }
            }
        }

        let mut tool_refs: Vec<(&str, &McpTool)> = Vec::new();
        for (srv, tools) in &discovered {
            for t in tools {
                tool_refs.push((srv.name.as_str(), t));
            }
        }

        let tools_schema = Self::build_tools_schema(&tool_refs);
        let mut messages = vec![
            json!({
                "role": "system",
                "content": "You are SilentExec Desktop Agent on Windows. Use execute_pc_action or available MCP tools step-by-step. Emit action='done' or 'reply' when complete."
            }),
            json!({
                "role": "user",
                "content": goal
            }),
        ];

        let mut steps = Vec::new();
        for step_idx in 1..=max_steps.max(1) {
            let raw =
                Self::run_chat_completion(endpoint_base, api_key, model, &messages, &tools_schema)?;
            let decision = Self::parse_response(&raw, &tool_refs)?;

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
                    let srv = discovered
                        .iter()
                        .map(|(s, _)| s)
                        .find(|s| s.name == server_name)
                        .ok_or_else(|| format!("MCP server '{}' not found", server_name))?;
                    let tool_out =
                        Self::execute_mcp_tool(srv, &tool_name, arguments.clone(), dry_run)?;
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
                        "content": format!("Tool observation:\n{}\nContinue or finish with action='done'.", tool_out)
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
                        "content": format!("Action {} result (ok={}): {}. Next step or action='done'.", action_name, ok, msg)
                    }));
                }
            }
        }

        Ok((steps, format!("Completed {} steps", max_steps), None))
    }
}

fn sanitize_ident(s: &str) -> String {
    s.chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '_' {
                c
            } else {
                '_'
            }
        })
        .collect()
}
