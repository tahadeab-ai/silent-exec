//! llama.cpp Runtime Manager for SilentExec PC
//! Manages automatic downloading, extraction, git-repo safety gating,
//! and background process lifecycle for `llama-server`.

use crate::model_manager::ModelManager;
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Duration;

/// GitHub Release Asset representation for llama.cpp releases.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ReleaseAsset {
    pub name: String,
    pub browser_download_url: String,
}

/// Minimal GitHub Release metadata representation.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GithubRelease {
    pub tag_name: Option<String>,
    pub assets: Vec<ReleaseAsset>,
}

/// Result of running the full auto-setup workflow.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SetupResult {
    pub model_path: PathBuf,
    pub server_path: PathBuf,
    pub model_tag: String,
    pub model_size_bytes: u64,
    pub already_cached: bool,
}

/// Selects the most appropriate llama.cpp release asset for the target platform.
/// For Windows x64: prioritizes `win-avx2-x64` or `win-cpu-x64` zip, excluding heavy GPU builds (CUDA/Vulkan).
/// For Linux: selects `ubuntu-x64` or `linux-x64` CPU archives.
pub fn select_llama_asset_for_platform<'a>(
    assets: &'a [ReleaseAsset],
    target_os: &str,
    target_arch: &str,
) -> Option<&'a ReleaseAsset> {
    let is_gpu = |name: &str| {
        let lower = name.to_lowercase();
        lower.contains("cuda")
            || lower.contains("cu11")
            || lower.contains("cu12")
            || lower.contains("cudart")
            || lower.contains("vulkan")
            || lower.contains("hip")
            || lower.contains("kompute")
            || lower.contains("sycl")
            || lower.contains("opencl")
    };

    if target_os == "windows" {
        let mut candidates: Vec<&'a ReleaseAsset> = assets
            .iter()
            .filter(|a| {
                let lower = a.name.to_lowercase();
                lower.ends_with(".zip")
                    && (lower.contains("win") || lower.contains("windows"))
                    && (lower.contains("x64")
                        || lower.contains("x86_64")
                        || target_arch == "x86_64")
                    && !lower.contains("arm64")
                    && !is_gpu(&lower)
            })
            .collect();

        if candidates.is_empty() {
            return None;
        }

        // Rank candidates:
        // Rank 0: contains "avx2" (e.g. win-avx2-x64)
        // Rank 1: contains "cpu" (e.g. win-cpu-x64)
        // Rank 2: generic win-x64
        candidates.sort_by_key(|a| {
            let lower = a.name.to_lowercase();
            if lower.contains("avx2") {
                0
            } else if lower.contains("cpu") {
                1
            } else {
                2
            }
        });

        Some(candidates[0])
    } else {
        let mut candidates: Vec<&'a ReleaseAsset> = assets
            .iter()
            .filter(|a| {
                let lower = a.name.to_lowercase();
                (lower.ends_with(".tar.gz") || lower.ends_with(".zip"))
                    && (lower.contains("ubuntu") || lower.contains("linux"))
                    && (lower.contains("x64") || lower.contains("x86_64"))
                    && !lower.contains("arm64")
                    && !is_gpu(&lower)
            })
            .collect();

        if candidates.is_empty() {
            return None;
        }

        candidates.sort_by_key(|a| {
            let lower = a.name.to_lowercase();
            if lower.contains("cpu") {
                0
            } else {
                1
            }
        });

        Some(candidates[0])
    }
}

/// Convenience helper selecting the asset matching the current operating system.
pub fn select_llama_asset(assets: &[ReleaseAsset]) -> Option<&ReleaseAsset> {
    let os = if cfg!(target_os = "windows") {
        "windows"
    } else {
        "linux"
    };
    select_llama_asset_for_platform(assets, os, "x86_64")
}

/// Normalizes and parses any server/health endpoint URL into a valid `/health` check URL.
pub fn parse_health_url(input: &str, default_port: u16) -> String {
    let trimmed = input.trim();
    let without_scheme = if let Some(stripped) = trimmed.strip_prefix("http://") {
        stripped
    } else if let Some(stripped) = trimmed.strip_prefix("https://") {
        stripped
    } else {
        trimmed
    };

    let (host_port, _path) = match without_scheme.split_once('/') {
        Some((hp, p)) => (hp, p),
        None => (without_scheme, ""),
    };

    let full_host_port = if host_port.contains(':') {
        host_port.to_string()
    } else {
        format!("{}:{}", host_port, default_port)
    };

    let scheme = if trimmed.starts_with("https://") {
        "https://"
    } else {
        "http://"
    };

    format!("{}{}/health", scheme, full_host_port)
}

