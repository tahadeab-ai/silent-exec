//! Deterministic Fast Rule Router for SilentExec PC
//! Evaluates obvious single-step commands in <15ms (<0.1ms typical),
//! bypassing LLM inference for high-frequency navigation and system controls.
//! Falls back to `None` for complex/natural commands.

use crate::indexer::normalize_arabic_english;
use silent_exec_core::{ActionCommand, ActionType, Platform};

pub struct FastRuleRouter;

impl FastRuleRouter {
    /// Detects compound, multi-step queries that must bypass deterministic fast routing
    /// and instead be processed by the Multi-Step Planner / LLM.
    #[must_use]
    pub fn is_compound_command(query: &str) -> bool {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return false;
        }

        // Exceeds 6 words
        let word_count = trimmed.split_whitespace().count();
        if word_count > 6 {
            return true;
        }

        // Punctuation / clause separators
        if trimmed.contains(',') || trimmed.contains('،') {
            return true;
        }

        // English multi-step connectors
        let lower = trimmed.to_lowercase();
        let en_connectors = [" and ", " then ", " after "];
        for conn in &en_connectors {
            if lower.contains(conn) {
                return true;
            }
        }

        // Arabic multi-step connectors (phrases)
        let ar_phrases = [" و ", " ثم ", " بعدين ", " بعدها "];
        for phrase in &ar_phrases {
            if trimmed.contains(phrase) {
                return true;
            }
        }

        // Arabic connector tokens
        let ar_connector_tokens = ["ثم", "بعدين", "بعدها", "و"];
        for tok in trimmed.split_whitespace() {
            if ar_connector_tokens.contains(&tok) {
                return true;
            }
            let norm_tok = normalize_arabic_english(tok);
            if ar_connector_tokens.contains(&norm_tok.as_str()) {
                return true;
            }
        }

        // Arabic multi-step verb clauses / prefixes (و + verb)
        let ar_verb_clauses = [
            "وافتح",
            "وابحث",
            "واكتب",
            "وارسل",
            "وأرسل",
            "واحذف",
            "وأحذف",
            "وانسخ",
            "واضغط",
            "وشغل",
            "ونزل",
        ];
        for tok in trimmed.split_whitespace() {
            let norm_tok = normalize_arabic_english(tok);
            for &verb in &ar_verb_clauses {
                let norm_verb = normalize_arabic_english(verb);
                if tok == verb
                    || norm_tok == norm_verb
                    || tok.starts_with(verb)
                    || norm_tok.starts_with(&norm_verb)
                {
                    return true;
                }
            }
        }

