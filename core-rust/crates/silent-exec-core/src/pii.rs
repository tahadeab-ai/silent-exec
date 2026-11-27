//! Clean-Room MIT PII & Secret Redaction Module for SilentExec Core.
//! Redacts Luhn-valid credit cards, OTP codes, API keys/Bearer tokens,
//! emails, phone numbers, and password fields before remote cloud calls
//! without corrupting valid JSON/tool-call syntax.

/// Validates a digit string with the Luhn checksum algorithm.
#[must_use]
pub fn is_luhn_valid(digits: &str) -> bool {
    let nums: Vec<u32> = digits.chars().filter_map(|c| c.to_digit(10)).collect();
    if nums.len() < 13 || nums.len() > 19 {
        return false;
    }
    let mut sum = 0u32;
    let mut double = false;
    for &d in nums.iter().rev() {
        let mut v = d;
        if double {
            v *= 2;
            if v > 9 {
                v -= 9;
            }
        }
        sum += v;
        double = !double;
    }
    sum % 10 == 0
}

/// Redacts sensitive PII and secrets from `input` while preserving valid JSON delimiters.
#[must_use]
pub fn redact_pii(input: &str, mask_contact_info: bool) -> String {
    if input.is_empty() {
        return String::new();
    }
    let mut text = redact_bearer_and_api_keys(input);
    text = redact_password_fields(&text);
    text = redact_luhn_cards(&text);
    if mask_contact_info {
        text = redact_emails(&text);
        text = redact_phones(&text);
    }
    text = redact_otp_codes(&text);
    text
}

fn redact_bearer_and_api_keys(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut i = 0usize;
    let mut token_idx = 0usize;
    let prefixes = ["Bearer ", "sk-", "AIza", "ghp_", "gho_"];
    while i < input.len() {
        let rest = &input[i..];
        if let Some(matched_prefix) = prefixes.iter().find(|p| rest.starts_with(**p)) {
            let start_tok = if *matched_prefix == "Bearer " {
                i + matched_prefix.len()
            } else {
                i
            };
            let mut end = start_tok;
            while end < input.len() {
                let ch = input[end..].chars().next().unwrap_or(' ');
                if ch.is_ascii_alphanumeric() || matches!(ch, '_' | '-' | '.') {
                    end += ch.len_utf8();
                } else {
                    break;
                }
            }
            if end > start_tok + 6 {
                token_idx += 1;
                if *matched_prefix == "Bearer " {
                    out.push_str("Bearer ");
                }
                out.push_str(&format!("[REDACTED_TOKEN_{}]", token_idx));
                i = end;
                continue;
            }
        }
        let ch = rest.chars().next().unwrap();
        out.push(ch);
        i += ch.len_utf8();
    }
    out
}

fn redact_password_fields(input: &str) -> String {
    let mut out = input.to_string();
    let lower = out.to_ascii_lowercase();
    let mut idx = 0usize;
    for key in ["\"password\":\"", "\"password\": \"", "\"passcode\":\""] {
        while let Some(pos) = lower[idx..].find(key) {
            let val_start = idx + pos + key.len();
            if let Some(rel_end) = out[val_start..].find('"') {
                let val_end = val_start + rel_end;
                let replacement = "[REDACTED_PASSWORD]";
                out.replace_range(val_start..val_end, replacement);
                idx = val_start + replacement.len();
                break;
            } else {
                break;
            }
        }
    }
    out
}

fn redact_luhn_cards(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let bytes = input.as_bytes();
    let mut i = 0usize;
    let mut card_idx = 0usize;

    while i < bytes.len() {
        if bytes[i].is_ascii_digit() {
            let mut j = i;
            let mut digits = String::new();
            while j < bytes.len()
                && (bytes[j].is_ascii_digit() || bytes[j] == b'-' || bytes[j] == b' ')
            {
                if bytes[j].is_ascii_digit() {
                    digits.push(bytes[j] as char);
                }
                j += 1;
            }
            let trimmed_end = input[i..j].trim_end_matches([' ', '-']).len() + i;
            if is_luhn_valid(&digits) {
                card_idx += 1;
                out.push_str(&format!("[REDACTED_CARD_{}]", card_idx));
                i = trimmed_end;
                continue;
            }
        }
        let ch = input[i..].chars().next().unwrap();
        out.push(ch);
        i += ch.len_utf8();
    }
    out
}

