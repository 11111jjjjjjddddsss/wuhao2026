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
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 3
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
export TZ=Asia/Shanghai

set -a
. /etc/nongjiqiancha/server.env
set +a

if [ -z "${DAILY_AGRI_JOB_SECRET:-}" ]; then
  echo '{"error":"missing_secret"}'
  exit 12
fi

active_port=$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' /etc/nginx/sites-available/nongjiqiancha-api 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | head -n 1)
if [ -z "${active_port:-}" ]; then
  active_port=3000
fi

curl --silent --show-error --fail \
  --max-time 120 \
  --request POST \
  --header "X-Internal-Job-Secret: ${DAILY_AGRI_JOB_SECRET}" \
  "http://127.0.0.1:${active_port}/internal/jobs/memory-document/probe" \
  | python3 -c 'import json, sys
p = json.load(sys.stdin)
doc = p.get("memory_document") or ""
print(json.dumps({
    "status": p.get("status"),
    "model": p.get("model"),
    "memory_chars": p.get("memory_chars"),
    "prompt_chars": p.get("prompt_chars"),
    "user_content_chars": p.get("user_content_chars"),
    "total_tokens": p.get("model_total_tokens"),
    "reasoning_tokens": p.get("model_reasoning_tokens"),
    "has_short_term": "\u77ed\u671f" in doc,
    "has_long_term": "\u957f\u671f" in doc,
    "has_user_portrait": "\u7528\u6237\u753b\u50cf" in doc,
    "has_agri_memory": "\u519c\u4e1a" in doc,
    "contains_model_word": any(x in doc for x in ["\u6a21\u578b", "\u7cfb\u7edf\u63d0\u793a\u8bcd", "API", "\u5de5\u5177\u8c03\u7528", "\u641c\u7d22\u914d\u7f6e"]),
    "preview": doc[:700],
}, ensure_ascii=False))'
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-probe-memory-document.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-probe-memory-document.sh",
    "--Timeout", "180"
)

Write-Host "Probe invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Probe failed: status=$($final.Status) exit=$($final.ExitCode)"
}
