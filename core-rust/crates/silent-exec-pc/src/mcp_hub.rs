//! Official Rust MCP Host (`rmcp`) + `CursorTouch/Windows-MCP` Sidecar Manager.
//! Manages multiple MCP servers concurrently over stdio (`TokioChildProcess`),
//! discovers tools dynamically via `list_tools`, and routes tool calls via `call_tool`.
//! All internal diagnostics log strictly to `stderr` (`eprintln!`) to protect JSON-RPC `stdout` framing.

use crate::config::{AppConfig, McpServerConfig};
use crate::process_helper::hidden_command;
use rmcp::{
    model::CallToolRequestParams,
    service::{RoleClient, RunningService, ServiceExt},
    transport::{ConfigureCommandExt, TokioChildProcess},
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;
use tokio::process::Command;
use tokio::sync::Mutex;

pub const WINDOWS_MCP_STARTUP_TIMEOUT_SECS: u64 = 90;
pub const WINDOWS_MCP_EXCLUDE_PROCESSES_ENV: &str = "SilentExec.exe,silent-exec-desktop.exe";
pub const WINDOWS_MCP_UIA_TIMEOUT_MS: &str = "2000";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct McpHubServerEntry {
    pub name: String,
    pub command: String,
    #[serde(default)]
    pub args: Vec<String>,
    #[serde(default)]
    pub env: HashMap<String, String>,
    #[serde(default = "default_true")]
    pub enabled: bool,
    #[serde(default)]
    pub description: String,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct McpHubTool {
    pub server_name: String,
    pub name: String,
    pub description: Option<String>,
    pub input_schema: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum McpToolOutcome {
    Success(Value),
    HumanTakeoverRequired { code: String, reason: String },
    Error(String),
}

pub struct McpHub {
    config_path: PathBuf,
    entries: Arc<Mutex<Vec<McpHubServerEntry>>>,
    connections: Arc<Mutex<HashMap<String, RunningService<RoleClient, ()>>>>,
}

impl McpHub {
    /// Creates a new `McpHub` backed by `%LOCALAPPDATA%/silent-exec/mcp_servers.json` (or `~/.config/silent-exec/mcp_servers.json`).
    pub fn new_default(safe_mode: bool) -> Self {
        let config_path = AppConfig::default_config_dir().join("mcp_servers.json");
        let entries = Self::load_or_init_entries(&config_path, safe_mode);
        Self {
            config_path,
            entries: Arc::new(Mutex::new(entries)),
            connections: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub fn with_config_path(config_path: PathBuf, safe_mode: bool) -> Self {
        let entries = Self::load_or_init_entries(&config_path, safe_mode);
        Self {
            config_path,
            entries: Arc::new(Mutex::new(entries)),
            connections: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Detects pre-installed `windows-mcp.exe` or bundled `--onedir` PyInstaller binary;
    /// falls back to `uvx windows-mcp serve`. Never uses `--onefile`.
    #[must_use]
    pub fn detect_windows_mcp_command() -> (String, Vec<String>) {
        if let Ok(bin_dir) = std::env::var("SILENT_EXEC_BIN_DIR") {
            let base = PathBuf::from(bin_dir);
            let onedir_exe = base.join("windows-mcp").join("windows-mcp.exe");
            if onedir_exe.is_file() {
                return (
                    onedir_exe.to_string_lossy().to_string(),
                    vec!["serve".to_string()],
                );
            }
            let direct_exe = base.join("windows-mcp.exe");
            if direct_exe.is_file() {
                return (
                    direct_exe.to_string_lossy().to_string(),
                    vec!["serve".to_string()],
                );
            }
        }

        let locator = if cfg!(windows) { "where.exe" } else { "which" };
        if let Ok(out) = hidden_command(locator).arg("windows-mcp").output() {
            if out.status.success() {
                if let Some(first) = String::from_utf8_lossy(&out.stdout)
                    .lines()
                    .next()
                    .map(str::trim)
                    .filter(|s| !s.is_empty())
                {
                    return (first.to_string(), vec!["serve".to_string()]);
                }
            }
        }

        (
            "uvx".to_string(),
            vec!["windows-mcp".to_string(), "serve".to_string()],
        )
    }

    /// Builds `Windows-MCP` server entry with all required safety & telemetry env vars
    /// and Safe Mode `--exclude-tools PowerShell,Registry` when `safe_mode == true`.
    #[must_use]
    pub fn build_windows_mcp_entry(safe_mode: bool) -> McpHubServerEntry {
        let (command, mut args) = Self::detect_windows_mcp_command();
        if safe_mode {
            args.push("--exclude-tools".to_string());
            args.push("PowerShell,Registry".to_string());
        }
        let mut env = HashMap::new();
        env.insert(
            "WINDOWS_MCP_EXCLUDE_PROCESSES".to_string(),
            WINDOWS_MCP_EXCLUDE_PROCESSES_ENV.to_string(),
        );
        env.insert("ANONYMIZED_TELEMETRY".to_string(), "false".to_string());
        env.insert(
            "WINDOWS_MCP_UIA_CONNECTION_TIMEOUT_MS".to_string(),
            WINDOWS_MCP_UIA_TIMEOUT_MS.to_string(),
        );
        McpHubServerEntry {
            name: "windows-mcp".to_string(),
            command,
            args,
            env,
            enabled: true,
            description: "CursorTouch/Windows-MCP (MIT) — Desktop UIA & Input Automation"
                .to_string(),
        }
    }

    /// Returns default `mcp_servers.json` entries (Windows-MCP + toggleable Playwright MCP & Mobile MCP).
    #[must_use]
    pub fn default_entries(safe_mode: bool) -> Vec<McpHubServerEntry> {
        vec![
            Self::build_windows_mcp_entry(safe_mode),
            McpHubServerEntry {
                name: "playwright-mcp".to_string(),
                command: "npx".to_string(),
                args: vec!["-y".to_string(), "@playwright/mcp@latest".to_string()],
                env: HashMap::new(),
                enabled: false,
                description: "Playwright MCP (Apache-2.0) — Fast DOM-based Browser Automation"
                    .to_string(),
            },
            McpHubServerEntry {
                name: "mobile-mcp".to_string(),
                command: "npx".to_string(),
                args: vec![
                    "-y".to_string(),
                    "@mobilenext/mobile-mcp@latest".to_string(),
                ],
                env: HashMap::new(),
                enabled: false,
                description: "Mobile MCP (Apache-2.0) — Control USB/Wi-Fi Android Phones"
                    .to_string(),
            },
        ]
    }

    pub fn load_or_init_entries(path: &Path, safe_mode: bool) -> Vec<McpHubServerEntry> {
        if path.is_file() {
            if let Ok(content) = fs::read_to_string(path) {
                if let Ok(mut parsed) = serde_json::from_str::<Vec<McpHubServerEntry>>(&content) {
                    if !parsed.is_empty() {
                        // Ensure Windows-MCP respects current safe_mode toggle
                        if let Some(win) = parsed.iter_mut().find(|e| e.name == "windows-mcp") {
                            *win = Self::build_windows_mcp_entry(safe_mode);
                        }
                        return parsed;
                    }
                }
            }
        }
        let defaults = Self::default_entries(safe_mode);
        let _ = Self::save_entries_to(path, &defaults);
        defaults
    }

    fn save_entries_to(path: &Path, entries: &[McpHubServerEntry]) -> Result<(), String> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed to create MCP config dir: {}", e))?;
        }
        let json = serde_json::to_string_pretty(entries)
            .map_err(|e| format!("Failed to serialize mcp_servers.json: {}", e))?;
        fs::write(path, json).map_err(|e| format!("Failed to write mcp_servers.json: {}", e))
    }

    pub async fn list_entries(&self) -> Vec<McpHubServerEntry> {
        self.entries.lock().await.clone()
    }

    pub async fn toggle_server(
        &self,
        name: &str,
        enabled: bool,
    ) -> Result<Vec<McpHubServerEntry>, String> {
        let mut guard = self.entries.lock().await;
        let entry = guard
            .iter_mut()
            .find(|e| e.name.eq_ignore_ascii_case(name.trim()))
            .ok_or_else(|| format!("MCP server '{}' not found", name))?;
        entry.enabled = enabled;
        let snapshot = guard.clone();
        Self::save_entries_to(&self.config_path, &snapshot)?;
        drop(guard);

        if !enabled {
            self.stop_server(name).await;
        }
        Ok(snapshot)
    }

    pub async fn start_server(&self, name: &str) -> Result<(), String> {
        let entry = {
            let guard = self.entries.lock().await;
            guard
                .iter()
                .find(|e| e.name.eq_ignore_ascii_case(name.trim()))
                .cloned()
                .ok_or_else(|| format!("MCP server '{}' not found", name))?
        };
        if !entry.enabled {
            return Err(format!("MCP server '{}' is disabled", entry.name));
        }

        let cmd = build_tokio_mcp_command(&entry);
        let transport = TokioChildProcess::new(cmd)
            .map_err(|e| format!("Failed to spawn MCP server '{}': {}", entry.name, e))?;

        let timeout = Duration::from_secs(WINDOWS_MCP_STARTUP_TIMEOUT_SECS);
        let service = tokio::time::timeout(timeout, ().serve(transport))
            .await
            .map_err(|_| {
                format!(
                    "MCP server '{}' handshake timed out after {}s",
                    entry.name, WINDOWS_MCP_STARTUP_TIMEOUT_SECS
                )
            })?
            .map_err(|e| format!("MCP server '{}' handshake failed: {}", entry.name, e))?;

        eprintln!("[McpHub] Connected to MCP server '{}'", entry.name);
        let mut conns = self.connections.lock().await;
        if let Some(old) = conns.remove(&entry.name) {
            let _ = old.cancel().await;
        }
        conns.insert(entry.name, service);
        Ok(())
    }

    pub async fn stop_server(&self, name: &str) {
        let mut conns = self.connections.lock().await;
        if let Some(service) = conns.remove(name) {
            eprintln!("[McpHub] Stopping MCP server '{}'", name);
            let _ = service.cancel().await;
        }
    }

    pub async fn restart_server(&self, name: &str) -> Result<(), String> {
        self.stop_server(name).await;
        self.start_server(name).await
    }

    pub async fn ensure_enabled_servers_started(&self) {
        let enabled_names: Vec<String> = {
            let guard = self.entries.lock().await;
            guard
                .iter()
                .filter(|e| e.enabled)
                .map(|e| e.name.clone())
                .collect()
        };
        for name in enabled_names {
            let already_connected = {
                let conns = self.connections.lock().await;
                conns.contains_key(&name)
            };
            if !already_connected {
                if let Err(err) = self.start_server(&name).await {
                    eprintln!("[McpHub] Warning starting '{}': {}", name, err);
                }
            }
        }
    }

    /// Discovers tools dynamically across all connected MCP servers via `list_tools`.
    pub async fn list_tools(&self) -> Vec<McpHubTool> {
        let mut all_tools = Vec::new();
        let conns = self.connections.lock().await;
        for (server_name, service) in conns.iter() {
            match service.list_tools(Default::default()).await {
                Ok(resp) => {
                    for tool in resp.tools {
                        let schema_val = serde_json::to_value(&tool.input_schema)
                            .unwrap_or_else(|_| json!({ "type": "object", "properties": {} }));
                        all_tools.push(McpHubTool {
                            server_name: server_name.clone(),
                            name: tool.name.to_string(),
                            description: tool.description.map(|d| d.to_string()),
                            input_schema: schema_val,
                        });
                    }
                }
                Err(err) => {
                    eprintln!("[McpHub] list_tools failed for '{}': {}", server_name, err);
                }
            }
        }
        all_tools
    }

    /// Converts discovered MCP tools into OpenAI/Ollama `tools` JSON schemas.
    #[must_use]
    pub fn to_openai_tools_json(tools: &[McpHubTool]) -> Vec<Value> {
        tools
            .iter()
            .map(|t| {
                json!({
                    "type": "function",
                    "function": {
                        "name": t.name,
                        "description": t.description.clone().unwrap_or_default(),
                        "parameters": t.input_schema
                    }
                })
            })
            .collect()
    }

    /// Routes an LLM tool call to the matching MCP server and handles `USER_CONTROL` / `CONTROL_DENIED` gracefully.
    pub async fn call_tool(
        &self,
        server_name: &str,
        tool_name: &str,
        arguments: Option<serde_json::Map<String, Value>>,
    ) -> McpToolOutcome {
        let conns = self.connections.lock().await;
        let Some(service) = conns.get(server_name) else {
            return McpToolOutcome::Error(format!("MCP server '{}' is not connected", server_name));
        };

        let param = CallToolRequestParams {
            meta: None,
            name: tool_name.to_string().into(),
            arguments,
            task: None,
        };
        match service.call_tool(param).await {
            Ok(result) => {
                let raw = serde_json::to_value(&result).unwrap_or(Value::Null);
                let text_blob = raw.to_string();
                if let Some(outcome) = classify_takeover_response(&text_blob) {
                    return outcome;
                }
                if result.is_error == Some(true) {
                    McpToolOutcome::Error(text_blob)
                } else {
                    McpToolOutcome::Success(raw)
                }
            }
            Err(err) => McpToolOutcome::Error(format!("call_tool '{}' failed: {}", tool_name, err)),
        }
    }

    /// Gracefully shuts down all spawned MCP child processes (called on `RunEvent::Exit`).
    pub async fn shutdown_all(&self) {
        let mut conns = self.connections.lock().await;
        for (name, service) in conns.drain() {
            eprintln!("[McpHub] Shutting down MCP server '{}'", name);
            let _ = service.cancel().await;
        }
    }
}

/// Classifies `USER_CONTROL` / `CONTROL_DENIED` tool responses into `HumanTakeoverRequired`.
#[must_use]
pub fn classify_takeover_response(raw_text: &str) -> Option<McpToolOutcome> {
    if raw_text.contains("USER_CONTROL") {
        return Some(McpToolOutcome::HumanTakeoverRequired {
            code: "USER_CONTROL".to_string(),
            reason: "Windows-MCP reported USER_CONTROL: human input or protected window active."
                .to_string(),
        });
    }
    if raw_text.contains("CONTROL_DENIED") {
        return Some(McpToolOutcome::HumanTakeoverRequired {
            code: "CONTROL_DENIED".to_string(),
            reason:
                "Windows-MCP reported CONTROL_DENIED: elevated/UAC prompt requires user control."
                    .to_string(),
        });
    }
    None
}

fn build_tokio_mcp_command(entry: &McpHubServerEntry) -> Command {
    #[cfg(windows)]
    let mut cmd = {
        let cmd_lower = entry.command.trim().to_ascii_lowercase();
        let needs_cmd_shim = matches!(cmd_lower.as_str(), "npx" | "uvx" | "npm")
            || cmd_lower.ends_with(".cmd")
            || cmd_lower.ends_with(".bat");
        if needs_cmd_shim {
            Command::new("cmd").configure(|c| {
                c.arg("/C").arg(entry.command.trim()).args(&entry.args);
                c.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
            })
        } else {
            Command::new(entry.command.trim()).configure(|c| {
                c.args(&entry.args);
                c.creation_flags(0x0800_0000);
            })
        }
    };

    #[cfg(not(windows))]
    let mut cmd = Command::new(entry.command.trim()).configure(|c| {
        c.args(&entry.args);
    });

    for (k, v) in &entry.env {
        cmd.env(k, v);
    }
    cmd
}

/// Converts a `McpHubServerEntry` to legacy `McpServerConfig` DTO compatibility.
impl From<&McpHubServerEntry> for McpServerConfig {
    fn from(value: &McpHubServerEntry) -> Self {
        Self {
            name: value.name.clone(),
            command: value.command.clone(),
            args: value.args.clone(),
            enabled: value.enabled,
        }
    }
}
