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
            -replace '(?i)((?:MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEYS?|DASHSCOPE_API_KEY_[0-9]+|DASHSCOPE_PRIMARY_API_KEYS?|DASHSCOPE_PRIMARY_API_KEY_[0-9]+|DASHSCOPE_SECONDARY_API_KEYS?|DASHSCOPE_SECONDARY_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content|MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEY|DASHSCOPE_API_KEYS|DASHSCOPE_API_KEY_[0-9]+|DASHSCOPE_PRIMARY_API_KEY|DASHSCOPE_PRIMARY_API_KEYS|DASHSCOPE_PRIMARY_API_KEY_[0-9]+|DASHSCOPE_SECONDARY_API_KEY|DASHSCOPE_SECONDARY_API_KEYS|DASHSCOPE_SECONDARY_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)"\s*:\s*")[^"]+', '${1}REDACTED'
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
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 2
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
set -u

redact() {
  sed -E \
    -e 's/(Bearer )[A-Za-z0-9._~+\/=-]+/\1REDACTED/g' \
    -e 's/sk-[A-Za-z0-9_-]{16,}/sk-REDACTED/g' \
    -e 's/([0-9]{1,3}\.[0-9]{1,3})\.[0-9]{1,3}\.[0-9]{1,3}/\1.*.*/g'
}

count_journal() {
  pattern="$1"
  journalctl -u nongji-server-3000 -u nongji-server-3001 --since "24 hours ago" --no-pager 2>/dev/null \
    | grep -E "$pattern" \
    | wc -l \
    | awk '{print $1}'
}

echo '== system resources =='
uptime || true
free -m || true
df -h / || true
echo
echo '== process snapshot =='
ps -eo pid,ppid,comm,%cpu,%mem,rss,vsz,etime --sort=-%cpu \
  | awk 'NR==1 || /nongji-server|nginx/ {print}' \
  | head -20 || true
echo
echo '== systemd services =='
for unit in nongji-server-3000.service nongji-server-3001.service nginx.service; do
  state=$(systemctl is-active "$unit" 2>/dev/null || true)
  enabled=$(systemctl is-enabled "$unit" 2>/dev/null || true)
  restarts=$(systemctl show "$unit" -p NRestarts --value 2>/dev/null || echo unknown)
  mem=$(systemctl show "$unit" -p MemoryCurrent --value 2>/dev/null || echo unknown)
  tasks=$(systemctl show "$unit" -p TasksCurrent --value 2>/dev/null || echo unknown)
  echo "unit=$unit state=$state enabled=$enabled restarts=$restarts memory_current=$mem tasks_current=$tasks"
done
echo
echo '== socket summary =='
ss -s 2>/dev/null || true
echo
echo '== local health latency =='
for i in 1 2 3; do
  curl -sS --resolve api.nongjiqiancha.cn:443:127.0.0.1 -o /dev/null \
    -w "healthz_probe=$i status=%{http_code} connect_ms=%{time_connect} tls_ms=%{time_appconnect} total_ms=%{time_total}\n" \
    https://api.nongjiqiancha.cn/healthz 2>/dev/null || true
done
echo
echo '== 24h app log counters =='
echo "http_request_error_24h=$(count_journal 'http_request_error')"
echo "http_request_slow_24h=$(count_journal 'http_request_slow')"
echo "http_sse_stream_24h=$(count_journal 'http_sse_stream')"
echo "panic_24h=$(count_journal 'panic|fatal error')"
echo
echo '== 24h nginx 429/5xx counters =='
if [ -f /var/log/nginx/access.log ]; then
  awk '$9 ~ /^429$/ {c429++} $9 ~ /^5[0-9][0-9]$/ {c5xx++} END {printf "nginx_429_24h=%d\nnginx_5xx_24h=%d\n", c429+0, c5xx+0}' /var/log/nginx/access.log 2>/dev/null || true
else
  echo 'nginx_access_log=missing'
fi
echo
echo '== recent slow/error lines =='
journalctl -u nongji-server-3000 -u nongji-server-3001 --since "24 hours ago" --no-pager 2>/dev/null \
  | grep -E 'http_request_error|http_request_slow|panic|fatal error' \
  | tail -30 \
  | redact || true
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-server-performance-check.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-server-performance-check.sh",
    "--Timeout", "180"
)

Write-Host "Server performance check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Server performance check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
