param(
    [string]$BackendBaseUrl = "https://api.nongjiqiancha.cn",
    [string]$DayCN = "",
    [Parameter(Mandatory = $true)][string]$Title1,
    [Parameter(Mandatory = $true)][string]$Summary1,
    [string]$Source1 = "",
    [Parameter(Mandatory = $true)][string]$Title2,
    [Parameter(Mandatory = $true)][string]$Summary2,
    [string]$Source2 = "",
    [Parameter(Mandatory = $true)][string]$Title3,
    [Parameter(Mandatory = $true)][string]$Summary3,
    [string]$Source3 = "",
    [string]$Actor = "codex_automation",
    [string]$SecretsPath = "$env:USERPROFILE\.nongjiqiancha\prod-secrets.json",
    [int]$TimeoutSec = 30,
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh",
    [switch]$UsePublicInternalApi,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RequiredSecret {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$EnvName,
        [Parameter(Mandatory = $true)][string]$JsonName
    )

    $fromEnv = [Environment]::GetEnvironmentVariable($EnvName)
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        return $fromEnv.Trim()
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "secret_missing"
    }

    $secretJson = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    $prop = $secretJson.PSObject.Properties[$JsonName]
    if ($null -eq $prop -or [string]::IsNullOrWhiteSpace([string]$prop.Value)) {
        throw "secret_missing"
    }
    return ([string]$prop.Value).Trim()
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
    if ($exe -eq "aliyun") {
        $arguments += @("--connect-timeout", "20", "--read-timeout", "120", "--retry-count", "3")
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
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $EcsRegionId,
            "--InvokeId", $InvokeId,
            "--InstanceId", $EcsInstanceId
        )
        $item = @($result.Invocation.InvocationResults.InvocationResult)[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([string]$item.Output))
            return [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

function Get-ChinaNow {
    try {
        $tz = [TimeZoneInfo]::FindSystemTimeZoneById("China Standard Time")
    } catch {
        $tz = [TimeZoneInfo]::FindSystemTimeZoneById("Asia/Shanghai")
    }
    return [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $tz)
}

function Get-DefaultDayCN {
    $now = Get-ChinaNow
    if ($now.Hour -ge 18) {
        $now = $now.AddDays(1)
    }
    return $now.ToString("yyyyMMdd")
}

function Get-ManualConfirmation {
    param([Parameter(Mandatory = $true)][string]$Day)
    $prefix = -join @([char]0x4EBA, [char]0x5DE5, [char]0x53D1, [char]0x5E03)
    return "$prefix $Day"
}

function Assert-Text {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowEmptyString()][string]$Value
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "${Name}_missing"
    }
}

if ([string]::IsNullOrWhiteSpace($DayCN)) {
    $DayCN = Get-DefaultDayCN
}
$DayCN = $DayCN.Trim()
if ($DayCN -notmatch '^\d{8}$') {
    throw "invalid_day_cn"
}
if ($TimeoutSec -le 0) {
    throw "invalid_timeout_sec"
}

Assert-Text -Name "title1" -Value $Title1
Assert-Text -Name "summary1" -Value $Summary1
Assert-Text -Name "title2" -Value $Title2
Assert-Text -Name "summary2" -Value $Summary2
Assert-Text -Name "title3" -Value $Title3
Assert-Text -Name "summary3" -Value $Summary3

$baseUrl = $BackendBaseUrl.TrimEnd("/")
$uri = "$baseUrl/internal/jobs/today-agri-card/manual"
$statusUri = "$baseUrl/internal/jobs/today-agri-card/status?day_cn=$DayCN"

$payload = [ordered]@{
    day_cn       = $DayCN
    confirmation = Get-ManualConfirmation -Day $DayCN
    items        = @(
        [ordered]@{ title = $Title1.Trim(); summary = $Summary1.Trim(); source = $Source1.Trim() },
        [ordered]@{ title = $Title2.Trim(); summary = $Summary2.Trim(); source = $Source2.Trim() },
        [ordered]@{ title = $Title3.Trim(); summary = $Summary3.Trim(); source = $Source3.Trim() }
    )
}

$json = $payload | ConvertTo-Json -Depth 8

if ($DryRun) {
    $transport = if ($UsePublicInternalApi) { "public_internal_api" } else { "cloud_assistant_local" }
    $target = if ($UsePublicInternalApi) { $uri } else { "ecs:$EcsInstanceId localhost active slot" }
    [pscustomobject]@{
        ok            = $true
        dry_run       = $true
        day_cn        = $DayCN
        transport     = $transport
        target        = $target
        item_count    = $payload.items.Count
        confirmation  = $payload.confirmation
        titles        = @(
            [pscustomobject]@{ title = $Title1.Trim(); source = $Source1.Trim() },
            [pscustomobject]@{ title = $Title2.Trim(); source = $Source2.Trim() },
            [pscustomobject]@{ title = $Title3.Trim(); source = $Source3.Trim() }
        )
    } | ConvertTo-Json -Depth 6
    return
}

