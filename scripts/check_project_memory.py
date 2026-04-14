#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
MEMORY_FILES = {
    "AGENTS.md",
    "docs/project-state/current-status.md",
    "docs/project-state/open-risks.md",
    "docs/project-state/pending-decisions.md",
    "docs/project-state/recent-changes.md",
}

WATCHED_PREFIXES = (
    "app/src/main/kotlin/com/nongjiqianwen/",
    "server-go/",
    "docs/adr/",
    "docs/runbooks/",
)

WATCHED_EXACT = {
    "README.md",
    "app/AGENTS.md",
    "server-go/AGENTS.md",
}

IGNORED_PREFIXES = (
    "docs/project-state/",
    ".github/",
    "scripts/",
    ".git",
)


def run_git(args: list[str]) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout


def git_success(args: list[str]) -> bool:
    result = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.returncode == 0


def normalize_paths(output: str) -> list[str]:
    return [line.strip().replace("\\", "/") for line in output.splitlines() if line.strip()]


def is_zero_ref(ref: str | None) -> bool:
    if not ref:
        return True
    stripped = ref.strip()
    return not stripped or set(stripped) == {"0"}


def ref_exists(ref: str | None) -> bool:
    if is_zero_ref(ref):
        return False
    return git_success(["rev-parse", "--verify", ref])


def changed_files(base_ref: str | None, head_ref: str | None) -> list[str]:
    if ref_exists(base_ref) and ref_exists(head_ref):
        return normalize_paths(run_git(["diff", "--name-only", f"{base_ref}..{head_ref}"]))

    if ref_exists(head_ref):
        return normalize_paths(run_git(["diff-tree", "--no-commit-id", "--name-only", "-r", head_ref]))

    staged = normalize_paths(run_git(["diff", "--cached", "--name-only"]))
    if staged:
        return staged

    return normalize_paths(run_git(["diff", "--name-only", "HEAD"]))


def is_memory_file(path: str) -> bool:
    return path in MEMORY_FILES


def is_ignored(path: str) -> bool:
    return any(path.startswith(prefix) for prefix in IGNORED_PREFIXES)


def is_watched(path: str) -> bool:
    if path in WATCHED_EXACT:
        return True
    return any(path.startswith(prefix) for prefix in WATCHED_PREFIXES)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fail when key project truth changes without updating project memory files."
    )
    parser.add_argument("--base", help="Base git ref for diff range")
    parser.add_argument("--head", help="Head git ref for diff range")
    args = parser.parse_args()

    try:
        files = changed_files(args.base, args.head)
    except RuntimeError as exc:
        print(f"[project-memory] {exc}", file=sys.stderr)
        return 2

    if not files:
        print("[project-memory] No changed files detected.")
        return 0

    touched_memory = sorted(path for path in files if is_memory_file(path))
    watched_changes = sorted(
        path for path in files if is_watched(path) and not is_memory_file(path) and not is_ignored(path)
    )

    if not watched_changes:
        print("[project-memory] No key truth files changed. Memory update not required.")
        return 0

    if touched_memory:
        print("[project-memory] Memory files updated:")
        for path in touched_memory:
            print(f"  - {path}")
        return 0

    print("[project-memory] Key project files changed but project memory was not updated.", file=sys.stderr)
    print("[project-memory] Changed truth files:", file=sys.stderr)
    for path in watched_changes:
        print(f"  - {path}", file=sys.stderr)
    print("[project-memory] Update at least one of:", file=sys.stderr)
    for path in sorted(MEMORY_FILES):
        print(f"  - {path}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
