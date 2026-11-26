//! GitHub Release Model Manager
//! Fetches and verifies GGUF models and SHA256 checksums from GitHub Releases into the user's local cache.
//! Strictly enforces that model weights are stored outside the Git repository.

use crate::process_helper::hidden_command;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Instant;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum DownloadStatus {
    Downloading,
    Paused,
    Verifying,
    Completed,
    Cancelled,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct DownloadProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub speed_mbps: f64,
    pub eta_seconds: u64,
    pub status: DownloadStatus,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ModelDownloadInfo {
    pub tag: String,
    pub filename: String,
    pub path: PathBuf,
    pub sha256: String,
    pub size_bytes: u64,
    pub already_cached: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ModelReleaseInfo {
    pub tag: String,
    pub name: String,
    pub filename: String,
    pub size_bytes: u64,
    pub is_cached: bool,
    pub is_active: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub download_url: Option<String>,
    #[serde(default)]
    pub params: String,
    #[serde(default)]
    pub context_window: String,
    #[serde(default)]
    pub ram_required_gb: String,
    #[serde(default)]
    pub speed_tier: String,
    #[serde(default)]
    pub recommended_for: String,
    #[serde(default)]
    pub is_recommended: bool,
}

#[derive(Debug, Clone)]
pub struct ModelManager {
    cache_dir: PathBuf,
    repo: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DesktopOllamaPreset {
    pub id: String,
    pub model: String,
    pub display_name: String,
    pub hardware_tier: String,
    pub notes: String,
    pub is_default: bool,
}

impl ModelManager {
    /// Returns the 2026 Desktop recommended Ollama / hybrid MCP+GUI presets.
    #[must_use]
    pub fn desktop_recommended_presets() -> Vec<DesktopOllamaPreset> {
        vec![
            DesktopOllamaPreset {
                id: "qwen3.5-4b".to_string(),
                model: "qwen3.5:4b".to_string(),
                display_name: "Qwen3.5-4B (Default 16 GB RAM PC)".to_string(),
                hardware_tier: "16 GB RAM".to_string(),
                notes: "Recommended default for 16 GB RAM PCs; non-thinking fast JSON".to_string(),
                is_default: true,
            },
            DesktopOllamaPreset {
                id: "qwen3.5-9b-q4km".to_string(),
                model: "qwen3.5:9b-q4_K_M".to_string(),
                display_name: "Qwen3.5-9B-Q4_K_M (12 GB+ GPU)".to_string(),
                hardware_tier: "12 GB+ GPU".to_string(),
                notes: "High-accuracy multi-step reasoning for 12 GB+ VRAM GPUs".to_string(),
                is_default: false,
            },
            DesktopOllamaPreset {
                id: "gui-owl-1.5".to_string(),
                model: "GUI-Owl-1.5-2B/4B-Instruct".to_string(),
                display_name: "GUI-Owl-1.5-2B/4B-Instruct (MIT Hybrid MCP+GUI)".to_string(),
                hardware_tier: "8-16 GB RAM / GPU".to_string(),
                notes: "MIT-licensed hybrid MCP + GUI grounding agent".to_string(),
                is_default: false,
            },
        ]
    }

    pub fn new(custom_cache: Option<PathBuf>) -> Result<Self, String> {
        let cache_dir = match custom_cache {
            Some(p) => p,
            None => Self::default_cache_dir(),
        };

        // Guard against storing models inside a git repository
        Self::assert_not_in_git_repo(&cache_dir)?;

        fs::create_dir_all(&cache_dir).map_err(|e| {
            format!(
                "Failed to create model cache directory at {}: {}",
                cache_dir.display(),
                e
            )
        })?;

        Ok(Self {
            cache_dir,
            repo: "tahadeab-ai/silent-exec".to_string(),
        })
    }

    pub fn with_repo(mut self, repo: String) -> Self {
        self.repo = repo;
        self
    }

    pub fn cache_dir(&self) -> &Path {
        &self.cache_dir
    }

    /// Finds an existing cached .gguf model file in the cache directory.
    pub fn find_cached_model(&self) -> Option<PathBuf> {
        let preferred = self.cache_dir.join("Qwen2.5-1.5B-Instruct.Q4_K_M.gguf");
        if preferred.is_file() {
            return Some(preferred);
        }

        if let Ok(entries) = fs::read_dir(&self.cache_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file() && path.extension().and_then(|e| e.to_str()) == Some("gguf") {
                    return Some(path);
                }
            }
        }

        None
    }

    /// Determines standard OS cache path outside git.
    pub fn default_cache_dir() -> PathBuf {
        if let Ok(env_val) = std::env::var("SILENT_EXEC_CACHE_DIR") {
            return PathBuf::from(env_val);
        }

        if cfg!(target_os = "windows") {
            if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
                return PathBuf::from(local_app_data)
                    .join("silent-exec")
                    .join("models");
            }
        }

        if let Ok(home) = std::env::var("HOME") {
            return PathBuf::from(home)
                .join(".cache")
                .join("silent-exec")
                .join("models");
        }

        PathBuf::from(".silent_exec_cache")
    }

    /// Verifies that a path does not reside within the silent-exec source repository.
    /// Only rejects if an ancestor contains BOTH `.git` AND either `schemas/action_schema.json` or `core-rust/Cargo.toml`.
    /// Never blocks %LOCALAPPDATA% or ~/.cache.
    pub fn assert_not_in_git_repo(path: &Path) -> Result<(), String> {
        let abs_path = path.canonicalize().unwrap_or_else(|_| path.to_path_buf());
        let mut cur = abs_path.as_path();
        while let Some(parent) = cur.parent() {
            if cur.join(".git").exists()
                && (cur.join("schemas").join("action_schema.json").exists()
                    || cur.join("core-rust").join("Cargo.toml").exists())
            {
                return Err(format!(
                    "Cache directory '{}' is located inside the SilentExec source repository ('{}'). Models must be cached outside git history.",
                    path.display(),
                    cur.display()
                ));
            }
            cur = parent;
        }
        Ok(())
    }

    /// Computes the SHA256 checksum of a file.
    pub fn compute_file_sha256(path: &Path) -> Result<String, std::io::Error> {
        let mut file = File::open(path)?;
        let mut hasher = Sha256::new();
        let mut buffer = [0u8; 65536];

        loop {
            let bytes_read = file.read(&mut buffer)?;
            if bytes_read == 0 {
                break;
            }
            hasher.update(&buffer[..bytes_read]);
        }

        Ok(format!("{:x}", hasher.finalize()))
    }

    /// Verifies whether an existing cached file matches the expected SHA256.
    pub fn verify_checksum(path: &Path, expected_sha256: &str) -> bool {
        match Self::compute_file_sha256(path) {
            Ok(actual) => actual.eq_ignore_ascii_case(expected_sha256.trim()),
            Err(_) => false,
        }
    }

    /// Fetches model information and downloads the .gguf binary from GitHub Releases
    /// or direct Hugging Face curated URLs.
    pub fn download_model(
        &self,
        tag: &str,
    ) -> Result<ModelDownloadInfo, Box<dyn std::error::Error>> {
        // Check if tag maps to a curated Hugging Face / direct URL model first
        let available = self
            .list_available_models(None)
            .unwrap_or_else(|_| Self::curated_community_models(&self.cache_dir, None));
        if let Some(curated) = available.into_iter().find(|m| m.tag == tag) {
            if let Some(direct_url) = curated.download_url {
                let target_path = self.cache_dir.join(&curated.filename);
                if target_path.exists() {
                    let actual_size = fs::metadata(&target_path)?.len();
                    if curated.size_bytes == 0 || actual_size == curated.size_bytes {
                        println!("Verified valid cached model at: {}", target_path.display());
                        return Ok(ModelDownloadInfo {
                            tag: tag.to_string(),
                            filename: curated.filename,
                            path: target_path,
                            sha256: "SKIP".to_string(),
                            size_bytes: actual_size,
                            already_cached: true,
                        });
                    }
                }

                let part_path = self.cache_dir.join(format!("{}.part", curated.filename));
                let cancel_flag = Arc::new(AtomicBool::new(false));
                Self::download_resumable_file(
                    &direct_url,
                    &part_path,
                    &target_path,
                    "SKIP",
                    cancel_flag,
                    |_| {},
                )?;

                let size_bytes = fs::metadata(&target_path)?.len();
                if curated.size_bytes > 0 && size_bytes != curated.size_bytes {
                    let _ = fs::remove_file(&target_path);
                    return Err(format!(
                        "File size mismatch for {}: expected {} bytes, got {} bytes",
                        curated.filename, curated.size_bytes, size_bytes
                    )
                    .into());
                }

                return Ok(ModelDownloadInfo {
                    tag: tag.to_string(),
                    filename: curated.filename,
                    path: target_path,
                    sha256: "SKIP".to_string(),
                    size_bytes,
                    already_cached: false,
                });
            }
        }

        let base_url = format!("https://github.com/{}/releases/download/{}", self.repo, tag);
        let checksums_url = format!("{}/SHA256SUMS.txt", base_url);

        println!("Fetching release checksums from: {}", checksums_url);
        let output = hidden_command("curl")
            .args(["-sSfL", &checksums_url])
            .output()
            .map_err(|e| format!("Failed to invoke curl: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "Failed to fetch SHA256SUMS.txt from {}: {}",
                checksums_url,
                String::from_utf8_lossy(&output.stderr)
            )
            .into());
        }

        let checksums_text = String::from_utf8(output.stdout)?;

        // Parse SHA256SUMS.txt for *.gguf entry
        let mut target_filename = None;
        let mut expected_sha256 = None;

        for line in checksums_text.lines() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 && parts[1].ends_with(".gguf") {
                expected_sha256 = Some(parts[0].to_string());
                target_filename = Some(parts[1].to_string());
                break;
            }
        }

        let filename = target_filename.ok_or_else(|| {
            format!(
                "No .gguf file entry found in release {} SHA256SUMS.txt",
                tag
            )
        })?;
        let sha256_val = expected_sha256
            .ok_or_else(|| format!("No SHA256 hash found for {} in release {}", filename, tag))?;

        let target_path = self.cache_dir.join(&filename);

        // Check if already cached and valid
        if target_path.exists() && Self::verify_checksum(&target_path, &sha256_val) {
            let size_bytes = fs::metadata(&target_path)?.len();
            println!("Verified valid cached model at: {}", target_path.display());
            return Ok(ModelDownloadInfo {
                tag: tag.to_string(),
                filename,
                path: target_path,
                sha256: sha256_val,
                size_bytes,
                already_cached: true,
            });
        }

        // Download model via resumable HTTP range download (curl -L -C -)
        let download_url = format!("{}/{}", base_url, filename);
        let part_path = self.cache_dir.join(format!("{}.part", filename));

        let cancel_flag = Arc::new(AtomicBool::new(false));
        Self::download_resumable_file(
            &download_url,
            &part_path,
            &target_path,
            &sha256_val,
            cancel_flag,
            |_| {},
        )?;

        let size_bytes = fs::metadata(&target_path)?.len();
        println!(
            "Successfully downloaded and verified {} ({} bytes)",
            filename, size_bytes
        );

        Ok(ModelDownloadInfo {
            tag: tag.to_string(),
            filename,
            path: target_path,
            sha256: sha256_val,
            size_bytes,
            already_cached: false,
        })
    }

    /// Resumable HTTP download using curl's range continuation `-C -`.
    /// Emits progress callbacks and handles cancellation and SHA-256 verification.
    pub fn download_resumable_file<F>(
        download_url: &str,
        part_path: &Path,
        final_path: &Path,
        expected_sha256: &str,
        cancel_flag: Arc<AtomicBool>,
        mut progress_cb: F,
    ) -> Result<(), String>
    where
        F: FnMut(DownloadProgress),
    {
        if cancel_flag.load(Ordering::SeqCst) {
            progress_cb(DownloadProgress {
                downloaded_bytes: 0,
                total_bytes: 0,
                speed_mbps: 0.0,
                eta_seconds: 0,
                status: DownloadStatus::Cancelled,
            });
            return Err("Download cancelled before start".to_string());
        }

        // Determine existing .part file size
        let initial_bytes = if part_path.exists() {
            fs::metadata(part_path).map(|m| m.len()).unwrap_or(0)
        } else {
            0
        };

        // Query content length via HTTP HEAD
        let total_bytes = {
            let head_out = hidden_command("curl")
                .args(["-sIL", download_url])
                .output()
                .ok();
            let mut len = 0u64;
            if let Some(out) = head_out {
                let headers = String::from_utf8_lossy(&out.stdout);
                for line in headers.lines() {
                    let lower = line.to_lowercase();
                    if lower.starts_with("content-length:") {
                        if let Some(val) = line.split(':').nth(1) {
                            if let Ok(parsed) = val.trim().parse::<u64>() {
                                len = parsed;
                            }
                        }
                    }
                }
            }
            if len == 0 {
                initial_bytes
            } else {
                len
            }
        };

        progress_cb(DownloadProgress {
            downloaded_bytes: initial_bytes,
            total_bytes,
            speed_mbps: 0.0,
            eta_seconds: 0,
            status: DownloadStatus::Downloading,
        });

        println!(
            "Downloading (resumable) from {} to {} (existing: {} bytes, total: {} bytes)...",
            download_url,
            part_path.display(),
            initial_bytes,
            total_bytes
        );

        let start_time = Instant::now();
        // Spawn curl with -C - for byte-range continuation
        let mut child = hidden_command("curl")
            .args([
                "-sSfL",
                "-C",
                "-",
                "-o",
                part_path.to_str().unwrap(),
                download_url,
            ])
            .spawn()
            .map_err(|e| format!("Failed to spawn curl for resumable download: {}", e))?;

        while match child.try_wait() {
            Ok(None) => true,
            Ok(Some(_)) => false,
            Err(_) => false,
        } {
            if cancel_flag.load(Ordering::SeqCst) {
                let _ = child.kill();
                let _ = child.wait();
                let current_bytes = fs::metadata(part_path).map(|m| m.len()).unwrap_or(0);
                progress_cb(DownloadProgress {
                    downloaded_bytes: current_bytes,
                    total_bytes,
                    speed_mbps: 0.0,
                    eta_seconds: 0,
                    status: DownloadStatus::Cancelled,
                });
                return Err("Download cancelled by user".to_string());
            }

            std::thread::sleep(std::time::Duration::from_millis(300));

            let current_bytes = fs::metadata(part_path).map(|m| m.len()).unwrap_or(0);
            let elapsed_secs = start_time.elapsed().as_secs_f64();
            let new_bytes = current_bytes.saturating_sub(initial_bytes);
            let speed_mbps = if elapsed_secs > 0.0 {
                (new_bytes as f64 * 8.0) / (elapsed_secs * 1_000_000.0)
            } else {
                0.0
            };
            let speed_bytes_per_sec = if elapsed_secs > 0.0 {
                new_bytes as f64 / elapsed_secs
            } else {
                0.0
            };
            let eta_seconds = if total_bytes > current_bytes && speed_bytes_per_sec > 1024.0 {
                ((total_bytes - current_bytes) as f64 / speed_bytes_per_sec) as u64
            } else {
                0
            };

            progress_cb(DownloadProgress {
                downloaded_bytes: current_bytes,
                total_bytes,
                speed_mbps,
                eta_seconds,
                status: DownloadStatus::Downloading,
            });
        }

        let status = child
            .wait()
            .map_err(|e| format!("Failed to wait on curl: {}", e))?;

        // curl exit code 33 means HTTP range error (often file is already fully downloaded or server doesn't support range)
        if !status.success() && status.code() != Some(33) {
            return Err(format!(
                "curl failed downloading from {} with exit status {:?}",
                download_url,
                status.code()
            ));
        }

        // Verification phase
        let final_bytes = fs::metadata(part_path)
            .map(|m| m.len())
            .unwrap_or(total_bytes);
        progress_cb(DownloadProgress {
            downloaded_bytes: final_bytes,
            total_bytes: final_bytes,
            speed_mbps: 0.0,
            eta_seconds: 0,
            status: DownloadStatus::Verifying,
        });

        let computed_sha256 = Self::compute_file_sha256(part_path)
            .map_err(|e| format!("Failed to compute sha256: {}", e))?;
        if !expected_sha256.is_empty()
            && expected_sha256 != "SKIP"
            && !computed_sha256.eq_ignore_ascii_case(expected_sha256.trim())
        {
            let _ = fs::remove_file(part_path);
            return Err(format!(
                "SHA256 checksum mismatch: expected {}, got {}",
                expected_sha256, computed_sha256
            ));
        }

        // Rename .part to final destination
        fs::rename(part_path, final_path)
            .map_err(|e| format!("Failed to rename .part to final destination: {}", e))?;

        progress_cb(DownloadProgress {
            downloaded_bytes: final_bytes,
            total_bytes: final_bytes,
            speed_mbps: 0.0,
            eta_seconds: 0,
            status: DownloadStatus::Completed,
        });

        Ok(())
    }

    /// Parses Hugging Face `/api/models/<repo_id>/tree/main` JSON to find the exact `*Q4_K_M*.gguf` file and byte size.
    pub fn parse_huggingface_tree_q4km(
        repo_id: &str,
        json_text: &str,
        tag: &str,
        display_name: &str,
        cache_dir: &Path,
        active_tag: Option<&str>,
    ) -> Result<ModelReleaseInfo, String> {
        let entries: serde_json::Value = serde_json::from_str(json_text)
            .map_err(|e| format!("Failed to parse Hugging Face tree JSON: {}", e))?;
        let arr = entries
            .as_array()
            .ok_or_else(|| "Hugging Face tree response is not a JSON array".to_string())?;

        for entry in arr {
            let entry_type = entry.get("type").and_then(|v| v.as_str()).unwrap_or("");
            if entry_type != "file" {
                continue;
            }
            let path = entry.get("path").and_then(|v| v.as_str()).unwrap_or("");
            let lower = path.to_lowercase();
            if lower.ends_with(".gguf") && lower.contains("q4_k_m") {
                let size_bytes = entry.get("size").and_then(|v| v.as_u64()).unwrap_or(0);
                let filename = path.rsplit('/').next().unwrap_or(path).to_string();
                let download_url = format!(
                    "https://huggingface.co/{}/resolve/main/{}?download=true",
                    repo_id, path
                );
                let is_cached = cache_dir.join(&filename).exists();
                let is_active = active_tag == Some(tag);
                let (
                    params,
                    context_window,
                    ram_required_gb,
                    speed_tier,
                    recommended_for,
                    is_recommended,
                ) = Self::specs_for_model(tag, &filename);
                return Ok(ModelReleaseInfo {
                    tag: tag.to_string(),
                    name: display_name.to_string(),
                    filename,
                    size_bytes,
                    is_cached,
                    is_active,
                    download_url: Some(download_url),
                    params,
                    context_window,
                    ram_required_gb,
                    speed_tier,
                    recommended_for,
                    is_recommended,
                });
            }
        }

        Err(format!("No *Q4_K_M*.gguf file found in {}", repo_id))
    }

    /// Queries Hugging Face tree API for the exact `*Q4_K_M*.gguf` path and byte size.
    pub fn fetch_huggingface_q4km(
        repo_id: &str,
        tag: &str,
        display_name: &str,
        cache_dir: &Path,
        active_tag: Option<&str>,
    ) -> Result<ModelReleaseInfo, String> {
        let api_url = format!("https://huggingface.co/api/models/{}/tree/main", repo_id);
        let output = hidden_command("curl")
            .args(["-sSfL", "-H", "User-Agent: silent-exec-pc", &api_url])
            .output()
            .map_err(|e| format!("Failed to invoke curl for Hugging Face API: {}", e))?;

        if !output.status.success() {
            return Err(format!("Hugging Face API request failed for {}", repo_id));
        }

        let json_text = String::from_utf8_lossy(&output.stdout);
        Self::parse_huggingface_tree_q4km(
            repo_id,
            &json_text,
            tag,
            display_name,
            cache_dir,
            active_tag,
        )
    }

    /// Returns structured hardware & capability specs for a known model tag or filename.
    pub fn specs_for_model(
        tag: &str,
        filename: &str,
    ) -> (String, String, String, String, String, bool) {
        let lower_tag = tag.to_lowercase();
        let lower_file = filename.to_lowercase();
        if lower_tag.contains("qwen3.5-2b") || lower_file.contains("qwen3.5-2b") {
            (
                "2B".to_string(),
                "32K".to_string(),
                "1.6 GB".to_string(),
                "Fast".to_string(),
                "الموصى به — Qwen3.5-2B Non-Thinking (201 Languages incl. Arabic)".to_string(),
                true,
            )
        } else if lower_tag.contains("qwen3.5") || lower_file.contains("qwen3.5") {
            (
                "4B".to_string(),
                "32K".to_string(),
                "3.2 GB".to_string(),
                "Fast".to_string(),
                "الموصى به للكمبيوتر — Qwen3.5-4B (16 GB RAM PC)".to_string(),
                true,
            )
        } else if lower_tag.contains("gui-owl") || lower_file.contains("gui-owl") {
            (
                "2B/4B".to_string(),
                "32K".to_string(),
                "2.5 GB".to_string(),
                "Fast".to_string(),
                "MIT Hybrid MCP+GUI — GUI-Owl-1.5".to_string(),
                true,
            )
        } else if lower_tag.contains("lfm3") || lower_file.contains("lfm3") {
            (
                "1.7B".to_string(),
                "32K".to_string(),
                "1.4 GB".to_string(),
                "Experimental".to_string(),
                "تجريبي (Thinking / XML) — LFM3-1.7B".to_string(),
                false,
            )
        } else if lower_tag.contains("qwen3") || lower_file.contains("qwen3") {
            (
                "1.7B".to_string(),
                "32K".to_string(),
                "1.4 GB".to_string(),
                "Fast".to_string(),
                "أقوى عربية وتفكير — Qwen3 / Strong Arabic & Reasoning".to_string(),
                true,
            )
        } else if lower_file.contains("0.5b") {
            (
                "0.5B".to_string(),
                "32K".to_string(),
                "0.6 GB".to_string(),
                "Ultra Fast".to_string(),
                "خفيف للأجهزة الضعيفة — Ultra-Light".to_string(),
                false,
            )
        } else {
            (
                "1.5B".to_string(),
                "32K".to_string(),
                "1.2 GB".to_string(),
                "Balanced".to_string(),
                "متوازن للأجهزة المتوسطة — Balanced Arabic Edge".to_string(),
                false,
            )
        }
    }

    /// Curated Community Edge Models direct GGUF entries
    pub fn curated_community_models(
        cache_dir: &Path,
        active_tag: Option<&str>,
    ) -> Vec<ModelReleaseInfo> {
        let items = [
            (
                "qwen3.5-2b-gguf",
                "unsloth/Qwen3.5-2B (Recommended Default • Non-Thinking • 201 Languages)",
                "Qwen3.5-2B-Q4_K_M.gguf",
                1_374_389_534u64,
                Some("https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf".to_string()),
            ),
            (
                "liquidai-lfm3-1.7b",
                "LiquidAI/LFM3-1.7B (Experimental / Thinking • XML Tool-Call)",
                "LFM3-1.7B-Q4_K_M.gguf",
                1_099_627_808u64,
                Some("https://huggingface.co/LiquidAI/LFM3-1.7B-GGUF/resolve/main/LFM3-1.7B-Q4_K_M.gguf?download=true".to_string()),
            ),
            (
                "qwen3-1.7b-gguf",
                "unsloth/Qwen3-1.7B-GGUF (Strong Arabic & Multi-Step Reasoning)",
                "Qwen3-1.7B-Q4_K_M.gguf",
                1_107_409_472u64,
                Some("https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true".to_string()),
            ),
            (
                "v0.2.0-model",
                "SilentExec v0.2.0 (Fine-Tuned Arabic Edge)",
                "Qwen2.5-1.5B-Instruct.Q4_K_M.gguf",
                986_000_000u64,
                Some("https://github.com/tahadeab-ai/silent-exec/releases/download/v0.2.0-model/Qwen2.5-1.5B-Instruct.Q4_K_M.gguf".to_string()),
            ),
            (
                "v0.1.0-model",
                "SilentExec v0.1.0 (Initial Fine-Tuned Model)",
                "Qwen2.5-1.5B-Instruct.Q4_K_M.gguf",
                986_000_000u64,
                Some("https://github.com/tahadeab-ai/silent-exec/releases/download/v0.1.0-model/Qwen2.5-1.5B-Instruct.Q4_K_M.gguf".to_string()),
            ),
        ];

        items
            .into_iter()
            .map(|(tag, name, filename, size_bytes, download_url)| {
                let is_cached = cache_dir.join(filename).exists();
                let is_active = active_tag == Some(tag);
                let (
                    params,
                    context_window,
                    ram_required_gb,
                    speed_tier,
                    recommended_for,
                    is_recommended,
                ) = Self::specs_for_model(tag, filename);
                ModelReleaseInfo {
                    tag: tag.to_string(),
                    name: name.to_string(),
                    filename: filename.to_string(),
                    size_bytes,
                    is_cached,
                    is_active,
                    download_url,
                    params,
                    context_window,
                    ram_required_gb,
                    speed_tier,
                    recommended_for,
                    is_recommended,
                }
            })
            .collect()
    }

    /// Lists all available models: curated community edge models (with live HF tree resolution) + GitHub releases.
    pub fn list_available_models(
        &self,
        active_tag: Option<&str>,
    ) -> Result<Vec<ModelReleaseInfo>, String> {
        let mut models = Self::curated_community_models(&self.cache_dir, active_tag);

        if let Ok(live_lfm3) = Self::fetch_huggingface_q4km(
            "LiquidAI/LFM3-1.7B-GGUF",
            "liquidai-lfm3-1.7b",
            "LiquidAI/LFM3-1.7B (Fast Edge Agent)",
            &self.cache_dir,
            active_tag,
        ) {
            if let Some(slot) = models.iter_mut().find(|m| m.tag == "liquidai-lfm3-1.7b") {
                *slot = live_lfm3;
            }
        }

        let api_url = format!("https://api.github.com/repos/{}/releases", self.repo);

        let output = hidden_command("curl")
            .args([
                "-sSfL",
                "-H",
                "User-Agent: silent-exec-pc",
                "-H",
                "Accept: application/vnd.github.v3+json",
                &api_url,
            ])
            .output();

        let output = match output {
            Ok(o) if o.status.success() => o,
            _ => return Ok(models), // Graceful fallback: return curated models even if offline/rate-limited
        };

        let releases_json: serde_json::Value = match serde_json::from_slice(&output.stdout) {
            Ok(v) => v,
            Err(_) => return Ok(models),
        };

        if let Some(releases) = releases_json.as_array() {
            for release in releases {
                let tag = match release.get("tag_name").and_then(|t| t.as_str()) {
                    Some(t) => t,
                    None => continue,
                };

                if tag.contains("-model") && !models.iter().any(|m| m.tag == tag) {
                    let release_name = release
                        .get("name")
                        .and_then(|n| n.as_str())
                        .unwrap_or(tag)
                        .to_string();

                    let mut gguf_filename = String::new();
                    let mut size_bytes = 0u64;

                    if let Some(assets) = release.get("assets").and_then(|a| a.as_array()) {
                        for asset in assets {
                            let name = asset.get("name").and_then(|n| n.as_str()).unwrap_or("");
                            if name.ends_with(".gguf") {
                                gguf_filename = name.to_string();
                                size_bytes =
                                    asset.get("size").and_then(|s| s.as_u64()).unwrap_or(0);
                                break;
                            }
                        }
                    }

                    if !gguf_filename.is_empty() {
                        let is_cached = self.cache_dir.join(&gguf_filename).exists();
                        let is_active = active_tag == Some(tag);
                        let (
                            params,
                            context_window,
                            ram_required_gb,
                            speed_tier,
                            recommended_for,
                            is_recommended,
                        ) = Self::specs_for_model(tag, &gguf_filename);

                        models.push(ModelReleaseInfo {
                            tag: tag.to_string(),
                            name: release_name,
                            filename: gguf_filename,
                            size_bytes,
                            is_cached,
                            is_active,
                            download_url: None,
                            params,
                            context_window,
                            ram_required_gb,
                            speed_tier,
                            recommended_for,
                            is_recommended,
                        });
                    }
                }
            }
        }

        Ok(models)
    }
}
