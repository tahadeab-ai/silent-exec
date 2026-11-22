# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v0.2.0--pc-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-pc)
[![Android Release](https://img.shields.io/badge/Android_Release-v0.2.0--android-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a high-performance, zero-chatter OS execution agent designed for PC (Windows & Linux) and Android. Rather than generating conversational filler or multi-paragraph responses, SilentExec maps multilingual user intent (Arabic and English) directly to deterministic, strictly schema-validated system actions.

---

## ⚡ Quick Start

### 🖥️ PC Agent (Windows x86_64)
```bash
# Step 1: Automatic one-time setup (downloads quantized GGUF model + managed llama-server runtime)
silent-exec-pc setup

# Step 2: Launch sleek native floating command bar (Alt+Space to toggle, <25 MB idle RAM)
silent-exec-pc bar
```

### 📱 Android Agent (`arm64-v8a`)
1. **Install APK**: Download [`silent-exec-android-arm64.apk`](https://github.com/tahadeab-ai/silent-exec/releases/download/v0.2.0-android/silent-exec-android-arm64.apk) from the official `v0.2.0-android` release and install via ADB (`adb install -r silent-exec-android-arm64.apk`).
2. **Download Model**: Open the app, select **`Offline (On-Device)`**, and tap **`تحميل النموذج (379 MB)`** to download and SHA-256 verify `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf`.
3. **Execute Offline**: Tap the **🎤** microphone button or type commands in Arabic or English — actions execute silently on-device via native `llama.cpp` JNI with zero conversational bubbles!

---

## 📦 Official Releases (`v0.2.0`)

> [!IMPORTANT]
> **Zero binaries or model weights are committed into this Git repository.**
> All deliverables are compiled in GitHub Actions cloud CI and distributed via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).

| Component | Target Platform | Release Tag | Asset Name | Size | SHA-256 Checksum | Quick Download Command |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PC Execution Agent** | Windows x86_64 | [`v0.2.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-pc) | `silent-exec-pc.exe` | **1.08 MB** | `bc010eeb94f8fd282f56a888e857a34eba2d3358983e00b01fd06173abe14486` | `gh release download v0.2.0-pc -R tahadeab-ai/silent-exec -p "silent-exec-pc.exe"` |
| **Android Execution Agent** | Android (`arm64-v8a`) | [`v0.2.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-android) | `silent-exec-android-arm64.apk` | **11.44 MB** | `899f41fe074302557c3ad433742037cb464bcba33021a84dd755b88cf4e5bd52` | `gh release download v0.2.0-android -R tahadeab-ai/silent-exec -p "*.apk"` |
| **Edge NLP Model (Recommended)** | GGUF (`q4_k_m`) | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) | `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` | **940.37 MB** | `e6368226026a79ee32717a6a427d1a1ddf77bc9d9cb30ce566d5b0bfd3aa4cb7` | `gh release download v0.2.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |
| **Edge NLP Model (Baseline)** | GGUF (`q4_k_m`) | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) | `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf` | **379.38 MB** | `5dd97b12d1b54a055cb16035fce5f762744795b5fe38fa7b864d4fcba8c1a18c` | `gh release download v0.1.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |

---

## 🏛️ System Architecture

```
                          [User Input (Arabic / English / Voice)]
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
             [Windows / Linux PC]                          [Android arm64-v8a]
          Alt+Space Floating Bar                         Zero-Chatter Dual UI
                       │                                           │
         ┌─────────────┴─────────────┐               ┌─────────────┴─────────────┐
         ▼                           ▼               ▼                           ▼
[Managed llama-server]      [LAN Bridge Server]   [Native llama.cpp JNI]   [LAN Bridge Client]
   (Local Qwen GGUF)        (Bearer JSON-RPC)       (GBNF action.gbnf)      (Remote PC Exec)
         │                           │               │                           │
         └─────────────┬─────────────┘               └─────────────┬─────────────┘
                       ▼                                           ▼
             [Core Rust SafetyGate]                      [Kotlin SafetyGate]
          (3-Tier Deterministic Check)                (3-Tier Deterministic Check)
                       │                                           │
         ┌─────────────┼─────────────┐               ┌─────────────┼─────────────┐
         ▼             ▼             ▼               ▼             ▼             ▼
    AllowSilent  RequireConfirm    Block        AllowSilent  RequireConfirm    Block
    (Execute ✓)    (Dialog ?)   (Reject ✗)      (Execute ✓)    (Dialog ?)   (Reject ✗)
```

### Key Modules
- **`core-rust/`**: High-performance core Rust workspace containing:
  - `silent-exec-core`: Cross-platform `ActionCommand` schema, deterministic 3-tier `SafetyGate`.
  - `silent-exec-pc`: Sub-15ms fuzzy Arabic/English indexer, safe executor, managed `llama-server` runtime supervisor, native Win32 `Alt+Space` floating command bar, and authenticated LAN bridge server.
- **`android-agent/`**: Native Android app containing:
  - Real C++ JNI bridge (`llama_jni.cpp`) compiling `llama.cpp` for native `arm64-v8a` CPU execution.
  - In-app model downloader with streaming I/O and SHA-256 integrity verification.
  - Native zero-overhead Arabic (`ar-SA`) and English speech recognition.
  - Zero-chatter dual-mode UI (`Offline` vs `LAN Bridge`).
- **`schemas/`**: Single Source of Truth schema definitions:
  - `action_schema.json`: JSON Schema (Draft 2020-12).
  - `action.gbnf`: llama.cpp BNF grammar constraining LLM token generation strictly to valid action JSON.
- **`nlp-training/`**: QLoRA dataset generation and fine-tuning recipes for edge LLMs on Google Colab with Unsloth.
- **`evals/`**: 250-test-case golden evaluation harness measuring intent classification accuracy, slot F1, and safety adherence.

---

## 📊 Benchmark Results (`v0.1.0-model` vs `v0.2.0-model`)

Evaluated on the 250-test-case Golden Benchmark ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)):

