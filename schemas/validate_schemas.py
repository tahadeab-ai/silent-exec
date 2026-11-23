#!/usr/bin/env python3
"""
SilentExec Schema, Safety Policy, and GBNF Grammar Validator
Validates JSON schemas, GBNF grammar for UTF-8 Arabic support, and cross-checks action integrity.
"""

import json
import sys
from pathlib import Path
import jsonschema


def main() -> int:
    schemas_dir = Path(__file__).resolve().parent

    action_schema_path = schemas_dir / "action_schema.json"
    safety_policy_path = schemas_dir / "safety_policy.json"
    action_gbnf_path = schemas_dir / "action.gbnf"

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

    # 2. Check 20 actions alignment
    schema_actions = set(action_schema["properties"]["action"]["enum"])
    tier1_actions = set(safety_policy["tier_1_silent"])
    tier2_actions = set(safety_policy["tier_2_confirm"])

    policy_actions = tier1_actions | tier2_actions
    assert schema_actions == policy_actions, (
        f"Mismatch between schema actions ({schema_actions}) "
        f"and safety policy actions ({policy_actions})"
    )
    assert len(schema_actions) == 20, f"Expected 20 actions, found {len(schema_actions)}"
    assert tier1_actions.isdisjoint(tier2_actions), "Overlap found between Tier 1 and Tier 2"
    print("✓ All 20 actions verified and aligned across schema and policy.")

    # 3. Verify action.gbnf existence, completeness, and UTF-8 Arabic compatibility
    print(f"Verifying {action_gbnf_path}...")
    assert action_gbnf_path.is_file(), f"Missing GBNF grammar file: {action_gbnf_path}"
    with open(action_gbnf_path, "r", encoding="utf-8") as f:
        gbnf_content = f.read()

    assert len(gbnf_content.strip()) > 0, "action.gbnf is empty"
    for action_name in schema_actions:
        assert f'\\"{action_name}\\"' in gbnf_content or f'"{action_name}"' in gbnf_content, (
            f"Action {action_name} missing from action.gbnf"
        )

    # Confirm json-string in GBNF permits multi-byte UTF-8 (Arabic) without ASCII-only restriction
    assert '[^"\\\\' in gbnf_content, "GBNF json-string must permit non-escaped UTF-8 characters"
    assert "[a-zA-Z0-9]" not in gbnf_content.split("json-string")[1].split("\n")[0], (
        "GBNF json-string should not be restricted to ASCII alphanumerics"
    )
    print("✓ action.gbnf verified: defines all 20 actions and permits full UTF-8 Arabic generation.")

    # 4. Test valid payloads
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
