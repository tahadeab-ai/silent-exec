# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v4.0.0--pc-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-pc)
[![Android Release](https://img.shields.io/badge/Android_Release-v4.0.0--android-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a high-performance, bilingual (Arabic & English) OS, GUI, and problem-solving AI agent engineered for PC (Windows & Linux) and Android (`arm64-v8a`). SilentExec combines deterministic intent routing, rich Markdown & code reasoning (`reply`), built-in Pro Tools (`web_fetch`, `read_file`, `write_file`, `memory_save`, `copy_to_clipboard`), MCP connectors, and schema-validated system & GUI automation.

---

## 🚀 What's New in v4.0.0

- **Deterministic State-Machine Agent Core (Android & PC)**:
  - **Compound Intent Guard & `PlanManager`**: Splits compound Arabic (MSA + Yemeni dialects) and English commands (`"و"`, `"ثم"`, `"بعدين"`, `"بعدها"`, `"and"`, `"then"`, `","`) into dependency-ordered `PlanStep` queues.
  - **30+ Native Android Intent Executors**: Deterministic `Intent` / deep-link execution (`open_app`, `youtube_search`, `web_search`, `dial_number`, `open_settings_section`, `set_alarm`, `copy_clipboard`, `share_text`) runs *before* any GUI/Accessibility step.
  - **Mobilerun-Style Filtered A11y + `phone_state`**: Compact `phone_state + a11y_tree [0..N]` screen representation paired with `batch_actions` (Mobile-MCP pattern) and `capture_id` staleness rejection.
  - **Mobile-Agent `StateHashReflector` & `Notetaker`**: Detects stuck UI loops via screen state hashing and maintains structured cross-step scratchpad notes.
- **Silent Desktop Execution & Vision Grounding**:
  - **`cua-driver` (MIT) Silent Background Execution**: Targets background windows without stealing focus, guarded by `capture_id` staleness checks.
  - **Agent-S3 Code-First + DPI Scaling**: Accurate coordinate normalization across high-DPI displays with code-first action synthesis.
  - **Microsoft OmniParser (`icon_detect_v3`) Fallback**: Visual icon/element grounding fallback when accessibility trees are sparse.
- **Authenticated LAN Bridge & Clean-Room PII Privacy Mode**:
  - **WebSocket JSON-RPC 2.0 (`8081`) + HTTP Health (`8080`)**: Per-session Bearer token (6-digit pairing code) supporting `device.get_state`, `device.execute_batch`, and `planner.offload_step` (offloading Android multi-step planning to Desktop Ollama).
  - **Clean-Room On-Device PII Privacy Mode**: Redacts sensitive tokens, phone numbers, and credentials before remote/LAN planner calls.
- **Overhauled Arabic RTL / English LTR UI & Human-in-the-Loop UX**:
  - **Android Edge-Docked HUD**: WindowInsets-aware `TYPE_ACCESSIBILITY_OVERLAY` HUD hidden whenever SilentExec is foregrounded, auto-fading (`alpha = 0f`) during gestures/screenshots, with 1-tap Stop & collapsible timeline.
  - **Desktop Tauri v2 RTL Split-Pane Workspace**: Live Inspector pane, streaming step timeline, and explicit human-in-the-loop Takeover Banner.
- **Upgraded On-Device `llama.cpp v1.1.0` & 2026 Model Stack**:
  - Replaced greedy decoding with `penalties(1.12)` -> `top_k(40)` -> `top_p(0.90)` -> `temp(0.20)` -> `dist`, added a 96-token `<think>` budget guard, and enabled `Q8_0` KV-cache prefix reuse across agent steps.
  - Added [`nlp-training/generate_v4_agent_dataset.py`](nlp-training/generate_v4_agent_dataset.py) and [`nlp-training/train_functiongemma_270m_colab.py`](nlp-training/train_functiongemma_270m_colab.py) to fine-tune `google/functiongemma-270m-it` (<250 MB GGUF) on Colab/Kaggle.

---

## 🤖 Recommended 2026 Models

| Target Platform | Recommended Model | Format / Runtime | Notes |
| :--- | :--- | :--- | :--- |
| **Android (`arm64-v8a`)** | `Qwen3.5-2B-Q4_K_M` / `LFM3-VL-2B` | GGUF via `llama.cpp v1.1.0` | On-device inference with `Q8_0` KV-cache & `<think>` guard |
| **Desktop (16 GB PC)** | `qwen3.5:4b` / `GUI-Owl-1.5` | Ollama `/api/chat` (`think: false`) | Fast local planning + Android LAN offload (`planner.offload_step`) |
| **Ultra-Fast Edge Router** | `google/functiongemma-270m-it` | GGUF `Q4_K_M` (<250 MB) | Fine-tuned via `nlp-training/train_functiongemma_270m_colab.py` |

---

## ⚡ Quick Start

### 🖥️ PC Agent (Windows x86_64)

#### Option A: Modern Desktop GUI App (Tauri v2)
1. Download [`SilentExec-Desktop-x64.exe`](https://github.com/tahadeab-ai/silent-exec/releases/download/v4.0.0-pc/SilentExec-Desktop-x64.exe) from the official [`v4.0.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-pc) release.
2. Double-click to launch the RTL/LTR Split-Pane Workspace with Live Inspector, collapsible `💭 Thinking & Steps`, inline Tier-2 `[✓ Allow | ✗ Deny]`, Takeover Banner, Ollama `qwen3.5:4b` / `GUI-Owl-1.5` (`think: false`), LAN Bridge (6-digit pairing), and 1-click Auto-Updater.

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
1. **Install APK**: Download [`silent-exec-android-arm64.apk`](https://github.com/tahadeab-ai/silent-exec/releases/download/v4.0.0-android/silent-exec-android-arm64.apk) from the official [`v4.0.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-android) release (`adb install -r silent-exec-android-arm64.apk`).
2. **Choose Provider or Download Model**: Open the **Models (`النماذج`)** tab and select **Gemini**, **Groq**, **OpenRouter**, **Ollama** (or LAN Bridge offload), or download `Qwen3.5-2B-Q4_K_M.gguf` / `LFM3-VL-2B` with byte-range pause/resume.
3. **Execute**: Tap **🎤** or type natural commands in Yemeni Arabic, MSA, English, or Arabizi — native intents run instantaneously, and multi-step UI flows execute with the Edge-Docked HUD and `StateHashReflector`!

---

## 📦 Official Production Releases

> [!IMPORTANT]
> **Zero binaries or model weights are committed into this Git repository.**
> All deliverables are compiled in GitHub Actions cloud CI and distributed via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).

| Component | Target Platform | Release Tag | Asset Name | Quick Download Command |
| :--- | :--- | :--- | :--- | :--- |
| **Android Execution Agent** | Android (`arm64-v8a`) | [`v4.0.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-android) | `silent-exec-android-arm64.apk` | `gh release download v4.0.0-android -R tahadeab-ai/silent-exec -p "*.apk"` |
| **Desktop GUI App (Tauri v2)** | Windows x86_64 | [`v4.0.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-pc) | `SilentExec-Desktop-x64.exe` | `gh release download v4.0.0-pc -R tahadeab-ai/silent-exec -p "*Desktop*.exe"` |
| **PC CLI & Floating Bar** | Windows x86_64 | [`v4.0.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v4.0.0-pc) | `silent-exec-pc.exe` | `gh release download v4.0.0-pc -R tahadeab-ai/silent-exec -p "silent-exec-pc.exe"` |
| **Edge NLP Model (Recommended)** | GGUF (`q4_k_m`) | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) | `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` | `gh release download v0.2.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |

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

## 🎮 The 25 Supported Actions

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
| 22 | `reply` | **Tier 1 (Silent)** | Streams generated text, summaries, or answers to user | `"لخص هذا النص"` / `"summarize this"` |
| 23 | `read_file` | **Tier 1 (Silent)** | Reads local text file content for agent context | `"اقرأ ملف notes.txt"` / `"read notes.txt"` |
| 24 | `write_file` | **Tier 2 (Confirm)** | Writes generated text, notes, or code to a file | `"اكتب ملخص في report.md"` / `"write report.md"` |
| 25 | `memory_save` | **Tier 1 (Silent)** | Saves preference, fact, or skill to persistent memory | `"تذكر أن مجلد العمل هو D:\\Work"` / `"remember..."` |

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

- **Core Rust**: **114 unit tests** across `silent-exec-core` (29 tests) and `silent-exec-pc` (85 tests) (`CARGO_BUILD_JOBS=2 cargo test --workspace`).
- **Android Agent**: **112 JVM unit tests** across 10 test suites including `AccessibilityAgentEngineTest`, `CoreSafetyTest`, `OfflineLlamaTest`, `UpdateAndModelsTest`, `GuiAgentAndRouterTest`, `AgentLoopAndThemeTest`, `ResumableAndBackendTest`, `HermesAgentAndBugfixTest`, `BuiltInSkillsAndHfTest`, and `V2SkillsAndMcpTest`.
- **Golden Evaluation**: **250 evaluation cases** ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)).
- **Schema & Policy Alignment**: 25-action JSON Schema validation, Draft 2020-12 compliance, and GBNF grammar verification.
- **Native NDK Verification**: Cloud CI confirms `libllama_jni.so` is bundled and validated inside the `arm64-v8a` APK.
- **Cryptographic Release Signing**: Permanent 2048-bit RSA release signing key verified via `apksigner verify --verbose`.

---

## 📄 License & Open-Source Attributions

MIT License. See [LICENSE](LICENSE) for details.

SilentExec `v4.0.0` incorporates architectural patterns and compatible open-source components under permissive **MIT** and **Apache-2.0** licenses:
- **OpenDroid** (Apache-2.0) — Compound intent splitting & native Android intent-first execution patterns
- **Mobilerun** (MIT) — Filtered accessibility tree + `phone_state` representation & WebSocket JSON-RPC bridge
- **Mobile-MCP** (Apache-2.0) — Batched UI action execution (`batch_actions`)
- **Mobile-Agent** (MIT) — `StateHashReflector` loop detection & `Notetaker` structured memory
- **cua-driver** (MIT) — Silent background desktop window targeting
- **Agent-S3** (Apache-2.0) — Code-first execution & DPI coordinate scaling
- **Microsoft OmniParser (`icon_detect_v3`)** (MIT) — Visual UI element grounding fallback
- **llama.cpp** (MIT) — On-device `arm64-v8a` GGUF inference (`v1.1.0`)
