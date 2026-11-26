#!/usr/bin/env python3
"""
SilentExec v4 Bilingual Dataset Generator (MSA Arabic, Yemeni Arabic, English, Arabizi)
Generates screen-aware ReAct trajectories (Mobilerun phone_state + a11y_tree [0..N])
and compound/single intent samples, strictly validated against schemas/action_schema.json.
"""

import argparse
import json
import random
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple
import jsonschema

SYSTEM_PROMPT = (
    "You are SilentExec, a fast deterministic OS & Mobile ReAct agent. "
    "Emit ONLY valid ActionCommand JSON matching schemas/action_schema.json. "
    "Use batch_actions (up to 4 atomic UI steps) for fast multi-step UI sequences. "
    "Use #index from a11y_tree for ui_click and ui_type."
)

# Dialect-tagged single & compound command templates: (dialect, user_prompt, phone_state, a11y_tree, expected_json)
def build_templates() -> List[Tuple[str, str, Dict[str, Any], List[str], Dict[str, Any]]]:
    samples: List[Tuple[str, str, Dict[str, Any], List[str], Dict[str, Any]]] = []

    # 1. Yemeni Arabic (شغل، دوّر، افتح، ارسل، احفظ، بعدين)
    yemeni_apps = [
        ("شغل اليوتيوب ودوّر على كورس بايثون", "com.google.android.youtube", "https://www.youtube.com/results?search_query=python+course"),
        ("افتح الواتس وارسل لخالد السلام عليكم", "com.whatsapp", None),
        ("شغل التليجرام وشوف الرسائل", "org.telegram.messenger", None),
        ("افتح الاعدادات وشغل الواي فاي", "com.android.settings", None),
        ("شغل الخرائط ودوّر على أقرب صيدلية", "com.google.android.apps.maps", None),
    ]
    for prompt, pkg, yt_url in yemeni_apps:
        ps = {"packageName": "com.android.launcher", "activityName": "Launcher", "keyboardVisible": False, "isSecureScreen": False}
        tree = ["[0] button: YouTube [140,420]", "[1] button: WhatsApp [380,420]", "[2] button: Settings [620,420]"]
        if yt_url:
            cmd = {
                "thought": "Open YouTube search directly via deep link",
                "action": "open_url",
                "target": yt_url,
                "platform": "android",
                "confidence": 0.96,
            }
        elif pkg == "com.whatsapp":
            cmd = {
                "thought": "Sensitive messaging action requires user confirmation",
                "action": "send_message",
                "target": "خالد: السلام عليكم",
                "platform": "android",
                "confidence": 0.92,
            }
        else:
            cmd = {
                "thought": f"Launch {pkg} natively",
                "action": "open_android_app",
                "target": pkg,
                "platform": "android",
                "confidence": 0.96,
            }
        samples.append(("yemeni", prompt, ps, tree, cmd))

    # 2. MSA Arabic (افتح، ابحث، اقرأ، اكتب، انزل، ارجع)
    msa_cases = [
        (
            "افتح ملف التقرير واقرأ محتواه",
            {"packageName": "pc.desktop", "activityName": "Workspace", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] file: report.md [120,200]"],
            {"thought": "Read report.md directly using read_file", "action": "read_file", "target": "report.md", "platform": "pc", "confidence": 0.95},
        ),
        (
            "اكتب ملخص الاجتماع في ملف summary.md",
            {"packageName": "pc.desktop", "activityName": "Workspace", "keyboardVisible": False, "isSecureScreen": False},
            [],
            {"thought": "Write summary to summary.md", "action": "write_file", "target": "summary.md", "args": {"content": "# Meeting Summary"}, "platform": "pc", "confidence": 0.91},
        ),
        (
            "انزل للأسفل في الصفحة",
            {"packageName": "com.android.chrome", "activityName": "Main", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] list: Feed [540,900]"],
            {"thought": "Scroll down the active list", "action": "ui_scroll", "target": "down", "platform": "android", "confidence": 0.97},
        ),
        (
            "ابحث في الويب عن أحدث إصدار Rust",
            {"packageName": "com.android.chrome", "activityName": "Main", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] input: Search [540,180]"],
            {"thought": "Open Google search for latest Rust release", "action": "open_url", "target": "https://www.google.com/search?q=latest+Rust+release", "platform": "any", "confidence": 0.95},
        ),
    ]
    for prompt, ps, tree, cmd in msa_cases:
        samples.append(("msa", prompt, ps, tree, cmd))

    # 3. English & batch_actions (Mobile-MCP 1-turn batched sequences)
    batch_cases = [
        (
            "en",
            "Click search bar, type nlp, and submit",
            {"packageName": "com.google.android.youtube", "activityName": "HomeActivity", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] button: Search [920,120]", "[1] input: Search YouTube [450,120]", "[2] button: Submit [960,120]"],
            {
                "thought": "Tap search, type nlp, and tap submit in one batch",
                "action": "batch_actions",
                "actions": [
                    {"action": "ui_click", "target": "#0", "platform": "android", "confidence": 0.96},
                    {"action": "ui_type", "target": "nlp", "platform": "android", "confidence": 0.96},
                    {"action": "ui_click", "target": "#2", "platform": "android", "confidence": 0.94},
                ],
                "platform": "android",
                "confidence": 0.95,
            },
        ),
        (
            "yemeni",
            "اضغط خانة البحث واكتب أخبار صنعاء واضغط بحث",
            {"packageName": "com.android.chrome", "activityName": "ChromeTabbedActivity", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] input: Search or type URL [480,140]", "[1] button: Go [940,140]"],
            {
                "thought": "Focus search input, type query, and tap Go",
                "action": "batch_actions",
                "actions": [
                    {"action": "ui_click", "target": "#0", "platform": "android", "confidence": 0.96},
                    {"action": "ui_type", "target": "أخبار صنعاء", "platform": "android", "confidence": 0.95},
                    {"action": "ui_click", "target": "#1", "platform": "android", "confidence": 0.94},
                ],
                "platform": "android",
                "confidence": 0.95,
            },
        ),
        (
            "arabizi",
            "afta7 youtube w dawer 3ala flutter tutorial",
            {"packageName": "com.android.launcher", "activityName": "Launcher", "keyboardVisible": False, "isSecureScreen": False},
            ["[0] button: YouTube [200,500]"],
            {
                "thought": "Open YouTube search for flutter tutorial",
                "action": "open_url",
                "target": "https://www.youtube.com/results?search_query=flutter+tutorial",
                "platform": "android",
                "confidence": 0.94,
            },
        ),
        (
            "en",
            "Read config.json from workspace",
            {"packageName": "pc.desktop", "activityName": "Editor", "keyboardVisible": False, "isSecureScreen": False},
            [],
            {"thought": "Read config.json directly", "action": "read_file", "target": "config.json", "platform": "pc", "confidence": 0.97},
        ),
    ]
    samples.extend(batch_cases)

    return samples