| Model | Format | Size | JSON Validity | Intent Accuracy | Slot Exact Match | Slot F1 | Release |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Qwen2.5-0.5B-Instruct** | GGUF (`q4_k_m`) | 379.38 MB | **97.20%** | **95.20%** | **44.00%** | — | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) |
| **Qwen2.5-1.5B-Instruct** | GGUF (`q4_k_m`) | 940.37 MB | **98.80%** | **98.00%** | **60.80%** | **78.90%** | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) |

### Per-Language Breakdown (`v0.2.0-model`)
- **English & Mixed Code-Switching (`en_mixed`)**: Intent **100.0%** (84/84), Slot Exact **72.62%** (61/84), Slot F1 **87.92%**
- **Colloquial Yemeni Arabic (`yemeni_ar`)**: Intent **95.24%** (80/84), Slot Exact **53.57%** (45/84), Slot F1 **74.36%**
- **Modern Standard Arabic (`msa_ar`)**: Intent **98.78%** (81/82), Slot Exact **56.10%** (46/82), Slot F1 **74.35%**

---

## 🛡️ Action Safety Tiers

SilentExec enforces a deterministic 3-tier safety policy:

| Tier | Name | Behavior | Actions |
| :--- | :--- | :--- | :--- |
| **Tier 1** | `tier_1_silent` | Direct silent execution | `open_folder`, `open_file`, `launch_app`, `search_files`, `set_volume`, `set_brightness`, `create_note`, `copy_to_clipboard`, `open_android_app`, `open_url` |
| **Tier 2** | `tier_2_confirm` | Interactive confirmation required (`[Confirm / Cancel]`) | `delete_file`, `move_file`, `system_power`, `toggle_setting`, `send_message` |
| **Tier 3** | `tier_3_blocked` | Hard-blocked; cannot be executed | Path traversal (`..`), system directories (`C:\Windows`, `/etc`, `/system`), shell injection (`&&`, `;`, `\|`, `$()`, ``` ` ```) |

*Low-Confidence Downgrade*: Any Tier 1 action with confidence `< 0.65` is automatically downgraded to Tier 2 (requiring confirmation).

---

## 🧪 Verification & Test Suite

All tests execute in automated GitHub Actions cloud CI pipelines across Ubuntu and Windows runners:

- **Core Rust**: 65 unit & integration tests (`CARGO_BUILD_JOBS=2 cargo test --workspace`)
- **Android Agent**: 31 JVM unit tests (`./gradlew testDebugUnitTest`)
- **Golden Evaluation**: 250 evaluation cases (`python evals/eval_runner.py`)
- **Native NDK Verification**: CI confirms `libllama_jni.so` is present in the compiled `arm64-v8a` APK.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
