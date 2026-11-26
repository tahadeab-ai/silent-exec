//! Authenticated LAN Bridge HTTP + WebSocket JSON-RPC 2.0 Server for SilentExec PC
//! Zero-bloat standard-library server allowing authorized LAN clients (such as Android device agents)
//! to communicate via:
//! - HTTP `GET /health` & `POST /v1/execute`
//! - WebSocket JSON-RPC 2.0 (`device.get_state`, `device.execute_batch`, `planner.offload_step`)
//!   with per-session Bearer token / 6-digit pairing code auth, `CaptureGuard` stale-click protection,
//!   and `PiiRedactor` privacy masking.

use crate::config::AppConfig;
use crate::executor::PcExecutor;
use crate::indexer::FileIndexer;
use crate::inference::{AiBackend, LlamaBackend};
use crate::pii_redactor::PiiRedactor;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use silent_exec_core::ActionCommand;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// Monotonic one-use `capture_id` guard (cua-driver / Mobilerun pattern).
/// Rejects coordinate/batch actions referencing stale or already-consumed observations.
#[derive(Debug, Default)]
pub struct CaptureGuard {
    next_id: u64,
    latest_id: Option<u64>,
}

impl CaptureGuard {
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Issues a fresh monotonic `capture_id` for the latest UI observation.
    pub fn issue_capture_id(&mut self) -> u64 {
        self.next_id += 1;
        self.latest_id = Some(self.next_id);
        self.next_id
    }

    /// Validates that `capture_id` matches the latest unconsumed observation and consumes it.
    pub fn validate_and_consume(&mut self, capture_id: u64) -> Result<(), String> {
        match self.latest_id {
            Some(latest) if latest == capture_id => {
                self.latest_id = None;
                Ok(())
            }
            Some(latest) => Err(format!(
                "STALE_CAPTURE_ID: expected {}, got {}",
                latest, capture_id
            )),
            None => Err(format!(
                "STALE_CAPTURE_ID: no active observation for capture_id {}",
                capture_id
            )),
        }
    }
}

/// Generates a deterministic-format 6-digit pairing code for per-session LAN Bridge auth.
#[must_use]
pub fn generate_pairing_code() -> String {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.subsec_nanos() ^ (d.as_secs() as u32))
        .unwrap_or(482910);
    format!("{:06}", nanos % 1_000_000)
}

#[derive(Debug, Clone)]
pub struct BridgeConfig {
    pub host: String,
    pub port: u16,
    pub ws_port: u16,
    pub token: String,
    pub pairing_code: String,
    pub pii_redaction_enabled: bool,
    pub dry_run: bool,
    pub server_url: Option<String>,
    pub roots: Vec<PathBuf>,
}

impl Default for BridgeConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".to_string(),
            port: 8765,
            ws_port: 8081,
            token: "silent-exec-secret".to_string(),
            pairing_code: generate_pairing_code(),
            pii_redaction_enabled: true,
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

#[derive(Debug)]
pub struct BridgeSessionState {
    pub capture_guard: CaptureGuard,
    pub redactor: PiiRedactor,
    pub last_phone_state: Value,
    pub last_a11y_tree: Vec<String>,
}

impl BridgeSessionState {
    #[must_use]
    pub fn new(pii_redaction_enabled: bool) -> Self {
        Self {
            capture_guard: CaptureGuard::new(),
            redactor: PiiRedactor::new(pii_redaction_enabled),
            last_phone_state: json!({
                "packageName": "pc.desktop",
                "activityName": "Desktop",
                "keyboardVisible": false,
                "isSecureScreen": false
            }),
            last_a11y_tree: Vec::new(),
        }
    }
}

