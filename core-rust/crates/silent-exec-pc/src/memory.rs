//! Persistent Memory Store for SilentExec
//! Stores bounded, structured memories in `%LOCALAPPDATA%\silent-exec\memory.json`
//! (Linux: `~/.config/silent-exec/memory.json`).
//! Automatically injects the top relevant memories into the agent's system context before inference.

use crate::config::AppConfig;
use crate::indexer::normalize_arabic_english;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

pub const MAX_MEMORIES: usize = 200;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct MemoryItem {
    pub id: String,
    pub key: String,
    pub content: String,
    pub category: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MemoryStore {
    #[serde(default)]
    pub memories: Vec<MemoryItem>,
}

fn current_timestamp_iso() -> String {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    let seconds_in_day = now % 86400;
    let days = now / 86400;

    let mut year = 1970;
    let mut d = days;
    loop {
        let leap = if (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0) {
            366
        } else {
            365
        };
        if d < leap {
            break;
        }
        d -= leap;
        year += 1;
    }

    let leap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    let month_days = [
        31,
        if leap { 29 } else { 28 },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ];
    let mut month = 1;
    for &md in &month_days {
        if d < md {
            break;
        }
        d -= md;
        month += 1;
    }
    let day = d + 1;
    let hour = seconds_in_day / 3600;
    let minute = (seconds_in_day % 3600) / 60;
    let second = seconds_in_day % 60;

    format!(
        "{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z",
        year, month, day, hour, minute, second
    )
}

fn generate_memory_id(key: &str) -> String {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis();
    let sanitized_key: String = key
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == '_' || *c == '-')
        .collect();
    let prefix = if sanitized_key.is_empty() {
        "mem"
    } else {
        &sanitized_key
    };
    format!("{}_{:x}", prefix, now)
}

impl MemoryStore {
    #[must_use]
    pub fn default_path() -> PathBuf {
        AppConfig::default_config_dir().join("memory.json")
    }

    pub fn load_from(path: &Path) -> Result<Self, String> {
        if !path.is_file() {
            return Ok(Self::default());
        }
        let content = fs::read_to_string(path)
            .map_err(|e| format!("Failed to read memory file from {}: {}", path.display(), e))?;
        serde_json::from_str(&content)
            .map_err(|e| format!("Failed to parse memory JSON from {}: {}", path.display(), e))
    }

    pub fn save_to(&self, path: &Path) -> Result<(), String> {
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let content = serde_json::to_string_pretty(self)
            .map_err(|e| format!("Failed to serialize memory store: {}", e))?;
        fs::write(path, content)
            .map_err(|e| format!("Failed to write memory file to {}: {}", path.display(), e))
    }

    pub fn list(&self) -> Vec<MemoryItem> {
        self.memories.clone()
    }

    pub fn save_item(&mut self, key: &str, content: &str, category: Option<&str>) -> MemoryItem {
        let cat = category.unwrap_or("general").trim();
        let cat_str = if cat.is_empty() { "general" } else { cat };
        let timestamp = current_timestamp_iso();

        if let Some(existing) = self
            .memories
            .iter_mut()
            .find(|m| m.key.eq_ignore_ascii_case(key.trim()))
        {
            existing.content = content.to_string();
            existing.category = cat_str.to_string();
            existing.updated_at = timestamp;
            return existing.clone();
        }

        let item = MemoryItem {
            id: generate_memory_id(key),
            key: key.trim().to_string(),
            content: content.to_string(),
            category: cat_str.to_string(),
            updated_at: timestamp,
        };

        self.memories.insert(0, item.clone());
        if self.memories.len() > MAX_MEMORIES {
            self.memories.truncate(MAX_MEMORIES);
        }

        item
    }

    pub fn delete_item(&mut self, id_or_key: &str) -> bool {
        let trimmed = id_or_key.trim();
        let initial_len = self.memories.len();
        self.memories
            .retain(|m| m.id != trimmed && !m.key.eq_ignore_ascii_case(trimmed));
        self.memories.len() < initial_len
    }