if (-not $UsePublicInternalApi) {
    . (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")
    $payloadBase64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($json))
    $safeTimeout = [Math]::Min([Math]::Max($TimeoutSec, 1), 120)
    $safeDayCN = $DayCN
    $safeActor = ($Actor -replace "'", "'\''")
    $remoteScript = @"
set -eu
set -a
. /etc/nongjiqiancha/server.env
set +a
if [ -z "`$DAILY_AGRI_JOB_SECRET" ]; then
  echo '{"error":"missing_secret"}'
  exit 12
fi
active_ports=`$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' /etc/nginx/sites-available/nongjiqiancha-api 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | sort -u)
active_port_count=`$(printf '%s\n' "`$active_ports" | sed '/^$/d' | wc -l | tr -d ' ')
if [ "`$active_port_count" != "1" ]; then
  echo '{"error":"active_port_not_unique"}'
  exit 13
fi
active_port=`$(printf '%s\n' "`$active_ports" | sed '/^$/d' | head -n 1)
payload_file="/tmp/nongji-today-agri-manual-$safeDayCN.json"
printf '%s' '$payloadBase64' | base64 -d > "`$payload_file"
status_json=`$(curl --silent --show-error --fail --max-time $safeTimeout \
  --header "X-Internal-Job-Secret: `$DAILY_AGRI_JOB_SECRET" \
  "http://127.0.0.1:`$active_port/internal/jobs/today-agri-card/status?day_cn=$safeDayCN")
python3 -c 'import json, sys
p = json.loads(sys.argv[1])
if p.get("manual_locked") is True and p.get("source_type") == "manual":
    print(json.dumps({
        "ok": True,
        "skipped": True,
        "reason": "manual_locked",
        "day_cn": "$safeDayCN",
        "status": p.get("status"),
        "source_type": p.get("source_type"),
        "manual_locked": p.get("manual_locked"),
        "item_count": p.get("item_count"),
    }, ensure_ascii=False))
    sys.exit(10)
' "`$status_json" || code=`$?
if [ "`${code:-0}" = "10" ]; then
  rm -f "`$payload_file"
  exit 0
elif [ "`${code:-0}" != "0" ]; then
  rm -f "`$payload_file"
  exit "`${code}"
fi
curl --silent --show-error --fail --max-time $safeTimeout \
  --request POST \
  --header "X-Internal-Job-Secret: `$DAILY_AGRI_JOB_SECRET" \
  --header "X-Admin-Actor: $safeActor" \
  --header "Content-Type: application/json; charset=utf-8" \
  --data-binary "@`$payload_file" \
  "http://127.0.0.1:`$active_port/internal/jobs/today-agri-card/manual"
rm -f "`$payload_file"
"@
    Send-CloudAssistantScriptFile -RegionId $EcsRegionId -InstanceId $EcsInstanceId -RemotePath "/tmp/nongji-today-agri-manual.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $EcsRegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $EcsInstanceId,
        "--Name", "today-agri-manual-$DayCN",
        "--CommandContent", "bash /tmp/nongji-today-agri-manual.sh",
        "--Timeout", [string]([Math]::Max($safeTimeout + 45, 90))
    )
    $final = Wait-RunCommand $run.InvokeId
    if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
        throw "manual_publish_failed: status=$($final.Status) exit=$($final.ExitCode) $($final.Output.Trim())"
    }
    $response = $final.Output | ConvertFrom-Json
} else {
    $secret = Get-RequiredSecret -Path $SecretsPath -EnvName "DAILY_AGRI_JOB_SECRET" -JsonName "daily_agri_job_secret"
    $headers = @{
        "X-Internal-Job-Secret" = $secret
        "X-Admin-Actor"         = $Actor
    }

    try {
        $statusResponse = Invoke-RestMethod -Method Get -Uri $statusUri -Headers $headers -TimeoutSec $TimeoutSec
        if ($statusResponse.manual_locked -eq $true -and $statusResponse.source_type -eq "manual") {
            [pscustomobject]@{
                ok             = $true
                skipped        = $true
                reason         = "manual_locked"
                day_cn         = $DayCN
                status         = $statusResponse.status
                source_type    = $statusResponse.source_type
                manual_locked  = $statusResponse.manual_locked
                item_count     = $statusResponse.item_count
            } | ConvertTo-Json -Depth 6
            return
        }
    } catch {
        $message = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $message = $_.ErrorDetails.Message
        }
        throw "manual_status_check_failed: $message"
    }

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($json)

    try {
        $response = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bodyBytes -TimeoutSec $TimeoutSec
    } catch {
        $message = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $message = $_.ErrorDetails.Message
        }
        throw "manual_publish_failed: $message"
    }
}

$safeItems = @()
if ($response.card -and $response.card.items) {
    foreach ($item in $response.card.items) {
        $safeItems += [pscustomobject]@{
            title  = $item.title
            source = $item.source
        }
    }
}

[pscustomobject]@{
    ok            = $true
    day_cn        = $DayCN
    status        = $response.status
    source_type   = $response.source_type
    manual_locked = $response.manual_locked
    item_count    = $response.item_count
    titles        = $safeItems
} | ConvertTo-Json -Depth 6
