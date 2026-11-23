//! Safe Silent PC Executor with SafetyGate enforcement and deterministic Dry-Run mode.

use crate::indexer::FileIndexer;
use serde::{Deserialize, Serialize};
use silent_exec_core::{ActionCommand, ActionType, SafetyDecision, SafetyGate};
use std::fs;
use std::path::PathBuf;
use std::process::Command;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "status", rename_all = "snake_case")]
pub enum ExecutionResult {
    Success {
        action: String,
        details: String,
        target_resolved: Option<String>,
    },
    DryRun {
        action: String,
        target_resolved: Option<String>,
        command_line: String,
        message: String,
    },
    ConfirmationRequired {
        prompt: String,
        action: String,
        confidence: f64,
        reason: String,
    },
    Blocked {
        reason: String,
        action: String,
    },
    Error {
        message: String,
        action: String,
    },
}

pub fn action_type_to_str(action: ActionType) -> &'static str {
    match action {
        ActionType::OpenFolder => "open_folder",
        ActionType::OpenFile => "open_file",
        ActionType::LaunchApp => "launch_app",
        ActionType::SearchFiles => "search_files",
        ActionType::SetVolume => "set_volume",
        ActionType::SetBrightness => "set_brightness",
        ActionType::CreateNote => "create_note",
        ActionType::CopyToClipboard => "copy_to_clipboard",
        ActionType::DeleteFile => "delete_file",
        ActionType::MoveFile => "move_file",
        ActionType::SystemPower => "system_power",
        ActionType::OpenAndroidApp => "open_android_app",
        ActionType::ToggleSetting => "toggle_setting",
        ActionType::SendMessage => "send_message",
        ActionType::OpenUrl => "open_url",
        ActionType::UiClick => "ui_click",
        ActionType::UiType => "ui_type",
        ActionType::UiScroll => "ui_scroll",
        ActionType::UiBack => "ui_back",
        ActionType::UiHome => "ui_home",
    }
}

pub struct PcExecutor {
    indexer: FileIndexer,
    dry_run: bool,
    notes_dir: PathBuf,
}

impl PcExecutor {
    pub fn new(indexer: FileIndexer, dry_run: bool) -> Self {
        let default_notes =
            if let Ok(home) = std::env::var("USERPROFILE").or_else(|_| std::env::var("HOME")) {
                PathBuf::from(home).join("Documents").join("Notes")
            } else {
                PathBuf::from("Notes")
            };

        Self {
            indexer,
            dry_run,
            notes_dir: default_notes,
        }
    }

    pub fn with_notes_dir(mut self, notes_dir: PathBuf) -> Self {
        self.notes_dir = notes_dir;
        self
    }

    pub fn is_dry_run(&self) -> bool {
        self.dry_run
    }

    pub fn indexer(&self) -> &FileIndexer {
        &self.indexer
    }

    /// Evaluates safety and executes or simulates an ActionCommand.
    pub fn execute(&self, cmd: &ActionCommand, confirm_flag: bool) -> ExecutionResult {
        let action_name = action_type_to_str(cmd.action).to_string();

        // ALWAYS evaluate SafetyGate first
        let decision = SafetyGate::evaluate(cmd);
        match decision {
            SafetyDecision::Block { reason } => {
                return ExecutionResult::Blocked {
                    reason,
                    action: action_name,
                };
            }
            SafetyDecision::RequireConfirm { reason } => {
                if !confirm_flag {
                    let prompt = format!(
                        "Confirm execution of action '{}' (target: '{:?}'): {}",
                        action_name, cmd.target, reason
                    );
                    return ExecutionResult::ConfirmationRequired {
                        prompt,
                        action: action_name,
                        confidence: cmd.confidence,
                        reason,
                    };
                }
            }
            SafetyDecision::AllowSilent => {}
        }

        // Execute or dry-run
        if self.dry_run {
            self.execute_dry_run(cmd, action_name)
        } else {
            self.execute_live(cmd, action_name)
        }
    }

