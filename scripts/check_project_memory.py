#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
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
    "admin/",
    "app/",
    "gradle/",
    "site/",
    "server-go/",
    ".github/workflows/",
    "docs/adr/",
    "docs/runbooks/",
)

WATCHED_EXACT = {
    "README.md",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "app/AGENTS.md",
    "server-go/AGENTS.md",
}

WATCHED_SCRIPT_EXACT = {
    "scripts/check_project_memory.py",
    "scripts/check-admin-surface.mjs",
    "scripts/check-android-build-parity.ps1",
    "scripts/check-android-release-artifact.ps1",
    "scripts/check-app-update-release-match.ps1",
    "scripts/check-ecs-readiness.ps1",
    "scripts/check-public-blackbox.ps1",
    "scripts/check-launch-readiness.ps1",
    "scripts/check-admin-authenticated-smoke.ps1",
    "scripts/deploy-ecs-server.ps1",
    "scripts/rollback-ecs-server.ps1",
    "scripts/deploy-ecs-admin.ps1",
    "scripts/deploy-ecs-site.ps1",
    "scripts/publish-android-test-apk.ps1",
    "scripts/clean-local-android-apks.ps1",
    "scripts/clean-oss-test-apks.ps1",
    "scripts/check-android-download-domain.ps1",
    "scripts/sign-oss-cname-url.py",
    "scripts/sync-oss-download-certificate.ps1",
    "scripts/check-sls-alert-readiness.ps1",
    "scripts/check-sls-cost-guard.ps1",
    "scripts/check-data-retention-cost.ps1",
    "scripts/check-aliyun-costs.ps1",
    "scripts/setup-sls-alerts.ps1",
    "scripts/query-ecs-logs.ps1",
    "scripts/check-server-performance.ps1",
    "scripts/check-auth-usage.ps1",
    "scripts/check-sms-usage.ps1",
    "scripts/check-resource-capacity.ps1",
}

IGNORED_PREFIXES = (
    "docs/project-state/",
    ".git",
)

CURRENT_STATUS_PREFIXES = (
    "admin/",
    "app/",
    "gradle/",
    "site/",
    "server-go/",
    ".github/workflows/",
    "docs/adr/",
    "docs/runbooks/",
)

CURRENT_STATUS_SCRIPT_EXACT = {
    "scripts/check-android-build-parity.ps1",
    "scripts/check-admin-surface.mjs",
    "scripts/check-android-release-artifact.ps1",
    "scripts/check-app-update-release-match.ps1",
    "scripts/check-ecs-readiness.ps1",
    "scripts/check-public-blackbox.ps1",
    "scripts/check-launch-readiness.ps1",
    "scripts/check-admin-authenticated-smoke.ps1",
    "scripts/deploy-ecs-server.ps1",
    "scripts/rollback-ecs-server.ps1",
    "scripts/deploy-ecs-admin.ps1",
    "scripts/deploy-ecs-site.ps1",
    "scripts/publish-android-test-apk.ps1",
    "scripts/clean-local-android-apks.ps1",
    "scripts/clean-oss-test-apks.ps1",
    "scripts/check-android-download-domain.ps1",
    "scripts/sign-oss-cname-url.py",
    "scripts/sync-oss-download-certificate.ps1",
    "scripts/check-sls-alert-readiness.ps1",
    "scripts/check-sls-cost-guard.ps1",
    "scripts/check-data-retention-cost.ps1",
    "scripts/check-aliyun-costs.ps1",
    "scripts/setup-sls-alerts.ps1",
    "scripts/query-ecs-logs.ps1",
    "scripts/check-server-performance.ps1",
    "scripts/check-auth-usage.ps1",
    "scripts/check-sms-usage.ps1",
    "scripts/check-resource-capacity.ps1",
}

