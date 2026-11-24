//! LLM Inference Client for SilentExec
//! Provides lightweight local inference against llama.cpp backends:
//! 1. HTTP Server mode (`llama-server`) via `/completion` endpoint with GBNF grammar.
//! 2. CLI Subprocess mode (`llama-cli`) via subprocess invocation.
//!
//! Also provides strict ChatML prompt construction, action JSON extraction,
//! and conversational chatter rejection.

use serde_json::Value;
use silent_exec_core::ActionCommand;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;

/// Embedded GBNF grammar for ActionCommand schema.
pub const EMBEDDED_ACTION_GBNF: &str = include_str!("../../../../schemas/action.gbnf");

/// Default system prompt strictly matching `nlp-training/generate_dataset.py`.
pub const DEFAULT_SYSTEM_PROMPT: &str = "You are SilentExec, a zero-chatter OS execution agent. \
    Emit only a valid ActionCommand JSON matching the strict schema. \
    No conversational responses.";

/// Builds a ChatML formatted prompt for Qwen models.
pub fn build_chatml_prompt(system_prompt: &str, user_query: &str) -> String {
    format!(
        "<|im_start|>system\n{}<|im_end|>\n<|im_start|>user\n{}<|im_end|>\n<|im_start|>assistant\n",
        system_prompt.trim(),
        user_query.trim()
    )
}

/// Convenience function using the default system prompt.
pub fn format_prompt(user_query: &str) -> String {
    build_chatml_prompt(DEFAULT_SYSTEM_PROMPT, user_query)
}

/// Strictly extracts ActionCommand JSON from raw model output.
/// Strips markdown fences (```json ... ``` or ``` ... ```).
/// Rejects conversational chatter and invalid output.
pub fn extract_action_json(raw: &str) -> Result<String, String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err("Model output is empty".to_string());
    }

    // Strip trailing ChatML eos tokens if present
    let mut cleaned = trimmed.to_string();
    if let Some(stripped) = cleaned.strip_suffix("<|im_end|>") {
        cleaned = stripped.trim().to_string();
    }
    if let Some(stripped) = cleaned.strip_suffix("<|endoftext|>") {
        cleaned = stripped.trim().to_string();
    }

    // Strip markdown code fences if present
    if cleaned.starts_with("```") {
        let lines: Vec<&str> = cleaned.lines().collect();
        if lines.len() >= 2 && lines.last().is_some_and(|l| l.trim().starts_with("```")) {
            // Drop first line (``` or ```json) and last line (```)
            cleaned = lines[1..lines.len() - 1].join("\n").trim().to_string();
        }
    }

    // Locate outer JSON object '{' and '}'
    let start_idx = match cleaned.find('{') {
        Some(idx) => idx,
        None => return Err(format!("No JSON object found in output: '{}'", raw)),
    };

    let end_idx = match cleaned.rfind('}') {
        Some(idx) if idx >= start_idx => idx,
        _ => return Err(format!("Unterminated JSON object in output: '{}'", raw)),
    };

    let prefix = cleaned[..start_idx].trim();
    let suffix = cleaned[end_idx + 1..].trim();

    // Check for conversational chatter in prefix or suffix
    let chatter_indicators = [
        "sure",
        "here is",
        "here's",
        "certainly",
        "i can",
        "of course",
        "sorry",
        "as requested",
        "here you go",
        "note that",
        "please find",
        "حاضر",
        "بالتأكيد",
        "تفضل",
        "إليك",
        "سأقوم",
    ];

    let prefix_lower = prefix.to_lowercase();
    let suffix_lower = suffix.to_lowercase();

    for indicator in &chatter_indicators {
        if prefix_lower.contains(indicator) || suffix_lower.contains(indicator) {
            return Err(format!(
                "Conversational chatter detected in model response: '{}'",
                cleaned
            ));
        }
    }

    // If prefix or suffix contains words (not just punctuation / whitespace)
    if prefix.chars().any(|c| c.is_alphabetic()) {
        return Err(format!("Conversational prefix text rejected: '{}'", prefix));
    }
    if suffix.chars().any(|c| c.is_alphabetic()) {
        return Err(format!("Conversational suffix text rejected: '{}'", suffix));
    }

    let json_candidate = &cleaned[start_idx..=end_idx];

    // Validate that it parses as valid JSON
    let parsed: Value = serde_json::from_str(json_candidate)
        .map_err(|e| format!("Invalid JSON structure: {}", e))?;

    if !parsed.is_object() {
        return Err("Extracted JSON is not an object".to_string());
    }

    Ok(json_candidate.to_string())
}

