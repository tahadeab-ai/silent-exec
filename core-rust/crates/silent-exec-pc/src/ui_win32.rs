//! Native Win32 Alt+Space Floating Command Bar for SilentExec PC
//! Provides a zero-bloat, lightweight (<25 MB RAM), borderless always-on-top command bar.

use std::path::PathBuf;

/// Converts a null-terminated or raw UTF-16 slice into a Rust UTF-8 String.
pub fn utf16_to_string(units: &[u16]) -> String {
    let len = units.iter().position(|&c| c == 0).unwrap_or(units.len());
    String::from_utf16_lossy(&units[..len])
}

/// Converts a Rust string slice into a null-terminated UTF-16 vector.
pub fn string_to_utf16(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

/// Action determined by evaluating an ExecutionResult for UI presentation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UiActionOutcome {
    SilentSuccess { action: String },
    RequireConfirmation { action: String, prompt: String },
    Blocked { reason: String },
    Error { message: String },
}

/// Pure state machine modeling the floating command bar's visibility, input, and lifecycle.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BarState {
    pub is_visible: bool,
    pub current_input: String,
    pub last_outcome: Option<UiActionOutcome>,
}

impl Default for BarState {
    fn default() -> Self {
        Self::new()
    }
}

impl BarState {
    pub fn new() -> Self {
        Self {
            is_visible: false,
            current_input: String::new(),
            last_outcome: None,
        }
    }

    /// Toggles visibility when the Alt+Space hotkey is triggered.
    pub fn handle_hotkey_toggle(&mut self) -> bool {
        self.is_visible = !self.is_visible;
        self.is_visible
    }

    /// Dismisses the bar immediately (e.g. on Escape or loss of focus).
    pub fn handle_escape(&mut self) {
        self.is_visible = false;
        self.current_input.clear();
    }

    /// Submits the input on Enter, clearing the bar and returning the prompt to execute.
    pub fn handle_submit(&mut self, text: String) -> Option<String> {
        self.is_visible = false;
        self.current_input.clear();
        let trimmed = text.trim();
        if trimmed.is_empty() {
            None
        } else {
            Some(trimmed.to_string())
        }
    }

    /// Records the latest action outcome from the executor pipeline.
    pub fn record_outcome(&mut self, outcome: UiActionOutcome) {
        self.last_outcome = Some(outcome);
    }
}

/// Maps an ExecutionResult to a compact UI action outcome.
pub fn map_result_to_ui_outcome(result: &crate::executor::ExecutionResult) -> UiActionOutcome {
    match result {
        crate::executor::ExecutionResult::Success { action, .. }
        | crate::executor::ExecutionResult::DryRun { action, .. } => {
            UiActionOutcome::SilentSuccess {
                action: action.clone(),
            }
        }
        crate::executor::ExecutionResult::ConfirmationRequired { action, prompt, .. } => {
            UiActionOutcome::RequireConfirmation {
                action: action.clone(),
                prompt: prompt.clone(),
            }
        }
        crate::executor::ExecutionResult::Blocked { reason, .. } => UiActionOutcome::Blocked {
            reason: reason.clone(),
        },
        crate::executor::ExecutionResult::Error { message, .. } => UiActionOutcome::Error {
            message: message.clone(),
        },
    }
}

#[cfg(windows)]
pub use win32_impl::run_command_bar;