/// Checks if an HTTP `/health` endpoint is responding with HTTP 200 OK.
pub fn check_health_http(health_url: &str, timeout: Duration) -> bool {
    // Attempt fast zero-dependency TCP stream check
    let without_scheme = health_url
        .strip_prefix("http://")
        .or_else(|| health_url.strip_prefix("https://"))
        .unwrap_or(health_url);

    let (host_port, path) = match without_scheme.split_once('/') {
        Some((hp, p)) => (hp, format!("/{}", p)),
        None => (without_scheme, "/health".to_string()),
    };

    let (host, port) = match host_port.split_once(':') {
        Some((h, p)) => (h.to_string(), p.parse::<u16>().unwrap_or(80)),
        None => (host_port.to_string(), 80),
    };

    let addr_str = format!("{}:{}", host, port);
    if let Ok(addrs) = std::net::ToSocketAddrs::to_socket_addrs(&addr_str) {
        for addr in addrs {
            if let Ok(stream) = TcpStream::connect_timeout(&addr, timeout) {
                let _ = stream.set_read_timeout(Some(timeout));
                let _ = stream.set_write_timeout(Some(timeout));
                let mut stream_writer = match stream.try_clone() {
                    Ok(s) => s,
                    Err(_) => continue,
                };
                let req = format!(
                    "GET {} HTTP/1.1\r\nHost: {}\r\nConnection: close\r\n\r\n",
                    path, host
                );
                if stream_writer.write_all(req.as_bytes()).is_ok() {
                    let mut reader = BufReader::new(stream);
                    let mut status_line = String::new();
                    if reader.read_line(&mut status_line).is_ok() && status_line.contains("200") {
                        return true;
                    }
                }
            }
        }
    }

    // Fallback to curl
    let res = Command::new("curl")
        .args(["-sSf", "--max-time", "1", health_url])
        .output();
    res.map(|o| o.status.success()).unwrap_or(false)
}

/// Runtime Manager for locating, installing, and supervising `llama-server`.
#[derive(Debug, Clone)]
pub struct RuntimeManager {
    runtime_dir: PathBuf,
    llama_repo: String,
}

impl RuntimeManager {
    pub fn new(custom_runtime: Option<PathBuf>) -> Result<Self, String> {
        let runtime_dir = match custom_runtime {
            Some(p) => p,
            None => Self::default_runtime_dir(),
        };

        Self::assert_not_in_git_repo(&runtime_dir)?;

        fs::create_dir_all(&runtime_dir).map_err(|e| {
            format!(
                "Failed to create runtime directory at {}: {}",
                runtime_dir.display(),
                e
            )
        })?;

        Ok(Self {
            runtime_dir,
            llama_repo: "ggml-org/llama.cpp".to_string(),
        })
    }

    pub fn with_llama_repo(mut self, repo: String) -> Self {
        self.llama_repo = repo;
        self
    }

    pub fn runtime_dir(&self) -> &Path {
        &self.runtime_dir
    }

    /// Determines standard OS runtime path outside git.
    pub fn default_runtime_dir() -> PathBuf {
        if let Ok(env_val) = std::env::var("SILENT_EXEC_RUNTIME_DIR") {
            return PathBuf::from(env_val);
        }

        if cfg!(target_os = "windows") {
            if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
                return PathBuf::from(local_app_data)
                    .join("silent-exec")
                    .join("runtime");
            }
        }

        if let Ok(home) = std::env::var("HOME") {
            return PathBuf::from(home)
                .join(".cache")
                .join("silent-exec")
                .join("runtime");
        }

