//! Deterministic Compound & Built-In Skills Router for SilentExec PC
//! Built-in deterministic skills for SilentExec: resolves everyday multi-step tasks
//! (YouTube Search, Web Search, and custom user routines in `skills.json`)
//! before falling back to local LLM inference.

use crate::config::AppConfig;
use crate::indexer::normalize_arabic_english;
use serde::{Deserialize, Serialize};
use silent_exec_core::{ActionCommand, ActionType, Platform};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct SkillDefinition {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub triggers: Vec<String>,
    pub steps: Vec<ActionCommand>,
    #[serde(default)]
    pub description: String,
    #[serde(default)]
    pub created_at: u64,
}

#[derive(Debug, Clone, Default)]
pub struct SkillStore {
    path: PathBuf,
    skills: Vec<SkillDefinition>,
}

impl SkillStore {
    pub fn default_path() -> PathBuf {
        AppConfig::default_config_dir().join("skills.json")
    }

    pub fn load_from(path: &Path) -> Result<Self, String> {
        if !path.exists() {
            return Ok(Self {
                path: path.to_path_buf(),
                skills: Vec::new(),
            });
        }
        let content = fs::read_to_string(path)
            .map_err(|e| format!("Failed to read skills file from {}: {}", path.display(), e))?;
        let skills: Vec<SkillDefinition> = serde_json::from_str(&content)
            .map_err(|e| format!("Failed to parse skills JSON from {}: {}", path.display(), e))?;
        Ok(Self {
            path: path.to_path_buf(),
            skills,
        })
    }

    pub fn save_to_disk(&self) -> Result<(), String> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("Failed to create skills directory: {}", e))?;
        }
        let json = serde_json::to_string_pretty(&self.skills)
            .map_err(|e| format!("Failed to serialize skills: {}", e))?;
        fs::write(&self.path, json).map_err(|e| {
            format!(
                "Failed to write skills file to {}: {}",
                self.path.display(),
                e
            )
        })
    }

    pub fn list(&self) -> &[SkillDefinition] {
        &self.skills
    }

    pub fn save_skill(
        &mut self,
        name: &str,
        triggers: Vec<String>,
        steps: Vec<ActionCommand>,
        description: &str,
    ) -> Result<SkillDefinition, String> {
        let clean_name = name.trim();
        if clean_name.is_empty() {
            return Err("Skill name cannot be empty".to_string());
        }
        if steps.is_empty() {
            return Err("Skill must contain at least one step".to_string());
        }

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let mut all_triggers = Vec::with_capacity(triggers.len() + 1);
        all_triggers.push(clean_name.to_string());
        for t in triggers {
            let trimmed = t.trim();
            if !trimmed.is_empty()
                && !all_triggers
                    .iter()
                    .any(|existing| existing.eq_ignore_ascii_case(trimmed))
            {
                all_triggers.push(trimmed.to_string());
            }
        }

        if let Some(existing) = self
            .skills
            .iter_mut()
            .find(|s| s.name.eq_ignore_ascii_case(clean_name))
        {
            existing.triggers = all_triggers;
            existing.steps = steps;
            existing.description = description.trim().to_string();
            existing.created_at = now;
            let cloned = existing.clone();
            self.save_to_disk()?;
            return Ok(cloned);
        }

        let skill = SkillDefinition {
            id: format!("skill_{}_{}", now, self.skills.len() + 1),
            name: clean_name.to_string(),
            triggers: all_triggers,
            steps,
            description: description.trim().to_string(),
            created_at: now,
        };
        self.skills.push(skill.clone());
        self.save_to_disk()?;
        Ok(skill)
    }

    pub fn delete_skill(&mut self, id_or_name: &str) -> Result<bool, String> {
        let target = id_or_name.trim();
        let before = self.skills.len();
        self.skills
            .retain(|s| s.id != target && !s.name.eq_ignore_ascii_case(target));
        let removed = self.skills.len() < before;
        if removed {
            self.save_to_disk()?;
        }
        Ok(removed)
    }

    pub fn find_matching_skill(&self, query: &str) -> Option<&SkillDefinition> {
        let norm_query = BuiltInSkills::normalize_text(query);
        if norm_query.is_empty() {
            return None;
        }
        self.skills.iter().find(|s| {
            BuiltInSkills::normalize_text(&s.name) == norm_query
                || s.triggers
                    .iter()
                    .any(|t| BuiltInSkills::normalize_text(t) == norm_query)
        })
    }
}

pub struct BuiltInSkills;

impl BuiltInSkills {
    #[must_use]
    pub fn normalize_text(input: &str) -> String {
        let norm = normalize_arabic_english(input.trim());
        norm.split_whitespace().collect::<Vec<_>>().join(" ")
    }

