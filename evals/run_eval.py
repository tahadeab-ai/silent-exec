#!/usr/bin/env python3
"""
SilentExec Golden Evaluation Harness
Evaluates model predictions or oracle gold targets against:
1. JSON Schema Draft 2020-12 validity (schemas/action_schema.json)
2. Intent classification accuracy & slot exact matches
3. Deterministic Rust SafetyGate decisions via `silent-exec-cli --eval-stdin`
"""

import argparse
import collections
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import jsonschema


def normalize_slot_val(val: Any) -> Any:
    """Normalize slot values for whitespace and case-insensitive comparison."""
    if val is None:
        return None
    if isinstance(val, str):
        return val.strip().lower()
    if isinstance(val, (int, float, bool)):
        return val
    if isinstance(val, dict):
        return {str(k).strip().lower(): normalize_slot_val(v) for k, v in val.items()}
    if isinstance(val, list):
        return [normalize_slot_val(x) for x in val]
    return val


def extract_slots(record: Dict[str, Any]) -> Dict[str, Any]:
    """Extract and normalize all slots from an ActionCommand record."""
    slots: Dict[str, Any] = {}
    if "target" in record and record["target"] is not None:
        slots["target"] = normalize_slot_val(record["target"])
    if "folder" in record and record["folder"] is not None:
        slots["folder"] = normalize_slot_val(record["folder"])
    if "platform" in record and record["platform"] is not None:
        slots["platform"] = normalize_slot_val(record["platform"])
    if "args" in record and isinstance(record["args"], dict):
        for arg_k, arg_v in record["args"].items():
            slots[f"args.{arg_k.strip().lower()}"] = normalize_slot_val(arg_v)
    return slots