/// Multi-backend AI Engine supporting:
/// 1. `local_llama`: managed llama-server with GBNF grammar and KV caching.
/// 2. `ollama`: Ollama API (/api/chat) with `format: "json"`.
/// 3. `openai_compat`: OpenAI-compatible endpoint with JSON schema prompt.
#[derive(Debug, Clone)]
pub enum AiBackend {
    LocalLlama {
        url: String,
        grammar: Option<String>,
    },
    Ollama {
        url: String,
        model: String,
    },
    OpenAiCompat {
        base_url: String,
        model: String,
        api_key: String,
    },
    Cli {
        bin_path: PathBuf,
        model_path: PathBuf,
        grammar_path: Option<PathBuf>,
    },
}

impl AiBackend {
    pub fn new_local_llama(url: Option<&str>, grammar: Option<String>) -> Self {
        let base_url = url.unwrap_or("http://127.0.0.1:8080").to_string();
        Self::LocalLlama {
            url: base_url,
            grammar,
        }
    }

    pub fn new_ollama(url: Option<&str>, model: Option<&str>) -> Self {
        Self::Ollama {
            url: url.unwrap_or("http://127.0.0.1:11434").to_string(),
            model: model.unwrap_or("qwen2.5:1.5b").to_string(),
        }
    }

    pub fn new_openai_compat(
        base_url: Option<&str>,
        model: Option<&str>,
        api_key: Option<&str>,
    ) -> Self {
        Self::OpenAiCompat {
            base_url: base_url.unwrap_or("https://api.openai.com/v1").to_string(),
            model: model.unwrap_or("gpt-4o-mini").to_string(),
            api_key: api_key.unwrap_or("").to_string(),
        }
    }

    pub fn new_cli(bin_path: PathBuf, model_path: PathBuf, grammar_path: Option<PathBuf>) -> Self {
        Self::Cli {
            bin_path,
            model_path,
            grammar_path,
        }
    }

    pub fn infer(&self, user_query: &str) -> Result<ActionCommand, String> {
        let raw_output = match self {
            Self::LocalLlama { url, grammar } => {
                let prompt = format_prompt(user_query);
                let grammar_str = grammar.as_deref().unwrap_or(EMBEDDED_ACTION_GBNF);
                LlamaBackend::complete_server(url, &prompt, grammar_str)?
            }
            Self::Ollama { url, model } => Self::complete_ollama(url, model, user_query)?,
            Self::OpenAiCompat {
                base_url,
                model,
                api_key,
            } => Self::complete_openai_compat(base_url, model, api_key, user_query)?,
            Self::Cli {
                bin_path,
                model_path,
                grammar_path,
            } => {
                let prompt = format_prompt(user_query);
                LlamaBackend::complete_cli(bin_path, model_path, grammar_path.as_deref(), &prompt)?
            }
        };

        let clean_json = extract_action_json(&raw_output)?;
        ActionCommand::from_json(&clean_json)
    }

