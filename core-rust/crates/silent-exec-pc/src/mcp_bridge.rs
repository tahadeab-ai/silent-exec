//! MCP JSON-RPC 2.0 Tool Bridge for SilentExec PC
//! Allows registering external MCP (Model Context Protocol) tool servers
//! via stdio JSON-RPC (`initialize`, `tools/list`, `tools/call`) gated by `SafetyGate`.

use crate::config::McpServerConfig;
use crate::executor::ExecutionResult;
use crate::process_helper::hidden_command;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use silent_exec_core::{
    ActionCommand, ActionType, ExecutionMode, Platform, SafetyDecision, SafetyGate,
};
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};

static RPC_ID: AtomicU64 = AtomicU64::new(1);

/// JSON-RPC 2.0 Request envelope.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct JsonRpcRequest {
    pub jsonrpc: String,
    pub id: u64,
    pub method: String,
    pub params: Option<Value>,
}

impl JsonRpcRequest {
    pub fn new(method: &str, params: Option<Value>) -> Self {
        Self {
            jsonrpc: "2.0".to_string(),
            id: RPC_ID.fetch_add(1, Ordering::SeqCst),
            method: method.to_string(),
            params,
        }
    }
}

/// JSON-RPC 2.0 Response envelope.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct JsonRpcResponse {
    pub jsonrpc: String,
    pub id: Option<u64>,
    pub result: Option<Value>,
    pub error: Option<JsonRpcError>,
}

/// JSON-RPC 2.0 Error object.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct JsonRpcError {
    pub code: i64,
    pub message: String,
    pub data: Option<Value>,
}

/// MCP Tool schema representation.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct McpTool {
    pub name: String,
    pub description: Option<String>,
    pub input_schema: Option<Value>,
}

/// MCP Tool call result.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct McpToolCallResult {
    pub content: Vec<Value>,
    pub is_error: bool,
}

/// Client session for an external MCP server running over stdio.
pub struct McpClient {
    pub config: McpServerConfig,
    child: Option<Child>,
}

impl McpClient {
    pub fn new(config: McpServerConfig) -> Self {
        Self {
            config,
            child: None,
        }
    }

    /// Spawns the MCP server child process over stdio.
    pub fn start(&mut self) -> Result<(), String> {
        let mut cmd = hidden_command(&self.config.command);
        cmd.args(&self.config.args)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());

