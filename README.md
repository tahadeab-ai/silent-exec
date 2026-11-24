# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v0.4.0--pc-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.4.0-pc)
[![Android Release](https://img.shields.io/badge/Android_Release-v0.4.0--android-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.4.0-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a high-performance, zero-chatter OS and GUI execution agent engineered for PC (Windows & Linux) and Android. Rather than generating verbose conversational text or streaming multi-paragraph responses, SilentExec translates multilingual user intent (Arabic and English) directly into deterministic, strictly schema-validated system and GUI actions.

---

## 🚀 What's New in v0.4.0

- **Multi-Step ReAct Planner (`Observe -> Think -> Act`)**: Full compound-command splitting and multi-step plan execution (`MultiStepPlan` JSON up to 6 steps) with an explicit `done` termination action (Tier 1 Silent).
- **Compound-Command Guard in FastRuleRouter**: Detects multi-step connectors (`و`, `ثم`, `بعدين`, `وافتح`, `وابحث`, `وارسل`, `واحذف`, `وانسخ`, `واضغط`, `وشغل`, `ونزل`, `،`, `and`, `then`, `after`, `,`) on both Rust and Kotlin, delegating compound queries directly to the Planner / LLM while preserving <15ms execution for simple commands.
- **Tauri v2 Desktop GUI (`SilentExec-Desktop-x64.exe`, Zero npm)**: Ultra-lightweight Windows GUI (zero Node.js build step) built with Tauri v2 and pure HTML5/CSS3/Vanilla JS. Includes Dark & Light themes, Arabic RTL / English LTR switcher, live step-by-step execution timeline, Models Hub with 1-click llama-server runtime setup, and comprehensive settings.
- **Modern Android v0.4.0 UI/UX Overhaul**: Material 3 Day/Night theme, crisp adaptive geometric launcher icon, distinct Silent / Interactive (with `[Run | Skip | Stop]` step preview cards) / Dry-Run modes, and interactive floating bubble with instant stop button (`⏹`).
- **Real Screen-Aware Agent Loop on Android**: Captures pruned Accessibility UI hierarchies (`[0] Search (clickable)`, `[1] Message input (editable)`), formats screen-aware ChatML prompts, and sequentially executes clicks by index or text, smart typing with search/send actions, and smooth scrolling.
- **Production GBNF Grammar Constraints**: Synchronized `schemas/action.gbnf` across PC and Android, enforcing single actions or multi-step plans with UTF-8 Arabic slot generation and zero conversational chatter.

---

## ⚡ Quick Start

### 🖥️ PC Agent (Windows x86_64)

#### Option A: Modern Desktop GUI App (Tauri v2)
1. Download [`SilentExec-Desktop-x64.exe`](https://github.com/tahadeab-ai/silent-exec/releases/download/v0.4.0-pc/SilentExec-Desktop-x64.exe) from the official `v0.4.0-pc` release.
2. Double-click to launch the sleek GUI window with live execution timeline, model manager, and dark/light mode.

#### Option B: Minimal CLI & Floating Command Bar
```bash
# 1. Download and run automated setup (fetches quantized model & managed llama-server)
silent-exec-pc setup

# 2. Launch sleek native floating command bar (Alt+Space to toggle, <25 MB idle RAM)
silent-exec-pc bar

# 3. Configure preferences, switch models, or update
silent-exec-pc config --lang ar --mode silent
silent-exec-pc models
silent-exec-pc update --check
```

### 📱 Android Agent (`arm64-v8a`)
1. **Install APK**: Download [`silent-exec-android-arm64.apk`](https://github.com/tahadeab-ai/silent-exec/releases/download/v0.4.0-android/silent-exec-android-arm64.apk) from the official `v0.4.0-android` release (`adb install -r silent-exec-android-arm64.apk`).
2. **Download Model**: Open the app, navigate to the **Models (`النماذج`)** tab, and tap **`Download`** on `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf` (379 MB) or `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` (940 MB) with cryptographic SHA-256 verification.
3. **Execute**: Tap **🎤** or type natural voice/text commands in Arabic or English — actions execute silently on-device via native `llama.cpp` JNI with live timeline feed and floating pill feedback!

---

## 📦 Official Production Releases

> [!IMPORTANT]
> **Zero binaries or model weights are committed into this Git repository.**
> All deliverables are compiled in GitHub Actions cloud CI and distributed via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).

| Component | Target Platform | Release Tag | Asset Name | Size | SHA-256 Checksum | Quick Download Command |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android Execution Agent** | Android (`arm64-v8a`) | [`v0.4.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.4.0-android) | `silent-exec-android-arm64.apk` | **12.15 MB** | `f05181fe3b79515a3146f915d574374fa2f81e0ccbf6a7a525b3bdcb6ea37c24` | `gh release download v0.4.0-android -R tahadeab-ai/silent-exec -p "*.apk"` |
| **Desktop GUI App (Tauri v2)** | Windows x86_64 | [`v0.4.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.4.0-pc) | `SilentExec-Desktop-x64.exe` | **9.99 MB** | `2f761551a596bc9e12243ec663b30e4a80eed2ab17b6c896b7b7c005f0deef16` | `gh release download v0.4.0-pc -R tahadeab-ai/silent-exec -p "*Desktop*.exe"` |
| **PC CLI & Floating Bar** | Windows x86_64 | [`v0.4.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.4.0-pc) | `silent-exec-pc.exe` | **1.03 MB** | `d083eb4918b2db5e70af5a4520d27851f25b789ab4f381bca1e8a371b14d60ae` | `gh release download v0.4.0-pc -R tahadeab-ai/silent-exec -p "silent-exec-pc.exe"` |
| **Edge NLP Model (Recommended)** | GGUF (`q4_k_m`) | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) | `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` | **940.37 MB** | `e636822696ecd8b9fb7fbcf2c8cc4ab12c04e681edf6308368a589eb219a0251` | `gh release download v0.2.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |
| **Edge NLP Model (Baseline)** | GGUF (`q4_k_m`) | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) | `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf` | **379.38 MB** | `5dd97b1269d01f77067fe6766f69a6e8b731efb2b5ba7ae05fce5d3580eb260b` | `gh release download v0.1.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |

---

## 🏛️ System Architecture

```
                          [User Input (Arabic / English / Voice)]
                                             │
                        ┌────────────────────┴────────────────────┐
                        ▼                                         ▼
              [Windows / Linux PC]                        [Android arm64-v8a]
          Tauri GUI / Alt+Space Bar                  Material 3 DayNight / Tile
                        │                                         │
        ┌───────────────┴───────────────┐         ┌───────────────┴───────────────┐
        ▼                               ▼         ▼                               ▼
  [Fast Router]               [Multi-Step LLM] [Fast Router]               [Multi-Step LLM]
  (Simple <15ms)              (ReAct Planner)  (Simple <15ms)              (ReAct Planner)
        │                               │         │                               │
        └───────────────┬───────────────┘         └───────────────┬───────────────┘
                        ▼                                         ▼
             [Rust MultiStepPlan Gate]                 [Kotlin MultiStepPlan Gate]
             (3-Tier Deterministic Check)              (3-Tier Deterministic Check)
                        │                                         │
         ┌──────────────┼──────────────┐           ┌──────────────┼──────────────┐
         ▼              ▼              ▼           ▼              ▼              ▼
    AllowSilent   RequireConfirm     Block    AllowSilent   RequireConfirm     Block
     (Execute)      (Dialog ?)     (Reject)    (Execute)      (Dialog ?)     (Reject)
                        │                                         │
                        ▼                                         ▼
             [Win32 SendInput Exec]                   [AccessibilityService Exec]
          (Click, Type, Scroll, Keys)                 (Tree Traversal & Gestures)
```

---

## 🎮 The 21 Supported Actions

All actions conform strictly to `schemas/action_schema.json` and are constrained at inference time via `schemas/action.gbnf`:

| # | Action Wire Name | Safety Tier | Description | Sample Query |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `open_folder` | Tier 1 (Silent) | Opens local filesystem folder | `"افتح مجلد التنزيلات"` / `"open downloads"` |
| 2 | `open_file` | Tier 1 (Silent) | Opens specific file with default app | `"افتح التقرير.pdf"` / `"open report.pdf"` |
| 3 | `launch_app` | Tier 1 (Silent) | Launches PC application | `"شغل الآلة الحاسبة"` / `"launch calculator"` |
| 4 | `search_files` | Tier 1 (Silent) | Searches filesystem by query | `"ابحث عن ملفات الصور"` / `"search photos"` |
| 5 | `set_volume` | Tier 1 (Silent) | Sets audio output volume (0-100) | `"ارفع الصوت 80"` / `"set volume 80"` |
| 6 | `set_brightness` | Tier 1 (Silent) | Sets display brightness (0-100) | `"اضبط الإضاءة 70"` / `"set brightness 70"` |
| 7 | `create_note` | Tier 1 (Silent) | Creates quick note / text entry | `"سجل ملاحظة موعد الطبيب"` / `"note doctor appointment"` |
| 8 | `copy_to_clipboard` | Tier 1 (Silent) | Copies payload into system clipboard | `"انسخ الرابط"` / `"copy text to clipboard"` |
| 9 | `delete_file` | **Tier 2 (Confirm)** | Deletes target file permanently | `"احذف الملف المؤقت"` / `"delete old.log"` |
| 10 | `move_file` | **Tier 2 (Confirm)** | Moves or renames target file | `"انقل الملف إلى المستندات"` / `"move file to docs"` |
| 11 | `system_power` | **Tier 2 (Confirm)** | Triggers sleep, reboot, or shutdown | `"أعد تشغيل الجهاز"` / `"restart system"` |
| 12 | `open_android_app` | Tier 1 (Silent) | Launches Android application | `"افتح الواتساب"` / `"open whatsapp"` |
| 13 | `toggle_setting` | **Tier 2 (Confirm)** | Toggles system setting (wifi/bt) | `"عطل البلوتوث"` / `"toggle wifi"` |
| 14 | `send_message` | **Tier 2 (Confirm)** | Dispatches SMS / chat message | `"أرسل رسالة إلى خالد"` / `"send message to John"` |
| 15 | `open_url` | Tier 1 (Silent) | Opens web URL in default browser | `"افتح موقع جوجل"` / `"open https://google.com"` |
| 16 | `ui_click` | **Tier 1 (Silent)** | Clicks visible UI element by label / index | `"اضغط على زر إرسال"` / `"click send button"` |
| 17 | `ui_type` | **Tier 1 (Silent)** | Types text into focused input field | `"اكتب مرحباً في البحث"` / `"type hello in search"` |
| 18 | `ui_scroll` | **Tier 1 (Silent)** | Scrolls screen (up/down/left/right) | `"انزل لتحت"` / `"scroll down"` |
| 19 | `ui_back` | **Tier 1 (Silent)** | Navigates back in current app/window | `"ارجع للخلف"` / `"back"` |
| 20 | `ui_home` | **Tier 1 (Silent)** | Returns to home screen / desktop | `"الشاشة الرئيسية"` / `"desktop"` |
| 21 | `done` | **Tier 1 (Silent)** | Signals completion of multi-step ReAct sequence | `"done"` / End of Plan |

---

## 📊 Benchmark Results (`v0.1.0-model` vs `v0.2.0-model`)

Evaluated on the 250-test-case Golden Benchmark ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)):

| Model | Format | Size | JSON Validity | Intent Accuracy | Slot Exact Match | Slot F1 | Release |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Qwen2.5-0.5B-Instruct** | GGUF (`q4_k_m`) | 379.38 MB | **97.20%** | **95.20%** | **44.00%** | — | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) |
| **Qwen2.5-1.5B-Instruct** | GGUF (`q4_k_m`) | 940.37 MB | **98.80%** | **98.00%** | **60.80%** | **78.90%** | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) |

### Per-Language Breakdown (`v0.2.0-model`)
- **English & Mixed Code-Switching (`en_mixed`)**: Intent **100.0%** (84/84), Slot Exact **72.62%** (61/84), Slot F1 **87.92%**
- **Colloquial Yemeni Arabic (`yemeni_ar`)**: Intent **95.24%** (80/84), Slot Exact **53.57%** (45/84), Slot F1 **74.36%**
- **Modern Standard Arabic (`msa_ar`)**: Intent **98.78%** (81/82), Slot Exact **56.10%** (46/82), Slot F1 **74.35%**

---

## 🛡️ Deterministic Safety Policy

SilentExec enforces a multi-step safety policy:

1. **Tier 1 (Silent)**: Direct silent execution for non-destructive operations.
2. **Tier 2 (Confirm)**: Explicit user confirmation dialog required before proceeding (`delete_file`, `move_file`, `system_power`, `toggle_setting`, `send_message`).
3. **Tier 3 (Hard Block)**: Immediate rejection with reason logged if commands attempt:
   - Path traversal (`..`)
   - Protected system directories (`C:\Windows`, `/etc`, `/system`, `/bin`)
   - Shell metacharacter injection (`&&`, `;`, `|`, `$()`, `` ` ``)
4. **Multi-Step Plan Safety**: If any step in a multi-step plan is blocked, the entire plan is halted. If any step requires confirmation, the plan requests approval before executing the sensitive step.
5. **Confidence Downgrade**: Any Tier 1 action with model confidence `< 0.65` is automatically downgraded to require user confirmation.

---

## 🧪 Verification & Test Suite

All tests execute in automated GitHub Actions cloud CI pipelines across Ubuntu and Windows runners:

- **Core Rust**: **88 unit tests** across `silent-exec-core` (27 tests) and `silent-exec-pc` (61 tests) (`CARGO_BUILD_JOBS=2 cargo test --workspace`).
- **Android Agent**: **61 JVM unit tests** across `CoreSafetyTest` (21 tests), `OfflineLlamaTest` (14 tests), `UpdateAndModelsTest` (10 tests), `GuiAgentAndRouterTest` (11 tests), and `AgentLoopAndThemeTest` (14 tests).
- **Golden Evaluation**: **250 evaluation cases** ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)).
- **Schema & Policy Alignment**: 21-action JSON Schema validation, Draft 2020-12 compliance, and GBNF grammar verification.
- **Native NDK Verification**: Cloud CI confirms `libllama_jni.so` is bundled and validated inside the `arm64-v8a` APK.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