def compute_slot_counts(expected: Dict[str, Any], predicted: Dict[str, Any]) -> Tuple[int, int, int]:
    """Compute (tp, fp, fn) for slot matching between expected and predicted slots."""
    exp_slots = extract_slots(expected)
    pred_slots = extract_slots(predicted)

    all_keys = set(exp_slots.keys()) | set(pred_slots.keys())
    tp = 0
    fp = 0
    fn = 0

    for k in all_keys:
        if k in exp_slots and k in pred_slots:
            if exp_slots[k] == pred_slots[k]:
                tp += 1
            else:
                fp += 1
                fn += 1
        elif k in exp_slots:
            fn += 1
        elif k in pred_slots:
            fp += 1

    return tp, fp, fn


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
    total_slot_tp = 0
    total_slot_fp = 0
    total_slot_fn = 0

    error_category_counts: Dict[str, int] = collections.defaultdict(int)

    lang_stats: Dict[str, Dict[str, int]] = {
        "yemeni_ar": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0, "slot_tp": 0, "slot_fp": 0, "slot_fn": 0},
        "msa_ar": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0, "slot_tp": 0, "slot_fp": 0, "slot_fn": 0},
        "en_mixed": {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0, "slot_tp": 0, "slot_fp": 0, "slot_fn": 0},
    }

    detailed_mismatches = []

    for idx, (gold, pred) in enumerate(zip(gold_records, predictions)):
        lang = gold.get("lang", "en_mixed")
        if lang not in lang_stats:
            lang_stats[lang] = {"total": 0, "valid_json": 0, "intent_correct": 0, "slot_match": 0, "safety_correct": 0, "slot_tp": 0, "slot_fp": 0, "slot_fn": 0}
        lang_stats[lang]["total"] += 1

        # Check JSON Schema Validity (note: confidence bounds can be checked by schema)
        is_schema_valid = schema_validator.is_valid(pred)
        if is_schema_valid:
            valid_json_count += 1
            lang_stats[lang]["valid_json"] += 1
        else:
            error_category_counts["Schema Invalid"] += 1

        # Check Intent Accuracy
        pred_action = pred.get("action")
        intent_match = (pred_action == gold["expected_action"])
        if intent_match:
            intent_correct_count += 1
            lang_stats[lang]["intent_correct"] += 1
        else:
            error_category_counts["Intent Mismatch"] += 1

        # Check Slot Exact Match & Slot F1
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

        # Field-level normalized Slot F1 computation
        tp, fp, fn = compute_slot_counts(exp_json, pred)
        total_slot_tp += tp
        total_slot_fp += fp
        total_slot_fn += fn
        lang_stats[lang]["slot_tp"] += tp
        lang_stats[lang]["slot_fp"] += fp
        lang_stats[lang]["slot_fn"] += fn

        # Granular slot mismatch error category tracking
        exp_slots = extract_slots(exp_json)
        pred_slots = extract_slots(pred)
        if exp_slots.get("target") != pred_slots.get("target"):
            error_category_counts["Target Mismatch"] += 1
        if exp_slots.get("folder") != pred_slots.get("folder"):
            error_category_counts["Folder Mismatch"] += 1
        if exp_slots.get("platform") != pred_slots.get("platform"):
            error_category_counts["Platform Mismatch"] += 1
        exp_args = {k: v for k, v in exp_slots.items() if k.startswith("args.")}
        pred_args = {k: v for k, v in pred_slots.items() if k.startswith("args.")}
        if exp_args != pred_args:
            error_category_counts["Args Mismatch"] += 1

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
                error_category_counts["Safety Decision Mismatch"] += 1
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

    # Calculate global Slot F1
    global_p = total_slot_tp / (total_slot_tp + total_slot_fp) if (total_slot_tp + total_slot_fp) > 0 else 1.0
    global_r = total_slot_tp / (total_slot_tp + total_slot_fn) if (total_slot_tp + total_slot_fn) > 0 else 1.0
    slot_f1 = (2 * global_p * global_r / (global_p + global_r) * 100.0) if (global_p + global_r) > 0 else 100.0

    safety_accuracy = (safety_correct_count / total * 100) if rust_eval_results else None

    # Top 5 Error Categories
    sorted_errors = sorted(error_category_counts.items(), key=lambda x: x[1], reverse=True)
    top_5_errors = [
        {"category": cat, "count": cnt, "percentage": round(cnt / total * 100, 2)}
        for cat, cnt in sorted_errors[:5]
    ]

    # Print Report
    print("\n" + "=" * 65)
    print("SILENTEXEC GOLDEN EVALUATION BENCHMARK REPORT")
    print("=" * 65)
    print(f"Total Test Cases:            {total}")
    print(f"JSON Validity Rate:          {json_validity_rate:.2f}% ({valid_json_count}/{total})")
    print(f"Intent Classification Acc:   {intent_accuracy:.2f}% ({intent_correct_count}/{total})")
    print(f"Slot Exact Match Rate:       {slot_exact_match:.2f}% ({slot_match_count}/{total})")
    print(f"Slot Normalized F1 Score:    {slot_f1:.2f}% (P: {global_p * 100:.1f}%, R: {global_r * 100:.1f}%)")
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
        l_tp, l_fp, l_fn = s["slot_tp"], s["slot_fp"], s["slot_fn"]
        l_p = l_tp / (l_tp + l_fp) if (l_tp + l_fp) > 0 else 1.0
        l_r = l_tp / (l_tp + l_fn) if (l_tp + l_fn) > 0 else 1.0
        l_f1 = (2 * l_p * l_r / (l_p + l_r) * 100.0) if (l_p + l_r) > 0 else 100.0
        s["slot_f1"] = round(l_f1, 2)
        l_safety = (s["safety_correct"] / l_tot * 100) if rust_eval_results else 0.0
        print(f"[{lang.upper()}] (n={l_tot})")
        print(f"   Intent Acc: {l_intent:.1f}% | Slot Exact: {l_slot:.1f}% | Slot F1: {l_f1:.1f}% | Safety Acc: {l_safety:.1f}%")
    print("=" * 65)

    if top_5_errors:
        print("\n" + "-" * 65)
        print("TOP ERROR CATEGORIES (DIAGNOSTICS)")
        print("-" * 65)
        for rank, err in enumerate(top_5_errors, 1):
            print(f"  {rank}. {err['category']:<26}: {err['count']} occurrences ({err['percentage']:.1f}% of tests)")
        print("-" * 65 + "\n")

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
        "slot_f1": round(slot_f1, 2),
        "safety_decision_accuracy": safety_accuracy,
        "top_error_categories": top_5_errors,
        "language_breakdown": lang_stats,
        "rust_binary_used": str(rust_bin) if rust_bin else None
    }

    if out_report_path:
        out_report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(out_report_path, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2, ensure_ascii=False)
        print(f"Evaluation report saved to: {out_report_path}")

    # For oracle-check, require 100% intent, slot exact, slot f1, and safety accuracy
    if oracle_check:
        if intent_accuracy < 100.0 or slot_exact_match < 100.0:
            print("FAILED: Oracle check failed on intent or slot matching!", file=sys.stderr)
            return 1
        if slot_f1 < 100.0:
            print("FAILED: Oracle check failed on slot F1 score!", file=sys.stderr)
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