#[cfg(not(windows))]
pub fn run_command_bar(
    dry_run: bool,
    confirm: bool,
    server_url: Option<String>,
    llama_bin: Option<PathBuf>,
    model_path: Option<PathBuf>,
    roots: Vec<PathBuf>,
) -> Result<(), String> {
    println!("Notice: Native Win32 Alt+Space Command Bar is only supported on Windows.");
    println!("Falling back cleanly to quick interactive mode...");
    // Emulate quick mode on non-windows platforms
    let mut indexer = crate::indexer::FileIndexer::new(roots);
    let _ = indexer.build_index();
    let executor = crate::executor::PcExecutor::new(indexer, dry_run);
    let backend = if let Some(bin) = llama_bin {
        let model = model_path.unwrap_or_else(|| {
            crate::model_manager::ModelManager::default_cache_dir()
                .join("Qwen2.5-1.5B-Instruct.Q4_K_M.gguf")
        });
        crate::inference::LlamaBackend::new_cli(bin, model, None)
    } else {
        crate::inference::LlamaBackend::new_server(server_url.as_deref(), None)
    };

    let stdin = std::io::stdin();
    let config = crate::config::AppConfig::load();
    let is_ar = config.language == "ar";

    for line_res in std::io::BufRead::lines(stdin.lock()) {
        let line = match line_res {
            Ok(l) => l,
            Err(_) => break,
        };
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        if trimmed.eq_ignore_ascii_case("exit") || trimmed.eq_ignore_ascii_case("quit") {
            break;
        }

        // Fast Rule Router check
        let cmd = if config.fast_router_enabled {
            if let Some(routed) = crate::fast_router::FastRuleRouter::route(trimmed) {
                routed
            } else {
                if is_ar {
                    println!("جاري الفهم...");
                } else {
                    println!("Understanding...");
                }
                match backend.infer(trimmed) {
                    Ok(c) => c,
                    Err(e) => {
                        println!("! inference failed: {}", e);
                        continue;
                    }
                }
            }
        } else {
            if is_ar {
                println!("جاري الفهم...");
            } else {
                println!("Understanding...");
            }
            match backend.infer(trimmed) {
                Ok(c) => c,
                Err(e) => {
                    println!("! inference failed: {}", e);
                    continue;
                }
            }
        };

        if is_ar {
            println!("جاري التنفيذ...");
        } else {
            println!("Executing...");
        }

        let res = executor.execute(&cmd, confirm);
        match map_result_to_ui_outcome(&res) {
            UiActionOutcome::SilentSuccess { action } => println!("✓ {}", action),
            UiActionOutcome::RequireConfirmation { action, prompt } => {
                println!("? Confirm: {} ({})", action, prompt)
            }
            UiActionOutcome::Blocked { reason } => println!("✗ Blocked: {}", reason),
            UiActionOutcome::Error { message } => println!("! Error: {}", message),
        }
    }
    Ok(())
}

#[cfg(windows)]
mod win32_impl {
    use super::*;
    use crate::executor::PcExecutor;
    use crate::indexer::FileIndexer;
    use crate::inference::LlamaBackend;
    use std::sync::atomic::{AtomicPtr, Ordering};
    use std::sync::mpsc::{channel, Receiver, Sender};
    use std::sync::{Arc, Mutex};
    use windows_sys::Win32::Foundation::*;
    use windows_sys::Win32::Graphics::Gdi::*;
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::*;
    use windows_sys::Win32::UI::WindowsAndMessaging::*;

    const HOTKEY_ID: i32 = 1;
    const TIMER_ID_BANNER: usize = 1001;

    const WM_USER_SUBMIT: u32 = WM_USER + 101;
    const WM_USER_DISMISS: u32 = WM_USER + 102;
    const WM_USER_SHOW_BANNER: u32 = WM_USER + 103;