    /// Builds payload for Ollama /api/chat with format: "json"
    pub fn build_ollama_payload(model: &str, user_query: &str) -> serde_json::Value {
        serde_json::json!({
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": DEFAULT_SYSTEM_PROMPT
                },
                {
                    "role": "user",
                    "content": user_query
                }
            ],
            "format": "json",
            "stream": false,
            "options": {
                "temperature": 0.0
            }
        })
    }

    /// Calls Ollama /api/chat endpoint
    fn complete_ollama(url: &str, model: &str, user_query: &str) -> Result<String, String> {
        let endpoint = format!("{}/api/chat", url.trim_end_matches('/'));
        let payload = Self::build_ollama_payload(model, user_query);
        let payload_str = serde_json::to_string(&payload)
            .map_err(|e| format!("Failed to serialize Ollama payload: {}", e))?;

        let mut child = Command::new("curl")
            .args([
                "-sSf",
                "-X",
                "POST",
                "-H",
                "Content-Type: application/json",
                "-d",
                "@-",
                &endpoint,
            ])
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .spawn()
            .map_err(|e| format!("Failed to invoke curl for Ollama at {}: {}", endpoint, e))?;

        if let Some(mut stdin) = child.stdin.take() {
            stdin
                .write_all(payload_str.as_bytes())
                .map_err(|e| format!("Failed to write request body to curl: {}", e))?;
        }

        let output = child
            .wait_with_output()
            .map_err(|e| format!("Failed to read curl output: {}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(format!(
                "Ollama request to {} failed with code {:?}: {}",
                endpoint,
                output.status.code(),
                stderr.trim()
            ));
        }

        let stdout_str = String::from_utf8_lossy(&output.stdout);
        let parsed: Value = serde_json::from_str(&stdout_str).map_err(|e| {
            format!(
                "Failed to parse Ollama JSON response: {} (response was: '{}')",
                e, stdout_str
            )
        })?;

        // Extract message.content: {"message": {"content": "..."}}
        if let Some(content) = parsed
            .get("message")
            .and_then(|m| m.get("content"))
            .and_then(|c| c.as_str())
        {
            return Ok(content.to_string());
        }

        Ok(stdout_str.into_owned())
    }

    /// Builds payload for OpenAI compatible /chat/completions endpoint
    pub fn build_openai_compat_payload(model: &str, user_query: &str) -> serde_json::Value {
        serde_json::json!({
            "model": model,
            "messages": [
                {
                    "role": "system",
                    "content": DEFAULT_SYSTEM_PROMPT
                },
                {
                    "role": "user",
                    "content": user_query
                }
            ],
            "response_format": { "type": "json_object" },
            "temperature": 0.0
        })
    }

    /// Calls OpenAI-compatible /chat/completions endpoint
    fn complete_openai_compat(
        base_url: &str,
        model: &str,
        api_key: &str,
        user_query: &str,
    ) -> Result<String, String> {
        let endpoint = if base_url.ends_with("/chat/completions") {
            base_url.to_string()
        } else {
            format!("{}/chat/completions", base_url.trim_end_matches('/'))
        };

        let payload = Self::build_openai_compat_payload(model, user_query);
        let payload_str = serde_json::to_string(&payload)
            .map_err(|e| format!("Failed to serialize OpenAI payload: {}", e))?;

        let mut args = vec![
            "-sSf".to_string(),
            "-X".to_string(),
            "POST".to_string(),
            "-H".to_string(),
            "Content-Type: application/json".to_string(),
        ];

        if !api_key.trim().is_empty() {
            args.push("-H".to_string());
            args.push(format!("Authorization: Bearer {}", api_key.trim()));
        }

        args.push("-d".to_string());
        args.push("@-".to_string());
        args.push(endpoint.clone());

        let mut child = Command::new("curl")
            .args(&args)
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .spawn()
            .map_err(|e| {
                format!(
                    "Failed to invoke curl for OpenAI-compat at {}: {}",
                    endpoint, e
                )
            })?;

        if let Some(mut stdin) = child.stdin.take() {
            stdin
                .write_all(payload_str.as_bytes())
                .map_err(|e| format!("Failed to write request body to curl: {}", e))?;
        }

        let output = child
            .wait_with_output()
            .map_err(|e| format!("Failed to read curl output: {}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(format!(
                "OpenAI-compat request to {} failed with code {:?}: {}",
                endpoint,
                output.status.code(),
                stderr.trim()
            ));
        }

        let stdout_str = String::from_utf8_lossy(&output.stdout);
        let parsed: Value = serde_json::from_str(&stdout_str).map_err(|e| {
            format!(
                "Failed to parse OpenAI-compat JSON response: {} (response was: '{}')",
                e, stdout_str
            )
        })?;

        // Extract choices[0].message.content
        if let Some(content) = parsed
            .get("choices")
            .and_then(|c| c.get(0))
            .and_then(|ch| ch.get("message"))
            .and_then(|m| m.get("content"))
            .and_then(|ct| ct.as_str())
        {
            return Ok(content.to_string());
        }

        Ok(stdout_str.into_owned())
    }
}

/// llama.cpp backend configuration for local inference.
#[derive(Debug, Clone)]
pub enum LlamaBackend {
    /// Connect to a running `llama-server` HTTP instance.
    Server {
        url: String,
        grammar: Option<String>,
    },
    /// Launch `llama-cli` as a subprocess.
    Cli {
        bin_path: PathBuf,
        model_path: PathBuf,
        grammar_path: Option<PathBuf>,
    },
}

impl LlamaBackend {
    /// Create a server backend with default URL `http://127.0.0.1:8080`.
    pub fn new_server(url: Option<&str>, grammar: Option<String>) -> Self {
        let base_url = url.unwrap_or("http://127.0.0.1:8080").to_string();
        Self::Server {
            url: base_url,
            grammar,
        }
    }

    /// Create a CLI backend with the specified binary and model path.
    pub fn new_cli(bin_path: PathBuf, model_path: PathBuf, grammar_path: Option<PathBuf>) -> Self {
        Self::Cli {
            bin_path,
            model_path,
            grammar_path,
        }
    }

    /// Run inference against the backend, extract JSON, and parse into `ActionCommand`.
    pub fn infer(&self, user_query: &str) -> Result<ActionCommand, String> {
        let prompt = format_prompt(user_query);

        let raw_output = match self {
            Self::Server { url, grammar } => {
                let grammar_str = grammar.as_deref().unwrap_or(EMBEDDED_ACTION_GBNF);
                Self::complete_server(url, &prompt, grammar_str)?
            }
            Self::Cli {
                bin_path,
                model_path,
                grammar_path,
            } => Self::complete_cli(bin_path, model_path, grammar_path.as_deref(), &prompt)?,
        };

        let clean_json = extract_action_json(&raw_output)?;
        ActionCommand::from_json(&clean_json)
    }

