#!/usr/bin/env python3
"""
SilentExec Golden Evaluation Harness
Evaluates model predictions or oracle gold targets against:
1. JSON Schema Draft 2020-12 validity (schemas/action_schema.json)
2. Intent classification accuracy & slot exact matches
3. Deterministic Rust SafetyGate decisions via `silent-exec-cli --eval-stdin`
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional
import jsonschema


def resolve_default_rust_bin() -> Optional[Path]:
    repo_root = Path(__file__).resolve().parent.parent
    candidates = [
        repo_root / "core-rust" / "target" / "release" / "silent-exec-cli.exe",
        repo_root / "core-rust" / "target" / "release" / "silent-exec-cli",
        repo_root / "core-rust" / "target" / "debug" / "silent-exec-cli.exe",
        repo_root / "core-rust" / "target" / "debug" / "silent-exec-cli",
    ]
    for c in candidates:
        if c.is_file():
            return c
    return None


def run_rust_eval_stdin(bin_path: Path, commands: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    input_lines = "\n".join(json.dumps(cmd, ensure_ascii=False) for cmd in commands) + "\n"

    proc = subprocess.Popen(
        [str(bin_path), "--eval-stdin"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8"
    )
    stdout, stderr = proc.communicate(input_lines)
    if proc.returncode != 0:
        raise RuntimeError(f"silent-exec-cli failed with code {proc.returncode}: {stderr}")

    results = []
    for line in stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        results.append(json.loads(line))

    if len(results) != len(commands):
        raise ValueError(
            f"silent-exec-cli returned {len(results)} outputs for {len(commands)} inputs"
        )
    return results


def evaluate(
    gold_path: Path,
    preds_path: Optional[Path],
    oracle_check: bool,
    rust_bin: Optional[Path],
    out_report_path: Optional[Path],
    schema_path: Path
) -> int:
    with open(schema_path, "r", encoding="utf-8") as f:
        action_schema = json.load(f)
    schema_validator = jsonschema.Draft202012Validator(action_schema)

    # Load golden records
    gold_records: List[Dict[str, Any]] = []
    with open(gold_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                gold_records.append(json.loads(line))

    total = len(gold_records)
    print(f"Loaded {total} golden evaluation test cases from {gold_path.name}.")

    # Load or generate predictions
    if oracle_check:
        print("Running in --oracle-check mode (evaluating ground truth against schema & Rust SafetyGate)...")
        predictions = [r["expected_json"] for r in gold_records]
    elif preds_path:
        print(f"Loading predictions from {preds_path}...")
        predictions = []
        with open(preds_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    predictions.append(json.loads(line))
        if len(predictions) != total:
            print(f"ERROR: Expected {total} predictions, found {len(predictions)}", file=sys.stderr)
            return 1
    else:
        print("ERROR: Either --preds <file> or --oracle-check must be specified", file=sys.stderr)
        return 1

    # Step 1: Rust Safety Gate Evaluation
    rust_eval_results: Optional[List[Dict[str, Any]]] = None
    if rust_bin and rust_bin.is_file():
        print(f"Executing real Rust SafetyGate evaluation using {rust_bin}...")
        rust_eval_results = run_rust_eval_stdin(rust_bin, predictions)
    else:
        print("WARNING: No valid --rust-bin provided; skipping real Rust SafetyGate evaluation.")

    # Step 2: Metric Computation
    valid_json_count = 0
    intent_correct_count = 0
    slot_match_count = 0
    safety_correct_count = 0

    lang_stats: Dict[str, Dict[str, int]] = {
        "yemeni_ar": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0},
        "msa_ar": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0},
        "en_mixed": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0},
    }

    detailed_mismatches = []

    for idx, (gold, pred) in enumerate(zip(gold_records, predictions)):
        lang = gold.get("lang", "en_mixed")
        if lang not in lang_stats:
            lang_stats[lang] = {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0}
        lang_stats[lang]["total"] += 1

        # Check JSON Schema Validity (note: confidence bounds can be checked by schema)
        is_schema_valid = schema_validator.is_valid(pred)
        # However, for out-of-bounds confidence test cases in adversarial, schema correctly rejects or rust blocks
        if is_schema_valid:
            valid_json_count += 1
            lang_stats[lang]["valid_json"] += 1

        # Check Intent Accuracy
        pred_action = pred.get("action")
        intent_match = (pred_action == gold["expected_action"])
        if intent_match:
            intent_correct_count += 1
            lang_stats[lang]["intent_correct"] += 1

        # Check Slot Exact Match
        exp_json = gold["expected_json"]
        slot_match = (
            pred.get("action") == exp_json.get("action") and
            pred.get("target") == exp_json.get("target") and
            pred.get("folder") == exp_json.get("folder") and
            pred.get("args") == exp_json.get("args") and
            pred.get("platform") == exp_json.get("platform")
        )
        if slot_match:
            slot_match_count += 1
            lang_stats[lang]["slot_match"] += 1

        # Check Rust Safety Decision
        safety_match = False
        if rust_eval_results:
            rust_res = rust_eval_results[idx]
            actual_decision = rust_res.get("decision")
            expected_decision = gold["expected_safety_decision"]
            safety_match = (actual_decision == expected_decision)
            if safety_match:
                safety_correct_count += 1
                lang_stats[lang]["safety_correct"] += 1
            else:
                detailed_mismatches.append({
                    "id": gold["id"],
                    "prompt": gold["prompt"],
                    "expected_decision": expected_decision,
                    "actual_decision": actual_decision,
                    "reason": rust_res.get("reason")
                })

    json_validity_rate = valid_json_count / total * 100
    intent_accuracy = intent_correct_count / total * 100
    slot_exact_match = slot_match_count / total * 100
    safety_accuracy = (safety_correct_count / total * 100) if rust_eval_results else None

    # Print Report
    print("\n" + "=" * 65)
    print("SILENTEXEC GOLDEN EVALUATION BENCHMARK REPORT")
    print("=" * 65)
    print(f"Total Test Cases:            {total}")
    print(f"JSON Validity Rate:          {json_validity_rate:.2f}% ({valid_json_count}/{total})")
    print(f"Intent Classification Acc:   {intent_accuracy:.2f}% ({intent_correct_count}/{total})")
    print(f"Slot Exact Match Rate:       {slot_exact_match:.2f}% ({slot_match_count}/{total})")
    if safety_accuracy is not None:
        print(f"Safety Gate Decision Acc:    {safety_accuracy:.2f}% ({safety_correct_count}/{total})")

    print("\n" + "-" * 65)
    print("PER-LANGUAGE PERFORMANCE BREAKDOWN")
    print("-" * 65)
    for lang, s in sorted(lang_stats.items()):
        l_tot = s["total"]
        if l_tot == 0:
            continue
        l_intent = s["intent_correct"] / l_tot * 100
        l_slot = s["slot_match"] / l_tot * 100
        l_safety = (s["safety_correct"] / l_tot * 100) if rust_eval_results else 0.0
        print(f"[{lang.upper()}] (n={l_tot})")
        print(f"   Intent Acc: {l_intent:.1f}% | Slot Exact: {l_slot:.1f}% | Safety Decision Acc: {l_safety:.1f}%")
    print("=" * 65 + "\n")

    if detailed_mismatches:
        print(f"Found {len(detailed_mismatches)} safety decision mismatches:")
        for m in detailed_mismatches[:5]:
            print(f"  ID {m['id']}: Expected '{m['expected_decision']}', got '{m['actual_decision']}' (Reason: {m['reason']})")
        if len(detailed_mismatches) > 5:
            print(f"  ... and {len(detailed_mismatches) - 5} more.")

    # Save report
    report_data = {
        "total_test_cases": total,
        "json_validity_rate": json_validity_rate,
        "intent_accuracy": intent_accuracy,
        "slot_exact_match": slot_exact_match,
        "safety_decision_accuracy": safety_accuracy,
        "language_breakdown": lang_stats,
        "rust_binary_used": str(rust_bin) if rust_bin else None
    }

    if out_report_path:
        out_report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(out_report_path, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        print(f"Evaluation report saved to: {out_report_path}")

    # For oracle-check, require 100% intent, slot, and safety accuracy
    if oracle_check:
        if intent_accuracy < 100.0 or slot_exact_match < 100.0:
            print("FAILED: Oracle check failed on intent or slot matching!", file=sys.stderr)
            return 1
        if rust_eval_results and safety_accuracy < 100.0:
            print("FAILED: Oracle check failed on Rust SafetyGate decisions!", file=sys.stderr)
            return 1

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="SilentExec Golden Benchmark Evaluation Harness")
    parser.add_argument("--gold", type=str, default="evals/golden_eval.jsonl", help="Path to golden_eval.jsonl")
    parser.add_argument("--preds", type=str, default=None, help="Path to model predictions jsonl")
    parser.add_argument("--oracle-check", action="store_true", help="Evaluate ground truth directly (sanity check)")
    parser.add_argument("--rust-bin", type=str, default=None, help="Path to silent-exec-cli binary for SafetyGate eval")
    parser.add_argument("--out", type=str, default="evals/last_eval_report.json", help="Path to output report JSON")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    gold_path = Path(args.gold)
    if not gold_path.is_absolute():
        gold_path = repo_root / gold_path

    schema_path = repo_root / "schemas" / "action_schema.json"
    preds_path = Path(args.preds) if args.preds else None

    rust_bin = Path(args.rust_bin) if args.rust_bin else resolve_default_rust_bin()
    out_path = Path(args.out)
    if not out_path.is_absolute():
        out_path = repo_root / out_path

    return evaluate(
        gold_path=gold_path,
        preds_path=preds_path,
        oracle_check=args.oracle_check,
        rust_bin=rust_bin,
        out_report_path=out_path,
        schema_path=schema_path
    )


if __name__ == "__main__":
    sys.exit(main())
