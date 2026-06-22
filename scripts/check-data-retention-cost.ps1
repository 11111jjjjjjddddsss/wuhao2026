param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [int]$ChatArchiveMaxDays = 31,
    [int]$AppLogMaxDays = 30,
    [int]$SupportReviewDays = 365,
    [int]$AuditReviewDays = 180,
    [int]$LedgerReviewDays = 365,
    [int]$TableSizeWarnMb = 1024,
    [int]$TotalSizeWarnMb = 10240,
    [switch]$FailOnWarning
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
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce)"\s*:\s*")[^"]+', '${1}REDACTED'
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
  echo 'mysql client is required for data retention checks' >&2
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

def env_int(name, default):
    try:
        return int(os.environ.get(name, str(default)))
    except Exception:
        return default

chat_archive_max_days = env_int("CHAT_ARCHIVE_MAX_DAYS", 31)
app_log_max_days = env_int("APP_LOG_MAX_DAYS", 30)
support_review_days = env_int("SUPPORT_REVIEW_DAYS", 365)
audit_review_days = env_int("AUDIT_REVIEW_DAYS", 365)
ledger_review_days = env_int("LEDGER_REVIEW_DAYS", 365)
table_size_warn_mb = env_int("TABLE_SIZE_WARN_MB", 1024)
total_size_warn_mb = env_int("TOTAL_SIZE_WARN_MB", 10240)

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

    def query_scalar(sql):
        rows = query_rows(sql)
        if not rows:
            return "0"
        return rows[0][-1].strip()

    def q_ident(name):
        if not re.match(r"^[A-Za-z0-9_]+$", name):
            raise ValueError(f"unsafe table name: {name}")
        return f"`{name}`"

    cn_tz = timezone(timedelta(hours=8))
    now_ms = int(query_scalar("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED)"))
    day_ms = 24 * 60 * 60 * 1000

    def age_days(value):
        try:
            millis = int(float(str(value).strip()))
        except Exception:
            return 0.0
        if millis <= 0:
            return 0.0
        return max(0.0, (now_ms - millis) / day_ms)

    def format_cn_ms(value):
        try:
            millis = int(float(str(value).strip()))
        except Exception:
            return "none"
        if millis <= 0:
            return "none"
        return datetime.fromtimestamp(millis / 1000, cn_tz).strftime("%Y-%m-%d %H:%M:%S+08:00")

    tables = [
        ("session_round_archive", "chat_archive", "hard_30d", chat_archive_max_days),
        ("client_app_logs", "app_auto_logs", "auto_prune_30d", app_log_max_days),
        ("support_messages", "support_text", "manual_review_365d", support_review_days),
        ("admin_audit_logs", "admin_audit", "auto_prune_180d", audit_review_days),
        ("session_round_ledger", "idempotency_ledger", "manual_review_365d", ledger_review_days),
        ("quota_ledger", "quota_ledger", "asset_record", 0),
        ("orders", "orders", "asset_record", 0),
        ("gift_card_redemption_attempts", "gift_attempts", "anti_abuse_record", 0),
        ("daily_agri_cards", "daily_agri_public_cards", "public_content", 0),
    ]
    table_names = [name for name, _, _, _ in tables]
    quoted_names = ",".join("'" + name + "'" for name in table_names)
    size_rows = query_rows(f"""
        SELECT table_name, ROUND((data_length + index_length) / 1024 / 1024, 3) AS size_mb
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN ({quoted_names})
    """)
    sizes = {row[0]: float(row[1] or 0) for row in size_rows}
    total_size_mb = sum(sizes.get(name, 0.0) for name in table_names)

    warnings = []
    errors = []
    print("== data retention and cost guard ==")
    print(
        "thresholds chat_archive_days={chat} app_log_days={app} support_review_days={support} "
        "audit_review_days={audit} ledger_review_days={ledger} table_size_warn_mb={table_size} "
        "total_size_warn_mb={total_size}".format(
            chat=chat_archive_max_days,
            app=app_log_max_days,
            support=support_review_days,
            audit=audit_review_days,
            ledger=ledger_review_days,
            table_size=table_size_warn_mb,
            total_size=total_size_warn_mb,
        )
    )

    for table_name, label, policy, max_days in tables:
        table = q_ident(table_name)
        count_text, oldest_text, newest_text = query_rows(
            f"SELECT COUNT(*), COALESCE(MIN(created_at), 0), COALESCE(MAX(created_at), 0) FROM {table}"
        )[0]
        count = int(count_text or 0)
        oldest_age = age_days(oldest_text)
        newest_age = age_days(newest_text)
        size_mb = sizes.get(table_name, 0.0)
        print(
            "table={table} label={label} policy={policy} rows={rows} size_mb={size:.3f} "
            "oldest_age_days={oldest:.1f} newest_age_days={newest:.1f} "
            "oldest_at_cn={oldest_at} newest_at_cn={newest_at}".format(
                table=table_name,
                label=label,
                policy=policy,
                rows=count,
                size=size_mb,
                oldest=oldest_age,
                newest=newest_age,
                oldest_at=format_cn_ms(oldest_text),
                newest_at=format_cn_ms(newest_text),
            )
        )
        if max_days > 0 and count > 0 and oldest_age > max_days:
            warnings.append(f"{table_name}_oldest_age_exceeds_{max_days}d:{oldest_age:.1f}d")
        if size_mb > table_size_warn_mb:
            warnings.append(f"{table_name}_size_exceeds_{table_size_warn_mb}mb:{size_mb:.1f}mb")

    print(f"tracked_tables_total_size_mb={total_size_mb:.3f}")
    if total_size_mb > total_size_warn_mb:
        warnings.append(f"tracked_tables_total_size_exceeds_{total_size_warn_mb}mb:{total_size_mb:.1f}mb")

    print()
    print(f"summary warnings={len(warnings)} errors={len(errors)}")
    for item in warnings:
        print("warning=" + item)
    for item in errors:
        print("error=" + item)
    if errors:
        print("status=failed")
        sys.exit(40)
    if warnings:
        print("status=attention")
        sys.exit(30)
    print("status=ready")
finally:
    try:
        os.unlink(defaults.name)
    except Exception:
        pass
PY
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-data-retention-cost-check.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$command = @(
    "CHAT_ARCHIVE_MAX_DAYS=$ChatArchiveMaxDays",
    "APP_LOG_MAX_DAYS=$AppLogMaxDays",
    "SUPPORT_REVIEW_DAYS=$SupportReviewDays",
    "AUDIT_REVIEW_DAYS=$AuditReviewDays",
    "LEDGER_REVIEW_DAYS=$LedgerReviewDays",
    "TABLE_SIZE_WARN_MB=$TableSizeWarnMb",
    "TOTAL_SIZE_WARN_MB=$TotalSizeWarnMb",
    "bash /tmp/nongji-data-retention-cost-check.sh"
) -join " "

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $command,
    "--Timeout", "180"
)

Write-Host "Data retention cost check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    if ($final.ExitCode -eq 30 -and -not $FailOnWarning) {
        return
    }
    throw "Data retention cost check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