RISK_SENSITIVE_KEYWORDS = (
    "admin",
    "app-update",
    "app_update",
    "auth",
    "capacity",
    "daily-agri",
    "daily_agri",
    "deploy",
    "ecs",
    "fusion",
    "gift",
    "health",
    "login",
    "logs",
    "member",
    "monitor",
    "order",
    "payment",
    "quota",
    "readiness",
    "redis",
    "rollback",
    "security",
    "sls",
    "sms",
    "support",
    "today-agri",
    "today_agri",
    "upload",
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


def merge_base(base_ref: str | None, head_ref: str | None) -> str | None:
    if not ref_exists(base_ref) or not ref_exists(head_ref):
        return None
    try:
        value = run_git(["merge-base", base_ref or "", head_ref or ""]).strip()
    except RuntimeError:
        return None
    return value or None


def github_pull_request_merge_base(head_ref: str | None) -> str | None:
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        return None
    if not ref_exists(head_ref):
        return None
    base_branch = os.environ.get("GITHUB_BASE_REF", "").strip()
    if not base_branch:
        return None
    candidates = [f"origin/{base_branch}", base_branch]
    for candidate in candidates:
        value = merge_base(candidate, head_ref)
        if value:
            return value
    return None


def changed_files(base_ref: str | None, head_ref: str | None) -> list[str]:
    if not base_ref and not head_ref:
        staged = normalize_paths(run_git(["diff", "--cached", "--name-only"]))
        unstaged = normalize_paths(run_git(["diff", "--name-only", "HEAD"]))
        untracked = normalize_paths(run_git(["ls-files", "--others", "--exclude-standard"]))
        local_changes = [*staged, *unstaged, *untracked]
        if local_changes:
            return list(dict.fromkeys(local_changes))
        return []

    effective_head = head_ref or "HEAD"

    pr_base = github_pull_request_merge_base(effective_head)
    if pr_base:
        return normalize_paths(run_git(["diff", "--name-only", f"{pr_base}..{effective_head}"]))

    if ref_exists(base_ref) and ref_exists(effective_head):
        diff_base = merge_base(base_ref, effective_head) or base_ref
        return normalize_paths(run_git(["diff", "--name-only", f"{diff_base}..{effective_head}"]))

    if ref_exists(effective_head):
        return normalize_paths(run_git(["diff-tree", "--no-commit-id", "--name-only", "-r", effective_head]))

    return []


def is_memory_file(path: str) -> bool:
    return path in MEMORY_FILES


def is_ignored(path: str) -> bool:
    return any(path.startswith(prefix) for prefix in IGNORED_PREFIXES)


def is_watched(path: str) -> bool:
    if path in WATCHED_EXACT or path in WATCHED_SCRIPT_EXACT:
        return True
    return any(path.startswith(prefix) for prefix in WATCHED_PREFIXES)


def requires_current_status(path: str) -> bool:
    if path in CURRENT_STATUS_SCRIPT_EXACT:
        return True
    return any(path.startswith(prefix) for prefix in CURRENT_STATUS_PREFIXES)


def is_risk_sensitive(path: str) -> bool:
    lowered = path.lower()
    return any(keyword in lowered for keyword in RISK_SENSITIVE_KEYWORDS)


def required_memory_updates(watched_changes: list[str]) -> dict[str, list[str]]:
    required: dict[str, list[str]] = {}

    def require(path: str, reason: str) -> None:
        required.setdefault(path, [])
        if reason not in required[path]:
            required[path].append(reason)

    if watched_changes:
        require(
            "docs/project-state/recent-changes.md",
            "关键代码、runbook 或运维脚本变更需要写入近期变更，方便后续窗口追溯",
        )

    for changed in watched_changes:
        if requires_current_status(changed):
            require(
                "docs/project-state/current-status.md",
                f"{changed} 可能改变当前系统真相，需要同步 current-status",
            )
        if is_risk_sensitive(changed):
            require(
                "docs/project-state/open-risks.md",
                f"{changed} 属于登录、运维、监控、支付或其它风险敏感区域，需要同步 open-risks",
            )
        if changed == "scripts/check_project_memory.py":
            require(
                "docs/project-state/pending-decisions.md",
                "项目记忆校验策略变化需要同步待决策事项",
            )
            require(
                "docs/project-state/open-risks.md",
                "项目记忆校验策略变化需要同步已知文档漂移风险",
            )

    return required


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

    required_memory = required_memory_updates(watched_changes)
    missing_required = sorted(path for path in required_memory if path not in touched_memory)

    if touched_memory and not missing_required:
        print("[project-memory] Memory files updated:")
        for path in touched_memory:
            print(f"  - {path}")
        return 0

    if missing_required:
        print("[project-memory] Key project files changed but required project memory files were not updated.", file=sys.stderr)
        print("[project-memory] Changed truth files:", file=sys.stderr)
        for path in watched_changes:
            print(f"  - {path}", file=sys.stderr)
        print("[project-memory] Required memory updates:", file=sys.stderr)
        for path in missing_required:
            print(f"  - {path}", file=sys.stderr)
            for reason in required_memory[path]:
                print(f"    reason: {reason}", file=sys.stderr)
        if touched_memory:
            print("[project-memory] Already updated memory files:", file=sys.stderr)
            for path in touched_memory:
                print(f"  - {path}", file=sys.stderr)
        return 1

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