        PathBuf::from(".silent_exec_runtime")
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
                    "Runtime directory '{}' is located inside the SilentExec source repository ('{}'). Binaries must be stored outside git history.",
                    path.display(),
                    cur.display()
                ));
            }
            cur = parent;
        }
        Ok(())
    }

    pub fn server_binary_name() -> &'static str {
        if cfg!(target_os = "windows") {
            "llama-server.exe"
        } else {
            "llama-server"
        }
    }

    /// Finds the `llama-server` binary in the managed runtime directory or subdirectories.
    pub fn find_llama_server_binary(&self) -> Option<PathBuf> {
        Self::find_server_in_dir(&self.runtime_dir)
    }

    /// Searches a directory (and up to 2 levels of subdirectories) for the server binary.
    pub fn find_server_in_dir(dir: &Path) -> Option<PathBuf> {
        let bin_name = Self::server_binary_name();
        let direct = dir.join(bin_name);
        if direct.is_file() {
            return Some(direct);
        }

        if let Ok(entries) = fs::read_dir(dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    let sub_bin = path.join(bin_name);
                    if sub_bin.is_file() {
                        return Some(sub_bin);
                    }
                    if let Ok(sub_entries) = fs::read_dir(&path) {
                        for sub_entry in sub_entries.flatten() {
                            let sub_path = sub_entry.path();
                            if sub_path.is_dir() {
                                let deep_bin = sub_path.join(bin_name);
                                if deep_bin.is_file() {
                                    return Some(deep_bin);
                                }
                            }
                        }
                    }
                }
            }
        }
        None
    }

    /// Extracts an archive (`.zip` or `.tar.gz`) into the target destination directory.
    pub fn extract_archive(
        archive_path: &Path,
        destination_dir: &Path,
    ) -> Result<(), Box<dyn std::error::Error>> {
        fs::create_dir_all(destination_dir)?;

        // Try tar first (handles both zip and tar.gz on Windows 10+ and Linux)
        let tar_status = Command::new("tar")
            .args([
                "-xf",
                archive_path.to_str().unwrap(),
                "-C",
                destination_dir.to_str().unwrap(),
            ])
            .status();

        if let Ok(status) = tar_status {
            if status.success() {
                return Ok(());
            }
        }

        #[cfg(target_os = "windows")]
        {
            let ps_cmd = format!(
                "Expand-Archive -Path '{}' -DestinationPath '{}' -Force",
                archive_path.display(),
                destination_dir.display()
            );
            let ps_status = Command::new("powershell")
                .args(["-NoProfile", "-Command", &ps_cmd])
                .status();

            if let Ok(status) = ps_status {
                if status.success() {
                    return Ok(());
                }
            }
        }

        #[cfg(not(target_os = "windows"))]
        {
            let unzip_status = Command::new("unzip")
                .args([
                    "-o",
                    archive_path.to_str().unwrap(),
                    "-d",
                    destination_dir.to_str().unwrap(),
                ])
                .status();

            if let Ok(status) = unzip_status {
                if status.success() {
                    return Ok(());
                }
            }
        }

        Err(format!(
            "Failed to extract archive {} into {}",
            archive_path.display(),
            destination_dir.display()
        )
        .into())
    }

    /// Queries the latest official `ggml-org/llama.cpp` release, selects the lightweight CPU archive,
    /// downloads it, and extracts `llama-server` and companion libraries into the runtime directory.
    pub fn download_and_extract_runtime(&self) -> Result<PathBuf, Box<dyn std::error::Error>> {
        if let Some(existing) = self.find_llama_server_binary() {
            println!("Found existing llama-server at: {}", existing.display());
            return Ok(existing);
        }

        let release_url = format!(
            "https://api.github.com/repos/{}/releases/latest",
            self.llama_repo
        );
        println!(
            "Fetching latest llama.cpp release metadata from: {}",
            release_url
        );

        let output = Command::new("curl")
            .args([
                "-sSfL",
                "-H",
                "User-Agent: silent-exec",
                "-H",
                "Accept: application/vnd.github.v3+json",
                &release_url,
            ])
            .output()
            .map_err(|e| format!("Failed to invoke curl: {}", e))?;

        if !output.status.success() {
            return Err(format!(
                "Failed to fetch llama.cpp release metadata: {}",
                String::from_utf8_lossy(&output.stderr)
            )
            .into());
        }

        let release_json = String::from_utf8(output.stdout)?;
        let release: GithubRelease = serde_json::from_str(&release_json)
            .map_err(|e| format!("Failed to parse release JSON: {}", e))?;

        let asset = select_llama_asset(&release.assets).ok_or_else(|| {
            "No suitable CPU llama.cpp binary asset found in latest release for this platform"
                .to_string()
        })?;

        println!(
            "Selected llama.cpp release asset: {} ({})",
            asset.name, asset.browser_download_url
        );

        let archive_path = self.runtime_dir.join(&asset.name);
        let part_path = self.runtime_dir.join(format!("{}.part", asset.name));

        println!(
            "Downloading {} to {}...",
            asset.browser_download_url,
            part_path.display()
        );
        let dl_status = Command::new("curl")
            .args([
                "-sSfL",
                "-o",
                part_path.to_str().unwrap(),
                &asset.browser_download_url,
            ])
            .status()
            .map_err(|e| format!("Failed to download asset: {}", e))?;

        if !dl_status.success() {
            let _ = fs::remove_file(&part_path);
            return Err(format!(
                "curl failed downloading asset from {}",
                asset.browser_download_url
            )
            .into());
        }

        fs::rename(&part_path, &archive_path)?;

        println!(
            "Extracting {} into {}...",
            archive_path.display(),
            self.runtime_dir.display()
        );
        Self::extract_archive(&archive_path, &self.runtime_dir)?;

        // Clean up temporary archive file
        let _ = fs::remove_file(&archive_path);

        let server_bin = self.find_llama_server_binary().ok_or_else(|| {
            format!(
                "llama-server executable not found after extracting {} to {}",
                asset.name,
                self.runtime_dir.display()
            )
        })?;

        println!(
            "Successfully installed llama-server to: {}",
            server_bin.display()
        );
        Ok(server_bin)
    }
}

