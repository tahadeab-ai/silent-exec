//! Self-Updater for SilentExec PC Agent
//! Queries GitHub Releases for newer `v*-pc` releases,
//! verifies SHA-256 checksums, and updates the executable.

use crate::model_manager::{DownloadProgress, ModelManager};
use crate::process_helper::hidden_command;
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::AtomicBool;
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UpdateInfo {
    pub current_version: String,
    pub latest_version: String,
    pub tag_name: String,
    pub download_url: String,
    pub sha256_url: String,
    pub update_available: bool,
}

pub struct SelfUpdater {
    repo: String,
}

impl Default for SelfUpdater {
    fn default() -> Self {
        Self::new("tahadeab-ai/silent-exec")
    }
}

impl SelfUpdater {
    pub fn new(repo: &str) -> Self {
        Self {
            repo: repo.to_string(),
        }
    }

    /// Extracts (major, minor, patch) from a tag or semver string like "v0.3.0-pc" or "0.3.0".
    #[must_use]
    pub fn parse_version(s: &str) -> (u32, u32, u32) {
        let trimmed = s.trim().trim_start_matches('v');
        let semver_part = trimmed.split('-').next().unwrap_or(trimmed);
        let parts: Vec<&str> = semver_part.split('.').collect();

        let major = parts.first().and_then(|p| p.parse().ok()).unwrap_or(0);
        let minor = parts.get(1).and_then(|p| p.parse().ok()).unwrap_or(0);
        let patch = parts.get(2).and_then(|p| p.parse().ok()).unwrap_or(0);

        (major, minor, patch)
    }

    /// Returns true if `target` is strictly newer than `current`.
    #[must_use]
    pub fn is_newer(current: &str, target: &str) -> bool {
        let (c_maj, c_min, c_pat) = Self::parse_version(current);
        let (t_maj, t_min, t_pat) = Self::parse_version(target);

        (t_maj, t_min, t_pat) > (c_maj, c_min, c_pat)
    }

    /// Checks GitHub Releases API for the latest `v*-pc` release targeting the CLI binary.
    pub fn check_for_update(&self) -> Result<UpdateInfo, String> {
        let preferred = if cfg!(target_os = "windows") {
            "silent-exec-pc.exe"
        } else {
            "silent-exec-pc"
        };
        self.check_for_asset_update(preferred, None)
    }

    /// Checks GitHub Releases API for the latest `v*-pc` release targeting the Desktop app binary.
    pub fn check_for_desktop_update(&self) -> Result<UpdateInfo, String> {
        self.check_for_asset_update("SilentExec-Desktop-x64.exe", None)
    }

    /// Checks GitHub Releases API for a `v*-pc` release (latest or matching `target_tag`) containing `asset_name`.
    pub fn check_for_asset_update(
        &self,
        asset_name: &str,
        target_tag: Option<&str>,
    ) -> Result<UpdateInfo, String> {
        let current_version = env!("CARGO_PKG_VERSION").to_string();
        let api_url = format!("https://api.github.com/repos/{}/releases", self.repo);

        let output = hidden_command("curl")
            .args([
                "-sSfL",
                "-H",
                "User-Agent: silent-exec-pc-updater",
                "-H",
                "Accept: application/vnd.github.v3+json",
                &api_url,
            ])
            .output()
            .map_err(|e| format!("Failed to invoke curl: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "Failed to fetch releases from GitHub: {}",
                String::from_utf8_lossy(&output.stderr)
            ));
        }

        let releases_json: serde_json::Value = serde_json::from_slice(&output.stdout)
            .map_err(|e| format!("Failed to parse releases JSON: {}", e))?;

        let releases = releases_json
            .as_array()
            .ok_or_else(|| "Releases response is not an array".to_string())?;

        // Find the matching release tagged like `v*-pc`
        for release in releases {
            let tag = match release.get("tag_name").and_then(|t| t.as_str()) {
                Some(t) => t,
                None => continue,
            };

            if let Some(wanted) = target_tag {
                if tag != wanted {
                    continue;
                }
            }

            if tag.starts_with('v') && tag.ends_with("-pc") {
                let assets = release.get("assets").and_then(|a| a.as_array());
                let mut exe_url = None;
                let mut sha_url = None;

                if let Some(asset_list) = assets {
                    for asset in asset_list {
                        let name = asset.get("name").and_then(|n| n.as_str()).unwrap_or("");
                        let d_url = asset
                            .get("browser_download_url")
                            .and_then(|u| u.as_str())
                            .unwrap_or("");

                        if name == asset_name
                            || (asset_name == "silent-exec-pc.exe" && name == "silent-exec-pc")
                        {
                            exe_url = Some(d_url.to_string());
                        } else if name == "SHA256SUMS.txt" {
                            sha_url = Some(d_url.to_string());
                        }
                    }
                }

                if let (Some(download_url), Some(sha256_url)) = (exe_url, sha_url) {
                    let update_available = Self::is_newer(&current_version, tag);
                    return Ok(UpdateInfo {
                        current_version,
                        latest_version: tag.to_string(),
                        tag_name: tag.to_string(),
                        download_url,
                        sha256_url,
                        update_available,
                    });
                }
            }
        }

