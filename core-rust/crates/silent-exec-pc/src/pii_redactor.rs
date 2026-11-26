//! Clean-Room On-Device PII Privacy Masker (MIT)
//! Pseudonymizes credit cards, emails, phone numbers, and 4-8 digit OTP codes
//! into indexed placeholders before sending UI trees over LAN or to external providers.

use serde_json::Value;

#[derive(Debug, Clone, Copy)]
pub struct PiiRedactor {
    pub enabled: bool,
}

impl Default for PiiRedactor {
    fn default() -> Self {
        Self { enabled: true }
    }
}

impl PiiRedactor {
    #[must_use]
    pub fn new(enabled: bool) -> Self {
        Self { enabled }
    }

    /// Redacts PII from a single string, returning the sanitized text.
    #[must_use]
    pub fn redact(&self, input: &str) -> String {
        if !self.enabled || input.is_empty() {
            return input.to_string();
        }
        let mut text = redact_emails(input);
        text = redact_cards(&text);
        text = redact_phones(&text);
        text = redact_otps(&text);
        text
    }

    /// Recursively redacts all string values inside a JSON `Value`.
    #[must_use]
    pub fn redact_json(&self, val: &Value) -> Value {
        if !self.enabled {
            return val.clone();
        }
        match val {
            Value::String(s) => Value::String(self.redact(s)),
            Value::Array(arr) => Value::Array(arr.iter().map(|v| self.redact_json(v)).collect()),
            Value::Object(map) => {
                let mut out = serde_json::Map::new();
                for (k, v) in map {
                    out.insert(k.clone(), self.redact_json(v));
                }
                Value::Object(out)
            }
            _ => val.clone(),
        }
    }
}

fn redact_emails(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut counter = 0usize;
    let bytes = input.as_bytes();
    let mut i = 0;

    while i < bytes.len() {
        if is_email_char(bytes[i]) {
            let start = i;
            while i < bytes.len() && is_email_char(bytes[i]) {
                i += 1;
            }
            if i < bytes.len() && bytes[i] == b'@' {
                let after_at = i + 1;
                let mut j = after_at;
                let mut has_dot = false;
                while j < bytes.len() && (is_domain_char(bytes[j]) || bytes[j] == b'.') {
                    if bytes[j] == b'.' {
                        has_dot = true;
                    }
                    j += 1;
                }
                if has_dot && j > after_at + 2 && bytes[j - 1] != b'.' {
                    counter += 1;
                    out.push_str(&format!("[REDACTED_EMAIL_{}]", counter));
                    i = j;
                    continue;
                }
            }
            out.push_str(&input[start..i]);
        } else {
            let ch = input[i..].chars().next().unwrap();
            out.push(ch);
            i += ch.len_utf8();
        }
    }
    out
}

fn is_email_char(b: u8) -> bool {
    b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'%' | b'+' | b'-')
}

fn is_domain_char(b: u8) -> bool {
    b.is_ascii_alphanumeric() || b == b'-'
}

fn redact_cards(input: &str) -> String {
    redact_digit_sequences(input, |digits, raw| {
        if (13..=19).contains(&digits.len()) && !raw.starts_with('+') {
            Some("CARD")
        } else {
            None
        }
    })
}

fn redact_phones(input: &str) -> String {
    redact_digit_sequences(input, |digits, raw| {
        let is_intl = raw.starts_with('+') && (8..=15).contains(&digits.len());
        let is_local = (9..=12).contains(&digits.len())
            && (raw.contains('-')
                || raw.contains(' ')
                || digits.starts_with("05")
                || digits.starts_with("77")
                || digits.starts_with("73"));
        if is_intl || is_local {
            Some("PHONE")
        } else {
            None
        }
    })
}

fn redact_otps(input: &str) -> String {
    redact_digit_sequences(input, |digits, raw| {
        if (4..=8).contains(&digits.len()) && digits.len() == raw.len() {
            Some("OTP")
        } else {
            None
        }
    })
}

fn redact_digit_sequences<F>(input: &str, classifier: F) -> String
where
    F: Fn(&str, &str) -> Option<&'static str>,
{
    let mut out = String::with_capacity(input.len());
    let mut counters = std::collections::HashMap::<&'static str, usize>::new();
    let chars: Vec<(usize, char)> = input.char_indices().collect();
    let mut idx = 0;

    while idx < chars.len() {
        let (byte_pos, ch) = chars[idx];
        let prev_is_alnum = idx > 0 && chars[idx - 1].1.is_ascii_alphanumeric();
        if !prev_is_alnum
            && (ch.is_ascii_digit()
                || (ch == '+' && idx + 1 < chars.len() && chars[idx + 1].1.is_ascii_digit()))
        {
            let start_byte = byte_pos;
            let mut end_idx = idx + 1;
            while end_idx < chars.len() {
                let c = chars[end_idx].1;
                if c.is_ascii_digit() || matches!(c, '-' | ' ') {
                    end_idx += 1;
                } else {
                    break;
                }
            }
            while end_idx > idx + 1 && matches!(chars[end_idx - 1].1, '-' | ' ') {
                end_idx -= 1;
            }
            let end_byte = if end_idx < chars.len() {
                chars[end_idx].0
            } else {
                input.len()
            };
            let next_is_alnum = end_idx < chars.len() && chars[end_idx].1.is_ascii_alphanumeric();
            let raw = &input[start_byte..end_byte];
            let digits: String = raw.chars().filter(|c| c.is_ascii_digit()).collect();
            if !next_is_alnum {
                if let Some(kind) = classifier(&digits, raw) {
                    let count = counters.entry(kind).or_insert(0);
                    *count += 1;
                    out.push_str(&format!("[REDACTED_{}_{}]", kind, *count));
                    idx = end_idx;
                    continue;
                }
            }
            out.push_str(raw);
            idx = end_idx;
        } else {
            out.push(ch);
            idx += 1;
        }
    }
    out
}