/// Managed server helper that checks `GET http://127.0.0.1:<port>/health`.
/// If already healthy, reuses it immediately.
/// Otherwise, silently spawns `llama-server` (using `CREATE_NO_WINDOW` on Windows)
/// and polls `/health` with a clean timeout before returning the base server URL.
pub fn ensure_llama_server_running(
    model_path: &Path,
    runtime_dir: &Path,
    port: u16,
) -> Result<String, String> {
    let health_url = format!("http://127.0.0.1:{}/health", port);

    // 1. Check if server is already running and healthy
    if check_health_http(&health_url, Duration::from_millis(500)) {
        println!("Reusing running llama-server at http://127.0.0.1:{}", port);
        return Ok(format!("http://127.0.0.1:{}", port));
    }

    // 2. Validate model existence
    if !model_path.exists() {
        return Err(format!(
            "Model file not found at '{}'. Run 'silent-exec-pc setup' to download model.",
            model_path.display()
        ));
    }

    // 3. Locate server binary in runtime directory
    let server_bin = RuntimeManager::find_server_in_dir(runtime_dir).ok_or_else(|| {
        format!(
            "llama-server executable not found in runtime directory '{}'. Run 'silent-exec-pc setup' first.",
            runtime_dir.display()
        )
    })?;

    let work_dir = server_bin.parent().unwrap_or(runtime_dir);

    println!(
        "Starting managed llama-server (port: {}, model: {})...",
        port,
        model_path.display()
    );

    let mut cmd = Command::new(&server_bin);
    cmd.args([
        "-m",
        model_path.to_str().ok_or("Invalid model path")?,
        "--host",
        "127.0.0.1",
        "--port",
        &port.to_string(),
        "--ctx-size",
        "1024",
        "-ngl",
        "0",
    ]);
    cmd.current_dir(work_dir);
    cmd.stdin(std::process::Stdio::null());
    cmd.stdout(std::process::Stdio::null());
    cmd.stderr(std::process::Stdio::null());

    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    let _child = cmd.spawn().map_err(|e| {
        format!(
            "Failed to spawn llama-server ('{}'): {}",
            server_bin.display(),
            e
        )
    })?;

    // Poll /health until server is ready
    let start = std::time::Instant::now();
    let timeout = Duration::from_secs(30);
    let poll_interval = Duration::from_millis(300);

    while start.elapsed() < timeout {
        if check_health_http(&health_url, Duration::from_millis(300)) {
            println!(
                "Managed llama-server is healthy at http://127.0.0.1:{}",
                port
            );
            return Ok(format!("http://127.0.0.1:{}", port));
        }
        std::thread::sleep(poll_interval);
    }

    Err(format!(
        "Timed out after 30 seconds waiting for llama-server to become healthy at {}",
        health_url
    ))
}

/// Runs the complete auto-setup workflow:
/// 1. Downloads and SHA-256 verifies the quantized GGUF model via ModelManager.
/// 2. Downloads and extracts `llama-server` and DLLs via RuntimeManager.
pub fn run_setup(
    model_tag: Option<&str>,
    custom_model_cache: Option<PathBuf>,
    custom_runtime_dir: Option<PathBuf>,
) -> Result<SetupResult, Box<dyn std::error::Error>> {
    let tag = model_tag.unwrap_or("v0.2.0-model");

    println!("=== Step 1/2: Preparing Model Weights ===");
    let model_mgr = ModelManager::new(custom_model_cache)?;
    let model_info = model_mgr.download_model(tag)?;

    println!("\n=== Step 2/2: Preparing llama.cpp Runtime ===");
    let runtime_mgr = RuntimeManager::new(custom_runtime_dir)?;
    let server_bin = runtime_mgr.download_and_extract_runtime()?;

    Ok(SetupResult {
        model_path: model_info.path,
        server_path: server_bin,
        model_tag: tag.to_string(),
        model_size_bytes: model_info.size_bytes,
        already_cached: model_info.already_cached,
    })
}
