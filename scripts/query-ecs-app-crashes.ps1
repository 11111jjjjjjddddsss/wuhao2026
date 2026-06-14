param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [ValidateRange(1, 720)]
    [int]$SinceHours = 24,
    [ValidateRange(1, 200)]
    [int]$Limit = 50,
    [ValidateSet("", "app.crash", "auth.app_crash")]
    [string]$Event = "",
    [ValidateSet("", "debug", "release")]
    [string]$BuildType = "",
    [ValidateRange(0, 2147483647)]
    [int]$AppVersionCode = 0,
    [string]$DeviceModel = ""
)

$ErrorActionPreference = "Stop"

if ($DeviceModel.Length -gt 80) {
    throw "DeviceModel filter is too long"
}

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
  echo 'mysql client is required for app crash queries' >&2
  exit 12
fi

NONGJI_ENV_FILE="$env_file" python3 - <<'PY'
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import Counter
from datetime import datetime, timezone
from urllib.parse import unquote, urlparse

try:
    from zoneinfo import ZoneInfo
except Exception:
    ZoneInfo = None

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

def get_int_env(name, default, minimum, maximum):
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        print(f"{name} must be an integer", file=sys.stderr)
        sys.exit(31)
    if value < minimum or value > maximum:
        print(f"{name} out of range", file=sys.stderr)
        sys.exit(32)
    return value

def get_b64_env(name):
    raw = os.environ.get(name, "").strip()
    if not raw:
        return ""
    try:
        return base64.b64decode(raw).decode("utf-8")
    except Exception:
        print(f"{name} is not valid base64", file=sys.stderr)
        sys.exit(36)