    pub fn get_relevant(&self, query: &str, limit: usize) -> Vec<MemoryItem> {
        if self.memories.is_empty() || limit == 0 {
            return Vec::new();
        }

        let trimmed_query = query.trim();
        if trimmed_query.is_empty() {
            return self.memories.iter().take(limit).cloned().collect();
        }

        let norm_query = normalize_arabic_english(trimmed_query);
        let query_tokens: Vec<&str> = norm_query.split_whitespace().collect();

        let mut scored: Vec<(i32, MemoryItem)> = self
            .memories
            .iter()
            .map(|item| {
                let mut score = 0i32;
                let norm_key = normalize_arabic_english(&item.key);
                let norm_content = normalize_arabic_english(&item.content);
                let norm_category = normalize_arabic_english(&item.category);

                if norm_key == norm_query {
                    score += 100;
                } else if norm_key.contains(&norm_query) || norm_query.contains(&norm_key) {
                    score += 50;
                }

                for token in &query_tokens {
                    if token.is_empty() {
                        continue;
                    }
                    if norm_key.contains(token) {
                        score += 30;
                    }
                    if norm_category.contains(token) {
                        score += 20;
                    }
                    if norm_content.contains(token) {
                        score += 10;
                    }
                }

                (score, item.clone())
            })
            .collect();

        // Sort descending by score; if scores equal, keep original order (recency)
        scored.sort_by_key(|a| std::cmp::Reverse(a.0));

        let matches: Vec<MemoryItem> = scored
            .into_iter()
            .filter(|(s, _)| *s > 0)
            .take(limit)
            .map(|(_, item)| item)
            .collect();

        if matches.is_empty() {
            // Graceful fallback to most recent memories for cross-lingual queries
            self.memories.iter().take(limit).cloned().collect()
        } else {
            matches
        }
    }

    pub fn format_for_context(&self, query: &str, limit: usize) -> String {
        let relevant = self.get_relevant(query, limit);
        if relevant.is_empty() {
            return String::new();
        }

        let mut out = String::from("[Persistent Memories]\n");
        for item in relevant {
            out.push_str(&format!(
                "- ({}) {}: {}\n",
                item.category, item.key, item.content
            ));
        }
        out
    }
}

static MEMORY_LOCK: Mutex<()> = Mutex::new(());

pub fn list_memories() -> Result<Vec<MemoryItem>, String> {
    let _guard = MEMORY_LOCK.lock().map_err(|e| e.to_string())?;
    let path = MemoryStore::default_path();
    let store = MemoryStore::load_from(&path)?;
    Ok(store.list())
}

pub fn save_memory(key: &str, content: &str, category: Option<&str>) -> Result<MemoryItem, String> {
    let _guard = MEMORY_LOCK.lock().map_err(|e| e.to_string())?;
    let path = MemoryStore::default_path();
    let mut store = MemoryStore::load_from(&path)?;
    let item = store.save_item(key, content, category);
    store.save_to(&path)?;
    Ok(item)
}

pub fn delete_memory(id_or_key: &str) -> Result<bool, String> {
    let _guard = MEMORY_LOCK.lock().map_err(|e| e.to_string())?;
    let path = MemoryStore::default_path();
    let mut store = MemoryStore::load_from(&path)?;
    let deleted = store.delete_item(id_or_key);
    if deleted {
        store.save_to(&path)?;
    }
    Ok(deleted)
}

pub fn get_relevant_memories_prompt(query: &str, limit: usize) -> String {
    let path = MemoryStore::default_path();
    let store = MemoryStore::load_from(&path).unwrap_or_default();
    store.format_for_context(query, limit)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;

    #[test]
    fn test_memory_crud_and_relevance() {
        let temp_dir = env::temp_dir().join(format!(
            "silent_exec_mem_test_{:x}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_millis()
        ));
        let mem_file = temp_dir.join("memory.json");

        let mut store = MemoryStore::load_from(&mem_file).expect("Load empty store");
        assert!(store.list().is_empty());

        // 1. Save memories
        let m1 = store.save_item(
            "user_language",
            "User prefers Arabic and English",
            Some("preference"),
        );
        let m2 = store.save_item("downloads_path", "D:\\Projects\\Downloads", Some("fact"));
        store.save_to(&mem_file).expect("Save store");

        assert_eq!(store.list().len(), 2);
        assert_eq!(m1.key, "user_language");
        assert_eq!(m2.key, "downloads_path");

        // 2. Update existing key
        let m1_updated = store.save_item(
            "user_language",
            "User strictly speaks Yemeni Arabic",
            Some("preference"),
        );
        assert_eq!(m1_updated.content, "User strictly speaks Yemeni Arabic");
        assert_eq!(store.list().len(), 2);

        // 3. Relevance search
        let relevant = store.get_relevant("language", 2);
        assert!(!relevant.is_empty());
        assert_eq!(relevant[0].key, "user_language");

        // 4. Context format
        let prompt_context = store.format_for_context("language", 2);
        assert!(prompt_context.contains("[Persistent Memories]"));
        assert!(prompt_context.contains("user_language"));

        // 5. Delete memory
        let deleted = store.delete_item("downloads_path");
        assert!(deleted);
        assert_eq!(store.list().len(), 1);

        let _ = fs::remove_dir_all(&temp_dir);
    }
}