    fn execute_dry_run(&self, cmd: &ActionCommand, action_name: String) -> ExecutionResult {
        match cmd.action {
            ActionType::OpenFolder => {
                let resolved = cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, true));
                let resolved_str = resolved.as_ref().map(|p| p.to_string_lossy().to_string());
                let cmd_line = if cfg!(target_os = "windows") {
                    format!(
                        "explorer.exe \"{}\"",
                        resolved_str.as_deref().unwrap_or(".")
                    )
                } else {
                    format!("xdg-open \"{}\"", resolved_str.as_deref().unwrap_or("."))
                };
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: resolved_str,
                    command_line: cmd_line,
                    message: "Would open folder in system file manager".to_string(),
                }
            }
            ActionType::OpenFile => {
                let resolved = cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, false));
                let resolved_str = resolved.as_ref().map(|p| p.to_string_lossy().to_string());
                let cmd_line = if cfg!(target_os = "windows") {
                    format!(
                        "cmd.exe /c start \"\" \"{}\"",
                        resolved_str.as_deref().unwrap_or("file")
                    )
                } else {
                    format!("xdg-open \"{}\"", resolved_str.as_deref().unwrap_or("file"))
                };
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: resolved_str,
                    command_line: cmd_line,
                    message: "Would open file in default application".to_string(),
                }
            }
            ActionType::SearchFiles => {
                let query = cmd.target.as_deref().unwrap_or("*");
                let matches = self.indexer.search(query, false);
                let first_match = matches
                    .first()
                    .map(|m| m.path.to_string_lossy().to_string());
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: first_match,
                    command_line: format!("search: \"{}\" found {} matches", query, matches.len()),
                    message: format!("Would return {} matching file paths", matches.len()),
                }
            }
            ActionType::OpenUrl => {
                let url = cmd.target.as_deref().unwrap_or("");
                let cmd_line = if cfg!(target_os = "windows") {
                    format!("explorer.exe \"{}\"", url)
                } else {
                    format!("xdg-open \"{}\"", url)
                };
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: Some(url.to_string()),
                    command_line: cmd_line,
                    message: "Would open URL in default browser".to_string(),
                }
            }
            ActionType::CreateNote => {
                let title = cmd.target.as_deref().unwrap_or("Note");
                let target_path = self.notes_dir.join(format!("{}.txt", title));
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: Some(target_path.to_string_lossy().to_string()),
                    command_line: format!("write_note: \"{}\"", target_path.display()),
                    message: "Would create note file in Notes directory".to_string(),
                }
            }
            ActionType::LaunchApp => {
                let app = cmd.target.as_deref().unwrap_or("");
                let cmd_line = if cfg!(target_os = "windows") {
                    format!("cmd.exe /c start \"\" \"{}\"", app)
                } else {
                    format!("{} &", app)
                };
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: Some(app.to_string()),
                    command_line: cmd_line,
                    message: "Would launch application executable".to_string(),
                }
            }
            ActionType::CopyToClipboard => {
                let text = cmd.target.as_deref().unwrap_or("");
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: Some(text.to_string()),
                    command_line: format!("clipboard.set(\"{}\")", text),
                    message: "Would copy text to system clipboard".to_string(),
                }
            }
            ActionType::DeleteFile => {
                let resolved = cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, false));
                let resolved_str = resolved.as_ref().map(|p| p.to_string_lossy().to_string());
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: resolved_str,
                    command_line: format!(
                        "delete_file: \"{}\"",
                        cmd.target.as_deref().unwrap_or("")
                    ),
                    message: "Would permanently remove file after confirmed prompt".to_string(),
                }
            }
            ActionType::MoveFile => {
                let resolved = cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, false));
                let resolved_str = resolved.as_ref().map(|p| p.to_string_lossy().to_string());
                let dest_str = cmd.folder.as_deref().unwrap_or("Destination");
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: resolved_str,
                    command_line: format!(
                        "move_file: \"{}\" -> \"{}\"",
                        cmd.target.as_deref().unwrap_or(""),
                        dest_str
                    ),
                    message: "Would move file to destination directory".to_string(),
                }
            }
            ActionType::SystemPower => {
                let target = cmd.target.as_deref().unwrap_or("shutdown");
                ExecutionResult::DryRun {
                    action: action_name,
                    target_resolved: Some(target.to_string()),
                    command_line: format!("system_power: {}", target),
                    message: "Would execute system power command".to_string(),
                }
            }
            _ => ExecutionResult::DryRun {
                action: action_name,
                target_resolved: cmd.target.clone(),
                command_line: format!("execute action on {:?}", cmd.target),
                message: "Generic dry-run action".to_string(),
            },
        }
    }

    fn execute_live(&self, cmd: &ActionCommand, action_name: String) -> ExecutionResult {
        match cmd.action {
            ActionType::OpenFolder => {
                let resolved = match cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, true))
                {
                    Some(p) => p,
                    None => {
                        return ExecutionResult::Error {
                            message: format!(
                                "Folder target '{:?}' could not be resolved",
                                cmd.target
                            ),
                            action: action_name,
                        };
                    }
                };

                let res = if cfg!(target_os = "windows") {
                    Command::new("explorer.exe").arg(&resolved).spawn()
                } else {
                    Command::new("xdg-open").arg(&resolved).spawn()
                };

                match res {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: "Folder opened successfully".to_string(),
                        target_resolved: Some(resolved.to_string_lossy().to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to open folder: {}", e),
                        action: action_name,
                    },
                }
            }
            ActionType::OpenFile => {
                let resolved = match cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, false))
                {
                    Some(p) => p,
                    None => {
                        return ExecutionResult::Error {
                            message: format!(
                                "File target '{:?}' could not be resolved",
                                cmd.target
                            ),
                            action: action_name,
                        };
                    }
                };

                let res = if cfg!(target_os = "windows") {
                    Command::new("cmd")
                        .args(["/c", "start", ""])
                        .arg(&resolved)
                        .spawn()
                } else {
                    Command::new("xdg-open").arg(&resolved).spawn()
                };

                match res {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: "File opened with default application".to_string(),
                        target_resolved: Some(resolved.to_string_lossy().to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to open file: {}", e),
                        action: action_name,
                    },
                }
            }
            ActionType::SearchFiles => {
                let query = cmd.target.as_deref().unwrap_or("*");
                let matches = self.indexer.search(query, false);
                let paths: Vec<String> = matches
                    .iter()
                    .map(|m| m.path.to_string_lossy().to_string())
                    .collect();
                ExecutionResult::Success {
                    action: action_name,
                    details: format!("Found {} matching files", paths.len()),
                    target_resolved: paths.first().cloned(),
                }
            }
            ActionType::OpenUrl => {
                let url = match cmd.target.as_deref() {
                    Some(u) if !u.is_empty() => u,
                    _ => {
                        return ExecutionResult::Error {
                            message: "Missing URL target".to_string(),
                            action: action_name,
                        };
                    }
                };

                let res = if cfg!(target_os = "windows") {
                    Command::new("explorer.exe").arg(url).spawn()
                } else {
                    Command::new("xdg-open").arg(url).spawn()
                };

                match res {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: "URL opened in default browser".to_string(),
                        target_resolved: Some(url.to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to open URL: {}", e),
                        action: action_name,
                    },
                }
            }
            ActionType::CreateNote => {
                let title = cmd.target.as_deref().unwrap_or("Untitled Note");
                if let Err(e) = fs::create_dir_all(&self.notes_dir) {
                    return ExecutionResult::Error {
                        message: format!("Failed to create Notes directory: {}", e),
                        action: action_name,
                    };
                }

                let safe_title = title.replace(['/', '\\', ':', '*', '?', '"', '<', '>', '|'], "_");
                let note_path = self.notes_dir.join(format!("{}.txt", safe_title));
                let content = format!("# {}\n\nCreated by SilentExec.\n", title);

                match fs::write(&note_path, content) {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: format!("Note created at {}", note_path.display()),
                        target_resolved: Some(note_path.to_string_lossy().to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to write note: {}", e),
                        action: action_name,
                    },
                }
            }
            ActionType::LaunchApp => {
                let app = match cmd.target.as_deref() {
                    Some(a) if !a.is_empty() => a,
                    _ => {
                        return ExecutionResult::Error {
                            message: "Missing application name".to_string(),
                            action: action_name,
                        };
                    }
                };

                let res = if cfg!(target_os = "windows") {
                    Command::new("cmd").args(["/c", "start", "", app]).spawn()
                } else {
                    Command::new(app).spawn()
                };

                match res {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: format!("Application '{}' launched", app),
                        target_resolved: Some(app.to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to launch app: {}", e),
                        action: action_name,
                    },
                }
            }
            ActionType::DeleteFile => {
                let target_path = match cmd
                    .target
                    .as_ref()
                    .and_then(|t| self.indexer.find_best(t, false))
                {
                    Some(p) => p,
                    None => {
                        return ExecutionResult::Error {
                            message: format!("File to delete '{:?}' not found", cmd.target),
                            action: action_name,
                        };
                    }
                };

                match fs::remove_file(&target_path) {
                    Ok(_) => ExecutionResult::Success {
                        action: action_name,
                        details: format!("Deleted file {}", target_path.display()),
                        target_resolved: Some(target_path.to_string_lossy().to_string()),
                    },
                    Err(e) => ExecutionResult::Error {
                        message: format!("Failed to delete file: {}", e),
                        action: action_name,
                    },
                }
            }
            other => ExecutionResult::Error {
                message: format!("Live execution for action '{:?}' not implemented", other),
                action: action_name,
            },
        }
    }
}