    /// Generates the canonical JSON request payload for llama-server `/completion` endpoint.
    /// Explicitly enables `"cache_prompt": true` for KV-cache reuse.
    pub fn build_completion_payload(prompt: &str, grammar: &str) -> serde_json::Value {
        serde_json::json!({
            "prompt": prompt,
            "grammar": grammar,
            "temperature": 0.0,
            "n_predict": 160,
            "stream": false,
            "cache_prompt": true
        })
    }

    /// Execute completion against `llama-server` `/completion` endpoint via `curl`.
    fn complete_server(url: &str, prompt: &str, grammar: &str) -> Result<String, String> {
        let endpoint = format!("{}/completion", url.trim_end_matches('/'));
        let payload = Self::build_completion_payload(prompt, grammar);

        let payload_str = serde_json::to_string(&payload)
            .map_err(|e| format!("Failed to serialize request payload: {}", e))?;

        let mut child = Command::new("curl")
            .args([
                "-sSf",
                "-X",
                "POST",
                "-H",
                "Content-Type: application/json",
                "-d",
                "@-",
                &endpoint,
            ])
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::piped())
            .spawn()
            .map_err(|e| {
                format!(
                    "Failed to invoke curl for llama-server at {}: {}",
                    endpoint, e
                )
            })?;

        if let Some(mut stdin) = child.stdin.take() {
            stdin
                .write_all(payload_str.as_bytes())
                .map_err(|e| format!("Failed to write request body to curl: {}", e))?;
        }

        let output = child
            .wait_with_output()
            .map_err(|e| format!("Failed to read curl output: {}", e))?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(format!(
                "llama-server request to {} failed with code {:?}: {}",
                endpoint,
                output.status.code(),
                stderr.trim()
            ));
        }

        let stdout_str = String::from_utf8_lossy(&output.stdout);
        let parsed: Value = serde_json::from_str(&stdout_str).map_err(|e| {
            format!(
                "Failed to parse llama-server JSON response: {} (response was: '{}')",
                e, stdout_str
            )
        })?;

        // Extract content field: {"content": "..."}
        if let Some(content) = parsed.get("content").and_then(|v| v.as_str()) {
            return Ok(content.to_string());
        }

        // Fallback for OpenAI compatible endpoint: {"choices": [{"text": "..."}]}
        if let Some(text) = parsed
            .get("choices")
            .and_then(|c| c.get(0))
            .and_then(|ch| ch.get("text"))
            .and_then(|t| t.as_str())
        {
            return Ok(text.to_string());
        }

        // Fallback to raw string
        Ok(stdout_str.into_owned())
    }

    /// Execute completion against `llama-cli` binary.
    fn complete_cli(
        bin_path: &Path,
        model_path: &Path,
        grammar_path: Option<&Path>,
        prompt: &str,
    ) -> Result<String, String> {
        if !bin_path.exists() {
            return Err(format!(
                "llama-cli binary not found at: {}",
                bin_path.display()
            ));
        }
        if !model_path.exists() {
            return Err(format!(
                "GGUF model file not found at: {}",
                model_path.display()
            ));
        }

        let mut cmd = Command::new(bin_path);
        cmd.arg("-m").arg(model_path);
        cmd.arg("-p").arg(prompt);
        cmd.args(["--temp", "0"]);
        cmd.args(["-n", "160"]);
        cmd.arg("--no-display-prompt");

        // Set up grammar
        let temp_grammar_file = if let Some(g_path) = grammar_path {
            cmd.arg("--grammar-file").arg(g_path);
            None
        } else {
            let temp_file = std::env::temp_dir().join("silent_exec_action.gbnf");
            let _ = fs::write(&temp_file, EMBEDDED_ACTION_GBNF);
            cmd.arg("--grammar-file").arg(&temp_file);
            Some(temp_file)
        };

        let output = cmd.output().map_err(|e| {
            format!(
                "Failed to execute llama-cli at {}: {}",
                bin_path.display(),
                e
            )
        })?;

        // Clean up temporary grammar file if created
        if let Some(p) = temp_grammar_file {
            let _ = fs::remove_file(p);
        }

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(format!(
                "llama-cli failed with exit code {:?}: {}",
                output.status.code(),
                stderr.trim()
            ));
        }

        Ok(String::from_utf8_lossy(&output.stdout).into_owned())
    }
}