/// Dispatches a Mobilerun-style JSON-RPC 2.0 request (`device.get_state`, `device.execute_batch`, `planner.offload_step`).
pub fn handle_json_rpc_message(
    raw_msg: &str,
    session: &Arc<Mutex<BridgeSessionState>>,
    executor: &PcExecutor,
) -> String {
    let req: Value = match serde_json::from_str(raw_msg) {
        Ok(v) => v,
        Err(e) => {
            return json!({
                "jsonrpc": "2.0",
                "id": Value::Null,
                "error": { "code": -32700, "message": format!("Parse error: {}", e) }
            })
            .to_string();
        }
    };

    let id = req.get("id").cloned().unwrap_or(Value::Null);
    let method = req.get("method").and_then(|m| m.as_str()).unwrap_or("");
    let params = req.get("params").cloned().unwrap_or_else(|| json!({}));

    match method {
        "device.get_state" => {
            let mut guard = session.lock().unwrap();
            let capture_id = guard.capture_guard.issue_capture_id();
            let redacted_tree: Vec<String> = guard
                .last_a11y_tree
                .iter()
                .map(|s| guard.redactor.redact(s))
                .collect();
            let redacted_state = guard.redactor.redact_json(&guard.last_phone_state);
            json!({
                "jsonrpc": "2.0",
                "id": id,
                "result": {
                    "capture_id": capture_id,
                    "phone_state": redacted_state,
                    "a11y_tree": redacted_tree
                }
            })
            .to_string()
        }
        "device.execute_batch" => {
            let capture_id = params
                .get("capture_id")
                .and_then(|v| v.as_u64())
                .unwrap_or(0);
            {
                let mut guard = session.lock().unwrap();
                if let Err(stale_err) = guard.capture_guard.validate_and_consume(capture_id) {
                    return json!({
                        "jsonrpc": "2.0",
                        "id": id,
                        "error": {
                            "code": -32001,
                            "message": stale_err,
                            "data": { "code": "STALE_CAPTURE_ID" }
                        }
                    })
                    .to_string();
                }
            }
            let cmd_val = params.get("command").cloned().unwrap_or(params.clone());
            let cmd_str = serde_json::to_string(&cmd_val).unwrap_or_default();
            match ActionCommand::from_llm_json(&cmd_str) {
                Ok(cmd) => {
                    let confirm = params
                        .get("confirm")
                        .and_then(|v| v.as_bool())
                        .unwrap_or(false);
                    let res = executor.execute(&cmd, confirm);
                    json!({
                        "jsonrpc": "2.0",
                        "id": id,
                        "result": res
                    })
                    .to_string()
                }
                Err(e) => json!({
                    "jsonrpc": "2.0",
                    "id": id,
                    "error": { "code": -32602, "message": e }
                })
                .to_string(),
            }
        }
        "planner.offload_step" => {
            let goal = params.get("goal").and_then(|v| v.as_str()).unwrap_or("");
            if goal.trim().is_empty() {
                return json!({
                    "jsonrpc": "2.0",
                    "id": id,
                    "error": { "code": -32602, "message": "Missing 'goal' parameter" }
                })
                .to_string();
            }
            let raw_tree = params
                .get("a11y_tree")
                .and_then(|v| v.as_array())
                .map(|arr| {
                    arr.iter()
                        .filter_map(|v| v.as_str().map(ToString::to_string))
                        .collect::<Vec<_>>()
                })
                .unwrap_or_default();
            let redactor = session.lock().unwrap().redactor;
            let clean_tree: Vec<String> = raw_tree.iter().map(|s| redactor.redact(s)).collect();
            let prompt = format!(
                "Goal: {}\nScreen elements:\n{}",
                redactor.redact(goal),
                clean_tree.join("\n")
            );
            let cfg = AppConfig::load();
            let backend = AiBackend::new_ollama_with_thinking(
                Some(&cfg.ollama_url),
                Some(&cfg.ollama_model),
                true,
            );
            match backend.infer(&prompt) {
                Ok(cmd) => json!({
                    "jsonrpc": "2.0",
                    "id": id,
                    "result": {
                        "action_command": cmd,
                        "stream_event": { "stage": "planned", "action": cmd.action }
                    }
                })
                .to_string(),
                Err(e) => json!({
                    "jsonrpc": "2.0",
                    "id": id,
                    "error": { "code": -32002, "message": e }
                })
                .to_string(),
            }
        }
        _ => json!({
            "jsonrpc": "2.0",
            "id": id,
            "error": { "code": -32601, "message": format!("Method not found: {}", method) }
        })
        .to_string(),
    }
}

/// Spawns the LAN bridge HTTP + WebSocket JSON-RPC 2.0 server in a background thread.
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

    let ws_listener = TcpListener::bind(format!("{}:{}", config.host, config.ws_port)).ok();
    if let Some(ref wsl) = ws_listener {
        let _ = wsl.set_nonblocking(true);
    }

    let shutdown = shutdown_signal.unwrap_or_else(|| Arc::new(AtomicBool::new(false)));
    let session = Arc::new(Mutex::new(BridgeSessionState::new(
        config.pii_redaction_enabled,
    )));

    let handle = thread::spawn(move || {
        let mut indexer = FileIndexer::new(config.roots);
        let _ = indexer.build_index();
        let executor = PcExecutor::new(indexer, config.dry_run);
        let backend = LlamaBackend::new_server(config.server_url.as_deref(), None);

        while !shutdown.load(Ordering::Relaxed) {
            let mut did_work = false;
            if let Ok((stream, _peer_addr)) = listener.accept() {
                did_work = true;
                let _ = stream.set_nonblocking(false);
                handle_client(
                    stream,
                    &config.token,
                    &config.pairing_code,
                    config.dry_run,
                    &executor,
                    &backend,
                    &session,
                );
            }
            if let Some(ref wsl) = ws_listener {
                if let Ok((stream, _peer_addr)) = wsl.accept() {
                    did_work = true;
                    let _ = stream.set_nonblocking(false);
                    handle_client(
                        stream,
                        &config.token,
                        &config.pairing_code,
                        config.dry_run,
                        &executor,
                        &backend,
                        &session,
                    );
                }
            }
            if !did_work {
                thread::sleep(Duration::from_millis(15));
            }
        }
    });

    Ok((local_addr, handle))
}

