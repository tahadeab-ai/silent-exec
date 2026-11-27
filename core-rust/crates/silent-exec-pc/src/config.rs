//! Persistent Configuration & System Modes for SilentExec PC
//! Stored in `%LOCALAPPDATA%\silent-exec\config.json` on Windows
//! or `~/.config/silent-exec/config.json` on Linux.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

fn default_true() -> bool {
    true
}

fn default_language() -> String {
    "ar".to_string()
}

fn default_mode() -> String {
    "silent".to_string()
}

fn default_model_tag() -> Option<String> {
    Some("v0.2.0-model".to_string())
}

fn default_backend() -> String {
    "local_llama".to_string()
}

fn default_ollama_url() -> String {
    "http://127.0.0.1:11434".to_string()
}

fn default_ollama_model() -> String {
    "qwen3.5:4b".to_string()
}

fn default_openai_url() -> String {
    "https://api.openai.com/v1".to_string()
}

fn default_openai_model() -> String {
    "gpt-4o-mini".to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct McpServerConfig {
    pub name: String,
    pub command: String,
    #[serde(default)]
    pub args: Vec<String>,
    #[serde(default = "default_true")]
    pub enabled: bool,
}

impl McpServerConfig {
    /// Returns a normalized lowercase name for deduplication.
    #[must_use]
    pub fn normalized_name(&self) -> String {
        self.name.trim().to_ascii_lowercase()
    }

    /// Returns a normalized `command + args` signature for deduplication.
    #[must_use]
    pub fn normalized_signature(&self) -> String {
        let cmd = self.command.trim().to_ascii_lowercase();
        let args: Vec<&str> = self
            .args
            .iter()
            .map(|a| a.trim())
            .filter(|a| !a.is_empty())
            .collect();
        if args.is_empty() {
            cmd
        } else {
            format!("{} {}", cmd, args.join(" "))
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct AppConfig {
    #[serde(default = "default_language")]
    pub language: String, // "ar" | "en"

    #[serde(default = "default_mode")]
    pub mode: String, // "silent" | "interactive" | "dry_run"

    #[serde(default = "default_model_tag", skip_serializing_if = "Option::is_none")]
    pub active_model_tag: Option<String>,

    #[serde(default = "default_true")]
    pub fast_router_enabled: bool,

    /// AI backend to use: "goose_acp" | "openai_compat" | "ollama" | "local_llama"
    #[serde(default = "default_backend")]
    pub backend: String,

    /// Optional Goose provider override (e.g. "google", "groq", "openai", "ollama")
    #[serde(default)]
    pub goose_provider: String,

    /// Optional Goose model override
    #[serde(default)]
    pub goose_model: String,

    /// Ollama endpoint URL (default: http://127.0.0.1:11434)
    #[serde(default = "default_ollama_url")]
    pub ollama_url: String,

    /// Ollama model name (e.g. qwen3.5:4b or qwen3.5:9b-q4_K_M)
    #[serde(default = "default_ollama_model")]
    pub ollama_model: String,

    /// Disable `<think>` reasoning traces on Ollama `/api/chat` (`"think": false`)
    #[serde(default = "default_true")]
    pub disable_thinking: bool,

    /// Safe Mode (ON by default): passes `--exclude-tools PowerShell,Registry` to Windows-MCP
    #[serde(default = "default_true")]
    pub safe_mode: bool,

    /// Optional OmniParser URL for visual GUI grounding
    #[serde(default)]
    pub omniparser_url: String,

    /// Tools disabled by user in Desktop Tools & MCP tab
    #[serde(default)]
    pub disabled_tools: Vec<String>,

    /// OpenAI compatible API base URL
    #[serde(default = "default_openai_url")]
    pub openai_api_base: String,

    /// OpenAI compatible API model
    #[serde(default = "default_openai_model")]
    pub openai_api_model: String,

    /// OpenAI compatible API key
    #[serde(default)]
    pub openai_api_key: String,

    /// Registered MCP tool servers
    #[serde(default)]
    pub mcp_servers: Vec<McpServerConfig>,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            language: default_language(),
            mode: default_mode(),
            active_model_tag: default_model_tag(),
            fast_router_enabled: true,
            backend: default_backend(),
            goose_provider: String::new(),
            goose_model: String::new(),
            ollama_url: default_ollama_url(),
            ollama_model: default_ollama_model(),
            disable_thinking: true,
            safe_mode: true,
            omniparser_url: String::new(),
            disabled_tools: Vec::new(),
            openai_api_base: default_openai_url(),
            openai_api_model: default_openai_model(),
            openai_api_key: String::new(),
            mcp_servers: Vec::new(),
        }
    }
}

impl AppConfig {
    /// Resolves the canonical config directory for the current OS.
    #[must_use]
    pub fn default_config_dir() -> PathBuf {
        #[cfg(windows)]
        {
            if let Ok(local_appdata) = std::env::var("LOCALAPPDATA") {
                return PathBuf::from(local_appdata).join("silent-exec");
            }
        }

        #[cfg(not(windows))]
        {
            if let Ok(xdg_config) = std::env::var("XDG_CONFIG_HOME") {
                return PathBuf::from(xdg_config).join("silent-exec");
            }
        }

        // Fallback for home directory
        if let Ok(home) = std::env::var("HOME").or_else(|_| std::env::var("USERPROFILE")) {
            PathBuf::from(home).join(".config").join("silent-exec")
        } else {
            PathBuf::from(".config").join("silent-exec")
        }
    }

    /// Resolves canonical config.json file path.
    #[must_use]
    pub fn default_config_path() -> PathBuf {
        Self::default_config_dir().join("config.json")
    }

    /// Loads the configuration from the default location, or creates default if missing.
    pub fn load() -> Self {
        let path = Self::default_config_path();
        if path.is_file() {
            Self::load_from(&path).unwrap_or_default()
        } else {
            Self::default()
        }
    }

    /// Loads the configuration from a specific path.
    pub fn load_from(path: &Path) -> Result<Self, String> {
        let content = fs::read_to_string(path)
            .map_err(|e| format!("Failed to read config file {}: {}", path.display(), e))?;
        let mut cfg: Self = serde_json::from_str(&content)
            .map_err(|e| format!("Invalid config JSON structure {}: {}", path.display(), e))?;
        cfg.deduplicate_mcp_servers();
        Ok(cfg)
    }

    /// Deduplicates `mcp_servers` by normalized name or normalized command+args signature.
    pub fn deduplicate_mcp_servers(&mut self) {
        let mut seen_names = std::collections::HashSet::new();
        let mut seen_sigs = std::collections::HashSet::new();
        self.mcp_servers.retain(|srv| {
            let name_key = srv.normalized_name();
            let sig_key = srv.normalized_signature();
            if name_key.is_empty() || seen_names.contains(&name_key) || seen_sigs.contains(&sig_key)
            {
                return false;
            }
            seen_names.insert(name_key);
            seen_sigs.insert(sig_key);
            true
        });
    }

    /// Inserts or updates an MCP server without creating duplicate name or command+args entries.
    pub fn upsert_mcp_server(&mut self, server: McpServerConfig) {
        let target_name = server.normalized_name();
        let target_sig = server.normalized_signature();
        if let Some(existing) = self
            .mcp_servers
            .iter_mut()
            .find(|s| s.normalized_name() == target_name || s.normalized_signature() == target_sig)
        {
            existing.name = server.name;
            existing.command = server.command;
            existing.args = server.args;
            existing.enabled = server.enabled;
        } else {
            self.mcp_servers.push(server);
        }
        self.deduplicate_mcp_servers();
    }

    /// Persists configuration to default location.
    pub fn save(&self) -> Result<(), String> {
        let path = Self::default_config_path();
        self.save_to(&path)
    }

    /// Persists configuration to a specific path.
    pub fn save_to(&self, path: &Path) -> Result<(), String> {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| {
                format!(
                    "Failed to create config directory {}: {}",
                    parent.display(),
                    e
                )
            })?;
        }
        let serialized = serde_json::to_string_pretty(self)
            .map_err(|e| format!("Failed to serialize config: {}", e))?;
        fs::write(path, serialized)
            .map_err(|e| format!("Failed to write config file {}: {}", path.display(), e))
    }

    /// Validates and updates configuration fields.
    pub fn update(
        &mut self,
        lang: Option<&str>,
        mode: Option<&str>,
        model_tag: Option<&str>,
        fast_router: Option<bool>,
    ) -> Result<(), String> {
        if let Some(l) = lang {
            let lower = l.trim().to_lowercase();
            if lower == "ar" || lower == "en" {
                self.language = lower;
            } else {
                return Err(format!(
                    "Invalid language '{}'. Valid options: 'ar', 'en'",
                    l
                ));
            }
        }

        if let Some(m) = mode {
            let lower = m.trim().to_lowercase();
            if lower == "silent" || lower == "interactive" || lower == "dry_run" {
                self.mode = lower;
            } else {
                return Err(format!(
                    "Invalid mode '{}'. Valid options: 'silent', 'interactive', 'dry_run'",
                    m
                ));
            }
        }

        if let Some(tag) = model_tag {
            self.active_model_tag = Some(tag.trim().to_string());
        }

        if let Some(fr) = fast_router {
            self.fast_router_enabled = fr;
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_deduplicate_mcp_servers_by_signature_and_name() {
        let mut cfg = AppConfig::default();
        cfg.mcp_servers = vec![
            McpServerConfig {
                name: "filesystem".to_string(),
                command: "npx".to_string(),
                args: vec![
                    "-y".to_string(),
                    "@modelcontextprotocol/server-filesystem".to_string(),
                    ".".to_string(),
                ],
                enabled: true,
            },
            McpServerConfig {
                name: "Filesystem MCP".to_string(),
                command: "NPX".to_string(),
                args: vec![
                    "-y".to_string(),
                    "@modelcontextprotocol/server-filesystem".to_string(),
                    ".".to_string(),
                ],
                enabled: true,
            },
            McpServerConfig {
                name: "fetch".to_string(),
                command: "uvx".to_string(),
                args: vec!["mcp-server-fetch".to_string()],
                enabled: true,
            },
        ];

        cfg.deduplicate_mcp_servers();
        assert_eq!(cfg.mcp_servers.len(), 2);
        assert_eq!(cfg.mcp_servers[0].name, "filesystem");
        assert_eq!(cfg.mcp_servers[1].name, "fetch");
    }
}