        let child = cmd
            .spawn()
            .map_err(|e| format!("Failed to spawn MCP server '{}': {}", self.config.name, e))?;
        self.child = Some(child);
        Ok(())
    }

    /// Sends a JSON-RPC request line and reads one newline-delimited JSON-RPC response.
    pub fn send_rpc(&mut self, req: &JsonRpcRequest) -> Result<JsonRpcResponse, String> {
        let child = self
            .child
            .as_mut()
            .ok_or_else(|| "MCP server process is not running".to_string())?;

        let stdin = child
            .stdin
            .as_mut()
            .ok_or_else(|| "Failed to get child stdin".to_string())?;

        let req_json = serde_json::to_string(req)
            .map_err(|e| format!("Failed to serialize JSON-RPC request: {}", e))?;

        writeln!(stdin, "{}", req_json)
            .map_err(|e| format!("Failed to write to MCP server stdin: {}", e))?;
        stdin
            .flush()
            .map_err(|e| format!("Failed to flush MCP server stdin: {}", e))?;

        let stdout = child
            .stdout
            .as_mut()
            .ok_or_else(|| "Failed to get child stdout".to_string())?;

        let mut reader = BufReader::new(stdout);
        let mut line = String::new();
        reader
            .read_line(&mut line)
            .map_err(|e| format!("Failed to read MCP server response line: {}", e))?;

        if line.trim().is_empty() {
            return Err("MCP server returned empty response line".to_string());
        }

        let resp: JsonRpcResponse = serde_json::from_str(&line).map_err(|e| {
            format!(
                "Invalid JSON-RPC response from MCP server: {} (line was: '{}')",
                e,
                line.trim()
            )
        })?;

        Ok(resp)
    }

    /// Performs the MCP protocol handshake `initialize`.
    pub fn initialize(&mut self) -> Result<Value, String> {
        let params = serde_json::json!({
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {}
            },
            "clientInfo": {
                "name": "silent-exec-pc",
                "version": env!("CARGO_PKG_VERSION")
            }
        });

        let req = JsonRpcRequest::new("initialize", Some(params));
        let resp = self.send_rpc(&req)?;

        if let Some(err) = resp.error {
            return Err(format!(
                "MCP initialize error (code {}): {}",
                err.code, err.message
            ));
        }

        resp.result
            .ok_or_else(|| "Missing result in initialize response".to_string())
    }

    /// Discovers tools supported by this MCP server via `tools/list`.
    pub fn list_tools(&mut self) -> Result<Vec<McpTool>, String> {
        let req = JsonRpcRequest::new("tools/list", None);
        let resp = self.send_rpc(&req)?;

        if let Some(err) = resp.error {
            return Err(format!(
                "MCP tools/list error (code {}): {}",
                err.code, err.message
            ));
        }

        let result = resp
            .result
            .ok_or_else(|| "Missing result in tools/list response".to_string())?;
        let tools_val = result
            .get("tools")
            .and_then(|t| t.as_array())
            .ok_or_else(|| "tools/list response missing 'tools' array".to_string())?;

        let mut tools = Vec::new();
        for t in tools_val {
            let name = t
                .get("name")
                .and_then(|n| n.as_str())
                .unwrap_or("")
                .to_string();
            let description = t
                .get("description")
                .and_then(|d| d.as_str())
                .map(ToString::to_string);
            let input_schema = t.get("inputSchema").cloned();
            if !name.is_empty() {
                tools.push(McpTool {
                    name,
                    description,
                    input_schema,
                });
            }
        }

        Ok(tools)
    }

    /// Calls an MCP tool (`tools/call`) gated by `SafetyGate`.
    pub fn call_tool(
        &mut self,
        tool_name: &str,
        arguments: Option<Value>,
        mode: ExecutionMode,
    ) -> Result<ExecutionResult, String> {
        // Construct synthetic ActionCommand for SafetyGate evaluation
        let synthetic_cmd = ActionCommand {
            thought: None,
            action: ActionType::LaunchApp,
            target: Some(format!("{}:{}", self.config.name, tool_name)),
            folder: None,
            args: arguments.clone(),
            platform: Platform::Pc,
            confidence: 0.95,
        };

        let decision = SafetyGate::evaluate(&synthetic_cmd);
        match decision {
            SafetyDecision::Block { reason } => {
                return Ok(ExecutionResult::Blocked {
                    reason,
                    action: format!("mcp_tool:{}", tool_name),
                });
            }
            SafetyDecision::RequireConfirm { reason } => {
                if mode != ExecutionMode::DryRun {
                    return Ok(ExecutionResult::ConfirmationRequired {
                        prompt: format!(
                            "Confirm execution of MCP tool '{}': {}",
                            tool_name, reason
                        ),
                        action: format!("mcp_tool:{}", tool_name),
                        confidence: synthetic_cmd.confidence,
                        reason,
                    });
                }
            }
            SafetyDecision::AllowSilent => {}
        }

        if mode == ExecutionMode::DryRun {
            return Ok(ExecutionResult::DryRun {
                action: format!("mcp_tool:{}", tool_name),
                target_resolved: Some(format!("{}:{}", self.config.name, tool_name)),
                command_line: format!("{} {:?}", self.config.command, self.config.args),
                message: format!(
                    "Simulation: Would invoke MCP tool '{}' on server '{}' with args {:?}",
                    tool_name, self.config.name, arguments
                ),
            });
        }

        // Invoke over stdio JSON-RPC
        let params = serde_json::json!({
            "name": tool_name,
            "arguments": arguments.unwrap_or(serde_json::json!({}))
        });

        let req = JsonRpcRequest::new("tools/call", Some(params));
        let resp = self.send_rpc(&req)?;

        if let Some(err) = resp.error {
            return Ok(ExecutionResult::Error {
                action: format!("mcp_tool:{}", tool_name),
                message: format!("MCP error {}: {}", err.code, err.message),
            });
        }

        let res = resp.result.unwrap_or(serde_json::json!({}));
        Ok(ExecutionResult::Success {
            action: format!("mcp_tool:{}", tool_name),
            details: serde_json::to_string(&res).unwrap_or_default(),
            target_resolved: Some(format!("{}:{}", self.config.name, tool_name)),
        })
    }
}