    static MAIN_HWND: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static EDIT_HWND: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static BANNER_HWND: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static BANNER_LABEL_HWND: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());

    static MAIN_BRUSH: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static BANNER_BRUSH: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static EDIT_FONT: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());
    static BANNER_FONT: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());

    static PREV_EDIT_WNDPROC: AtomicPtr<core::ffi::c_void> = AtomicPtr::new(std::ptr::null_mut());

    static BANNER_SENDER: Mutex<Option<Sender<String>>> = Mutex::new(None);
    static BANNER_RECEIVER: Mutex<Option<Receiver<String>>> = Mutex::new(None);

    static EXECUTOR_CTX: Mutex<Option<Arc<Mutex<PcExecutor>>>> = Mutex::new(None);
    static BACKEND_CTX: Mutex<Option<LlamaBackend>> = Mutex::new(None);

    unsafe extern "system" fn edit_subclass_proc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        if msg == WM_KEYDOWN {
            if wparam == VK_RETURN as usize {
                let parent = GetParent(hwnd);
                if !parent.is_null() {
                    PostMessageW(parent, WM_USER_SUBMIT, 0, 0);
                }
                return 0;
            } else if wparam == VK_ESCAPE as usize {
                let parent = GetParent(hwnd);
                if !parent.is_null() {
                    PostMessageW(parent, WM_USER_DISMISS, 0, 0);
                }
                return 0;
            }
        }
        let prev_ptr = PREV_EDIT_WNDPROC.load(Ordering::SeqCst);
        if !prev_ptr.is_null() {
            let prev: unsafe extern "system" fn(HWND, u32, WPARAM, LPARAM) -> LRESULT =
                std::mem::transmute(prev_ptr);
            prev(hwnd, msg, wparam, lparam)
        } else {
            DefWindowProcW(hwnd, msg, wparam, lparam)
        }
    }

    unsafe extern "system" fn banner_wndproc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        match msg {
            WM_TIMER => {
                if wparam == TIMER_ID_BANNER {
                    KillTimer(hwnd, TIMER_ID_BANNER);
                    ShowWindow(hwnd, SW_HIDE);
                }
                0
            }
            WM_CTLCOLORSTATIC => {
                let hdc = wparam as HDC;
                SetTextColor(hdc, 0x00E0E0E0);
                SetBkColor(hdc, 0x00141414);
                BANNER_BRUSH.load(Ordering::SeqCst) as LRESULT
            }
            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }

    unsafe extern "system" fn main_wndproc(
        hwnd: HWND,
        msg: u32,
        wparam: WPARAM,
        lparam: LPARAM,
    ) -> LRESULT {
        match msg {
            WM_HOTKEY => {
                if wparam as i32 == HOTKEY_ID {
                    let is_vis = IsWindowVisible(hwnd) != 0;
                    if is_vis {
                        ShowWindow(hwnd, SW_HIDE);
                    } else {
                        ShowWindow(hwnd, SW_SHOW);
                        SetForegroundWindow(hwnd);
                        let edit = EDIT_HWND.load(Ordering::SeqCst);
                        if !edit.is_null() {
                            SetFocus(edit);
                            SendMessageW(edit, 0x00B1, 0, -1); // EM_SETSEL
                        }
                    }
                }
                0
            }
            WM_USER_DISMISS => {
                ShowWindow(hwnd, SW_HIDE);
                let edit = EDIT_HWND.load(Ordering::SeqCst);
                if !edit.is_null() {
                    let empty = [0u16];
                    SetWindowTextW(edit, empty.as_ptr());
                }
                0
            }
            WM_USER_SUBMIT => {
                ShowWindow(hwnd, SW_HIDE);
                let edit = EDIT_HWND.load(Ordering::SeqCst);
                if !edit.is_null() {
                    let len = GetWindowTextLengthW(edit);
                    if len > 0 {
                        let mut buf = vec![0u16; (len + 1) as usize];
                        let copied = GetWindowTextW(edit, buf.as_mut_ptr(), len + 1);
                        let empty = [0u16];
                        SetWindowTextW(edit, empty.as_ptr());

                        if copied > 0 {
                            let prompt = utf16_to_string(&buf[..copied as usize]);
                            let trimmed = prompt.trim().to_string();
                            if !trimmed.is_empty() {
                                spawn_execution_worker(hwnd, trimmed);
                            }
                        }
                    }
                }
                0
            }
            WM_USER_SHOW_BANNER => {
                if let Ok(guard) = BANNER_RECEIVER.lock() {
                    if let Some(ref rx) = *guard {
                        if let Ok(msg) = rx.try_recv() {
                            let banner = BANNER_HWND.load(Ordering::SeqCst);
                            let banner_label = BANNER_LABEL_HWND.load(Ordering::SeqCst);
                            if !banner.is_null() && !banner_label.is_null() {
                                let utf16_msg = string_to_utf16(&msg);
                                SetWindowTextW(banner_label, utf16_msg.as_ptr());
                                ShowWindow(banner, SW_SHOW);
                                SetTimer(banner, TIMER_ID_BANNER, 1000, None);
                            }
                        }
                    }
                }
                0
            }
            WM_CTLCOLOREDIT => {
                let hdc = wparam as HDC;
                SetTextColor(hdc, 0x00FFFFFF);
                SetBkColor(hdc, 0x001E1E1E);
                MAIN_BRUSH.load(Ordering::SeqCst) as LRESULT
            }
            WM_CTLCOLORSTATIC => {
                let hdc = wparam as HDC;
                SetTextColor(hdc, 0x00E0E0E0);
                SetBkColor(hdc, 0x001E1E1E);
                MAIN_BRUSH.load(Ordering::SeqCst) as LRESULT
            }
            WM_DESTROY => {
                UnregisterHotKey(hwnd, HOTKEY_ID);
                PostQuitMessage(0);
                0
            }
            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }

    fn spawn_execution_worker(target_hwnd: HWND, prompt: String) {
        let (tx, rx_target) = (target_hwnd as usize, prompt);
        std::thread::spawn(move || {
            let hwnd = tx as HWND;
            let backend_opt = BACKEND_CTX.lock().unwrap().clone();
            let executor_opt = EXECUTOR_CTX.lock().unwrap().clone();

            let backend = match backend_opt {
                Some(b) => b,
                None => return,
            };
            let executor = match executor_opt {
                Some(e) => e,
                None => return,
            };

            let config = crate::config::AppConfig::load();
            let is_ar = config.language == "ar";

            // Stage 1: Fast Router or LLM Understanding
            let cmd = if config.fast_router_enabled {
                if let Some(routed) = crate::fast_router::FastRuleRouter::route(&rx_target) {
                    routed
                } else {
                    let understanding_msg = if is_ar {
                        "جاري الفهم...".to_string()
                    } else {
                        "Understanding...".to_string()
                    };
                    send_banner_update(hwnd, understanding_msg);

                    match backend.infer(&rx_target) {
                        Ok(c) => c,
                        Err(e) => {
                            let msg = format!("✗ Inference failed: {}", e);
                            send_banner_update(hwnd, msg);
                            return;
                        }
                    }
                }
            } else {
                let understanding_msg = if is_ar {
                    "جاري الفهم...".to_string()
                } else {
                    "Understanding...".to_string()
                };
                send_banner_update(hwnd, understanding_msg);

                match backend.infer(&rx_target) {
                    Ok(c) => c,
                    Err(e) => {
                        let msg = format!("✗ Inference failed: {}", e);
                        send_banner_update(hwnd, msg);
                        return;
                    }
                }
            };

            // Stage 2: Execution stage feedback
            let executing_msg = if is_ar {
                "جاري التنفيذ...".to_string()
            } else {
                "Executing...".to_string()
            };
            send_banner_update(hwnd, executing_msg);

            let res = executor.lock().unwrap().execute(&cmd, false);
            let outcome = map_result_to_ui_outcome(&res);

            match outcome {
                UiActionOutcome::SilentSuccess { action } => {
                    let msg = format!("✓ {}", action);
                    send_banner_update(hwnd, msg);
                }
                UiActionOutcome::RequireConfirmation { action, prompt: _ } => {
                    let title = if is_ar {
                        string_to_utf16("SilentExec - يلزم التأكيد")
                    } else {
                        string_to_utf16("SilentExec - Authorization Required")
                    };
                    let text = if is_ar {
                        string_to_utf16(&format!(
                            "إجراء المستوى 2: {}\n\nيتطلب هذا الأمر تأكيد المستخدم.\nهل ترغب في التنفيذ؟",
                            action
                        ))
                    } else {
                        string_to_utf16(&format!(
                            "Tier-2 Action: {}\n\nThis command requires explicit user confirmation.\nAuthorize execution?",
                            action
                        ))
                    };

                    let choice = unsafe {
                        MessageBoxW(
                            0 as HWND,
                            text.as_ptr(),
                            title.as_ptr(),
                            MB_OKCANCEL | MB_ICONWARNING | 0x00040000, // MB_TOPMOST
                        )
                    };

                    if choice == IDOK {
                        let confirmed = executor.lock().unwrap().execute(&cmd, true);
                        match map_result_to_ui_outcome(&confirmed) {
                            UiActionOutcome::SilentSuccess { action } => {
                                let msg = format!("✓ {}", action);
                                send_banner_update(hwnd, msg);
                            }
                            _ => {
                                let msg = format!("✗ Execution failed for {}", action);
                                send_banner_update(hwnd, msg);
                            }
                        }
                    } else {
                        let msg = format!("✗ Cancelled: {}", action);
                        send_banner_update(hwnd, msg);
                    }
                }
                UiActionOutcome::Blocked { reason } => {
                    let msg = format!("✗ Blocked: {}", reason);
                    send_banner_update(hwnd, msg);
                }
                UiActionOutcome::Error { message } => {
                    let msg = format!("! Error: {}", message);
                    send_banner_update(hwnd, msg);
                }
            }
        });
    }

    fn send_banner_update(target_hwnd: HWND, message: String) {
        if let Ok(guard) = BANNER_SENDER.lock() {
            if let Some(ref tx) = *guard {
                let _ = tx.send(message);
                unsafe {
                    PostMessageW(target_hwnd, WM_USER_SHOW_BANNER, 0, 0);
                }
            }
        }
    }

    pub fn run_command_bar(
        dry_run: bool,
        _confirm: bool,
        server_url: Option<String>,
        llama_bin: Option<PathBuf>,
        model_path: Option<PathBuf>,
        roots: Vec<PathBuf>,
    ) -> Result<(), String> {
        let (tx, rx) = channel::<String>();
        *BANNER_SENDER.lock().unwrap() = Some(tx);
        *BANNER_RECEIVER.lock().unwrap() = Some(rx);

        let mut indexer = FileIndexer::new(roots);
        let _ = indexer.build_index();
        let executor = Arc::new(Mutex::new(PcExecutor::new(indexer, dry_run)));

        // Resolve backend using auto-setup managed helper if available
        let backend = if let Some(bin) = llama_bin {
            let model = model_path.unwrap_or_else(|| {
                crate::model_manager::ModelManager::default_cache_dir()
                    .join("Qwen2.5-1.5B-Instruct.Q4_K_M.gguf")
            });
            LlamaBackend::new_cli(bin, model, None)
        } else if let Some(url) =
            server_url.or_else(|| std::env::var("SILENT_EXEC_SERVER_URL").ok())
        {
            LlamaBackend::new_server(Some(&url), None)
        } else {
            let model_mgr = crate::model_manager::ModelManager::new(None)
                .map_err(|e| format!("ModelManager error: {}", e))?;
            let resolved_model = match model_path {
                Some(p) => p,
                None => model_mgr.find_cached_model().ok_or_else(|| {
                    format!(
                        "No model found in '{}'. Please run 'silent-exec-pc setup' first.",
                        model_mgr.cache_dir().display()
                    )
                })?,
            };
            let runtime_dir = crate::runtime_manager::RuntimeManager::default_runtime_dir();
            let port = 8080u16;
            let health_url = format!("http://127.0.0.1:{}/health", port);
            let managed_url = if crate::runtime_manager::check_health_http(
                &health_url,
                std::time::Duration::from_millis(300),
            ) {
                format!("http://127.0.0.1:{}", port)
            } else {
                crate::runtime_manager::ensure_llama_server_running(
                    &resolved_model,
                    &runtime_dir,
                    port,
                )
                .map_err(|e| format!("Failed to start managed llama-server: {}", e))?
            };
            LlamaBackend::new_server(Some(&managed_url), None)
        };

        *EXECUTOR_CTX.lock().unwrap() = Some(executor);
        *BACKEND_CTX.lock().unwrap() = Some(backend);

        unsafe {
            let class_name = string_to_utf16("SilentExecCommandBarClass");
            let banner_class_name = string_to_utf16("SilentExecBannerClass");
            let font_name = string_to_utf16("Segoe UI");

            let main_brush = CreateSolidBrush(0x001E1E1E);
            let banner_brush = CreateSolidBrush(0x00141414);
            MAIN_BRUSH.store(main_brush, Ordering::SeqCst);
            BANNER_BRUSH.store(banner_brush, Ordering::SeqCst);

            let edit_font = CreateFontW(
                -22,
                0,
                0,
                0,
                FW_NORMAL as i32,
                0,
                0,
                0,
                DEFAULT_CHARSET as u32,
                OUT_DEFAULT_PRECIS as u32,
                CLIP_DEFAULT_PRECIS as u32,
                CLEARTYPE_QUALITY as u32,
                DEFAULT_PITCH as u32 | FF_DONTCARE as u32,
                font_name.as_ptr(),
            );
            EDIT_FONT.store(edit_font, Ordering::SeqCst);

            let banner_font = CreateFontW(
                -18,
                0,
                0,
                0,
                FW_NORMAL as i32,
                0,
                0,
                0,
                DEFAULT_CHARSET as u32,
                OUT_DEFAULT_PRECIS as u32,
                CLIP_DEFAULT_PRECIS as u32,
                CLEARTYPE_QUALITY as u32,
                DEFAULT_PITCH as u32 | FF_DONTCARE as u32,
                font_name.as_ptr(),
            );
            BANNER_FONT.store(banner_font, Ordering::SeqCst);

            let main_wc = WNDCLASSW {
                style: 0,
                lpfnWndProc: Some(main_wndproc),
                cbClsExtra: 0,
                cbWndExtra: 0,
                hInstance: std::ptr::null_mut(),
                hIcon: std::ptr::null_mut(),
                hCursor: std::ptr::null_mut(),
                hbrBackground: main_brush,
                lpszMenuName: std::ptr::null(),
                lpszClassName: class_name.as_ptr(),
            };
            RegisterClassW(&main_wc);

            let banner_wc = WNDCLASSW {
                style: 0,
                lpfnWndProc: Some(banner_wndproc),
                cbClsExtra: 0,
                cbWndExtra: 0,
                hInstance: std::ptr::null_mut(),
                hIcon: std::ptr::null_mut(),
                hCursor: std::ptr::null_mut(),
                hbrBackground: banner_brush,
                lpszMenuName: std::ptr::null(),
                lpszClassName: banner_class_name.as_ptr(),
            };
            RegisterClassW(&banner_wc);

            let screen_w = GetSystemMetrics(SM_CXSCREEN);
            let screen_h = GetSystemMetrics(SM_CYSCREEN);

            let bar_width = 720;
            let bar_height = 56;
            let bar_x = (screen_w - bar_width) / 2;
            let bar_y = screen_h / 6;

            let main_title = string_to_utf16("SilentExec Floating Command Bar");
            let main_hwnd = CreateWindowExW(
                WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
                class_name.as_ptr(),
                main_title.as_ptr(),
                WS_POPUP,
                bar_x,
                bar_y,
                bar_width,
                bar_height,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            );

            if main_hwnd.is_null() {
                return Err("Failed to create native Win32 command bar window".to_string());
            }
            MAIN_HWND.store(main_hwnd, Ordering::SeqCst);

            let edit_class = string_to_utf16("EDIT");
            let edit_hwnd = CreateWindowExW(
                0,
                edit_class.as_ptr(),
                [0u16].as_ptr(),
                WS_CHILD | WS_VISIBLE | (ES_LEFT as u32) | (ES_AUTOHSCROLL as u32),
                16,
                11,
                bar_width - 32,
                34,
                main_hwnd,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            );

            if !edit_hwnd.is_null() {
                EDIT_HWND.store(edit_hwnd, Ordering::SeqCst);
                SendMessageW(edit_hwnd, 0x0030, edit_font as WPARAM, 1); // WM_SETFONT
                let prev = SetWindowLongPtrW(
                    edit_hwnd,
                    GWLP_WNDPROC,
                    edit_subclass_proc as *const () as usize as isize,
                );
                PREV_EDIT_WNDPROC.store(prev as *mut core::ffi::c_void, Ordering::SeqCst);
            }

            // Create non-blocking status banner window
            let banner_width = 540;
            let banner_height = 42;
            let banner_x = (screen_w - banner_width) / 2;
            let banner_y = bar_y + bar_height + 12;

            let banner_title = string_to_utf16("SilentExec Status Banner");
            let banner_hwnd = CreateWindowExW(
                WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
                banner_class_name.as_ptr(),
                banner_title.as_ptr(),
                WS_POPUP,
                banner_x,
                banner_y,
                banner_width,
                banner_height,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            );
            BANNER_HWND.store(banner_hwnd, Ordering::SeqCst);

            let static_class = string_to_utf16("STATIC");
            let banner_label_hwnd = CreateWindowExW(
                0,
                static_class.as_ptr(),
                [0u16].as_ptr(),
                WS_CHILD | WS_VISIBLE | 0x00000001, // SS_CENTER
                12,
                9,
                banner_width - 24,
                26,
                banner_hwnd,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            );
            BANNER_LABEL_HWND.store(banner_label_hwnd, Ordering::SeqCst);

            if !banner_label_hwnd.is_null() {
                SendMessageW(banner_label_hwnd, 0x0030, banner_font as WPARAM, 1);
            }

            // Register global Alt+Space hotkey
            let registered = RegisterHotKey(main_hwnd, HOTKEY_ID, MOD_ALT, VK_SPACE as u32);
            if registered == 0 {
                eprintln!(
                    "Warning: Failed to register global Alt+Space hotkey (may already be in use)."
                );
            } else {
                println!("Registered global hotkey Alt+Space. Press Alt+Space anywhere to summon SilentExec bar.");
            }

            // Show window immediately on startup so user sees it right away
            ShowWindow(main_hwnd, SW_SHOW);
            SetForegroundWindow(main_hwnd);
            if !edit_hwnd.is_null() {
                SetFocus(edit_hwnd);
            }

            // Run native Win32 message loop
            let mut msg: MSG = std::mem::zeroed();
            while GetMessageW(&mut msg, std::ptr::null_mut(), 0, 0) > 0 {
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }

            if !main_brush.is_null() {
                DeleteObject(main_brush);
            }
            if !banner_brush.is_null() {
                DeleteObject(banner_brush);
            }
            if !edit_font.is_null() {
                DeleteObject(edit_font);
            }
            if !banner_font.is_null() {
                DeleteObject(banner_font);
            }
        }

        Ok(())
    }
}
