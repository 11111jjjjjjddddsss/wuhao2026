#!/usr/bin/env python3
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASELINE = ROOT / ".mojibake-baseline.txt"
BUSINESS_ROOTS = (
    ROOT / "app/src",
    ROOT / "server/src",
    ROOT / "server/assets",
    ROOT / "server/migrations",
    ROOT / "scripts",
)

INCLUDE_EXT = {
    ".kt",
    ".kts",
    ".java",
    ".ts",
    ".js",
    ".html",
    ".css",
    ".xml",
    ".md",
    ".txt",
    ".json",
    ".yml",
    ".yaml",
    ".properties",
    ".sql",
}

EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    "build",
    "node_modules",
    "dist",
}

# Common mojibake glyphs seen in UTF-8/GBK or UTF-8/Latin-1 corruption.
SUSPECT_CODEPOINTS = {
    0x9359,  # 鍙
    0x9286,  # 銆
    0x951B,  # 锛
    0x951F,  # 锟
    0x9225,  # 鈥
    0x9229,  # 鈩
}

REPLACEMENT_CHAR = chr(0xFFFD)
SUSPECT_TOKENS = (
    "锟斤拷",
    "鍙",
    "銆",
    "锛",
    "锟",
    "鈥",
    "鈩",
    "Ã",
    "Â",
    "â€",
    "â€™",
    "â€œ",
)


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


def is_in_business_roots(path: pathlib.Path) -> bool:
    try:
        resolved = path.resolve()
    except FileNotFoundError:
        return False
    for root in BUSINESS_ROOTS:
        try:
            resolved.relative_to(root.resolve())
            return True
        except ValueError:
            continue
    return False


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
        if REPLACEMENT_CHAR in line or "锟斤拷" in line:
            issues.append(Issue(rel, idx, "replacement_char", clean_snippet(line)))
            continue

        if any(token in line for token in SUSPECT_TOKENS):
            issues.append(Issue(rel, idx, "suspect_token", clean_snippet(line)))
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


def iter_files(scan_all: bool, staged_only: bool) -> list[pathlib.Path]:
    candidates: list[pathlib.Path] = []
    if staged_only:
        result = subprocess.run(
            ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        for raw in result.stdout.splitlines():
            rel = raw.strip()
            if not rel:
                continue
            path = ROOT / rel
            if path.is_file() and should_scan(path) and (scan_all or is_in_business_roots(path)):
                candidates.append(path)
        return candidates

    roots = [ROOT] if scan_all else [root for root in BUSINESS_ROOTS if root.exists()]
    for base in roots:
        for path in base.rglob("*"):
            if path.is_file() and should_scan(path):
                candidates.append(path)
    return candidates


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="Fail on any issue, ignore baseline.")
    parser.add_argument("--update-baseline", action="store_true", help="Rewrite baseline from current scan.")
    parser.add_argument("--all", action="store_true", help="Scan the full repository instead of business source roots.")
    parser.add_argument("--staged", action="store_true", help="Scan only staged files.")
    args = parser.parse_args()

    files = iter_files(scan_all=args.all, staged_only=args.staged)
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
