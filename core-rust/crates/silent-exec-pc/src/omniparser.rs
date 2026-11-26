//! Microsoft OmniParser (`icon_detect_v3`, MIT) Fallback Client
//! Invoked when `omniparser_url` is configured AND native Windows UIA / Linux AT-SPI2 / Android WebView
//! returns fewer than 3 interactive elements.

use crate::agent_engine::cap_screenshot_bytes;
use crate::process_helper::hidden_command;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::io::Write;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct OmniElement {
    pub index: usize,
    pub label: String,
    pub center_x: i32,
    pub center_y: i32,
    pub bbox_norm: [f64; 4],
    pub interactive: bool,
}

impl OmniElement {
    #[must_use]
    pub fn format_for_prompt(&self) -> String {
        let role = if self.interactive { "control" } else { "text" };
        format!(
            "[{}] {}: {} [{},{}]",
            self.index, role, self.label, self.center_x, self.center_y
        )
    }
}

pub struct OmniParserClient;

impl OmniParserClient {
    pub const MIN_NATIVE_ELEMENTS: usize = 3;
    pub const MAX_SCREENSHOT_BYTES: usize = 1_048_576; // 1 MB

    /// Returns true if OmniParser fallback should be triggered.
    #[must_use]
    pub fn should_fallback(omniparser_url: &str, native_element_count: usize) -> bool {
        !omniparser_url.trim().is_empty() && native_element_count < Self::MIN_NATIVE_ELEMENTS
    }

    /// Parses OmniParser `/parse/` JSON response into indexed `[0..N]` `OmniElement`s.
    pub fn parse_response(
        json_text: &str,
        screen_w: u32,
        screen_h: u32,
    ) -> Result<Vec<OmniElement>, String> {
        let root: Value = serde_json::from_str(json_text)
            .map_err(|e| format!("Invalid OmniParser JSON: {}", e))?;

        let list = root
            .get("parsed_content_list")
            .or_else(|| root.get("elements"))
            .and_then(|v| v.as_array())
            .ok_or_else(|| "OmniParser response missing 'parsed_content_list'".to_string())?;

        let mut elements = Vec::new();
        for item in list {
            let bbox_arr = match item.get("bbox").and_then(|b| b.as_array()) {
                Some(a) if a.len() == 4 => a,
                _ => continue,
            };
            let x1 = bbox_arr[0].as_f64().unwrap_or(0.0);
            let y1 = bbox_arr[1].as_f64().unwrap_or(0.0);
            let x2 = bbox_arr[2].as_f64().unwrap_or(0.0);
            let y2 = bbox_arr[3].as_f64().unwrap_or(0.0);

            // Handle both [0..1] normalized coordinates and pixel coordinates
            let (nx1, ny1, nx2, ny2) = if x2 <= 1.0 && y2 <= 1.0 {
                (x1, y1, x2, y2)
            } else {
                let w = f64::from(screen_w.max(1));
                let h = f64::from(screen_h.max(1));
                (x1 / w, y1 / h, x2 / w, y2 / h)
            };

            let cx = (((nx1 + nx2) / 2.0) * f64::from(screen_w)).round() as i32;
            let cy = (((ny1 + ny2) / 2.0) * f64::from(screen_h)).round() as i32;

            let label = item
                .get("content")
                .or_else(|| item.get("label"))
                .and_then(|v| v.as_str())
                .unwrap_or("icon")
                .trim()
                .to_string();

            let interactive = item
                .get("interactivity")
                .and_then(|v| v.as_bool())
                .unwrap_or(true);

            elements.push(OmniElement {
                index: elements.len(),
                label,
                center_x: cx,
                center_y: cy,
                bbox_norm: [nx1, ny1, nx2, ny2],
                interactive,
            });
        }

        Ok(elements)
    }

    /// POSTs a compressed screenshot (`<= 1 MB`) as base64 to `{omniparser_url}/parse/`.
    pub fn parse_screenshot(
        omniparser_url: &str,
        screenshot_bytes: &[u8],
        screen_w: u32,
        screen_h: u32,
    ) -> Result<Vec<OmniElement>, String> {
        let trimmed_url = omniparser_url.trim().trim_end_matches('/');
        if trimmed_url.is_empty() {
            return Err("OmniParser URL is empty".to_string());
        }

        let capped = cap_screenshot_bytes(screenshot_bytes, Self::MAX_SCREENSHOT_BYTES)?;
        let b64 = base64_encode(&capped);
        let endpoint = format!("{}/parse/", trimmed_url);
        let payload = serde_json::json!({ "base64_image": b64 });
        let body_str = serde_json::to_string(&payload)
            .map_err(|e| format!("Failed to serialize OmniParser request: {}", e))?;

        let mut child = hidden_command("curl")
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
            .map_err(|e| format!("Failed to invoke curl for OmniParser: {}", e))?;

        if let Some(mut stdin) = child.stdin.take() {
            stdin
                .write_all(body_str.as_bytes())
                .map_err(|e| format!("Failed to write OmniParser body: {}", e))?;
        }

        let output = child
            .wait_with_output()
            .map_err(|e| format!("Failed to read OmniParser response: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "OmniParser request failed: {}",
                String::from_utf8_lossy(&output.stderr).trim()
            ));
        }

        let resp_str = String::from_utf8_lossy(&output.stdout);
        Self::parse_response(&resp_str, screen_w, screen_h)
    }
}

fn base64_encode(input: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(((input.len() + 2) / 3) * 4);
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
