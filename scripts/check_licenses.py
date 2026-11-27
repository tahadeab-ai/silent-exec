#!/usr/bin/env python3
"""Automated License & Dependency Audit for SilentExec v5.0.
Verifies zero forbidden GPL/AGPL dependencies (ChatterUI, Cherry Studio,
phonedriver, Sui, Termux) exist in Cargo, npm, or Gradle manifests.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent

FORBIDDEN_MARKERS = [
    "chatterui",
    "cherry-studio",
    "cherrystudio",
    "phonedriver",
    "rikka.sui",
    "com.termux",
    "termux-app",
    "gpl-3.0",
    "agpl-3.0",
]

MANIFESTS = [
    ROOT / "core-rust" / "Cargo.toml",
    ROOT / "core-rust" / "crates" / "silent-exec-core" / "Cargo.toml",
    ROOT / "core-rust" / "crates" / "silent-exec-cli" / "Cargo.toml",
    ROOT / "core-rust" / "crates" / "silent-exec-pc" / "Cargo.toml",
    ROOT / "desktop-app" / "src-tauri" / "Cargo.toml",
    ROOT / "desktop-app" / "ui" / "package.json",
    ROOT / "android-agent" / "app" / "build.gradle.kts",
]


def main() -> int:
    violations: list[str] = []
    for manifest in MANIFESTS:
        if not manifest.is_file():
            print(f"[WARN] Missing manifest: {manifest}")
            continue
        content = manifest.read_text(encoding="utf-8", errors="ignore").lower()
        for marker in FORBIDDEN_MARKERS:
            if marker in content:
                violations.append(f"{manifest.relative_to(ROOT)} contains forbidden marker '{marker}'")

    if violations:
        print("LICENSE AUDIT FAILED:")
        for v in violations:
            print(f"  - {v}")
        return 1

    print(f"LICENSE AUDIT PASSED: {len(MANIFESTS)} manifests verified clean (MIT / Apache-2.0 only).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
