# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/silent-exec/blob/main/nlp-training/train_silent_exec_qlora.ipynb)
[![Model Release](https://img.shields.io/badge/Model_Release-v0.2.0--model-brightgreen.svg)](https://github.com/tahadeab-ai/silent-exec/releases/tag/v0.2.0-model)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a lightweight, zero-chatter, action-only OS execution agent designed for PC (Windows & Linux) and Android. Rather than generating conversational filler or multi-paragraph chit-chat, SilentExec maps multilingual user intent (Arabic and English) directly to deterministic, strictly schema-validated system actions.

---

## ⚡ Core Philosophy & Architecture

1. **Zero-Chatter Execution**: LLMs run constrained with GBNF grammars, producing only valid `ActionCommand` JSON payloads.
2. **Deterministic Safety Gating**: A high-performance Rust safety engine verifies all actions, blocking path traversal, shell injection, and sensitive directory access before execution.
3. **Cross-Platform Modular Design**:
   - **`core-rust/`**: High-performance core library and safety gate with serde validation.
   - **`android-agent/`**: Native Kotlin execution engine for Android with accessibility/system integration.
   - **`nlp-training/`**: QLoRA fine-tuning recipes for edge LLMs optimized on Google Colab.
   - **`evals/`**: Dry-run evaluation harness for measuring action accuracy, latency, and safety gate coverage.
   - **`schemas/`**: Single Source of Truth JSON Schema (Draft 2020-12) and llama.cpp GBNF grammar.

---

## 📦 Binaries & Model Weights Distribution Policy

> [!IMPORTANT]
> **No binaries or model weights are stored in this Git repository.**
> To keep the repository lightweight and developer-friendly:
> - **Precompiled Executables (`.exe`, `.apk`, Linux binaries)** are published exclusively via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).
> - **Quantized Model Weights (`*.gguf`)** are distributed directly via GitHub Releases (`gh release download`).

Download the latest recommended quantized model (`v0.2.0-model`, Qwen2.5-1.5B):
```bash
gh release download v0.2.0-model -R tahadeab-ai/silent-exec --pattern "*.gguf"
```
Or download the ultra-compact baseline model (`v0.1.0-model`, Qwen2.5-0.5B):
```bash
gh release download v0.1.0-model -R tahadeab-ai/silent-exec --pattern "*.gguf"
```

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

SilentExec enforces a 3-tier safety policy:

| Tier | Name | Behavior | Actions |
| :--- | :--- | :--- | :--- |
| **Tier 1** | `tier_1_silent` | Direct execution without prompt | `open_folder`, `open_file`, `launch_app`, `search_files`, `set_volume`, `set_brightness`, `create_note`, `copy_to_clipboard`, `open_android_app`, `open_url` |
| **Tier 2** | `tier_2_confirm` | Interactive confirmation required | `delete_file`, `move_file`, `system_power`, `toggle_setting`, `send_message` |
| **Tier 3** | `tier_3_blocked` | Hard-blocked; cannot be executed | Path traversal (`..`), system directories (`C:\Windows`, `/etc`, `/system`), shell injection (`&&`, `;`, `\|`, `$()`, ``` ` ```) |

*Low-Confidence Safety Downgrade*: Any Tier 1 action with confidence `< 0.65` is automatically downgraded to Tier 2 (requiring confirmation).

---

## 🚀 Building & Testing Core Rust

```bash
# Clone the repository
git clone https://github.com/tahadeab-ai/silent-exec.git
cd silent-exec/core-rust

# Run tests with resource caps
CARGO_BUILD_JOBS=2 cargo test

# Run linter and format checks
CARGO_BUILD_JOBS=2 cargo clippy -- -D warnings
cargo fmt --check
```

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
