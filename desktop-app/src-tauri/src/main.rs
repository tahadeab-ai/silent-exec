// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use silent_exec_core::{
    action_type_to_str as core_action_type_to_str, ActionCommand, ExecutionMode, MultiStepPlan,
    SafetyDecision, SafetyGate,
};
use silent_exec_pc::{
    action_type_to_str, hidden_command, parse_plan, resolve_clause, split_compound_prompt,
    start_bridge_server, AiBackend, AppConfig, BridgeConfig, ExecutionResult, FastRuleRouter,
    FileIndexer, McpClient, McpServerConfig, McpTool, MemoryItem, ModelDownloadInfo, ModelManager,
    ModelReleaseInfo, PcExecutor, RuntimeManager, SelfUpdater, SetupResult,
};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{Emitter, Manager, State};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, Shortcut};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StepInfo {
    pub step_index: usize,
    pub total_steps: usize,
    pub stage: String,
    pub description: String,
    pub action: Option<String>,
    pub details: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExecutionReport {
    pub success: bool,
    pub query: String,
    pub steps: Vec<StepInfo>,
    pub message: String,
    pub requires_confirm: bool,
    pub pending_step_index: Option<usize>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BridgeStatusResponse {
    pub is_running: bool,
    pub address: Option<String>,
    pub token: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfigDto {
    pub language: String,
    pub mode: String,
    pub active_model_tag: Option<String>,
    pub fast_router_enabled: bool,
    pub backend: String,
    pub ollama_url: String,
    pub ollama_model: String,
    pub openai_api_base: String,
    pub openai_api_model: String,
    pub openai_api_key: String,
    pub bridge_running: bool,
    pub bridge_url: Option<String>,
    pub bridge_token: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackendTestResult {
    pub success: bool,
    pub latency_ms: u64,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct McpServerDto {
    pub name: String,
    pub command: String,
    pub args: Vec<String>,
    pub enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateInfoDto {
    pub current_version: String,
    pub latest_version: String,
    pub tag_name: String,
    pub download_url: String,
    pub sha256_url: String,
    pub update_available: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeStatusDto {
    pub is_running: bool,
    pub endpoint: String,
    pub server_path: Option<String>,
}

type BridgeInstance = (SocketAddr, Arc<AtomicBool>, JoinHandle<()>);

struct AppState {
    bridge_handle: Mutex<Option<BridgeInstance>>,
    pending_execution: Arc<Mutex<Option<(ActionCommand, ExecutionMode)>>>,
    download_cancellations: Mutex<HashMap<String, Arc<AtomicBool>>>,
}

fn create_default_executor(dry_run: bool) -> PcExecutor {
    let indexer = FileIndexer::new(FileIndexer::default_user_roots());
    PcExecutor::new(indexer, dry_run)
}

fn format_execution_result(res: ExecutionResult) -> (String, String) {
    match res {
        ExecutionResult::Success { details, .. } => ("completed".to_string(), details),
        ExecutionResult::DryRun { message, .. } => {
            ("completed".to_string(), format!("Simulation: {}", message))
        }
        ExecutionResult::Error { message, .. } => ("failed".to_string(), message),
        ExecutionResult::Blocked { reason, .. } => ("blocked".to_string(), reason),
        ExecutionResult::ConfirmationRequired { prompt, .. } => {
            ("awaiting_confirm".to_string(), prompt)
        }
    }
}

// ---------------------------------------------------------
// Commands (100% Non-Blocking async fn)
// ---------------------------------------------------------

#[tauri::command]
async fn get_app_config(state: State<'_, AppState>) -> Result<AppConfigDto, String> {
    let bridge_guard = state.bridge_handle.lock().unwrap();
    let (running, url, token) = match &*bridge_guard {
        Some((addr, _, _)) => (
            true,
            Some(format!("http://{}", addr)),
            Some("silent-exec-secret".to_string()),
        ),
        None => (false, None, None),
    };

    let cfg = AppConfig::load();
    Ok(AppConfigDto {
        language: cfg.language,
        mode: cfg.mode,
        active_model_tag: cfg.active_model_tag,
        fast_router_enabled: cfg.fast_router_enabled,
        backend: cfg.backend,
        ollama_url: cfg.ollama_url,
        ollama_model: cfg.ollama_model,
        openai_api_base: cfg.openai_api_base,
        openai_api_model: cfg.openai_api_model,
        openai_api_key: cfg.openai_api_key,
        bridge_running: running,
        bridge_url: url,
        bridge_token: token,
    })
}

#[tauri::command]
async fn save_app_config(
    language: Option<String>,
    mode: Option<String>,
    active_model_tag: Option<String>,
    fast_router_enabled: Option<bool>,
    backend: Option<String>,
    ollama_url: Option<String>,
    ollama_model: Option<String>,
    openai_api_base: Option<String>,
    openai_api_model: Option<String>,
    openai_api_key: Option<String>,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let mut cfg = AppConfig::load();
        if let Some(l) = language {
            cfg.language = l;
        }
        if let Some(m) = mode {
            cfg.mode = m;
        }
        if let Some(t) = active_model_tag {
            cfg.active_model_tag = Some(t);
        }
        if let Some(f) = fast_router_enabled {
            cfg.fast_router_enabled = f;
        }
        if let Some(b) = backend {
            cfg.backend = b;
        }
        if let Some(ou) = ollama_url {
            cfg.ollama_url = ou;
        }
        if let Some(om) = ollama_model {
            cfg.ollama_model = om;
        }
        if let Some(oab) = openai_api_base {
            cfg.openai_api_base = oab;
        }
        if let Some(oam) = openai_api_model {
            cfg.openai_api_model = oam;
        }
        if let Some(oak) = openai_api_key {
            cfg.openai_api_key = oak;
        }

        let path = AppConfig::default_config_path();
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let data = serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string())?;
        std::fs::write(&path, data).map_err(|e| e.to_string())?;
        Ok(())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn execute_query(
    query: String,
    mode: Option<String>,
    state: State<'_, AppState>,
) -> Result<ExecutionReport, String> {
    let pending_exec = state.pending_execution.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let trimmed = query.trim();
        if trimmed.is_empty() {
            return Err("Query cannot be empty".to_string());
        }

        let cfg = AppConfig::load();
        let exec_mode = match mode.as_deref().unwrap_or(&cfg.mode) {
            "interactive" => ExecutionMode::Interactive,
            "dry_run" => ExecutionMode::DryRun,
            _ => ExecutionMode::Silent,
        };

        let mut steps_out = Vec::new();
        let executor = create_default_executor(exec_mode == ExecutionMode::DryRun);

        // 1. Fast Rule Router check (if single command and enabled)
        if cfg.fast_router_enabled {
            if let Some(cmd) = FastRuleRouter::route(trimmed) {
                let target_str = cmd.target.as_deref().unwrap_or("").to_string();
                steps_out.push(StepInfo {
                    step_index: 1,
                    total_steps: 1,
                    stage: "understanding".to_string(),
                    description: "Fast Rule Router matched (<15ms)".to_string(),
                    action: Some(action_type_to_str(cmd.action).to_string()),
                    details: Some(format!("Target: {}", target_str)),
                });

                let decision = SafetyGate::evaluate(&cmd);
                match decision {
                    SafetyDecision::Block { reason } => {
                        steps_out.push(StepInfo {
                            step_index: 1,
                            total_steps: 1,
                            stage: "blocked".to_string(),
                            description: "Blocked by Safety Gate".to_string(),
                            action: Some(action_type_to_str(cmd.action).to_string()),
                            details: Some(reason.clone()),
                        });
                        return Ok(ExecutionReport {
                            success: false,
                            query: query.clone(),
                            steps: steps_out,
                            message: format!("Blocked: {}", reason),
                            requires_confirm: false,
                            pending_step_index: None,
                        });
                    }
                    SafetyDecision::RequireConfirm { reason } => {
                        if exec_mode != ExecutionMode::DryRun {
                            *pending_exec.lock().unwrap() = Some((cmd.clone(), exec_mode));
                            steps_out.push(StepInfo {
                                step_index: 1,
                                total_steps: 1,
                                stage: "awaiting_confirm".to_string(),
                                description: "User confirmation required".to_string(),
                                action: Some(action_type_to_str(cmd.action).to_string()),
                                details: Some(reason.clone()),
                            });
                            return Ok(ExecutionReport {
                                success: true,
                                query: query.clone(),
                                steps: steps_out,
                                message: format!("Confirmation required: {}", reason),
                                requires_confirm: true,
                                pending_step_index: Some(1),
                            });
                        }
                    }
                    SafetyDecision::AllowSilent => {}
                }

                if exec_mode == ExecutionMode::Interactive {
                    *pending_exec.lock().unwrap() = Some((cmd.clone(), exec_mode));
                    steps_out.push(StepInfo {
                        step_index: 1,
                        total_steps: 1,
                        stage: "awaiting_confirm".to_string(),
                        description: "Interactive mode: Confirm step".to_string(),
                        action: Some(action_type_to_str(cmd.action).to_string()),
                        details: Some(format!("Target: {}", target_str)),
                    });
                    return Ok(ExecutionReport {
                        success: true,
                        query: query.clone(),
                        steps: steps_out,
                        message: "Interactive mode confirmation".to_string(),
                        requires_confirm: true,
                        pending_step_index: Some(1),
                    });
                }

                let dry_run = exec_mode == ExecutionMode::DryRun;
                let result = executor.execute(&cmd, dry_run);
                let (stage, details) = format_execution_result(result);

                steps_out.push(StepInfo {
                    step_index: 1,
                    total_steps: 1,
                    stage,
                    description: format!("Execute action '{}'", action_type_to_str(cmd.action)),
                    action: Some(action_type_to_str(cmd.action).to_string()),
                    details: Some(details),
                });

                return Ok(ExecutionReport {
                    success: true,
                    query: query.clone(),
                    steps: steps_out,
                    message: "Completed successfully".to_string(),
                    requires_confirm: false,
                    pending_step_index: None,
                });
            }
        }

        // Helper to run AI backend inference
        let run_ai_backend = |prompt_str: &str| -> Result<ActionCommand, String> {
            let backend = match cfg.backend.as_str() {
                "ollama" => AiBackend::new_ollama(Some(&cfg.ollama_url), Some(&cfg.ollama_model)),
                "openai_compat" => AiBackend::new_openai_compat(
                    Some(&cfg.openai_api_base),
                    Some(&cfg.openai_api_model),
                    Some(&cfg.openai_api_key),
                ),
                _ => {
                    let manager = ModelManager::new(None).ok();
                    if let Some(m) = manager {
                        if m.find_cached_model().is_some() {
                            AiBackend::new_local_llama(None, None)
                        } else {
                            return Err(
                                "AI backend not ready — click Setup in Models tab or configure Ollama/API in Settings"
                                    .to_string(),
                            );
                        }
                    } else {
                        return Err(
                            "AI backend not ready — click Setup in Models tab or configure Ollama/API in Settings"
                                .to_string(),
                        );
                    }
                }
            };

            backend.infer(prompt_str).map_err(|e| {
                format!(
                    "AI backend error ({}) — click Setup in Models tab or configure Ollama/API in Settings",
                    e
                )
            })
        };

        // 2. Compound or Multi-Step Planning
        let clauses = split_compound_prompt(trimmed);
        let plan = if clauses.len() > 1 {
            let mut steps = Vec::new();
            for clause in &clauses {
                if let Some(cmd) = resolve_clause(clause) {
                    steps.push(cmd);
                } else {
                    match run_ai_backend(clause) {
                        Ok(cmd) => steps.push(cmd),
                        Err(err_msg) => {
                            steps_out.push(StepInfo {
                                step_index: steps.len() + 1,
                                total_steps: clauses.len(),
                                stage: "failed".to_string(),
                                description: "AI Backend Required".to_string(),
                                action: None,
                                details: Some(err_msg.clone()),
                            });
                            return Ok(ExecutionReport {
                                success: false,
                                query: query.clone(),
                                steps: steps_out,
                                message: err_msg,
                                requires_confirm: false,
                                pending_step_index: None,
                            });
                        }
                    }
                }
            }
            MultiStepPlan { steps }
        } else if let Some(cmd) = resolve_clause(trimmed) {
            MultiStepPlan { steps: vec![cmd] }
        } else {
            match parse_plan(trimmed) {
                Ok(p) => p,
                Err(_) => match run_ai_backend(trimmed) {
                    Ok(cmd) => MultiStepPlan { steps: vec![cmd] },
                    Err(err_msg) => {
                        steps_out.push(StepInfo {
                            step_index: 1,
                            total_steps: 1,
                            stage: "failed".to_string(),
                            description: "AI Backend Required".to_string(),
                            action: None,
                            details: Some(err_msg.clone()),
                        });
                        return Ok(ExecutionReport {
                            success: false,
                            query: query.clone(),
                            steps: steps_out,
                            message: err_msg,
                            requires_confirm: false,
                            pending_step_index: None,
                        });
                    }
                },
            }
        };

        let total = plan.steps.len();
        steps_out.push(StepInfo {
            step_index: 1,
            total_steps: total,
            stage: "planned".to_string(),
            description: format!("Formulated plan with {} step(s)", total),
            action: None,
            details: Some(format!("Clauses: {:?}", clauses)),
        });

        for (idx, step) in plan.steps.iter().enumerate() {
            let step_num = idx + 1;
            let decision = SafetyGate::evaluate(step);
            match decision {
                SafetyDecision::Block { reason } => {
                    steps_out.push(StepInfo {
                        step_index: step_num,
                        total_steps: total,
                        stage: "blocked".to_string(),
                        description: format!("Step {} blocked by Safety Policy", step_num),
                        action: Some(action_type_to_str(step.action).to_string()),
                        details: Some(reason.clone()),
                    });
                    return Ok(ExecutionReport {
                        success: false,
                        query: query.clone(),
                        steps: steps_out,
                        message: format!("Step {} blocked: {}", step_num, reason),
                        requires_confirm: false,
                        pending_step_index: None,
                    });
                }
                SafetyDecision::RequireConfirm { reason } => {
                    if exec_mode != ExecutionMode::DryRun {
                        *pending_exec.lock().unwrap() = Some((step.clone(), exec_mode));
                        steps_out.push(StepInfo {
                            step_index: step_num,
                            total_steps: total,
                            stage: "awaiting_confirm".to_string(),
                            description: format!("Step {} requires user confirmation", step_num),
                            action: Some(action_type_to_str(step.action).to_string()),
                            details: Some(reason.clone()),
                        });
                        return Ok(ExecutionReport {
                            success: true,
                            query: query.clone(),
                            steps: steps_out,
                            message: format!("Confirm step {}: {}", step_num, reason),
                            requires_confirm: true,
                            pending_step_index: Some(step_num),
                        });
                    }
                }
                SafetyDecision::AllowSilent => {}
            }

            if exec_mode == ExecutionMode::Interactive {
                let target_str = step.target.as_deref().unwrap_or("").to_string();
                *pending_exec.lock().unwrap() = Some((step.clone(), exec_mode));
                steps_out.push(StepInfo {
                    step_index: step_num,
                    total_steps: total,
                    stage: "awaiting_confirm".to_string(),
                    description: format!("Interactive mode: Step {} confirmation", step_num),
                    action: Some(action_type_to_str(step.action).to_string()),
                    details: Some(format!("Target: {}", target_str)),
                });
                return Ok(ExecutionReport {
                    success: true,
                    query: query.clone(),
                    steps: steps_out,
                    message: format!("Confirm step {} of {}", step_num, total),
                    requires_confirm: true,
                    pending_step_index: Some(step_num),
                });
            }

            let dry_run = exec_mode == ExecutionMode::DryRun;
            let res = executor.execute(step, dry_run);
            let (stage, details) = format_execution_result(res);

            steps_out.push(StepInfo {
                step_index: step_num,
                total_steps: total,
                stage,
                description: format!("Step {}: {}", step_num, action_type_to_str(step.action)),
                action: Some(action_type_to_str(step.action).to_string()),
                details: Some(details),
            });
        }

        Ok(ExecutionReport {
            success: true,
            query,
            steps: steps_out,
            message: "Plan executed successfully".to_string(),
            requires_confirm: false,
            pending_step_index: None,
        })
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn run_agent_plan(
    query: String,
    mode: Option<String>,
    state: State<'_, AppState>,
) -> Result<ExecutionReport, String> {
    execute_query(query, mode, state).await
}

#[tauri::command]
async fn confirm_step(
    step_index: usize,
    proceed: bool,
    state: State<'_, AppState>,
) -> Result<ExecutionReport, String> {
    let pending = state.pending_execution.lock().unwrap().take();
    let (cmd, mode) = match pending {
        Some(p) => p,
        None => return Err("No pending step awaiting confirmation".to_string()),
    };

    tauri::async_runtime::spawn_blocking(move || {
        let mut steps_out = Vec::new();
        if !proceed {
            steps_out.push(StepInfo {
                step_index,
                total_steps: step_index,
                stage: "failed".to_string(),
                description: format!("Step {} skipped or cancelled by user", step_index),
                action: Some(action_type_to_str(cmd.action).to_string()),
                details: Some("User chose not to proceed".to_string()),
            });
            return Ok(ExecutionReport {
                success: false,
                query: "User skipped".to_string(),
                steps: steps_out,
                message: "Step skipped".to_string(),
                requires_confirm: false,
                pending_step_index: None,
            });
        }

        let executor = create_default_executor(mode == ExecutionMode::DryRun);
        let dry_run = mode == ExecutionMode::DryRun;
        let res = executor.execute(&cmd, dry_run);
        let (stage, details) = format_execution_result(res);

        steps_out.push(StepInfo {
            step_index,
            total_steps: step_index,
            stage,
            description: format!(
                "Confirmed and executed '{}'",
                action_type_to_str(cmd.action)
            ),
            action: Some(action_type_to_str(cmd.action).to_string()),
            details: Some(details),
        });

        Ok(ExecutionReport {
            success: true,
            query: "Confirmed step".to_string(),
            steps: steps_out,
            message: "Step executed successfully".to_string(),
            requires_confirm: false,
            pending_step_index: None,
        })
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn list_models() -> Result<Vec<ModelReleaseInfo>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let cfg = AppConfig::load();
        let mgr = ModelManager::new(None)?;
        mgr.list_available_models(cfg.active_model_tag.as_deref())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn check_runtime_status() -> Result<RuntimeStatusDto, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let mgr = RuntimeManager::new(None)?;
        let server_path = mgr
            .find_llama_server_binary()
            .map(|p| p.to_string_lossy().to_string());
        let health_url = "http://127.0.0.1:8080/health";
        let is_running =
            silent_exec_pc::check_health_http(health_url, std::time::Duration::from_millis(500));
        Ok(RuntimeStatusDto {
            is_running,
            endpoint: "http://127.0.0.1:8080".to_string(),
            server_path,
        })
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn run_setup(model_tag: Option<String>) -> Result<SetupResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let tag = model_tag.unwrap_or_else(|| "v0.3.0-model".to_string());
        silent_exec_pc::run_setup(Some(&tag), None, None).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn setup_runtime(model_tag: Option<String>) -> Result<SetupResult, String> {
    run_setup(model_tag).await
}

#[tauri::command]
async fn start_model_download(
    tag: String,
    window: tauri::Window,
    state: State<'_, AppState>,
) -> Result<ModelDownloadInfo, String> {
    let cancel_flag = Arc::new(AtomicBool::new(false));
    {
        let mut cancellations = state.download_cancellations.lock().unwrap();
        cancellations.insert(tag.clone(), cancel_flag.clone());
    }

    let tag_clone = tag.clone();
    let cancel_clone = cancel_flag.clone();
    let res = tauri::async_runtime::spawn_blocking(move || {
        let mgr = ModelManager::new(None)?;
        let models = mgr.list_available_models(None)?;
        let target_model = models
            .into_iter()
            .find(|m| m.tag == tag_clone)
            .ok_or_else(|| format!("Model with tag {} not found", tag_clone))?;

        let filename = target_model.filename.clone();
        let (download_url, sha256_val) = if let Some(url) = target_model.download_url {
            (url, "SKIP".to_string())
        } else {
            let base_url = format!(
                "https://github.com/{}/releases/download/{}",
                "tahadeab-ai/silent-exec", tag_clone
            );
            let dl_url = format!("{}/{}", base_url, filename);
            let checksums_url = format!("{}/SHA256SUMS.txt", base_url);
            let out = hidden_command("curl")
                .args(["-sSfL", &checksums_url])
                .output()
                .map_err(|e| format!("Failed to fetch SHA256SUMS.txt: {}", e))?;

            if !out.status.success() {
                return Err(format!(
                    "Failed to download checksums from {}",
                    checksums_url
                ));
            }

            let sums_text = String::from_utf8_lossy(&out.stdout);
            let mut expected_hash = None;
            for line in sums_text.lines() {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 && (parts[1] == filename || parts[1].ends_with(&filename)) {
                    expected_hash = Some(parts[0].to_string());
                    break;
                }
            }

            let hash = expected_hash.unwrap_or_else(|| "SKIP".to_string());
            (dl_url, hash)
        };

        let final_path = mgr.cache_dir().join(&filename);
        let part_path = mgr.cache_dir().join(format!("{}.part", filename));

        let tag_for_event = tag_clone.clone();
        let window_clone = window.clone();
        ModelManager::download_resumable_file(
            &download_url,
            &part_path,
            &final_path,
            &sha256_val,
            cancel_clone,
            move |progress| {
                let _ =
                    window_clone.emit(&format!("download-progress:{}", tag_for_event), progress);
            },
        )?;

        let size_bytes = std::fs::metadata(&final_path).map(|m| m.len()).unwrap_or(0);
        Ok(ModelDownloadInfo {
            tag: tag_clone,
            filename,
            path: final_path,
            sha256: sha256_val,
            size_bytes,
            already_cached: false,
        })
    })
    .await
    .map_err(|e| format!("Task execution error: {}", e))?;

    {
        let mut cancellations = state.download_cancellations.lock().unwrap();
        cancellations.remove(&tag);
    }

    res
}

#[tauri::command]
async fn pause_model_download(tag: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut cancellations = state.download_cancellations.lock().unwrap();
    if let Some(flag) = cancellations.remove(&tag) {
        flag.store(true, Ordering::SeqCst);
    }
    Ok(())
}

#[tauri::command]
async fn resume_model_download(
    tag: String,
    window: tauri::Window,
    state: State<'_, AppState>,
) -> Result<ModelDownloadInfo, String> {
    start_model_download(tag, window, state).await
}

#[tauri::command]
async fn cancel_model_download(tag: String, state: State<'_, AppState>) -> Result<(), String> {
    let mut cancellations = state.download_cancellations.lock().unwrap();
    if let Some(flag) = cancellations.remove(&tag) {
        flag.store(true, Ordering::SeqCst);
    }

    tauri::async_runtime::spawn_blocking(move || {
        if let Ok(mgr) = ModelManager::new(None) {
            if let Ok(models) = mgr.list_available_models(None) {
                if let Some(m) = models.into_iter().find(|m| m.tag == tag) {
                    let part_path = mgr.cache_dir().join(format!("{}.part", m.filename));
                    if part_path.exists() {
                        let _ = std::fs::remove_file(&part_path);
                    }
                }
            }
        }
        Ok(())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn test_ai_backend() -> Result<BackendTestResult, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let cfg = AppConfig::load();
        let start = std::time::Instant::now();

        let backend = match cfg.backend.as_str() {
            "ollama" => AiBackend::new_ollama(Some(&cfg.ollama_url), Some(&cfg.ollama_model)),
            "openai_compat" => AiBackend::new_openai_compat(
                Some(&cfg.openai_api_base),
                Some(&cfg.openai_api_model),
                Some(&cfg.openai_api_key),
            ),
            _ => AiBackend::new_local_llama(None, None),
        };

        match backend.infer("ping") {
            Ok(_) => Ok(BackendTestResult {
                success: true,
                latency_ms: start.elapsed().as_millis() as u64,
                message: format!("Backend '{}' responded successfully", cfg.backend),
            }),
            Err(e) => Ok(BackendTestResult {
                success: false,
                latency_ms: start.elapsed().as_millis() as u64,
                message: format!("Backend test failed: {}", e),
            }),
        }
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn list_mcp_servers() -> Result<Vec<McpServerDto>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let cfg = AppConfig::load();
        Ok(cfg
            .mcp_servers
            .into_iter()
            .map(|s| McpServerDto {
                name: s.name,
                command: s.command,
                args: s.args,
                enabled: s.enabled,
            })
            .collect())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn save_mcp_server(
    name: String,
    command: String,
    args: Vec<String>,
    enabled: bool,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let mut cfg = AppConfig::load();
        if let Some(existing) = cfg.mcp_servers.iter_mut().find(|s| s.name == name) {
            existing.command = command;
            existing.args = args;
            existing.enabled = enabled;
        } else {
            cfg.mcp_servers.push(McpServerConfig {
                name,
                command,
                args,
                enabled,
            });
        }

        let path = AppConfig::default_config_path();
        let data = serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string())?;
        std::fs::write(&path, data).map_err(|e| e.to_string())?;
        Ok(())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn delete_mcp_server(name: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let mut cfg = AppConfig::load();
        cfg.mcp_servers.retain(|s| s.name != name);
        let path = AppConfig::default_config_path();
        let data = serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string())?;
        std::fs::write(&path, data).map_err(|e| e.to_string())?;
        Ok(())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn test_mcp_server(name: String) -> Result<Vec<McpTool>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let cfg = AppConfig::load();
        let s_cfg = cfg
            .mcp_servers
            .into_iter()
            .find(|s| s.name == name)
            .ok_or_else(|| format!("Server '{}' not found", name))?;

        let mut client = McpClient::new(s_cfg);
        client.start()?;
        let _ = client.initialize()?;
        client.list_tools()
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn check_app_update() -> Result<UpdateInfoDto, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let updater = SelfUpdater::default();
        let info = updater.check_for_update()?;
        Ok(UpdateInfoDto {
            current_version: info.current_version,
            latest_version: info.latest_version,
            tag_name: info.tag_name,
            download_url: info.download_url,
            sha256_url: info.sha256_url,
            update_available: info.update_available,
        })
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn check_updates() -> Result<UpdateInfoDto, String> {
    check_app_update().await
}

#[tauri::command]
async fn activate_model(tag: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let mut cfg = AppConfig::load();
        cfg.active_model_tag = Some(tag);
        let path = AppConfig::default_config_path();
        let data = serde_json::to_string_pretty(&cfg).map_err(|e| e.to_string())?;
        std::fs::write(&path, data).map_err(|e| e.to_string())?;
        Ok(())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn delete_model(tag: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let mgr = ModelManager::new(None)?;
        let models = mgr.list_available_models(None)?;
        for m in models {
            if m.tag == tag {
                let file_path = mgr.cache_dir().join(&m.filename);
                if file_path.exists() {
                    std::fs::remove_file(&file_path).map_err(|e| e.to_string())?;
                }
                return Ok(());
            }
        }
        Err(format!("Model with tag {} not found", tag))
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn toggle_lan_bridge(
    enable: bool,
    state: State<'_, AppState>,
) -> Result<BridgeStatusResponse, String> {
    let mut guard = state.bridge_handle.lock().unwrap();

    if enable {
        if guard.is_some() {
            let (addr, _, _) = guard.as_ref().unwrap();
            return Ok(BridgeStatusResponse {
                is_running: true,
                address: Some(format!("http://{}", addr)),
                token: Some("silent-exec-secret".to_string()),
            });
        }

        let config = BridgeConfig {
            host: "0.0.0.0".to_string(),
            port: 8765,
            token: "silent-exec-secret".to_string(),
            dry_run: false,
            server_url: None,
            roots: vec![PathBuf::from(".")],
        };
        let shutdown = Arc::new(AtomicBool::new(false));
        let (addr, handle) = start_bridge_server(config, Some(shutdown.clone()))?;
        *guard = Some((addr, shutdown, handle));

        Ok(BridgeStatusResponse {
            is_running: true,
            address: Some(format!("http://{}", addr)),
            token: Some("silent-exec-secret".to_string()),
        })
    } else {
        if let Some((_, shutdown, handle)) = guard.take() {
            shutdown.store(true, Ordering::SeqCst);
            let _ = handle.join();
        }
        Ok(BridgeStatusResponse {
            is_running: false,
            address: None,
            token: None,
        })
    }
}

#[tauri::command]
async fn list_memories() -> Result<Vec<MemoryItem>, String> {
    tauri::async_runtime::spawn_blocking(|| silent_exec_pc::list_memories())
        .await
        .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn save_memory(
    key: String,
    content: String,
    category: Option<String>,
) -> Result<MemoryItem, String> {
    tauri::async_runtime::spawn_blocking(move || {
        silent_exec_pc::save_memory(&key, &content, category.as_deref())
    })
    .await
    .map_err(|e| format!("Task execution failed: {}", e))?
}

#[tauri::command]
async fn delete_memory(id_or_key: String) -> Result<bool, String> {
    tauri::async_runtime::spawn_blocking(move || silent_exec_pc::delete_memory(&id_or_key))
        .await
        .map_err(|e| format!("Task execution failed: {}", e))?
}

// ---------------------------------------------------------
// Main Entrypoint
// ---------------------------------------------------------

fn main() {
    let app_state = AppState {
        bridge_handle: Mutex::new(None),
        pending_execution: Arc::new(Mutex::new(None)),
        download_cancellations: Mutex::new(HashMap::new()),
    };

    tauri::Builder::default()
        .manage(app_state)
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .setup(|app| {
            // Setup Tray Icon & Context Menu
            let show_i = MenuItem::with_id(app, "show", "Show SilentExec", true, None::<&str>)?;
            let hide_i = MenuItem::with_id(app, "hide", "Hide to Tray", true, None::<&str>)?;
            let quit_i = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show_i, &hide_i, &quit_i])?;

            let _tray = TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.unminimize();
                            let _ = window.set_focus();
                        }
                    }
                    "hide" => {
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.hide();
                        }
                    }
                    "quit" => {
                        app.exit(0);
                    }
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            if window.is_visible().unwrap_or(false) {
                                let _ = window.hide();
                            } else {
                                let _ = window.show();
                                let _ = window.unminimize();
                                let _ = window.set_focus();
                            }
                        }
                    }
                })
                .build(app)?;

            // Register global shortcut: Alt+Space
            let shortcut = "Alt+Space".parse::<Shortcut>().unwrap();
            let app_handle = app.handle().clone();
            let _ = app
                .global_shortcut()
                .on_shortcut(shortcut, move |_app, _sc, _ev| {
                    if let Some(window) = app_handle.get_webview_window("main") {
                        if window.is_visible().unwrap_or(false) {
                            let _ = window.hide();
                        } else {
                            let _ = window.show();
                            let _ = window.unminimize();
                            let _ = window.set_focus();
                        }
                    }
                });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_app_config,
            save_app_config,
            execute_query,
            confirm_step,
            list_models,
            start_model_download,
            pause_model_download,
            resume_model_download,
            cancel_model_download,
            activate_model,
            delete_model,
            setup_runtime,
            run_setup,
            check_runtime_status,
            toggle_lan_bridge,
            check_updates,
            check_app_update,
            test_ai_backend,
            list_mcp_servers,
            save_mcp_server,
            delete_mcp_server,
            test_mcp_server,
            run_agent_plan,
            list_memories,
            save_memory,
            delete_memory
        ])
        .run(tauri::generate_context!())
        .expect("error while running SilentExec Desktop application");
}