    fn url_encode(q: &str) -> String {
        let mut out = String::with_capacity(q.len() * 2);
        for b in q.bytes() {
            if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') {
                out.push(b as char);
            } else if b == b' ' {
                out.push('+');
            } else {
                out.push_str(&format!("%{:02X}", b));
            }
        }
        out
    }

    fn extract_query_after_prefix(norm_text: &str, prefixes: &[&str]) -> Option<String> {
        for prefix in prefixes {
            let norm_prefix = format!("{} ", Self::normalize_text(prefix));
            if let Some(rest) = norm_text.strip_prefix(&norm_prefix) {
                let q = rest.trim();
                if !q.is_empty() {
                    return Some(q.to_string());
                }
            }
        }
        None
    }

    fn has_chained_followup_actions(norm_text: &str) -> bool {
        let padded = format!(" {} ", norm_text);
        const CHAINED_MARKERS: &[&str] = &[
            " وافتح ",
            " و افتح ",
            " واضغط ",
            " و اضغط ",
            " واكتب ",
            " و اكتب ",
            " وانزل ",
            " و انزل ",
            " وارسل ",
            " و ارسل ",
            " ولايك ",
            " و لايك ",
            " وشغل ",
            " و شغل ",
            " ثم ",
            " بعدين ",
            " بعدها ",
            " and open ",
            " and click ",
            " and tap ",
            " and like ",
            " and type ",
            " and scroll ",
            " and send ",
            " then ",
        ];
        CHAINED_MARKERS.iter().any(|m| padded.contains(m))
    }

    #[must_use]
    pub fn resolve(query: &str) -> Option<Vec<ActionCommand>> {
        let store = SkillStore::load_from(&SkillStore::default_path()).ok();
        Self::resolve_with_store(query, store.as_ref())
    }

    #[must_use]
    pub fn resolve_with_store(
        query: &str,
        store: Option<&SkillStore>,
    ) -> Option<Vec<ActionCommand>> {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return None;
        }

        // 1. Custom User Routines / Skills (skills.json)
        if let Some(s) = store {
            if let Some(skill) = s.find_matching_skill(trimmed) {
                if !skill.steps.is_empty() {
                    return Some(skill.steps.clone());
                }
            }
        }

        let norm = Self::normalize_text(trimmed);

        // Compound-intent guard: let the Thinking ReAct Agent Loop handle multi-step goals
        if Self::has_chained_followup_actions(&norm) {
            return None;
        }

        // 2. YouTube Search Skill
        let yt_prefixes = [
            "افتح اليوتيوب و ابحث عن",
            "افتح اليوتيوب وابحث عن",
            "افتح يوتيوب و ابحث عن",
            "افتح يوتيوب وابحث عن",
            "شغل اليوتيوب و ابحث عن",
            "شغل اليوتيوب وابحث عن",
            "شغل يوتيوب و ابحث عن",
            "شغل يوتيوب وابحث عن",
            "افتح اليوتيوب و ابحث",
            "افتح اليوتيوب وابحث",
            "افتح يوتيوب ابحث عن",
            "ابحث في اليوتيوب عن",
            "ابحث في يوتيوب عن",
            "دور في اليوتيوب عن",
            "دور في يوتيوب عن",
            "open youtube and search for",
            "open youtube and search",
            "launch youtube and search for",
            "launch youtube and search",
            "search youtube for",
            "youtube search for",
            "youtube search",
        ];

        if let Some(q) = Self::extract_query_after_prefix(&norm, &yt_prefixes) {
            let url = format!(
                "https://www.youtube.com/results?search_query={}",
                Self::url_encode(&q)
            );
            return Some(vec![ActionCommand {
                thought: None,
                action: ActionType::OpenUrl,
                target: Some(url),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 1.0,
            }]);
        }

        // 3. Web Search Skill
        let web_prefixes = [
            "افتح المتصفح و ابحث عن",
            "افتح المتصفح وابحث عن",
            "افتح المتصفح و ابحث",
            "افتح المتصفح وابحث",
            "افتح جوجل و ابحث عن",
            "افتح جوجل وابحث عن",
            "افتح قوقل و ابحث عن",
            "افتح قوقل وابحث عن",
            "ابحث في جوجل عن",
            "ابحث في قوقل عن",
            "ابحث في المتصفح عن",
            "دور في جوجل عن",
            "open browser and search for",
            "open browser and search",
            "open chrome and search for",
            "open chrome and search",
            "search google for",
            "search the web for",
            "search web for",
            "google search",
        ];

        if let Some(q) = Self::extract_query_after_prefix(&norm, &web_prefixes) {
            let url = format!("https://www.google.com/search?q={}", Self::url_encode(&q));
            return Some(vec![ActionCommand {
                thought: None,
                action: ActionType::OpenUrl,
                target: Some(url),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 1.0,
            }]);
        }

        None
    }
}
