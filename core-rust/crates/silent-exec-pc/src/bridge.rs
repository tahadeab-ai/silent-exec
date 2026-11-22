//! Authenticated LAN Bridge HTTP Server for SilentExec PC
//! Zero-bloat standard-library HTTP server allowing authorized LAN clients (such as Android device agents)
//! to send action commands or natural language prompts and receive execution results over HTTP.
//!
//! Endpoints:
//! - GET /health -> {"status":"ok","version":"0.1.0"}
//! - POST /v1/execute (Authorization: Bearer <TOKEN>) -> ExecutionResult JSON

use crate::executor::PcExecutor;
use crate::indexer::FileIndexer;
use crate::inference::LlamaBackend;
use serde::{Deserialize, Serialize};
use silent_exec_core::ActionCommand;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::Duration;

#[derive(Debug, Clone)]
pub struct BridgeConfig {
    pub host: String,
    pub port: u16,
    pub token: String,
    pub dry_run: bool,
    pub server_url: Option<String>,
    pub roots: Vec<PathBuf>,
}

impl Default for BridgeConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".to_string(),
            port: 8765,
            token: "silent-exec-secret".to_string(),
            dry_run: false,
            server_url: None,
            roots: FileIndexer::default_user_roots(),
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ExecuteRequest {
    pub prompt: Option<String>,
    pub json_command: Option<ActionCommand>,
    pub confirm: Option<bool>,
    pub dry_run: Option<bool>,
}

/// Spawns the LAN bridge server in a background thread with an optional shutdown signal.
/// Returns the bound `SocketAddr` and the thread `JoinHandle`.
pub fn start_bridge_server(
    config: BridgeConfig,
    shutdown_signal: Option<Arc<AtomicBool>>,
) -> Result<(SocketAddr, JoinHandle<()>), String> {
    let bind_addr = format!("{}:{}", config.host, config.port);
    let listener = TcpListener::bind(&bind_addr)
        .map_err(|e| format!("Failed to bind LAN bridge to {}: {}", bind_addr, e))?;

    let local_addr = listener
        .local_addr()
        .map_err(|e| format!("Failed to retrieve local address: {}", e))?;

    listener
        .set_nonblocking(true)
        .map_err(|e| format!("Failed to set listener nonblocking: {}", e))?;

    let shutdown = shutdown_signal.unwrap_or_else(|| Arc::new(AtomicBool::new(false)));

    let handle = thread::spawn(move || {
        let mut indexer = FileIndexer::new(config.roots);
        let _ = indexer.build_index();
        let executor = PcExecutor::new(indexer, config.dry_run);
        let backend = LlamaBackend::new_server(config.server_url.as_deref(), None);

        while !shutdown.load(Ordering::Relaxed) {
            match listener.accept() {
                Ok((stream, _peer_addr)) => {
                    let _ = stream.set_nonblocking(false);
                    handle_client(stream, &config.token, config.dry_run, &executor, &backend);
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                    thread::sleep(Duration::from_millis(15));
                }
                Err(_) => {
                    break;
                }
            }
        }
    });

    Ok((local_addr, handle))
}

fn handle_client(
    mut stream: TcpStream,
    expected_token: &str,
    default_dry_run: bool,
    executor: &PcExecutor,
    backend: &LlamaBackend,
) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let mut reader = BufReader::new(&stream);
    let mut request_line = String::new();

    if reader.read_line(&mut request_line).is_err() || request_line.is_empty() {
        return;
    }

    let parts: Vec<&str> = request_line.split_whitespace().collect();
    if parts.len() < 2 {
        let _ = write_response(
            &mut stream,
            400,
            "Bad Request",
            r#"{"error":"malformed request"}"#,
        );
        return;
    }
    let method = parts[0];
    let path = parts[1];

    let mut content_length = 0usize;
    let mut auth_header: Option<String> = None;

    loop {
        let mut header_line = String::new();
        if reader.read_line(&mut header_line).is_err() {
            break;
        }
        let trimmed = header_line.trim();
        if trimmed.is_empty() {
            break;
        }
        if let Some((k, v)) = trimmed.split_once(':') {
            let key = k.trim().to_lowercase();
            let val = v.trim();
            if key == "content-length" {
                content_length = val.parse().unwrap_or(0);
            } else if key == "authorization" {
                auth_header = Some(val.to_string());
            }
        }
    }

    // Health check endpoint
    if method == "GET" && path == "/health" {
        let _ = write_response(
            &mut stream,
            200,
            "OK",
            r#"{"status":"ok","version":"0.1.0"}"#,
        );
        return;
    }

    // Execute endpoint
    if method == "POST" && path == "/v1/execute" {
        // Bearer Token Authorization check
        let is_authorized = match auth_header {
            Some(ref h) => h.strip_prefix("Bearer ") == Some(expected_token),
            None => false,
        };

        if !is_authorized {
            let _ = write_response(
                &mut stream,
                401,
                "Unauthorized",
                r#"{"error":{"code":"unauthorized","message":"Missing or invalid Bearer token"}}"#,
            );
            return;
        }

        let mut body = vec![0u8; content_length];
        if reader.read_exact(&mut body).is_err() {
            let _ = write_response(
                &mut stream,
                400,
                "Bad Request",
                r#"{"error":{"code":"bad_request","message":"Failed to read body"}}"#,
            );
            return;
        }

        let execute_req: ExecuteRequest = match serde_json::from_slice(&body) {
            Ok(r) => r,
            Err(e) => {
                let _ = write_response(
                    &mut stream,
                    400,
                    "Bad Request",
                    &format!(r#"{{"error":{{"code":"bad_request","message":"{}"}}}}"#, e),
                );
                return;
            }
        };

        let cmd = if let Some(c) = execute_req.json_command {
            c
        } else if let Some(ref p) = execute_req.prompt {
            match backend.infer(p) {
                Ok(c) => c,
                Err(e) => {
                    let _ = write_response(
                        &mut stream,
                        500,
                        "Internal Server Error",
                        &format!(
                            r#"{{"error":{{"code":"inference_failed","message":"{}"}}}}"#,
                            e
                        ),
                    );
                    return;
                }
            }
        } else {
            let _ = write_response(
                &mut stream,
                400,
                "Bad Request",
                r#"{"error":{"code":"bad_request","message":"Either 'json_command' or 'prompt' must be provided"}}"#,
            );
            return;
        };

        let is_dry_run = execute_req.dry_run.unwrap_or(default_dry_run);
        let confirm = execute_req.confirm.unwrap_or(false);

        let result = if is_dry_run == executor.is_dry_run() {
            executor.execute(&cmd, confirm)
        } else {
            let temp_exec = PcExecutor::new(executor.indexer().clone(), is_dry_run);
            temp_exec.execute(&cmd, confirm)
        };

        let resp_json = match serde_json::to_string(&result) {
            Ok(j) => j,
            Err(e) => format!(
                r#"{{"error":{{"code":"serialize_error","message":"{}"}}}}"#,
                e
            ),
        };

        let _ = write_response(&mut stream, 200, "OK", &resp_json);
        return;
    }

    let _ = write_response(
        &mut stream,
        404,
        "Not Found",
        r#"{"error":{"code":"not_found","message":"Endpoint not found"}}"#,
    );
}

fn write_response(
    stream: &mut TcpStream,
    status_code: u16,
    status_text: &str,
    body: &str,
) -> std::io::Result<()> {
    let response = format!(
        "HTTP/1.1 {} {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        status_code,
        status_text,
        body.len(),
        body
    );
    stream.write_all(response.as_bytes())?;
    stream.flush()
}
