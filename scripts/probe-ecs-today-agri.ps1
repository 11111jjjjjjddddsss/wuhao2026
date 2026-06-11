param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [ValidateRange(1, 5)]
    [int]$Runs = 2
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
    for ($i = 0; $i -lt 160; $i++) {
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
runs="${NONGJI_PROBE_RUNS:-2}"
case "$runs" in
  ''|*[!0-9]*) runs=2 ;;
esac
if [ "$runs" -lt 1 ]; then runs=1; fi
if [ "$runs" -gt 5 ]; then runs=5; fi

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
  --max-time 360 \
  --request POST \
  --header "X-Internal-Job-Secret: ${DAILY_AGRI_JOB_SECRET}" \
  "http://127.0.0.1:${active_port}/internal/jobs/today-agri-card/probe?runs=${runs}" \
  | python3 -c 'import json, sys
p = json.load(sys.stdin)
rows = []
for r in p.get("runs", []):
    items = ((r.get("card") or {}).get("items") or [])
    rows.append({
        "ok": r.get("ok"),
        "error": r.get("error"),
        "candidate_items": r.get("candidate_items"),
        "displayable_items": r.get("displayable_items"),
        "source_count": r.get("source_count"),
        "search_count": r.get("model_search_count"),
        "total_tokens": r.get("model_total_tokens"),
        "reasoning_tokens": r.get("model_reasoning_tokens"),
        "titles": [i.get("title") for i in items],
        "sources": [i.get("source") for i in items],
        "summary_lengths": [len(i.get("summary") or "") for i in items],
    })
print(json.dumps({
    "ok_count": p.get("ok_count"),
    "total_runs": p.get("total_runs"),
    "model": p.get("model"),
    "strategy": p.get("strategy"),
    "prompt_version": p.get("prompt_version"),
    "rows": rows,
}, ensure_ascii=False))'
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-probe-today-agri.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "NONGJI_PROBE_RUNS=$Runs bash /tmp/nongji-probe-today-agri.sh",
    "--Timeout", "420"
)

Write-Host "Probe invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Probe failed: status=$($final.Status) exit=$($final.ExitCode)"
}
