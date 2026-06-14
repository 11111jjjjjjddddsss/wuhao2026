param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonCommand {
    param([string[]]$CommandArgs)
    if ($CommandArgs.Length -eq 0) {
        throw "Command failed: empty command"
    }
    $exe = $CommandArgs[0]
    $arguments = @()
    if ($CommandArgs.Length -gt 1) {
        $arguments = $CommandArgs[1..($CommandArgs.Length - 1)]
    }
    $stderrPath = [IO.Path]::GetTempFileName()
    $stdout = @()
    $stderr = ""
    $exitCode = 0
    $oldErrorActionPreference = $ErrorActionPreference
    $oldNativeErrorPreference = $null
    $hasNativeErrorPreference = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
    try {
        $ErrorActionPreference = "Continue"
        if ($null -ne $hasNativeErrorPreference) {
            $oldNativeErrorPreference = $PSNativeCommandUseErrorActionPreference
            $PSNativeCommandUseErrorActionPreference = $false
        }
        $stdout = & $exe @arguments 2> $stderrPath
        $exitCode = $LASTEXITCODE
        if (Test-Path -LiteralPath $stderrPath) {
            $stderr = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
        }
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
        if ($null -ne $hasNativeErrorPreference) {
            $PSNativeCommandUseErrorActionPreference = $oldNativeErrorPreference
        }
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
    if ($exitCode -ne 0) {
        $safeOutput = (($stdout | Out-String) + "`n" + $stderr) `
            -replace '(?i)(AccessKeyId=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(AccessKeySecret=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(SecurityToken=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(Signature=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(SignatureNonce=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(Content=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content)"\s*:\s*")[^"]+', '${1}REDACTED'
        $safeCommand = if ($CommandArgs.Length -ge 3) {
            "$($CommandArgs[0]) $($CommandArgs[1]) $($CommandArgs[2])"
        } else {
            $CommandArgs -join " "
        }
        throw "Command failed: $safeCommand`n$safeOutput"
    }
    $jsonText = $stdout | Out-String
    if ([string]::IsNullOrWhiteSpace($jsonText)) {
        return $null
    }
    return $jsonText | ConvertFrom-Json
}

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 36; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId
        )
        $item = $result.Invocation.InvocationResults.InvocationResult[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($item.Output))
            [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
            return
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

$remoteScript = @'
set -euo pipefail

env_file='/etc/nongjiqiancha/server.env'
if [ ! -r "$env_file" ]; then
  echo "server env file is missing or unreadable: $env_file" >&2
  exit 10
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo 'python3 is required for safe DSN parsing' >&2
  exit 11
fi
if ! command -v mysql >/dev/null 2>&1; then
  echo 'mysql client is required for backend data boundary checks' >&2
  exit 12
fi

NONGJI_ENV_FILE="$env_file" python3 - <<'PY'
import os
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from urllib.parse import unquote, urlparse

def read_env_value(path, wanted):
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[len("export "):].strip()
            key, sep, value = line.partition("=")
            if sep and key.strip() == wanted:
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                    value = value[1:-1]
                return value
    return ""

env_file = os.environ.get("NONGJI_ENV_FILE", "/etc/nongjiqiancha/server.env")
dsn = read_env_value(env_file, "MYSQL_URL").strip()
if not dsn:
    print("MYSQL_URL is missing", file=sys.stderr)
    sys.exit(20)

if dsn.startswith(("mysql://", "mysql2://")):
    parsed = urlparse(dsn)
    user = unquote(parsed.username or "")
    password = unquote(parsed.password or "")
    host = parsed.hostname or ""
    port = str(parsed.port or 3306)
    database = (parsed.path or "").lstrip("/")
else:
    match = re.match(r"^([^:/@]+):(.*?)@tcp\(([^)]+)\)/([^?]+)", dsn)
    if not match:
        print("MYSQL_URL format is not supported by this checker", file=sys.stderr)
        sys.exit(21)
    user, password, host_port, database = match.groups()
    if ":" in host_port:
        host, port = host_port.rsplit(":", 1)
    else:
        host, port = host_port, "3306"

if not user or not host or not database:
    print("MYSQL_URL is missing required user, host, or database fields", file=sys.stderr)
    sys.exit(22)

defaults = tempfile.NamedTemporaryFile("w", delete=False)
try:
    defaults.write("[client]\n")
    defaults.write(f"user={user}\n")
    defaults.write(f"password={password}\n")
    defaults.write(f"host={host}\n")
    defaults.write(f"port={port}\n")
    defaults.write(f"database={database}\n")
    defaults.write("default-character-set=utf8mb4\n")
    defaults.close()
    os.chmod(defaults.name, 0o600)

    def query_scalar(sql):
        output = subprocess.check_output(
            [
                "mysql",
                f"--defaults-extra-file={defaults.name}",
                "--batch",
                "--raw",
                "--skip-column-names",
                "-e",
                sql,
            ],
            text=True,
        )
        first = output.strip().splitlines()[0] if output.strip() else "0"
        return first.split("\t")[-1].strip()

    def query_rows(sql):
        output = subprocess.check_output(
            [
                "mysql",
                f"--defaults-extra-file={defaults.name}",
                "--batch",
                "--raw",
                "--skip-column-names",
                "-e",
                sql,
            ],
            text=True,
        )
        rows = []
        for line in output.strip().splitlines():
            if line.strip():
                rows.append(line.split("\t"))
        return rows

    cn_tz = timezone(timedelta(hours=8))

    def format_cn_ms(value):
        try:
            millis = int(float(str(value).strip()))
        except Exception:
            return "unknown"
        if millis <= 0:
            return "unknown"
        return datetime.fromtimestamp(millis / 1000, cn_tz).strftime("%Y-%m-%d %H:%M:%S+08:00")

    now_ms = "CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)"
    day_ms = 24 * 60 * 60 * 1000

    count_queries = [
        ("app_accounts", "SELECT COUNT(*) FROM app_accounts"),
        ("auth_sessions", "SELECT COUNT(*) FROM auth_sessions"),
        ("auth_sessions_active", f"SELECT COUNT(*) FROM auth_sessions WHERE revoked_at IS NULL AND token_expires_at > {now_ms}"),
        ("user_id_migrations", "SELECT COUNT(*) FROM user_id_migrations"),
        ("user_entitlement", "SELECT COUNT(*) FROM user_entitlement"),
        ("paid_entitlements_active", f"SELECT COUNT(*) FROM user_entitlement WHERE tier IN ('plus','pro') AND tier_expire_at IS NOT NULL AND tier_expire_at > {now_ms}"),
        ("daily_usage", "SELECT COUNT(*) FROM daily_usage"),
        ("quota_ledger", "SELECT COUNT(*) FROM quota_ledger"),
        ("topup_packs", "SELECT COUNT(*) FROM topup_packs"),
        ("topup_packs_active", f"SELECT COUNT(*) FROM topup_packs WHERE status = 'active' AND remaining > 0 AND expire_at > {now_ms}"),
        ("upgrade_credits", "SELECT COUNT(*) FROM upgrade_credits"),
        ("orders", "SELECT COUNT(*) FROM orders"),
        ("session_ab", "SELECT COUNT(*) FROM session_ab"),
        ("session_ab_memory_docs", "SELECT COUNT(*) FROM session_ab WHERE NULLIF(TRIM(b_summary), '') IS NOT NULL"),
        ("session_round_ledger", "SELECT COUNT(*) FROM session_round_ledger"),
        ("session_round_archive", "SELECT COUNT(*) FROM session_round_archive"),
        ("chat_stream_inflight", "SELECT COUNT(*) FROM chat_stream_inflight"),
        ("chat_stream_inflight_active", f"SELECT COUNT(*) FROM chat_stream_inflight WHERE lease_until > {now_ms}"),
        ("support_conversations", "SELECT COUNT(*) FROM support_conversations"),
        ("support_messages", "SELECT COUNT(*) FROM support_messages"),
        ("client_app_logs", "SELECT COUNT(*) FROM client_app_logs"),
        ("client_app_errors_24h", f"SELECT COUNT(*) FROM client_app_logs WHERE level = 'error' AND created_at >= {now_ms} - {day_ms}"),
        ("client_app_auth_failures_24h", f"SELECT COUNT(*) FROM client_app_logs WHERE event LIKE 'auth.%' AND level IN ('warn','error') AND created_at >= {now_ms} - {day_ms}"),
        ("daily_agri_cards", "SELECT COUNT(*) FROM daily_agri_cards"),
        ("daily_agri_cards_ready", "SELECT COUNT(*) FROM daily_agri_cards WHERE status = 'ready'"),
        ("daily_agri_cards_failed", "SELECT COUNT(*) FROM daily_agri_cards WHERE status = 'failed'"),
        ("gift_card_batches", "SELECT COUNT(*) FROM gift_card_batches"),
        ("gift_cards", "SELECT COUNT(*) FROM gift_cards"),
        ("gift_cards_active", "SELECT COUNT(*) FROM gift_cards WHERE status = 'active'"),
        ("gift_cards_redeemed", "SELECT COUNT(*) FROM gift_cards WHERE status = 'redeemed'"),
        ("gift_card_redemption_attempts", "SELECT COUNT(*) FROM gift_card_redemption_attempts"),
        ("account_deletion_requests", "SELECT COUNT(*) FROM account_deletion_requests"),
        ("app_release_configs", "SELECT COUNT(*) FROM app_release_configs"),
        ("app_release_configs_enabled", "SELECT COUNT(*) FROM app_release_configs WHERE enabled = 1"),
        ("admin_users", "SELECT COUNT(*) FROM admin_users"),
        ("admin_sessions_active", f"SELECT COUNT(*) FROM admin_sessions WHERE revoked_at IS NULL AND expires_at > {now_ms}"),
        ("admin_audit_logs", "SELECT COUNT(*) FROM admin_audit_logs"),
    ]

    owner_checks = [
        ("non_acct_app_accounts", "SELECT COUNT(*) FROM app_accounts WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_auth_sessions", "SELECT COUNT(*) FROM auth_sessions WHERE user_id NOT REGEXP '^acct_'"),
        ("bad_new_user_id_migrations", "SELECT COUNT(*) FROM user_id_migrations WHERE new_user_id NOT REGEXP '^acct_'"),
        ("acct_legacy_user_id_migrations", "SELECT COUNT(*) FROM user_id_migrations WHERE old_user_id REGEXP '^acct_'"),
        ("non_acct_user_entitlement", "SELECT COUNT(*) FROM user_entitlement WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_daily_usage", "SELECT COUNT(*) FROM daily_usage WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_quota_ledger", "SELECT COUNT(*) FROM quota_ledger WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_topup_packs", "SELECT COUNT(*) FROM topup_packs WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_upgrade_credits", "SELECT COUNT(*) FROM upgrade_credits WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_orders", "SELECT COUNT(*) FROM orders WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_session_ab", "SELECT COUNT(*) FROM session_ab WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_session_round_ledger", "SELECT COUNT(*) FROM session_round_ledger WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_session_round_archive", "SELECT COUNT(*) FROM session_round_archive WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_chat_stream_inflight", "SELECT COUNT(*) FROM chat_stream_inflight WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_support_conversations", "SELECT COUNT(*) FROM support_conversations WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_support_messages", "SELECT COUNT(*) FROM support_messages WHERE user_id NOT REGEXP '^acct_'"),
        ("non_acct_client_app_logs", "SELECT COUNT(*) FROM client_app_logs WHERE user_id <> 'preauth' AND user_id NOT REGEXP '^acct_'"),
        ("non_acct_gift_cards_redeemed_user", "SELECT COUNT(*) FROM gift_cards WHERE redeemed_user_id IS NOT NULL AND redeemed_user_id NOT REGEXP '^acct_'"),
        ("non_acct_gift_card_attempts_user", "SELECT COUNT(*) FROM gift_card_redemption_attempts WHERE user_id IS NOT NULL AND user_id NOT REGEXP '^acct_'"),
        ("non_acct_account_deletion_requests", "SELECT COUNT(*) FROM account_deletion_requests WHERE user_id NOT REGEXP '^acct_'"),
    ]

    account_integrity_checks = [
        ("app_accounts_missing_phone_ciphertext", "SELECT COUNT(*) FROM app_accounts WHERE phone_ciphertext IS NULL OR phone_ciphertext = ''"),
        ("orphan_acct_auth_sessions", "SELECT COUNT(*) FROM auth_sessions t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_user_entitlement", "SELECT COUNT(*) FROM user_entitlement t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_daily_usage", "SELECT COUNT(*) FROM daily_usage t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_quota_ledger", "SELECT COUNT(*) FROM quota_ledger t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_topup_packs", "SELECT COUNT(*) FROM topup_packs t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_upgrade_credits", "SELECT COUNT(*) FROM upgrade_credits t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_orders", "SELECT COUNT(*) FROM orders t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_session_ab", "SELECT COUNT(*) FROM session_ab t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_session_round_ledger", "SELECT COUNT(*) FROM session_round_ledger t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_session_round_archive", "SELECT COUNT(*) FROM session_round_archive t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_chat_stream_inflight", "SELECT COUNT(*) FROM chat_stream_inflight t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_support_conversations", "SELECT COUNT(*) FROM support_conversations t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_support_messages", "SELECT COUNT(*) FROM support_messages t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_client_app_logs", "SELECT COUNT(*) FROM client_app_logs t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_gift_cards_redeemed_user", "SELECT COUNT(*) FROM gift_cards t LEFT JOIN app_accounts a ON a.user_id = t.redeemed_user_id WHERE t.redeemed_user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_gift_card_attempts_user", "SELECT COUNT(*) FROM gift_card_redemption_attempts t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
        ("orphan_acct_account_deletion_requests", "SELECT COUNT(*) FROM account_deletion_requests t LEFT JOIN app_accounts a ON a.user_id = t.user_id WHERE t.user_id REGEXP '^acct_' AND a.user_id IS NULL"),
    ]

    print("== backend data counts ==")
    for label, sql in count_queries:
        print(f"{label}={query_scalar(sql)}")

    print()
    print("== client app warn/error top events 24h ==")
    top_log_rows = query_rows(f"""
        SELECT
            event,
            level,
            COALESCE(NULLIF(build_type, ''), 'unknown') AS build_type,
            COALESCE(CAST(app_version_code AS CHAR), 'unknown') AS app_version_code,
            COUNT(*) AS event_count,
            MAX(created_at) AS latest_created_at
        FROM client_app_logs
        WHERE created_at >= {now_ms} - {day_ms}
          AND level IN ('warn', 'error')
        GROUP BY event, level, build_type, app_version_code
        ORDER BY event_count DESC, latest_created_at DESC
        LIMIT 12
    """)
    if not top_log_rows:
        print("none")
    for row in top_log_rows:
        event, level, build_type, app_version_code, event_count, latest_created_at = (row + [""] * 6)[:6]
        latest_created_at_cn = format_cn_ms(latest_created_at)
        print(
            "event={event} level={level} build_type={build_type} "
            "app_version_code={app_version_code} count={event_count} "
            "latest_created_at={latest_created_at} latest_created_at_cn={latest_created_at_cn}".format(
                event=event,
                level=level,
                build_type=build_type,
                app_version_code=app_version_code,
                event_count=event_count,
                latest_created_at=latest_created_at,
                latest_created_at_cn=latest_created_at_cn,
            )
        )

    print()
    print("== acct ownership checks ==")
    bad = []
    for label, sql in owner_checks:
        value = int(query_scalar(sql) or "0")
        print(f"{label}={value}")
        if value != 0:
            bad.append((label, value))

    print()
    print("== acct account integrity checks ==")
    integrity_bad = []
    for label, sql in account_integrity_checks:
        value = int(query_scalar(sql) or "0")
        print(f"{label}={value}")
        if value != 0:
            integrity_bad.append((label, value))

    print()
    all_bad = bad + integrity_bad
    if all_bad:
        print(f"ownership_errors={len(bad)} account_integrity_errors={len(integrity_bad)}")
        print("status=failed")
        sys.exit(30)
    print("ownership_errors=0")
    print("account_integrity_errors=0")
    print("status=ok")
finally:
    try:
        os.unlink(defaults.name)
    except Exception:
        pass
PY
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-backend-data-boundary-check.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-backend-data-boundary-check.sh",
    "--Timeout", "180"
)

Write-Host "Backend data boundary check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Backend data boundary check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
