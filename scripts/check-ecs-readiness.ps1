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

$remoteScript = @'
set -u
env_file='/etc/nongjiqiancha/server.env'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'

echo '== service =='
for svc in nongji-server nongji-server-3000 nongji-server-3001; do
  state=$(systemctl is-active "$svc" 2>/dev/null || true)
  enabled=$(systemctl is-enabled "$svc" 2>/dev/null || true)
  echo "$svc state=${state:-unknown} enabled=${enabled:-unknown}"
done

echo
echo '== nginx =='
nginx -t 2>&1 || true
active_port=$(grep -oE 'proxy_pass http://127\.0\.0\.1:(3000|3001);' "$nginx_site" 2>/dev/null | head -1 | sed -E 's/.*:([0-9]+);/\1/' || true)
if [ -z "$active_port" ]; then
  active_port=unknown
fi
echo "active_upstream_port=$active_port"
if [ "$active_port" != "unknown" ]; then
  active_service="nongji-server-${active_port}"
  active_state=$(systemctl is-active "$active_service" 2>/dev/null || true)
  echo "active_upstream_service=$active_service state=${active_state:-unknown}"
  if [ "$active_state" != "active" ]; then
    echo "active upstream service is not active: $active_service" >&2
    exit 10
  fi
fi

echo
echo '== healthz =='
health_body='/tmp/nongji-readiness-health.json'
health_status=$(curl -sS -o "$health_body" -w '%{http_code}' -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz || true)
echo "http_status=$health_status"
cat "$health_body" 2>/dev/null || true
echo
if [ "$health_status" != "200" ]; then
  echo "healthz is not ready: $health_status" >&2
  exit 11
fi
for expected in '"ok":true' '"auth_strict":true' '"bailian":"ok"' '"redis":"ok"' '"upload_storage":"oss"'; do
  if ! grep -q "$expected" "$health_body" 2>/dev/null; then
    echo "healthz missing expected marker: $expected" >&2
    exit 12
  fi
done

echo
echo '== env readiness (values redacted) =='
check_env() {
  key="$1"
  if [ ! -f "$env_file" ]; then
    echo "$key=missing_env_file"
    return
  fi
  line=$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$env_file" | tail -n 1 || true)
  if [ -z "$line" ]; then
    echo "$key=missing"
    return
  fi
  value=$(printf '%s' "$line" | sed -E "s/^[[:space:]]*(export[[:space:]]+)?${key}=//" | sed -E "s/^['\"]|['\"]$//g")
  if [ -z "$value" ]; then
    echo "$key=empty"
  else
    echo "$key=set"
  fi
}

for key in \
  APP_ENV AUTH_STRICT APP_SECRET MYSQL_URL MYSQL_MAX_OPEN_CONNS MYSQL_MAX_IDLE_CONNS BASE_PUBLIC_URL UPLOAD_BASE_URL \
  IP2REGION_V4_XDB_PATH IP2REGION_V6_XDB_PATH IP2REGION_XDB_PATH \
  LISTEN_ADDR LISTEN_HOST PORT \
  UPLOAD_STORAGE_BACKEND OSS_BUCKET OSS_ENDPOINT OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET \
  DASHSCOPE_API_KEY DASHSCOPE_API_KEY_1 DASHSCOPE_API_KEY_2 DASHSCOPE_API_KEY_3 DASHSCOPE_API_KEYS DASHSCOPE_KEY_COOLDOWN_SECONDS \
  DASHSCOPE_KEY_SELECTION_MODE DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS \
  DYPNS_ACCESS_KEY_ID DYPNS_ACCESS_KEY_SECRET DYPNS_FUSION_SCHEME_CODE \
  DYPNS_SMS_SIGN_NAME DYPNS_SMS_TEMPLATE_CODE \
  REDIS_ADDR REDIS_USERNAME REDIS_PASSWORD \
  SUPPORT_ADMIN_SECRET DAILY_AGRI_JOB_SECRET; do
  check_env "$key"
done

echo
echo '== ip2region data =='
ip2region_v4_path=''
if [ -f "$env_file" ]; then
  ip2region_v4_line=$(grep -E '^[[:space:]]*(export[[:space:]]+)?(IP2REGION_V4_XDB_PATH|IP2REGION_XDB_PATH)=' "$env_file" | tail -n 1 || true)
  if [ -n "$ip2region_v4_line" ]; then
    ip2region_v4_path=$(printf '%s' "$ip2region_v4_line" | sed -E 's/^[[:space:]]*(export[[:space:]]+)?(IP2REGION_V4_XDB_PATH|IP2REGION_XDB_PATH)=//' | sed -E "s/^['\"]|['\"]$//g")
  fi
fi
if [ -z "$ip2region_v4_path" ]; then
  echo 'v4_xdb=missing_path'
elif [ -r "$ip2region_v4_path" ]; then
  size=$(wc -c < "$ip2region_v4_path" 2>/dev/null || true)
  echo "v4_xdb=present readable=true bytes=${size:-unknown}"
else
  echo 'v4_xdb=present readable=false'
fi

echo
echo '== local upload dir =='
if [ -d /var/lib/nongjiqiancha/uploads ]; then
  find /var/lib/nongjiqiancha/uploads -maxdepth 1 -type f | wc -l | awk '{print "files="$1}'
  du -sh /var/lib/nongjiqiancha/uploads 2>/dev/null || true
else
  echo 'missing'
fi

echo
echo '== ports =='
ss -ltnp 2>/dev/null | grep -E '(:80|:443|:3000|:3001)[[:space:]]' || true
'@

$remoteBytes = [Text.Encoding]::UTF8.GetBytes(($remoteScript -replace "`r`n", "`n"))
$remoteBase64 = [Convert]::ToBase64String($remoteBytes)
$command = "printf '%s' '$remoteBase64' | base64 -d >/tmp/nongji-readiness-check.sh && bash /tmp/nongji-readiness-check.sh"

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $command,
    "--Timeout", "180"
)

Write-Host "Readiness check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Readiness check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