        Ok(UpdateInfo {
            current_version: current_version.clone(),
            latest_version: current_version,
            tag_name: String::new(),
            download_url: String::new(),
            sha256_url: String::new(),
            update_available: false,
        })
    }

    /// Returns `%LOCALAPPDATA%\silent-exec\updates` on Windows or `$HOME/.local/share/silent-exec/updates` on Unix.
    pub fn default_updates_dir() -> PathBuf {
        if cfg!(target_os = "windows") {
            if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
                return PathBuf::from(local_app_data)
                    .join("silent-exec")
                    .join("updates");
            }
        }
        if let Ok(home) = std::env::var("HOME") {
            return PathBuf::from(home)
                .join(".local")
                .join("share")
                .join("silent-exec")
                .join("updates");
        }
        std::env::temp_dir().join("silent_exec_updates")
    }

    /// Downloads `SilentExec-Desktop-x64.exe` (resumable), verifies SHA-256 against `SHA256SUMS.txt`,
    /// and spawns a hidden self-cleaning PowerShell swapper script on Windows to replace & relaunch.
    pub fn download_and_schedule_desktop_update<F>(
        &self,
        tag: &str,
        cancel_flag: Arc<AtomicBool>,
        progress_cb: F,
    ) -> Result<PathBuf, String>
    where
        F: FnMut(DownloadProgress),
    {
        let info = self.check_for_asset_update("SilentExec-Desktop-x64.exe", Some(tag))?;
        if info.download_url.is_empty() || info.sha256_url.is_empty() {
            return Err(format!(
                "Release '{}' does not contain SilentExec-Desktop-x64.exe and SHA256SUMS.txt",
                tag
            ));
        }

        let updates_dir = Self::default_updates_dir();
        fs::create_dir_all(&updates_dir).map_err(|e| {
            format!(
                "Failed to create updates directory {}: {}",
                updates_dir.display(),
                e
            )
        })?;

        let asset_name = "SilentExec-Desktop-x64.exe";
        let downloaded_exe = updates_dir.join(asset_name);
        let downloaded_part = updates_dir.join(format!("{}.part", asset_name));
        let downloaded_sums = updates_dir.join("SHA256SUMS.txt");

        let sums_status = hidden_command("curl")
            .args([
                "-sSfL",
                "-o",
                downloaded_sums.to_str().unwrap_or_default(),
                &info.sha256_url,
            ])
            .status()
            .map_err(|e| format!("Failed to download SHA256SUMS.txt: {}", e))?;

        if !sums_status.success() {
            return Err("Failed to download SHA256SUMS.txt".to_string());
        }

        let sums_content = fs::read_to_string(&downloaded_sums)
            .map_err(|e| format!("Failed to read SHA256SUMS.txt: {}", e))?;

        let expected_sha256 = sums_content
            .lines()
            .find_map(|line| {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 && (parts[1] == asset_name || parts[1].ends_with(asset_name)) {
                    Some(parts[0].to_string())
                } else {
                    None
                }
            })
            .ok_or_else(|| {
                format!(
                    "No SHA-256 hash found for {} in release {}",
                    asset_name, info.tag_name
                )
            })?;

        ModelManager::download_resumable_file(
            &info.download_url,
            &downloaded_part,
            &downloaded_exe,
            &expected_sha256,
            cancel_flag,
            progress_cb,
        )?;

        let current_exe = std::env::current_exe()
            .map_err(|e| format!("Failed to resolve current executable path: {}", e))?;

        #[cfg(target_os = "windows")]
        {
            let pid = std::process::id();
            let swap_script = updates_dir.join("apply_desktop_update.ps1");
            let current_str = current_exe.to_string_lossy().replace('\'', "''");
            let new_str = downloaded_exe.to_string_lossy().replace('\'', "''");
            let sums_str = downloaded_sums.to_string_lossy().replace('\'', "''");

            let ps_content = format!(
                "$ErrorActionPreference = 'SilentlyContinue'\n\
                 $pidToWait = {pid}\n\
                 for ($i = 0; $i -lt 60; $i++) {{\n\
                     if (-not (Get-Process -Id $pidToWait -ErrorAction SilentlyContinue)) {{ break }}\n\
                     Start-Sleep -Milliseconds 250\n\
                 }}\n\
                 for ($r = 0; $r -lt 10; $r++) {{\n\
                     try {{\n\
                         Copy-Item -LiteralPath '{new_str}' -Destination '{current_str}' -Force -ErrorAction Stop\n\
                         break\n\
                     }} catch {{\n\
                         Start-Sleep -Milliseconds 300\n\
                     }}\n\
                 }}\n\
                 Remove-Item -LiteralPath '{new_str}' -Force\n\
                 Remove-Item -LiteralPath '{sums_str}' -Force\n\
                 Start-Process -FilePath '{current_str}'\n\
                 Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force\n"
            );

            fs::write(&swap_script, ps_content)
                .map_err(|e| format!("Failed to write update swapper script: {}", e))?;

            hidden_command("powershell")
                .args([
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle",
                    "Hidden",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    swap_script.to_str().unwrap_or_default(),
                ])
                .spawn()
                .map_err(|e| format!("Failed to launch update swapper: {}", e))?;
        }

        #[cfg(not(target_os = "windows"))]
        {
            let _ = fs::copy(&downloaded_exe, &current_exe);
        }

        Ok(downloaded_exe)
    }

    /// Computes the SHA256 checksum of a file.
    pub fn compute_sha256(path: &Path) -> Result<String, String> {
        let mut file =
            File::open(path).map_err(|e| format!("Failed to open {}: {}", path.display(), e))?;
        let mut hasher = Sha256::new();
        let mut buffer = [0u8; 65536];

        loop {
            let n = file
                .read(&mut buffer)
                .map_err(|e| format!("Read error: {}", e))?;
            if n == 0 {
                break;
            }
            hasher.update(&buffer[..n]);
        }

        Ok(format!("{:x}", hasher.finalize()))
    }

    /// Downloads the updated binary, validates SHA-256 against SHA256SUMS.txt,
    /// and replaces current executable.
    pub fn download_and_apply(&self, info: &UpdateInfo) -> Result<PathBuf, String> {
        let cancel_flag = Arc::new(AtomicBool::new(false));
        self.download_and_apply_with_progress(info, cancel_flag, |_| {})
    }

    /// Resumable download and apply with progress callback and cancellation support.
    pub fn download_and_apply_with_progress<F>(
        &self,
        info: &UpdateInfo,
        cancel_flag: Arc<AtomicBool>,
        progress_cb: F,
    ) -> Result<PathBuf, String>
    where
        F: FnMut(DownloadProgress),
    {
        if !info.update_available {
            return Err("No update available".to_string());
        }

        let temp_dir = std::env::temp_dir().join("silent_exec_update");
        fs::create_dir_all(&temp_dir)
            .map_err(|e| format!("Failed to create temp dir {}: {}", temp_dir.display(), e))?;

        let exe_name = if cfg!(target_os = "windows") {
            "silent-exec-pc.exe"
        } else {
            "silent-exec-pc"
        };
        let downloaded_exe = temp_dir.join(exe_name);
        let downloaded_part = temp_dir.join(format!("{}.part", exe_name));
        let downloaded_sums = temp_dir.join("SHA256SUMS.txt");

        println!("Fetching checksums from: {}", info.sha256_url);
        let sums_status = hidden_command("curl")
            .args([
                "-sSfL",
                "-o",
                downloaded_sums.to_str().unwrap(),
                &info.sha256_url,
            ])
            .status()
            .map_err(|e| format!("Failed to download checksums: {}", e))?;

        if !sums_status.success() {
            return Err("Failed to download SHA256SUMS.txt".to_string());
        }

        let sums_content = fs::read_to_string(&downloaded_sums)
            .map_err(|e| format!("Failed to read SHA256SUMS.txt: {}", e))?;

        let mut expected_hash = None;
        for line in sums_content.lines() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 && (parts[1] == exe_name || parts[1].ends_with(exe_name)) {
                expected_hash = Some(parts[0].to_string());
                break;
            }
        }

        let expected_sha256 = expected_hash.ok_or_else(|| {
            format!(
                "No hash found for {} in release {}",
                exe_name, info.tag_name
            )
        })?;

        println!(
            "Downloading new binary (resumable) from: {}",
            info.download_url
        );
        ModelManager::download_resumable_file(
            &info.download_url,
            &downloaded_part,
            &downloaded_exe,
            &expected_sha256,
            cancel_flag,
            progress_cb,
        )?;

        // Replace current executable
        let current_exe = std::env::current_exe()
            .map_err(|e| format!("Failed to get current_exe path: {}", e))?;

        // On Windows, rename current exe to .old first because running exe cannot be directly overwritten
        let backup_exe = current_exe.with_extension("old");
        if backup_exe.exists() {
            let _ = fs::remove_file(&backup_exe);
        }

        let _ = fs::rename(&current_exe, &backup_exe);
        fs::copy(&downloaded_exe, &current_exe).map_err(|e| {
            format!(
                "Failed to replace executable {}: {}",
                current_exe.display(),
                e
            )
        })?;

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = fs::set_permissions(&current_exe, fs::Permissions::from_mode(0o755));
        }

        let _ = fs::remove_file(&downloaded_exe);
        let _ = fs::remove_file(&downloaded_sums);

        Ok(current_exe)
    }
}
