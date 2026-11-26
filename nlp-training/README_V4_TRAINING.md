# SilentExec v4 Fine-Tuning Guide (`FunctionGemma-270M`)

This pipeline fine-tunes `google/functiongemma-270m-it` (<250 MB `Q4_K_M` GGUF) as SilentExec's ultra-fast bilingual (**Yemeni Arabic, MSA Arabic, English, Arabizi**) Intent-First & Compound-Intent Router on **Google Colab (T4)** or **Kaggle (T4x2)**.

> **Note:** Never run GPU training on a local 16 GB Windows PC; run these scripts on Colab or Kaggle GPUs.

## 1. Install Dependencies (Colab / Kaggle)

```bash
pip install unsloth trl datasets jsonschema
```

## 2. Generate Schema-Validated Bilingual Dataset

```bash
python nlp-training/generate_v4_agent_dataset.py --count 2400 --out-dir nlp-training/data_v4
```

All generated samples use the compact Mobilerun-style `phone_state` + `a11y_tree [0..N]` format and `batch_actions`, and are strictly validated against `schemas/action_schema.json`.

## 3. Fine-Tune & Export `Q4_K_M` GGUF

```bash
python nlp-training/train_functiongemma_270m_colab.py \
  --data-dir nlp-training/data_v4 \
  --output-dir outputs_270m \
  --epochs 3
```

Outputs:
- `outputs_270m/silentexec-router-270m-q4_k_m.gguf` (<250 MB)
- `outputs_270m/SHA256SUMS.txt`
- `outputs_270m/eval_metrics.json` (JSON validity, per-dialect intent accuracy, `batch_actions` exact match)

## 4. Publish to GitHub Releases

```bash
gh release upload v4.0.0-model outputs_270m/silentexec-router-270m-q4_k_m.gguf outputs_270m/SHA256SUMS.txt
```
