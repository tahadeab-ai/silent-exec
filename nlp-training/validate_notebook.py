#!/usr/bin/env python3
"""
SilentExec Notebook Validator
Validates that nlp-training/train_silent_exec_qlora.ipynb conforms to nbformat 4
and that all Python code cells contain 100% valid Python syntax via ast.parse.
"""

import ast
import json
import sys
from pathlib import Path


def validate_notebook(ipynb_path: Path) -> int:
    print(f"Validating notebook: {ipynb_path}...")
    if not ipynb_path.is_file():
        print(f"ERROR: Notebook file not found at {ipynb_path}", file=sys.stderr)
        return 1

    try:
        with open(ipynb_path, "r", encoding="utf-8") as f:
            nb = json.load(f)
    except Exception as e:
        print(f"ERROR: Failed to parse notebook JSON: {e}", file=sys.stderr)
        return 1

    # Verify nbformat
    nbformat = nb.get("nbformat")
    if nbformat != 4:
        print(f"ERROR: Expected nbformat 4, got {nbformat}", file=sys.stderr)
        return 1

    cells = nb.get("cells", [])
    if not cells:
        print("ERROR: Notebook has no cells", file=sys.stderr)
        return 1

    print(f"Notebook has {len(cells)} cells. Validating Python code syntax with ast.parse...")

    code_cell_count = 0
    for idx, cell in enumerate(cells, 1):
        cell_type = cell.get("cell_type")
        if cell_type != "code":
            continue

        code_cell_count += 1
        source = cell.get("source", [])
        if isinstance(source, list):
            source_text = "".join(source)
        else:
            source_text = str(source)

        # Strip IPython magics and shell escapes for AST parsing
        clean_lines = []
        for line in source_text.splitlines():
            stripped = line.strip()
            if stripped.startswith("!") or stripped.startswith("%"):
                clean_lines.append("# " + line)
            else:
                clean_lines.append(line)

        clean_code = "\n".join(clean_lines)

        try:
            ast.parse(clean_code, filename=f"cell_{idx}")
            print(f"  ✓ Cell {idx} (Code Cell #{code_cell_count}): Valid syntax")
        except SyntaxError as se:
            print(f"ERROR: Syntax error in cell {idx} at line {se.lineno}: {se.msg}", file=sys.stderr)
            print("--- Problematic code ---", file=sys.stderr)
            print(clean_code, file=sys.stderr)
            return 1

    print(f"\nAll {code_cell_count} code cells passed AST syntax validation successfully!")
    return 0


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    nb_path = repo_root / "nlp-training" / "train_silent_exec_qlora.ipynb"
    return validate_notebook(nb_path)


if __name__ == "__main__":
    sys.exit(main())
