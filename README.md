# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Android CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/android-ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![PC Release](https://img.shields.io/badge/PC_Release-v0.1.0--pc-blue.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-pc)
[![Android Release](https://img.shields.io/badge/Android_Release-v0.1.0--android-green.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a lightweight, zero-chatter, action-only OS execution agent designed for PC (Windows & Linux) and Android. Rather than generating conversational filler or multi-paragraph chit-chat, SilentExec maps multilingual user intent (Arabic and English) directly to deterministic, strictly schema-validated system actions.

---

## ⚡ Core Philosophy & Architecture

1. **Zero-Chatter Execution**: LLMs run constrained with GBNF grammars, producing only valid `ActionCommand` JSON payloads.
2. **Deterministic Safety Gating**: A high-performance Rust & Kotlin safety engine verifies all actions, blocking path traversal, shell injection, and sensitive directory access before execution.
3. **Cross-Platform Modular Design**:
   - **`core-rust/`**: High-performance core library, safe PC executor, sub-15ms Arabic/English fuzzy indexer, and authenticated LAN bridge server.
   - **`android-agent/`**: Native Kotlin execution engine for Android with accessibility/system integration and LAN client.
   - **`nlp-training/`**: QLoRA fine-tuning recipes for edge LLMs optimized on Google Colab with Unsloth.
   - **`evals/`**: Dry-run evaluation harness for measuring action accuracy, latency, and safety gate coverage.
   - **`schemas/`**: Single Source of Truth JSON Schema (Draft 2020-12) and llama.cpp GBNF grammar.

---

## 📦 Downloads & Official Releases

> [!IMPORTANT]
> **No precompiled binaries or model weights are stored in this Git repository.**
> All deliverables are built in automated GitHub Actions cloud pipelines and distributed exclusively via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).

| Component | Target Platform | Release Tag | Asset Name | Size | SHA-256 Checksum | Quick Download Command |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PC Execution Agent** | Windows x86_64 | [`v0.1.0-pc`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-pc) | `silent-exec-pc.exe` | **747 KB** | `bb00b123...` | `gh release download v0.1.0-pc -R tahadeab-ai/silent-exec -p "silent-exec-pc.exe"` |
| **Android Execution Agent** | Android (`arm64-v8a`) | [`v0.1.0-android`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-android) | `silent-exec-android-arm64.apk` | **6.15 MB** | `4634cc38...` | `gh release download v0.1.0-android -R tahadeab-ai/silent-exec -p "*.apk"` |
| **Edge NLP Model (Recommended)** | GGUF (`q4_k_m`) | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) | `Qwen2.5-1.5B-Instruct.Q4_K_M.gguf` | **940.37 MB** | `e6368226...` | `gh release download v0.2.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |
| **Edge NLP Model (Baseline)** | GGUF (`q4_k_m`) | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) | `Qwen2.5-0.5B-Instruct.Q4_K_M.gguf` | **379.38 MB** | `5dd97b12...` | `gh release download v0.1.0-model -R tahadeab-ai/silent-exec -p "*.gguf"` |

---

## 📊 Benchmark Results (`v0.1.0-model` vs `v0.2.0-model`)

Evaluated on the 250-test-case Golden Benchmark ([evals/golden_eval.jsonl](evals/golden_eval.jsonl)):

| Model | Format | Size | JSON Validity | Intent Accuracy | Slot Exact Match | Slot F1 | Release |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Qwen2.5-0.5B-Instruct** | GGUF (`q4_k_m`) | 379.38 MB | **97.20%** | **95.20%** | **44.00%** | — | [`v0.1.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.1.0-model) |
| **Qwen2.5-1.5B-Instruct** | GGUF (`q4_k_m`) | 940.37 MB | **98.80%** | **98.00%** | **60.80%** | **78.90%** | [`v0.2.0-model`](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model) |

### Per-Language Breakdown (`v0.2.0-model`)
- **English & Code-Switching (`en_mixed`)**: Intent **100.0%** (84/84), Slot Exact **72.62%** (61/84), Slot F1 **87.92%**
- **Colloquial Yemeni Arabic (`yemeni_ar`)**: Intent **95.24%** (80/84), Slot Exact **53.57%** (45/84), Slot F1 **74.36%**
- **Modern Standard Arabic (`msa_ar`)**: Intent **98.78%** (81/82), Slot Exact **56.10%** (46/82), Slot F1 **74.35%**

*Full evaluation reports available at [evals/results/v0.1.0_qwen2.5_0.5b_metrics.json](evals/results/v0.1.0_qwen2.5_0.5b_metrics.json) and [evals/results/v0.2.0_qwen2.5_1.5b_metrics.json](evals/results/v0.2.0_qwen2.5_1.5b_metrics.json).*

---

## 🤖 Training Your Own Edge Model on Google Colab

You can fine-tune a model on a free or Pro T4 GPU using our one-click training notebook:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)

### Automatic GitHub Release Uploads
To allow the Colab notebook to automatically publish `.gguf` weights and evaluation reports to GitHub Releases:
1. Open Google Colab and click on the **Secrets (Key icon)** in the left sidebar.
2. Add a new secret named `GITHUB_TOKEN`.
3. Paste a GitHub Personal Access Token (classic with `repo` scope, or fine-grained with `contents:write` on `tahadeab-ai/silent-exec`).
4. Toggle "Notebook access" to ON.

---

## 🛡️ Action Safety Tiers

SilentExec enforces a deterministic 3-tier safety policy:

| Tier | Name | Behavior | Actions |
| :--- | :--- | :--- | :--- |
| **Tier 1** | `tier_1_silent` | Direct execution without prompt | `open_folder`, `open_file`, `launch_app`, `search_files`, `set_volume`, `set_brightness`, `create_note`, `copy_to_clipboard`, `open_android_app`, `open_url` |
| **Tier 2** | `tier_2_confirm` | Interactive confirmation required | `delete_file`, `move_file`, `system_power`, `toggle_setting`, `send_message` |
| **Tier 3** | `tier_3_blocked` | Hard-blocked; cannot be executed | Path traversal (`..`), system directories (`C:\Windows`, `/etc`, `/system`), shell injection (`&&`, `;`, `\|`, `$()`, ``` ` ```) |

*Low-Confidence Safety Downgrade*: Any Tier 1 action with confidence `< 0.65` is automatically downgraded to Tier 2 (requiring confirmation).

---

## 🚀 Building & Testing

### Core Rust (`core-rust/`)
```bash
cd core-rust

# Run 50 unit and integration tests
CARGO_BUILD_JOBS=2 cargo test --workspace

# Run linter and formatting checks
CARGO_BUILD_JOBS=2 cargo clippy --workspace -- -D warnings
cargo fmt --all --check
```

### PC Agent CLI Usage (`silent-exec-pc`)
```bash
# 1. End-to-end natural language run (Arabic or English)
silent-exec-pc run "افتح مجلد التنزيلات" --dry-run

# 2. Zero-chatter stdin quick loop (outputs: ✓ <action> / ? confirm / ✗ blocked)
silent-exec-pc quick --dry-run

# 3. Authenticated LAN Bridge for mobile and network execution
silent-exec-pc serve --host 0.0.0.0 --port 8765 --token my-secret-token --dry-run

# 4. Download latest fine-tuned Qwen GGUF model from GitHub Releases
silent-exec-pc download-model --tag v0.2.0-model
```

### Android Agent (`android-agent/`)
```bash
cd android-agent

# Run JVM Unit Tests
./gradlew testDebugUnitTest

# Build arm64-v8a debug APK
./gradlew assembleDebug

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