def format_training_record(
    dialect: str,
    goal: str,
    phone_state: Dict[str, Any],
    a11y_tree: List[str],
    expected_cmd: Dict[str, Any],
) -> Dict[str, Any]:
    user_msg = (
        f"Goal: {goal}\n"
        f"phone_state: {json.dumps(phone_state, ensure_ascii=False)}\n"
        f"a11y_tree:\n" + ("\n".join(a11y_tree) if a11y_tree else "(empty)")
    )
    return {
        "dialect": dialect,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_msg},
            {"role": "assistant", "content": json.dumps(expected_cmd, ensure_ascii=False)},
        ],
        "expected_action": expected_cmd,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="SilentExec v4 Bilingual Dataset Generator")
    parser.add_argument("--count", type=int, default=1200, help="Total samples to generate")
    parser.add_argument("--dry-run", action="store_true", help="Validate 50 samples in memory without writing large files")
    parser.add_argument("--out-dir", type=str, default="nlp-training/data_v4", help="Output directory for train/val/test JSONL")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    schema_path = repo_root / "schemas" / "action_schema.json"
    with open(schema_path, "r", encoding="utf-8") as f:
        action_schema = json.load(f)
    validator = jsonschema.Draft202012Validator(action_schema)

    rng = random.Random(args.seed)
    templates = build_templates()
    target_count = 50 if args.dry_run else max(1, args.count)

    records: List[Dict[str, Any]] = []
    for i in range(target_count):
        dialect, goal, ps, tree, cmd = templates[i % len(templates)]
        # Validate every single action against schemas/action_schema.json
        validator.validate(cmd)
        records.append(format_training_record(dialect, goal, ps, tree, cmd))

    rng.shuffle(records)

    if args.dry_run:
        dialects = sorted({r["dialect"] for r in records})
        print(f"DRY-RUN OK: validated {len(records)} samples against {schema_path.name} (dialects={dialects})")
        return 0

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    n_train = int(len(records) * 0.8)
    n_val = int(len(records) * 0.1)
    splits = {
        "train.jsonl": records[:n_train],
        "val.jsonl": records[n_train : n_train + n_val],
        "test.jsonl": records[n_train + n_val :],
    }
    for fname, rows in splits.items():
        with open(out_dir / fname, "w", encoding="utf-8") as f:
            for row in rows:
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(f"Wrote {len(rows)} samples to {out_dir / fname}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
