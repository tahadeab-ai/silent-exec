# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v0.3.0--pc-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.3.0-pc)
[![Android Release](https://img.shields.io/badge/Android_Release-v0.3.0--android-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.3.0-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a high-performance, zero-chatter OS and GUI execution agent engineered for PC (Windows & Linux) and Android. Rather than generating verbose conversational text or streaming multi-paragraph responses, SilentExec translates multilingual user intent (Arabic and English) directly into deterministic, strictly schema-validated system and GUI actions.

---

## 🚀 What's New in v0.3.0

- **20 GUI & OS Actions**: Expanded from 15 to 20 actions with full GUI Agent automation (`ui_click`, `ui_type`, `ui_scroll`, `ui_back`, `ui_home`) across Windows (`SendInput`) and Android (`AccessibilityService` tree traversal & gesture dispatch).
- **Deterministic Fast Rule Router (<15ms)**: Sub-15ms regex & normalization router for Yemeni Arabic, MSA, and English single-step navigation and volume commands, bypassing local LLM inference entirely when possible.
- **KV-Cache Prompt Caching**: Retains the static ChatML system prompt prefix in KV cache across consecutive queries on both `silent-exec-pc` (`cache_prompt: true`) and Android native `libllama_jni.so`, drastically reducing repeat token generation latency.
- **Material 3 3-Tab Android UI**: Clean dark Material 3 redesign with instant bilingual locale switching (العربية RTL / English LTR), 1-tap command history chips, and live execution feeds.
- **Floating Live Status Pill**: Real-time overlay pill on Android showing active multi-step execution stages with a 1-tap instant cancellation button (`⏹`).
- **Dynamic Model Hub & Self-Updater**: In-app model discovery, streaming downloads with SHA-256 integrity verification, activation, and background self-updating from GitHub Releases on both PC and Android.
- **Quick Settings Tile**: 1-tap voice and text trigger directly from Android's quick settings drawer.

---

## ⚡ Quick Start

### 🖥️ PC Agent (Windows x86_64 & Linux)
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
1. **Install APK**: Download [`silent-exec-android-arm64.apk`](https://github.com/tahadeab-ai/silent-exec/releases/download/v0.3.0-android/silent-exec-android-arm64.apk) from the official `v0.3.0-android` release (`adb install -r silent-exec-android-arm64.apk`).
2. **Download Model**: Open the app, navigate to the **Models (`النماذج`)** tab, and tap **`Download`** on `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf` (379 MB) or `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` (940 MB) with cryptographic SHA-256 verification.
3. **Execute**: Tap **🎤** or type natural voice/text commands in Arabic or English — actions execute silently on-device via native `llama.cpp` JNI with live visual feed and floating pill feedback!

---

## 📦 Official Production Releases

> [!IMPORTANT]
> **Zero binaries or model weights are committed into this Git repository.**
> All deliverables are compiled in GitHub Actions cloud CI and distributed via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).

| Component | Target Platform | Release Tag | Asset Name | Size | SHA-256 Checksum | Quick Download Command |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android Execution Agent** | Android (`arm64-v8a`) | [`v0.3.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.3.0-android) | `silent-exec-android-arm64.apk` | **11.53 MB** | `00d0165152dd0cc05d8754561a14d67b3125423c9f7d78729ea7f6bb33f40038` | `gh release download v0.3.0-android -R tahadeab-ai/silent-exec -p "*.apk"` |
| **PC Execution Agent** | Windows x86_64 | [`v0.3.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.3.0-pc) | `silent-exec-pc.exe` | **0.96 MB** | `817fc7e8625ffe4905b341474b09b6cdb8d25f802e4750d6bbf5e1db09503dde` | `gh release download v0.3.0-pc -R tahadeab-ai/silent-exec -p "*.exe"` |
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
         Alt+Space Bar / Fast Router                  Material 3 UI / Fast Router
                        │                                         │
         ┌──────────────┴──────────────┐           ┌──────────────┴──────────────┐
         ▼                             ▼           ▼                             ▼
   [Fast Router]               [Managed Llama]  [Fast Router]            [Native Llama JNI]
   (<15ms match)             (cache_prompt: true)(<15ms match)         (KV-cache prefix reuse)
         │                             │           │                             │
         └──────────────┬──────────────┘           └──────────────┬──────────────┘
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

## 🎮 The 20 Supported Actions

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
| 16 | `ui_click` | **Tier 1 (Silent)** | Clicks visible UI element by label | `"اضغط على زر إرسال"` / `"click send button"` |
| 17 | `ui_type` | **Tier 1 (Silent)** | Types text into focused input field | `"اكتب مرحباً في البحث"` / `"type hello in search"` |
| 18 | `ui_scroll` | **Tier 1 (Silent)** | Scrolls screen (up/down/left/right) | `"انزل لتحت"` / `"scroll down"` |
| 19 | `ui_back` | **Tier 1 (Silent)** | Navigates back in current app/window | `"ارجع للخلف"` / `"back"` |
| 20 | `ui_home` | **Tier 1 (Silent)** | Returns to home screen / desktop | `"الشاشة الرئيسية"` / `"desktop"` |

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

- **Core Rust**: **83 unit tests** across `silent-exec-core` (25 tests) and `silent-exec-pc` (58 tests) (`CARGO_BUILD_JOBS=2 cargo test --workspace`).
- **Android Agent**: **56 JVM unit tests** across `CoreSafetyTest` (21 tests), `OfflineLlamaTest` (14 tests), `UpdateAndModelsTest` (10 tests), and `GuiAgentAndRouterTest` (11 tests).
- **Golden Evaluation**: **250 evaluation cases** ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)).
- **Schema & Policy Alignment**: 20-action JSON Schema validation, Draft 2020-12 compliance, and GBNF grammar verification.
- **Native NDK Verification**: Cloud CI confirms `libllama_jni.so` is bundled and validated inside the `arm64-v8a` APK.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
