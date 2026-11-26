#!/usr/bin/env python3
"""
SilentExec Schema and Safety Policy Validator
Validates JSON schemas and cross-checks action integrity.
"""

import json
import sys
from pathlib import Path
import jsonschema


def main() -> int:
    schemas_dir = Path(__file__).resolve().parent

    action_schema_path = schemas_dir / "action_schema.json"
    safety_policy_path = schemas_dir / "safety_policy.json"

    print(f"Loading {action_schema_path}...")
    with open(action_schema_path, "r", encoding="utf-8") as f:
        action_schema = json.load(f)

    print(f"Loading {safety_policy_path}...")
    with open(safety_policy_path, "r", encoding="utf-8") as f:
        safety_policy = json.load(f)

    # 1. Validate action_schema against Draft 2020-12 meta-schema
    validator_cls = jsonschema.validators.validator_for(action_schema)
    validator_cls.check_schema(action_schema)
    print("✓ action_schema.json is a valid Draft 2020-12 JSON Schema.")

    schema_validator = jsonschema.Draft202012Validator(action_schema)

    # 2. Check 25 actions alignment (24 actions + 'done')
    if "$defs" in action_schema and "ActionCommand" in action_schema["$defs"]:
        schema_actions = set(action_schema["$defs"]["ActionCommand"]["properties"]["action"]["enum"])
    else:
        schema_actions = set(action_schema["properties"]["action"]["enum"])

    tier1_actions = set(safety_policy["tier_1_silent"])
    tier2_actions = set(safety_policy["tier_2_confirm"])

    policy_actions = tier1_actions | tier2_actions
    assert schema_actions == policy_actions, (
        f"Mismatch between schema actions ({schema_actions}) "
        f"and safety policy actions ({policy_actions})"
    )
    assert len(schema_actions) == 25, f"Expected 25 actions (including 'done'), found {len(schema_actions)}"
    assert "done" in tier1_actions, "'done' action must be Tier 1 Silent"
    assert tier1_actions.isdisjoint(tier2_actions), "Overlap found between Tier 1 and Tier 2"
    print("✓ All 25 actions (24 tools + done) verified and aligned across schema and policy.")

    # 3. Test valid payloads
    valid_payloads = [
        {
            "action": "open_folder",
            "target": "Downloads",
            "platform": "pc",
            "confidence": 0.95
        },
        {
            "action": "create_note",
            "target": "قائمة المهام اليومية",
            "folder": "ملاحظاتي",
            "platform": "any",
            "confidence": 0.88
        },
        {
            "action": "set_volume",
            "args": {"level": 80},
            "platform": "pc",
            "confidence": 0.99
        },
        {
            "action": "delete_file",
            "target": "temp.txt",
            "platform": "pc",
            "confidence": 0.75
        },
        {
            "thought": "Need to click the send button visible on screen",
            "action": "ui_click",
            "target": "زر ارسال",
            "platform": "android",
            "confidence": 0.92
        },
        {
            "action": "ui_type",
            "target": "شريط البحث",
            "args": {"text": "مرحبا بك"},
            "platform": "android",
            "confidence": 0.95
        },
        {
            "action": "ui_scroll",
            "args": {"direction": "down"},
            "platform": "android",
            "confidence": 0.90
        },
        {
            "action": "ui_back",
            "platform": "android",
            "confidence": 0.98
        },
        {
            "action": "ui_home",
            "platform": "pc",
            "confidence": 0.97
        },
        {
            "action": "done",
            "platform": "any",
            "confidence": 1.0
        },
        {
            "action": "reply",
            "target": "مرحبا! كيف يمكنني مساعدتك اليوم؟",
            "args": {"text": "مرحبا! كيف يمكنني مساعدتك اليوم؟"},
            "platform": "any",
            "confidence": 0.95
        },
        {
            "action": "read_file",
            "target": "notes/todo.txt",
            "platform": "pc",
            "confidence": 0.92
        },
        {
            "action": "write_file",
            "target": "summary.md",
            "args": {"content": "# Project Summary\nAll tests passed."},
            "platform": "pc",
            "confidence": 0.88
        },
        {
            "action": "memory_save",
            "target": "favorite_editor",
            "args": {"key": "favorite_editor", "content": "VSCode", "category": "preference"},
            "platform": "any",
            "confidence": 0.96
        },
        {
            "steps": [
                {
                    "action": "launch_app",
                    "target": "Chrome",
                    "platform": "pc",
                    "confidence": 0.95
                },
                {
                    "action": "ui_click",
                    "target": "Search bar",
                    "platform": "pc",
                    "confidence": 0.92
                },
                {
                    "action": "done",
                    "platform": "pc",
                    "confidence": 1.0
                }
            ]
        }
    ]

    for i, payload in enumerate(valid_payloads, 1):
        schema_validator.validate(payload)
        print(f"✓ Valid sample {i} passed schema validation.")

    # 5. Test invalid payloads (must fail)
    invalid_payloads = [
        # Unknown action
        {
            "action": "format_disk",
            "platform": "pc",
            "confidence": 0.9
        },
        # Additional property (arbitrary injection attempt)
        {
            "action": "open_file",
            "target": "report.pdf",
            "platform": "pc",
            "confidence": 0.9,
            "shell_cmd": "whoami"
        },
        # Out-of-range confidence
        {
            "action": "open_file",
            "target": "report.pdf",
            "platform": "pc",
            "confidence": 1.5
        },
        # Missing required field platform
        {
            "action": "open_file",
            "confidence": 0.9
        },
        # Empty steps array
        {
            "steps": []
        },
        # Exceeds max 6 steps
        {
            "steps": [
                {"action": "done", "platform": "any", "confidence": 1.0}
            ] * 7
        },
        # Invalid step inside steps
        {
            "steps": [
                {"action": "malicious_exec", "platform": "pc", "confidence": 0.9}
            ]
        }
    ]

    for i, invalid in enumerate(invalid_payloads, 1):
        errors = list(schema_validator.iter_errors(invalid))
        assert len(errors) > 0, f"Expected error for invalid payload #{i}, but none raised"
        print(f"✓ Invalid sample {i} correctly rejected.")

    print("\nAll schema, safety policy, and GBNF checks passed successfully!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