fn redact_emails(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut email_idx = 0usize;
    for token in
        input.split_inclusive(|c: char| c.is_whitespace() || matches!(c, '"' | '\'' | ',' | ';'))
    {
        let trimmed = token
            .trim_end_matches(|c: char| c.is_whitespace() || matches!(c, '"' | '\'' | ',' | ';'));
        let suffix = &token[trimmed.len()..];
        if let Some(at_pos) = trimmed.find('@') {
            let domain = &trimmed[at_pos + 1..];
            if at_pos > 0 && domain.contains('.') && !domain.ends_with('.') {
                email_idx += 1;
                out.push_str(&format!("[REDACTED_EMAIL_{}]", email_idx));
                out.push_str(suffix);
                continue;
            }
        }
        out.push_str(token);
    }
    out
}

fn redact_phones(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let bytes = input.as_bytes();
    let mut i = 0usize;
    let mut phone_idx = 0usize;
    while i < bytes.len() {
        if bytes[i] == b'+' && i + 1 < bytes.len() && bytes[i + 1].is_ascii_digit() {
            let mut j = i + 1;
            let mut digits = 0usize;
            while j < bytes.len()
                && (bytes[j].is_ascii_digit() || bytes[j] == b'-' || bytes[j] == b' ')
            {
                if bytes[j].is_ascii_digit() {
                    digits += 1;
                }
                j += 1;
            }
            if digits >= 8 {
                let trimmed_end = input[i..j].trim_end_matches([' ', '-']).len() + i;
                phone_idx += 1;
                out.push_str(&format!("[REDACTED_PHONE_{}]", phone_idx));
                i = trimmed_end;
                continue;
            }
        }
        let ch = input[i..].chars().next().unwrap();
        out.push(ch);
        i += ch.len_utf8();
    }
    out
}

fn redact_otp_codes(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let bytes = input.as_bytes();
    let mut i = 0usize;
    let mut otp_idx = 0usize;
    while i < bytes.len() {
        if bytes[i].is_ascii_digit() && (i == 0 || !bytes[i - 1].is_ascii_alphanumeric()) {
            let mut j = i;
            while j < bytes.len() && bytes[j].is_ascii_digit() {
                j += 1;
            }
            let len = j - i;
            let next_ok =
                j == bytes.len() || (!bytes[j].is_ascii_alphanumeric() && bytes[j] != b'.');
            if (4..=8).contains(&len) && next_ok {
                otp_idx += 1;
                out.push_str(&format!("[REDACTED_OTP_{}]", otp_idx));
                i = j;
                continue;
            }
        }
        let ch = input[i..].chars().next().unwrap();
        out.push(ch);
        i += ch.len_utf8();
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_luhn_and_json_safe_pii_redaction() {
        assert!(is_luhn_valid("4111111111111111"));
        assert!(!is_luhn_valid("4111111111111112"));

        let json_prompt = r#"{"action":"ui_type","target":"OTP 482910 card 4111-1111-1111-1111 token sk-test12345678 email alice@example.com phone +967777123456","password":"MySecretPassword","platform":"android","confidence":0.95}"#;
        let redacted = redact_pii(json_prompt, true);

        assert!(redacted.contains("[REDACTED_OTP_1]"));
        assert!(redacted.contains("[REDACTED_CARD_1]"));
        assert!(redacted.contains("[REDACTED_TOKEN_1]"));
        assert!(redacted.contains("[REDACTED_EMAIL_1]"));
        assert!(redacted.contains("[REDACTED_PHONE_1]"));
        assert!(redacted.contains("[REDACTED_PASSWORD]"));
        assert!(!redacted.contains("MySecretPassword"));

        // Must remain valid JSON!
        let parsed: serde_json::Value = serde_json::from_str(&redacted).unwrap();
        assert_eq!(parsed["action"], "ui_type");
    }
}
