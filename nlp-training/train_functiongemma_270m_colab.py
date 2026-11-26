#!/usr/bin/env python3
"""
SilentExec v4 Fine-Tuning & GGUF Export Pipeline for Google Colab / Kaggle GPUs.
Fine-tunes `google/functiongemma-270m-it` (<250 MB Q4_K_M GGUF) as an ultra-fast
bilingual (Yemeni Arabic, MSA Arabic, English, Arabizi) Intent-First & Compound-Intent Router.

Usage (on Google Colab T4 or Kaggle T4x2):
  1. python nlp-training/generate_v4_agent_dataset.py --count 2400 --out-dir nlp-training/data_v4
  2. python nlp-training/train_functiongemma_270m_colab.py --data-dir nlp-training/data_v4 --output-dir outputs_270m
"""

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Dict, List


DEFAULT_BASE_MODEL = "google/functiongemma-270m-it"
GGUF_FILENAME = "silentexec-router-270m-q4_k_m.gguf"


def compute_sha256(file_path: Path) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_jsonl(path: Path) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def evaluate_predictions(test_rows: List[Dict[str, Any]], predictions: List[str]) -> Dict[str, Any]:
    """
    Computes JSON validity %, intent accuracy per dialect (msa, yemeni, en, arabizi),
    and batch_actions exact match rate.
    """
    total = len(test_rows)
    valid_json = 0
    intent_correct = 0
    batch_total = 0
    batch_exact = 0
    dialect_stats: Dict[str, Dict[str, int]] = {}

    for row, raw_pred in zip(test_rows, predictions):
        dialect = row.get("dialect", "unknown")
        stats = dialect_stats.setdefault(dialect, {"total": 0, "correct": 0})
        stats["total"] += 1

        expected = row["expected_action"]
        if expected.get("action") == "batch_actions":
            batch_total += 1

        try:
            pred_obj = json.loads(raw_pred.strip())
            if isinstance(pred_obj, dict):
                valid_json += 1
                if pred_obj.get("action") == expected.get("action"):
                    intent_correct += 1
                    stats["correct"] += 1
                if expected.get("action") == "batch_actions" and pred_obj.get("actions") == expected.get("actions"):
                    batch_exact += 1
        except json.JSONDecodeError:
            pass

    return {
        "total": total,
        "json_validity_pct": (valid_json / total * 100.0) if total else 0.0,
        "intent_accuracy_pct": (intent_correct / total * 100.0) if total else 0.0,
        "batch_exact_match_pct": (batch_exact / batch_total * 100.0) if batch_total else 100.0,
        "by_dialect": {
            d: (s["correct"] / s["total"] * 100.0) if s["total"] else 0.0
            for d, s in dialect_stats.items()
        },
    }


def run_colab_training(args: argparse.Namespace) -> None:
    from datasets import Dataset
    from unsloth import FastLanguageModel
    from trl import SFTTrainer
    from transformers import TrainingArguments

    data_dir = Path(args.data_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    train_rows = load_jsonl(data_dir / "train.jsonl")
    val_rows = load_jsonl(data_dir / "val.jsonl")
    test_rows = load_jsonl(data_dir / "test.jsonl")

    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=args.base_model,
        max_seq_length=args.max_seq_length,
        dtype=None,
        load_in_4bit=True,
    )

    model = FastLanguageModel.get_peft_model(
        model,
        r=16,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_alpha=32,
        lora_dropout=0.0,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=42,
    )

    def format_row(row: Dict[str, Any]) -> Dict[str, str]:
        text = tokenizer.apply_chat_template(
            row["messages"],
            tokenize=False,
            add_generation_prompt=False,
        )
        return {"text": text}

    train_ds = Dataset.from_list([format_row(r) for r in train_rows])
    val_ds = Dataset.from_list([format_row(r) for r in val_rows])

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        dataset_text_field="text",
        max_seq_length=args.max_seq_length,
        args=TrainingArguments(
            per_device_train_batch_size=8,
            gradient_accumulation_steps=2,
            warmup_steps=20,
            num_train_epochs=args.epochs,
            learning_rate=2e-4,
            fp16=True,
            logging_steps=10,
            eval_strategy="epoch",
            output_dir=str(output_dir / "checkpoints"),
            report_to="none",
        ),
    )
    trainer.train()

    # Export directly to Q4_K_M GGUF (<250 MB)
    gguf_dir = output_dir / "gguf"
    gguf_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained_gguf(
        str(gguf_dir),
        tokenizer,
        quantization_method="q4_k_m",
    )

    gguf_files = list(gguf_dir.glob("*.gguf"))
    if gguf_files:
        target_gguf = output_dir / GGUF_FILENAME
        gguf_files[0].rename(target_gguf)
        sha = compute_sha256(target_gguf)
        (output_dir / "SHA256SUMS.txt").write_text(f"{sha}  {GGUF_FILENAME}\n", encoding="utf-8")
        print(f"Exported GGUF: {target_gguf} ({target_gguf.stat().st_size / (1024 * 1024):.1f} MB)")
        print(f"SHA256: {sha}")

    # Run evaluation on test split
    FastLanguageModel.for_inference(model)
    predictions: List[str] = []
    for row in test_rows:
        prompt_msgs = row["messages"][:2]
        inputs = tokenizer.apply_chat_template(
            prompt_msgs,
            tokenize=True,
            add_generation_prompt=True,
            return_tensors="pt",
        ).to(model.device)
        out_ids = model.generate(input_ids=inputs, max_new_tokens=192, temperature=0.1)
        gen_text = tokenizer.decode(out_ids[0][inputs.shape[-1] :], skip_special_tokens=True)
        predictions.append(gen_text)

    metrics = evaluate_predictions(test_rows, predictions)
    print("Evaluation Metrics:", json.dumps(metrics, indent=2, ensure_ascii=False))
    (output_dir / "eval_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fine-tune FunctionGemma-270M on Google Colab / Kaggle")
    parser.add_argument("--base-model", type=str, default=DEFAULT_BASE_MODEL)
    parser.add_argument("--data-dir", type=str, default="nlp-training/data_v4")
    parser.add_argument("--output-dir", type=str, default="outputs_270m")
    parser.add_argument("--max-seq-length", type=int, default=1024)
    parser.add_argument("--epochs", type=int, default=3)
    args = parser.parse_args()
    run_colab_training(args)


if __name__ == "__main__":
    main()
