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
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            language: default_language(),
            mode: default_mode(),
            active_model_tag: default_model_tag(),
            fast_router_enabled: true,
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
        serde_json::from_str(&content)
            .map_err(|e| format!("Invalid config JSON structure {}: {}", path.display(), e))
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
