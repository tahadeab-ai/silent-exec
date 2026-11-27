# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v5.0.0-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v5.0.0)
[![Android Release](https://img.shields.io/badge/Android_Release-v5.0.0-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v5.0.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec v5.0.0** is a high-performance, bilingual (Arabic & English) OS, GUI, and problem-solving AI agent engineered for PC (Windows & Linux) and Android (`arm64-v8a`).

---

## 🚀 What's New in v5.0.0

- **Android Agent (`arm64-v8a`)**:
  - **Material 3 + AI Elements Compose UI**: Complete dark surface hierarchy (`#0B0F17` canvas, `#131A26` cards, `#38BDF8` accent), strict BiDi/LTR code & log isolation (`LtrCodeBlock`), live `PlanCard`, collapsible `ToolCallCard` & `ReasoningAccordion`, and `SensitiveScreenGuard` takeover sheet.
  - **Constrained On-Device Inference (`InferenceEngine`)**: `n_ctx = 4096`, `max_tokens = 384`, `Qwen3.5-2B-Q4_K_M` non-thinking default (`/no_think`), GBNF grammar enforcement, and automatic single non-thinking retry on blank/`<think>` outputs.
  - **Accessibility & Fallback**: `moveTaskToBack(true)` before Step 1, `Mobilerun Portal` filtered `a11y_tree`, API 30+ compressed screenshot fallback when `<= 3` interactive nodes are detected, and `Shizuku-API` non-root `input` fallback.
- **Desktop App (Tauri v2 + React + `rmcp`)**:
  - **Modern React + Tailwind + `assistant-ui` + `prompt-kit` UI**: Strict Arabic RTL (`IBM Plex Sans Arabic` / `Cairo`) with `dir="ltr"` isolation for technical readouts, `ModelsHub` (16 GB RAM-fit badges for `qwen3.5:4b` & `lfm3:1.7b`), `MCP & Bridges` view, and 2-column `Settings` grid.
  - **Official Rust MCP Host (`rmcp`)**: `McpHub` managing `CursorTouch/Windows-MCP` (`uvx windows-mcp serve` / bundled `--onedir` sidecar, `WINDOWS_MCP_EXCLUDE_PROCESSES`, `ANONYMIZED_TELEMETRY=false`, Safe Mode `--exclude-tools PowerShell,Registry`), plus toggleable `Playwright MCP` and `Mobile MCP`.
  - **Clean-Room MIT PII Redactor**: Luhn-checked credit cards, OTPs, API keys/Bearer tokens, emails, phones, and password fields redacted in both Rust (`silent_exec_core::pii`) and Kotlin (`com.silentexec.android.security.PiiRedactor`).

---

## ⚡ Quick Start

### 🖥️ Windows Desktop (`x86_64`)
1. Install [`uv`](https://docs.astral.sh/uv/) and pre-install `windows-mcp` (optional, auto-detected by `McpHub`):
   ```powershell
   uv tool install windows-mcp
   ```
   *(Or let SilentExec launch `uvx windows-mcp serve` automatically on first run; if bundling, use PyInstaller `--onedir`, never `--onefile`.)*
2. Download `SilentExec-Desktop-x64.exe` from [`v5.0.0`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v5.0.0).
3. Pull the recommended 16 GB CPU model in Ollama:
   ```powershell
   ollama pull qwen3.5:4b
   ```

### 📱 Android (`arm64-v8a`)
1. Download `SilentExec-v5.0.0-android.apk` from [`v5.0.0`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v5.0.0) (`adb install -r SilentExec-v5.0.0-android.apk`).
2. In the **النماذج (Models)** tab, download **`Qwen3.5-2B-Q4_K_M.gguf`** (Recommended Default, 1.28 GB) or configure Gemini / Groq / Ollama.
3. Enable Accessibility Service (and optionally authorize **Shizuku** in Settings for custom views).

---

## 📜 Open-Source Attribution (MIT / Apache-2.0 Only)

| Component | License | Role in SilentExec v5.0 |
| :--- | :--- | :--- |
| [`modelcontextprotocol/rust-sdk` (`rmcp`)](https://github.com/modelcontextprotocol/rust-sdk) | Apache-2.0 | Official Rust MCP stdio host |
| [`CursorTouch/Windows-MCP`](https://github.com/cursortouch/windows-mcp) | MIT | Windows UIA & input automation sidecar |
| [`microsoft/playwright-mcp`](https://github.com/microsoft/playwright-mcp) | Apache-2.0 | Optional DOM-based browser MCP server |
| [`mobile-next/mobile-mcp`](https://github.com/mobile-next/mobile-mcp) | Apache-2.0 | Optional USB/Wi-Fi phone control MCP server |
| [`assistant-ui/assistant-ui`](https://github.com/assistant-ui/assistant-ui) | MIT | Desktop Agent thread runtime |
| [`ibelick/prompt-kit`](https://github.com/ibelick/prompt-kit) | MIT | Composable AI UI component patterns |
| [`droidrun/mobilerun-portal`](https://github.com/droidrun/mobilerun-portal) | MIT | Structured `a11y_tree` + `phone_state` schema |
| [`RikkaApps/Shizuku-API`](https://github.com/RikkaApps/Shizuku-API) | MIT | Optional non-root `input` fallback on Android |
| [`ggml-org/llama.cpp`](https://github.com/ggml-org/llama.cpp) | MIT | On-device GGUF inference fallback (`n_ctx=4096`) |
| `Qwen3.5-2B` / `Qwen3.5-4B` | Apache-2.0 | Default non-thinking bilingual agent models |

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
