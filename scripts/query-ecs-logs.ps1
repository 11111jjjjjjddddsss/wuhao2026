param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [int]$Lines = 240
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
            -replace '(?i)((?:MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEYS?|DASHSCOPE_API_KEY_[0-9]|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content|MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEY|DASHSCOPE_API_KEYS|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)"\s*:\s*")[^"]+', '${1}REDACTED'
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

if ($Lines -le 0) {
    $Lines = 240
}

$remoteScript = @'
set -u
lines="${NONGJI_LOG_LINES:-240}"
case "$lines" in
  ''|*[!0-9]*) lines=240 ;;
esac

redact() {
  sed -E \
    -e 's#(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH) ([^ ?"]+)\?[^ "]*#\1 \2?REDACTED#g' \
    -e 's/(Bearer )[A-Za-z0-9._~+\/=-]+/\1REDACTED/g' \
    -e 's/sk-[A-Za-z0-9_-]{16,}/sk-REDACTED/g' \
    -e 's/([0-9]{1,3}\.[0-9]{1,3})\.[0-9]{1,3}\.[0-9]{1,3}/\1.*.*/g' \
    -e 's/(^|[^0-9])1[3-9][0-9]{9}([^0-9]|$)/\1PHONE_REDACTED\2/g' \
    -e 's/(AccessKey(Id|Secret)?[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(SecurityToken[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(Signature(Nonce)?[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's#((MYSQL_URL|MYSQL_DSN)[=:][[:space:]]*)[^, "&]+#\1REDACTED#Ig' \
    -e 's/(REDIS_PASSWORD[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(DYPNS_ACCESS_KEY_(ID|SECRET)[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(ALIYUN_DYPNS_ACCESS_KEY_(ID|SECRET)[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(SMS_ACCESS_KEY_(ID|SECRET)[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(DASHSCOPE_API_KEY(_[0-9])?[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(DASHSCOPE_API_KEYS[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(OSS_ACCESS_KEY_(ID|SECRET)[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(APP_SECRET[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(SUPPORT_ADMIN_SECRET[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig' \
    -e 's/(DAILY_AGRI_JOB_SECRET[=:][[:space:]]*)[^, "&]+/\1REDACTED/Ig'
}

journal_units='-u nongji-server-3000 -u nongji-server-3001'

echo '== go request errors and slow requests =='
journalctl $journal_units -n "$lines" --no-pager 2>/dev/null \
  | grep -E 'http_request_error|http_request_slow|server bootstrap failed|server stopped|upstream|daily agri card|summary .*failed|auth session check failed|panic' \
  | redact || true

echo
echo '== go request tail =='
journalctl $journal_units -n "$lines" --no-pager 2>/dev/null \
  | grep -E 'http_request' \
  | tail -80 \
  | redact || true

echo
echo '== go warn/error tail =='
journalctl $journal_units -n "$lines" --no-pager 2>/dev/null \
  | grep -E '"level":"(WARN|ERROR)"|level=(WARN|ERROR)' \
  | tail -80 \
  | redact || true

echo
echo '== nginx error tail =='
tail -80 /var/log/nginx/error.log 2>/dev/null | redact || true

echo
echo '== nginx 429/5xx access tail =='
awk '$9 ~ /^(429|5[0-9][0-9])$/ {print}' /var/log/nginx/access.log 2>/dev/null \
  | tail -80 \
  | redact || true
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-query-logs.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "NONGJI_LOG_LINES=$Lines bash /tmp/nongji-query-logs.sh",
    "--Timeout", "180"
)

Write-Host "Log query invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Log query failed: status=$($final.Status) exit=$($final.ExitCode)"
}
