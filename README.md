# SilentExec

[![CI](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml/badge.svg)](https://github.com/tahadeab-ai/silent-exec/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SilentExec** is a lightweight, zero-chatter, action-only OS execution agent designed for PC (Windows & Linux) and Android. Rather than generating conversational filler or multi-paragraph chit-chat, SilentExec maps multilingual user intent (Arabic and English) directly to deterministic, strictly schema-validated system actions.

---

## ⚡ Core Philosophy & Architecture

1. **Zero-Chatter Execution**: LLMs run constrained with GBNF grammars, producing only valid `ActionCommand` JSON payloads.
2. **Deterministic Safety Gating**: A high-performance Rust safety engine verifies all actions, blocking path traversal, shell injection, and sensitive directory access before execution.
3. **Cross-Platform Modular Design**:
   - **`core-rust/`**: High-performance core library and safety gate with serde validation.
   - **`android-agent/`**: Native Kotlin execution engine for Android with accessibility/system integration.
   - **`nlp-training/`**: QLoRA fine-tuning recipes for edge LLMs (e.g., Llama 3 / Qwen) optimized on Google Colab.
   - **`evals/`**: Dry-run evaluation harness for measuring action accuracy, latency, and safety gate coverage.
   - **`schemas/`**: Single Source of Truth JSON Schema (Draft 2020-12) and llama.cpp GBNF grammar.

---

## 📦 Binaries & Model Weights Distribution Policy

> [!IMPORTANT]
> **No binaries or model weights are stored in this Git repository.**
> To keep the repository lightweight and developer-friendly:
> - **Precompiled Executables (`.exe`, `.apk`, Linux binaries)** are published exclusively via [GitHub Releases](https://github.com/tahadeab-ai/silent-exec/releases).
> - **Quantized Model Weights (`*.gguf`, `*.onnx`)** are attached as assets to GitHub Releases or hosted on Hugging Face model cards linked in release notes.
>
> Download release artifacts via GitHub CLI:
> ```bash
> gh release download -R tahadeab-ai/silent-exec --pattern "*.exe"
> ```

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
