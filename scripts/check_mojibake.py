#!/usr/bin/env python3
from __future__ import annotations

import argparse
import pathlib
import re
import sys
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASELINE = ROOT / ".mojibake-baseline.txt"

INCLUDE_EXT = {
    ".kt",
    ".kts",
    ".ts",
    ".js",
    ".html",
    ".css",
    ".md",
    ".json",
    ".yml",
    ".yaml",
}

EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    "build",
    "node_modules",
    "dist",
}

SUSPECT_CODEPOINTS = {
    0x9286,  # 銆
    0x951b,  # 锛
    0x9359,  # 鍙
    0x9428,  # 鐨
    0x935D,  # 鍝
    0x934F,  # 鍏
    0x935A,  # 鍚
    0x9354,  # 鍔
}

REPLACEMENT_CHAR = chr(0xFFFD)


@dataclass(frozen=True)
class Issue:
    path: str
    line_no: int
    kind: str
    snippet: str

    @property
    def key(self) -> str:
        return f"{self.path}:{self.line_no}:{self.kind}"


def should_scan(path: pathlib.Path) -> bool:
    if any(part in EXCLUDED_DIRS for part in path.parts):
        return False
    return path.suffix.lower() in INCLUDE_EXT


def clean_snippet(line: str, width: int = 120) -> str:
    line = line.replace("\t", " ")
    line = re.sub(r"\s+", " ", line).strip()
    return line[:width]


def scan_file(path: pathlib.Path) -> list[Issue]:
    issues: list[Issue] = []
    rel = path.relative_to(ROOT).as_posix()
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        issues.append(Issue(rel, 1, "decode_error", "not valid UTF-8"))
        return issues

    for idx, line in enumerate(text.splitlines(), start=1):
        if REPLACEMENT_CHAR in line or "��" in line:
            issues.append(Issue(rel, idx, "replacement_char", clean_snippet(line)))
            continue

        suspect_hits = sum(1 for ch in line if ord(ch) in SUSPECT_CODEPOINTS)
        if suspect_hits >= 2:
            issues.append(Issue(rel, idx, "suspect_mojibake", clean_snippet(line)))

    return issues


def load_baseline() -> set[str]:
    if not BASELINE.exists():
        return set()
    items: set[str] = set()
    for raw in BASELINE.read_text(encoding="utf-8").splitlines():
        row = raw.strip()
        if not row or row.startswith("#"):
            continue
        items.add(row)
    return items


def save_baseline(issues: list[Issue]) -> None:
    keys = sorted({issue.key for issue in issues})
    lines = [
        "# Mojibake baseline (existing known issues).",
        "# Format: path:line:kind",
        *keys,
        "",
    ]
    BASELINE.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="Fail on any issue, ignore baseline.")
    parser.add_argument("--update-baseline", action="store_true", help="Rewrite baseline from current scan.")
    args = parser.parse_args()

    files = [p for p in ROOT.rglob("*") if p.is_file() and should_scan(p)]
    all_issues: list[Issue] = []
    for file in files:
        all_issues.extend(scan_file(file))

    if args.update_baseline:
        save_baseline(all_issues)
        print(f"Baseline updated: {BASELINE.relative_to(ROOT).as_posix()} ({len(all_issues)} entries)")
        return 0

    baseline = set() if args.strict else load_baseline()
    new_issues = [issue for issue in all_issues if issue.key not in baseline]

    if not new_issues:
        print("Mojibake check passed (no new issues).")
        return 0

    print("Mojibake check failed. New issues:")
    for issue in new_issues:
        print(f"- {issue.path}:{issue.line_no} [{issue.kind}] {issue.snippet}")
    print(f"Total new issues: {len(new_issues)}")
    return 1


if __name__ == "__main__":
    sys.exit(main())