        false
    }

    /// CamelCase alias for Kotlin/cross-language API parity.
    #[must_use]
    #[allow(non_snake_case)]
    pub fn isCompoundCommand(query: &str) -> bool {
        Self::is_compound_command(query)
    }

    /// Attempts to route the user query deterministically.
    /// Returns `Some(ActionCommand)` if matched, or `None` to fall back to LLM inference.
    #[must_use]
    pub fn route(query: &str) -> Option<ActionCommand> {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return None;
        }

        if Self::is_compound_command(trimmed) {
            return None;
        }

        let normalized = normalize_arabic_english(trimmed);
        let tokens: Vec<&str> = normalized.split_whitespace().collect();
        if tokens.is_empty() {
            return None;
        }

        // 1. UI Back
        if Self::is_back_command(&normalized, &tokens) {
            return Some(ActionCommand {
                thought: None,
                action: ActionType::UiBack,
                target: None,
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Any,
                confidence: 1.0,
            });
        }

        // 2. UI Home / Desktop
        if Self::is_home_command(&normalized, &tokens) {
            return Some(ActionCommand {
                thought: None,
                action: ActionType::UiHome,
                target: None,
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 1.0,
            });
        }

        // 3. UI Scroll
        if let Some(direction) = Self::is_scroll_command(&normalized, &tokens) {
            return Some(ActionCommand {
                thought: None,
                action: ActionType::UiScroll,
                actions: None,
                target: None,
                folder: None,
                args: Some(serde_json::json!({ "direction": direction })),
                platform: Platform::Any,
                confidence: 1.0,
            });
        }

        // 4. Set Volume
        if let Some(level) = Self::is_volume_command(&normalized, &tokens) {
            return Some(ActionCommand {
                thought: None,
                action: ActionType::SetVolume,
                actions: None,
                target: None,
                folder: None,
                args: Some(serde_json::json!({ "level": level })),
                platform: Platform::Pc,
                confidence: 1.0,
            });
        }

        // 5. Open Folder
        if let Some(folder_target) = Self::is_open_folder_command(&normalized, &tokens) {
            return Some(ActionCommand {
                thought: None,
                action: ActionType::OpenFolder,
                target: Some(folder_target),
                folder: None,
                args: None,
                actions: None,
                platform: Platform::Pc,
                confidence: 1.0,
            });
        }

        None
    }

    fn is_back_command(norm: &str, tokens: &[&str]) -> bool {
        let single_word_matches = ["ارجع", "رجوع", "باك", "back", "return"];
        if tokens.len() == 1 && single_word_matches.contains(&tokens[0]) {
            return true;
        }

        let phrases = [
            "ارجع للخلف",
            "ارجع ورا",
            "ارجع خطوه لورا",
            "سوي باك",
            "سوي رجوع",
            "الرجوع للخلف",
            "go back",
            "navigate back",
            "step back",
        ];

        for phrase in phrases {
            if norm == phrase || norm == normalize_arabic_english(phrase) {
                return true;
            }
        }

        false
    }

    fn is_home_command(norm: &str, tokens: &[&str]) -> bool {
        let single_word_matches = ["الرئيسيه", "الهوم", "desktop", "home"];
        if tokens.len() == 1 && single_word_matches.contains(&tokens[0]) {
            return true;
        }

        let phrases = [
            "الشاشه الرئيسيه",
            "سطح المكتب",
            "روح للشاشه الرئيسيه",
            "ارجع للشاشه الرئيسيه",
            "ارجع لسطح المكتب",
            "افتح الشاشه الرئيسيه",
            "home screen",
            "go home",
            "go to home screen",
            "show desktop",
            "minimize all",
        ];

        for phrase in phrases {
            if norm == phrase || norm == normalize_arabic_english(phrase) {
                return true;
            }
        }

        false
    }

    fn is_scroll_command(norm: &str, tokens: &[&str]) -> Option<&'static str> {
        // Down
        let down_exact = ["انزل", "تحت", "للRoot", "down"];
        if tokens.len() == 1 && down_exact.contains(&tokens[0]) {
            return Some("down");
        }
        let down_phrases = [
            "انزل لتحت",
            "انزل تحت",
            "للRoot",
            "للRoot الصفحه",
            "مرر للRoot",
            "تمرير للRoot",
            "scroll down",
            "swipe down",
        ];
        for phrase in down_phrases {
            let p_norm = normalize_arabic_english(phrase);
            if norm == phrase || norm == p_norm {
                return Some("down");
            }
        }

        // Up
        let up_exact = ["اطلع", "فوق", "up"];
        if tokens.len() == 1 && up_exact.contains(&tokens[0]) {
            return Some("up");
        }
        let up_phrases = [
            "اطلع لفوق",
            "اطلع فوق",
            "للاعلى",
            "مرر للاعلى",
            "تمرير للاعلى",
            "scroll up",
            "swipe up",
        ];
        for phrase in up_phrases {
            let p_norm = normalize_arabic_english(phrase);
            if norm == phrase || norm == p_norm {
                return Some("up");
            }
        }

        // Right
        let right_exact = ["يمين", "right"];
        if tokens.len() == 1 && right_exact.contains(&tokens[0]) {
            return Some("right");
        }
        let right_phrases = ["لليمين", "مرر لليمين", "scroll right", "swipe right"];
        for phrase in right_phrases {
            let p_norm = normalize_arabic_english(phrase);
            if norm == phrase || norm == p_norm {
                return Some("right");
            }
        }

        // Left
        let left_exact = ["يسار", "شمال", "left"];
        if tokens.len() == 1 && left_exact.contains(&tokens[0]) {
            return Some("left");
        }
        let left_phrases = [
            "لليسار",
            "للشمال",
            "مرر لليسار",
            "scroll left",
            "swipe left",
        ];
        for phrase in left_phrases {
            let p_norm = normalize_arabic_english(phrase);
            if norm == phrase || norm == p_norm {
                return Some("left");
            }
        }

        None
    }

    fn is_volume_command(_norm: &str, tokens: &[&str]) -> Option<i64> {
        let has_volume_kw = tokens.iter().any(|&t| {
            t == "الصوت"
                || t == "صوت"
                || t == "volume"
                || t == "vol"
                || t == "ارفع"
                || t == "وطي"
                || t == "اخفض"
        });

        if !has_volume_kw {
            return None;
        }

        // Check for numeric token (e.g. "80", "50", "100")
        for token in tokens {
            let clean_num: String = token.chars().filter(|c| c.is_ascii_digit()).collect();
            if let Ok(val) = clean_num.parse::<i64>() {
                if (0..=100).contains(&val) {
                    return Some(val);
                }
            }
        }

        None
    }

    fn is_open_folder_command(norm: &str, tokens: &[&str]) -> Option<String> {
        let standard_folders = [
            ("تنزيلات", "Downloads"),
            ("التنزيلات", "Downloads"),
            ("داونلودز", "Downloads"),
            ("downloads", "Downloads"),
            ("مستندات", "Documents"),
            ("المستندات", "Documents"),
            ("دوكيومنتس", "Documents"),
            ("documents", "Documents"),
            ("صور", "Pictures"),
            ("الصور", "Pictures"),
            ("pictures", "Pictures"),
            ("موسيقى", "Music"),
            ("الموسيقى", "Music"),
            ("music", "Music"),
            ("فيديوهات", "Videos"),
            ("الفيديوهات", "Videos"),
            ("videos", "Videos"),
            ("مشاريع", "Projects"),
            ("المشاريع", "Projects"),
            ("projects", "Projects"),
            ("سطح المكتب", "Desktop"),
            ("desktop", "Desktop"),
        ];

        for (pattern, target) in standard_folders {
            let p_norm = normalize_arabic_english(pattern);
            if norm == p_norm
                || norm == format!("افتح {}", p_norm)
                || norm == format!("مجلد {}", p_norm)
                || norm == format!("افتح مجلد {}", p_norm)
                || norm == format!("open {}", pattern)
                || norm == format!("open {} folder", pattern)
                || norm == format!("{} folder", pattern)
            {
                return Some(target.to_string());
            }
        }

        // Check if starts with "افتح" or "open" followed by standard folder
        if tokens.len() == 2 && (tokens[0] == "افتح" || tokens[0] == "open") {
            for (pattern, target) in standard_folders {
                let p_norm = normalize_arabic_english(pattern);
                if tokens[1] == p_norm || tokens[1] == pattern {
                    return Some(target.to_string());
                }
            }
        }

        None
    }
}
