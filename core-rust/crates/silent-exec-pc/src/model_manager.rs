//! GitHub Release Model Manager
//! Fetches and verifies GGUF models and SHA256 checksums from GitHub Releases into the user's local cache.
//! Strictly enforces that model weights are stored outside the Git repository.

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::Command;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ModelDownloadInfo {
    pub tag: String,
    pub filename: String,
    pub path: PathBuf,
    pub sha256: String,
    pub size_bytes: u64,
    pub already_cached: bool,
}

#[derive(Debug, Clone)]
pub struct ModelManager {
    cache_dir: PathBuf,
    repo: String,
}

impl ModelManager {
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

    /// Verifies that a path does not reside within a `.git` workspace.
    pub fn assert_not_in_git_repo(path: &Path) -> Result<(), String> {
        let abs_path = path.canonicalize().unwrap_or_else(|_| path.to_path_buf());
        let mut cur = abs_path.as_path();
        while let Some(parent) = cur.parent() {
            if cur.join(".git").exists() {
                return Err(format!(
                    "Cache directory '{}' is located inside a Git repository ('{}'). Models must be cached outside git history.",
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

    /// Fetches model information and downloads the .gguf binary from GitHub Releases.
    pub fn download_model(
        &self,
        tag: &str,
    ) -> Result<ModelDownloadInfo, Box<dyn std::error::Error>> {
        let base_url = format!("https://github.com/{}/releases/download/{}", self.repo, tag);
        let checksums_url = format!("{}/SHA256SUMS.txt", base_url);

        println!("Fetching release checksums from: {}", checksums_url);
        let output = Command::new("curl")
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

        // Stream download into .part file via curl
        let download_url = format!("{}/{}", base_url, filename);
        let part_path = self.cache_dir.join(format!("{}.part", filename));

        println!(
            "Downloading model from {} to {}...",
            download_url,
            part_path.display()
        );
        let dl_status = Command::new("curl")
            .args(["-sSfL", "-o", part_path.to_str().unwrap(), &download_url])
            .status()
            .map_err(|e| format!("Failed to invoke curl for download: {}", e))?;

        if !dl_status.success() {
            let _ = fs::remove_file(&part_path);
            return Err(format!("curl failed downloading model from {}", download_url).into());
        }

        let computed_sha256 = Self::compute_file_sha256(&part_path)?;
        if !computed_sha256.eq_ignore_ascii_case(&sha256_val) {
            let _ = fs::remove_file(&part_path);
            return Err(format!(
                "SHA256 checksum mismatch for {}: expected {}, got {}",
                filename, sha256_val, computed_sha256
            )
            .into());
        }

        // Rename .part to final
        fs::rename(&part_path, &target_path)?;
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
}