fn is_authorized_token(auth_header: Option<&str>, expected: &str, pairing_code: &str) -> bool {
    match auth_header {
        Some(h) => {
            let bearer = h.strip_prefix("Bearer ").unwrap_or("").trim();
            !bearer.is_empty() && (bearer == expected || bearer == pairing_code)
        }
        None => false,
    }
}

fn handle_client(
    mut stream: TcpStream,
    expected_token: &str,
    pairing_code: &str,
    default_dry_run: bool,
    executor: &PcExecutor,
    backend: &LlamaBackend,
    session: &Arc<Mutex<BridgeSessionState>>,
) {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let mut reader = BufReader::new(stream.try_clone().unwrap_or_else(|_| return_dummy()));
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
    let mut upgrade_ws = false;
    let mut ws_key: Option<String> = None;

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
            } else if key == "upgrade" && val.eq_ignore_ascii_case("websocket") {
                upgrade_ws = true;
            } else if key == "sec-websocket-key" {
                ws_key = Some(val.to_string());
            }
        }
    }

    // WebSocket JSON-RPC 2.0 upgrade
    if upgrade_ws {
        let token_ok = is_authorized_token(auth_header.as_deref(), expected_token, pairing_code)
            || path.contains(&format!("token={}", expected_token))
            || path.contains(&format!("token={}", pairing_code));
        if !token_ok {
            let _ = write_response(
                &mut stream,
                401,
                "Unauthorized",
                r#"{"error":{"code":"unauthorized","message":"Missing or invalid Bearer token"}}"#,
            );
            return;
        }
        if let Some(key) = ws_key {
            let accept = compute_ws_accept(&key);
            let handshake = format!(
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
                accept
            );
            if stream.write_all(handshake.as_bytes()).is_ok() {
                let _ = stream.flush();
                let _ = stream.set_read_timeout(Some(Duration::from_secs(30)));
                while let Some(msg) = read_ws_text_frame(&mut reader) {
                    let reply = handle_json_rpc_message(&msg, session, executor);
                    if write_ws_text_frame(&mut stream, &reply).is_err() {
                        break;
                    }
                }
            }
        }
        return;
    }

    // Health check endpoint
    if method == "GET" && path == "/health" {
        let _ = write_response(
            &mut stream,
            200,
            "OK",
            r#"{"status":"ok","version":"0.1.0","ws_jsonrpc":true}"#,
        );
        return;
    }

    // Execute endpoint
    if method == "POST" && path == "/v1/execute" {
        if !is_authorized_token(auth_header.as_deref(), expected_token, pairing_code) {
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

fn return_dummy() -> TcpStream {
    TcpStream::connect("127.0.0.1:0").unwrap()
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

fn read_ws_text_frame<R: Read>(reader: &mut R) -> Option<String> {
    let mut header = [0u8; 2];
    reader.read_exact(&mut header).ok()?;
    let opcode = header[0] & 0x0F;
    if opcode == 0x8 {
        return None; // Close frame
    }
    let masked = (header[1] & 0x80) != 0;
    let mut len = (header[1] & 0x7F) as usize;
    if len == 126 {
        let mut ext = [0u8; 2];
        reader.read_exact(&mut ext).ok()?;
        len = u16::from_be_bytes(ext) as usize;
    } else if len == 127 {
        let mut ext = [0u8; 8];
        reader.read_exact(&mut ext).ok()?;
        len = u64::from_be_bytes(ext) as usize;
    }
    if len > 4 * 1024 * 1024 {
        return None;
    }
    let mask = if masked {
        let mut m = [0u8; 4];
        reader.read_exact(&mut m).ok()?;
        Some(m)
    } else {
        None
    };
    let mut payload = vec![0u8; len];
    reader.read_exact(&mut payload).ok()?;
    if let Some(m) = mask {
        for (i, b) in payload.iter_mut().enumerate() {
            *b ^= m[i % 4];
        }
    }
    String::from_utf8(payload).ok()
}

fn write_ws_text_frame(stream: &mut TcpStream, text: &str) -> std::io::Result<()> {
    let bytes = text.as_bytes();
    let mut frame = Vec::with_capacity(bytes.len() + 10);
    frame.push(0x81); // FIN + Text
    if bytes.len() < 126 {
        frame.push(bytes.len() as u8);
    } else if bytes.len() <= 65535 {
        frame.push(126);
        frame.extend_from_slice(&(bytes.len() as u16).to_be_bytes());
    } else {
        frame.push(127);
        frame.extend_from_slice(&(bytes.len() as u64).to_be_bytes());
    }
    frame.extend_from_slice(bytes);
    stream.write_all(&frame)?;
    stream.flush()
}

/// Computes RFC 6455 `Sec-WebSocket-Accept` using pure-Rust SHA-1 + Base64.
#[must_use]
pub fn compute_ws_accept(client_key: &str) -> String {
    let concat = format!("{}258EAFA5-E914-47DA-95CA-C5AB0DC85B11", client_key.trim());
    let digest = sha1_digest(concat.as_bytes());
    base64_encode(&digest)
}

fn sha1_digest(data: &[u8]) -> [u8; 20] {
    let mut msg = data.to_vec();
    let bit_len = (data.len() as u64) * 8;
    msg.push(0x80);
    while (msg.len() % 64) != 56 {
        msg.push(0);
    }
    msg.extend_from_slice(&bit_len.to_be_bytes());

    let mut h0: u32 = 0x6745_2301;
    let mut h1: u32 = 0xEFCD_AB89;
    let mut h2: u32 = 0x98BA_DCFE;
    let mut h3: u32 = 0x1032_5476;
    let mut h4: u32 = 0xC3D2_E1F0;

    for chunk in msg.as_chunks::<64>().0 {
        let mut w = [0u32; 80];
        for i in 0..16 {
            w[i] = u32::from_be_bytes([
                chunk[i * 4],
                chunk[i * 4 + 1],
                chunk[i * 4 + 2],
                chunk[i * 4 + 3],
            ]);
        }
        for i in 16..80 {
            w[i] = (w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16]).rotate_left(1);
        }
        let (mut a, mut b, mut c, mut d, mut e) = (h0, h1, h2, h3, h4);
        for (i, &wi) in w.iter().enumerate() {
            let (f, k) = match i {
                0..=19 => ((b & c) | ((!b) & d), 0x5A82_7999u32),
                20..=39 => (b ^ c ^ d, 0x6ED9_EBA1u32),
                40..=59 => ((b & c) | (b & d) | (c & d), 0x8F1B_BCDCu32),
                _ => (b ^ c ^ d, 0xCA62_C1D6u32),
            };
            let temp = a
                .rotate_left(5)
                .wrapping_add(f)
                .wrapping_add(e)
                .wrapping_add(k)
                .wrapping_add(wi);
            e = d;
            d = c;
            c = b.rotate_left(30);
            b = a;
            a = temp;
        }
        h0 = h0.wrapping_add(a);
        h1 = h1.wrapping_add(b);
        h2 = h2.wrapping_add(c);
        h3 = h3.wrapping_add(d);
        h4 = h4.wrapping_add(e);
    }

    let mut out = [0u8; 20];
    out[0..4].copy_from_slice(&h0.to_be_bytes());
    out[4..8].copy_from_slice(&h1.to_be_bytes());
    out[8..12].copy_from_slice(&h2.to_be_bytes());
    out[12..16].copy_from_slice(&h3.to_be_bytes());
    out[16..20].copy_from_slice(&h4.to_be_bytes());
    out
}

fn base64_encode(input: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(input.len().div_ceil(3) * 4);
    for chunk in input.chunks(3) {
        let b0 = chunk[0] as u32;
        let b1 = if chunk.len() > 1 { chunk[1] as u32 } else { 0 };
        let b2 = if chunk.len() > 2 { chunk[2] as u32 } else { 0 };
        let triple = (b0 << 16) | (b1 << 8) | b2;
        out.push(TABLE[((triple >> 18) & 0x3F) as usize] as char);
        out.push(TABLE[((triple >> 12) & 0x3F) as usize] as char);
        out.push(if chunk.len() > 1 {
            TABLE[((triple >> 6) & 0x3F) as usize] as char
        } else {
            '='
        });
        out.push(if chunk.len() > 2 {
            TABLE[(triple & 0x3F) as usize] as char
        } else {
            '='
        });
    }
    out
}