def sql_quote(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"

def safe_text(value, max_len=160):
    if value is None:
        return ""
    value = str(value).replace("\r", " ").replace("\n", " ").replace("\t", " ").strip()
    if len(value) > max_len:
        return value[:max_len] + "..."
    return value

def fmt_time(ms_text):
    try:
        ms = int(ms_text)
    except Exception:
        return "unknown"
    tz = ZoneInfo("Asia/Shanghai") if ZoneInfo else timezone.utc
    return datetime.fromtimestamp(ms / 1000, timezone.utc).astimezone(tz).strftime("%Y-%m-%d %H:%M:%S%z")

def user_kind(user_id):
    if user_id == "preauth":
        return "preauth"
    if user_id and user_id.startswith("acct_"):
        return "acct"
    if not user_id:
        return "unknown"
    return "other"

def first_nonempty(attrs, *keys):
    for key in keys:
        value = attrs.get(key)
        if value is not None and str(value).strip():
            return safe_text(value)
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

since_hours = get_int_env("NONGJI_CRASH_SINCE_HOURS", 24, 1, 720)
limit = get_int_env("NONGJI_CRASH_LIMIT", 50, 1, 200)
event_filter = get_b64_env("NONGJI_CRASH_EVENT_B64").strip()
build_type = get_b64_env("NONGJI_CRASH_BUILD_TYPE_B64").strip()
app_version_code = get_int_env("NONGJI_CRASH_APP_VERSION_CODE", 0, 0, 2147483647)
device_model = get_b64_env("NONGJI_CRASH_DEVICE_MODEL_B64").strip()

if event_filter and event_filter not in ("app.crash", "auth.app_crash"):
    print("unsupported event filter", file=sys.stderr)
    sys.exit(33)
if build_type and build_type not in ("debug", "release"):
    print("unsupported build_type filter", file=sys.stderr)
    sys.exit(34)
if len(device_model) > 80:
    print("device model filter too long", file=sys.stderr)
    sys.exit(35)

filters = [
    "event IN ('app.crash', 'auth.app_crash')",
    f"created_at >= CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED) - {since_hours * 60 * 60 * 1000}",
]
if event_filter:
    filters.append(f"event = {sql_quote(event_filter)}")
if build_type:
    filters.append(f"build_type = {sql_quote(build_type)}")
if app_version_code:
    filters.append(f"app_version_code = {app_version_code}")
if device_model:
    filters.append(f"device_model LIKE {sql_quote(device_model + '%')}")

where_sql = " AND ".join(filters)
scan_limit = min(max(limit * 20, 200), 2000)

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
        for line in output.splitlines():
            if line.strip():
                rows.append(line.split("\t"))
        return rows

    rows = query_rows(f"""
        SELECT
            id,
            created_at,
            event,
            level,
            user_id,
            COALESCE(build_type, ''),
            COALESCE(CAST(app_version_code AS CHAR), ''),
            COALESCE(app_version_name, ''),
            COALESCE(os_version, ''),
            COALESCE(device_model, ''),
            COALESCE(attrs_json, '{{}}')
        FROM client_app_logs
        WHERE {where_sql}
        ORDER BY created_at DESC, id DESC
        LIMIT {scan_limit}
    """)

    records = []
    for row in rows:
        padded = (row + [""] * 11)[:11]
        (
            row_id,
            created_at,
            event,
            level,
            uid,
            row_build_type,
            row_app_version_code,
            row_app_version_name,
            os_version,
            row_device_model,
            attrs_raw,
        ) = padded
        try:
            attrs = json.loads(attrs_raw or "{}")
            if not isinstance(attrs, dict):
                attrs = {}
        except Exception:
            attrs = {}
        exception = first_nonempty(attrs, "exception")
        top_class = first_nonempty(attrs, "top_class")
        top_method = first_nonempty(attrs, "top_method")
        top_line = first_nonempty(attrs, "top_line")
        signature = (
            event or "unknown",
            exception or "unknown",
            top_class or "unknown",
            top_method or "unknown",
            top_line or "unknown",
        )
        records.append(
            {
                "id": row_id,
                "created_at": created_at,
                "time": fmt_time(created_at),
                "event": event,
                "level": level,
                "user_kind": user_kind(uid),
                "build_type": row_build_type or "unknown",
                "app_version_code": row_app_version_code or "unknown",
                "app_version_name": row_app_version_name or "unknown",
                "os_version": os_version or "unknown",
                "device_model": row_device_model or "unknown",
                "exception": exception or "unknown",
                "cause": first_nonempty(attrs, "cause") or "unknown",
                "top_class": top_class or "unknown",
                "top_method": top_method or "unknown",
                "top_line": top_line or "unknown",
                "stack_top": first_nonempty(attrs, "stack_top") or "unknown",
                "stack_next": first_nonempty(attrs, "stack_next") or "",
                "stack_third": first_nonempty(attrs, "stack_third") or "",
                "login_stage": first_nonempty(attrs, "login_stage") or "",
                "thread": first_nonempty(attrs, "thread") or "",
                "report_attempt": first_nonempty(attrs, "report_attempt") or "",
                "crashed_at": first_nonempty(attrs, "crashed_at") or "",
                "signature": signature,
            }
        )

    counts = Counter(record["signature"] for record in records)
    latest = {}
    for record in records:
        sig = record["signature"]
        if sig not in latest or int(record["created_at"] or 0) > int(latest[sig]["created_at"] or 0):
            latest[sig] = record

    print("== app crash query ==")
    print(f"since_hours={since_hours}")
    print(f"detail_limit={limit}")
    print(f"scan_limit={scan_limit}")
    print(f"event_filter={event_filter or 'all'}")
    print(f"build_type_filter={build_type or 'all'}")
    print(f"app_version_code_filter={app_version_code or 'all'}")
    print(f"device_model_filter={device_model or 'all'}")

    print()
    print("== crash signature summary ==")
    if not records:
        print("none")
    else:
        for sig, count in sorted(counts.items(), key=lambda item: (-item[1], -(int(latest[item[0]]["created_at"] or 0))))[:20]:
            event, exception, top_class, top_method, top_line = sig
            latest_record = latest[sig]
            print(
                "event={event} exception={exception} top={top_class}.{top_method}:{top_line} "
                "count={count} latest={latest_time} latest_ms={latest_ms}".format(
                    event=safe_text(event, 80),
                    exception=safe_text(exception, 120),
                    top_class=safe_text(top_class, 160),
                    top_method=safe_text(top_method, 80),
                    top_line=safe_text(top_line, 20),
                    count=count,
                    latest_time=latest_record["time"],
                    latest_ms=latest_record["created_at"],
                )
            )

    print()
    print("== latest crash events ==")
    if not records:
        print("none")
    else:
        for record in records[:limit]:
            print(
                "id={id} time={time} event={event} level={level} user_kind={user_kind} "
                "build_type={build_type} app_version={app_version_code}/{app_version_name} "
                "os={os_version} device={device_model}".format(**record)
            )
            print(
                "  exception={exception} cause={cause} top={top_class}.{top_method}:{top_line}".format(**record)
            )
            print("  stack_top={stack_top}".format(**record))
            if record["stack_next"]:
                print("  stack_next={stack_next}".format(**record))
            if record["stack_third"]:
                print("  stack_third={stack_third}".format(**record))
            extras = []
            for key in ("login_stage", "thread", "report_attempt", "crashed_at"):
                if record[key]:
                    extras.append(f"{key}={record[key]}")
            if extras:
                print("  " + " ".join(extras))

    print()
    print("status=ok")
finally:
    try:
        os.unlink(defaults.name)
    except Exception:
        pass
PY
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-app-crash-query.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$eventB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Event))
$buildTypeB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($BuildType))
$deviceModelB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($DeviceModel))
$commandContent = "NONGJI_CRASH_SINCE_HOURS=$SinceHours NONGJI_CRASH_LIMIT=$Limit NONGJI_CRASH_EVENT_B64=$eventB64 NONGJI_CRASH_BUILD_TYPE_B64=$buildTypeB64 NONGJI_CRASH_APP_VERSION_CODE=$AppVersionCode NONGJI_CRASH_DEVICE_MODEL_B64=$deviceModelB64 bash /tmp/nongji-app-crash-query.sh"

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $commandContent,
    "--Timeout", "180"
)

Write-Host "App crash query invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "App crash query failed: status=$($final.Status) exit=$($final.ExitCode)"
}
