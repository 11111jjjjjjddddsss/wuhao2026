param(
    [string]$BackendBaseUrl = "https://api.nongjiqiancha.cn",
    [string]$DayCN = "",
    [string]$SecretsPath = "$env:USERPROFILE\.nongjiqiancha\prod-secrets.json",
    [int]$TimeoutSec = 30,
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh",
    [switch]$UsePublicInternalApi
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
    for ($i = 0; $i -lt 60; $i++) {
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

if (-not $UsePublicInternalApi) {
    . (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")
    $safeDayCN = $DayCN
    $safeTimeout = [Math]::Min([Math]::Max($TimeoutSec, 1), 120)
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
curl --silent --show-error --fail --max-time $safeTimeout \
  --header "X-Internal-Job-Secret: `$DAILY_AGRI_JOB_SECRET" \
  "http://127.0.0.1:`$active_port/internal/jobs/today-agri-card/status?day_cn=$safeDayCN"
"@
    Send-CloudAssistantScriptFile -RegionId $EcsRegionId -InstanceId $EcsInstanceId -RemotePath "/tmp/nongji-today-agri-status.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $EcsRegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $EcsInstanceId,
        "--Name", "today-agri-status-$DayCN",
        "--CommandContent", "bash /tmp/nongji-today-agri-status.sh",
        "--Timeout", [string]([Math]::Max($safeTimeout + 30, 60))
    )
    $final = Wait-RunCommand $run.InvokeId
    if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
        throw "manual_status_check_failed: status=$($final.Status) exit=$($final.ExitCode) $($final.Output.Trim())"
    }
    $response = $final.Output | ConvertFrom-Json
} else {
    $secret = Get-RequiredSecret -Path $SecretsPath -EnvName "DAILY_AGRI_JOB_SECRET" -JsonName "daily_agri_job_secret"
    $baseUrl = $BackendBaseUrl.TrimEnd("/")
    $uri = "$baseUrl/internal/jobs/today-agri-card/status?day_cn=$DayCN"
    $headers = @{
        "X-Internal-Job-Secret" = $secret
    }

    try {
        $response = Invoke-RestMethod -Method Get -Uri $uri -Headers $headers -TimeoutSec $TimeoutSec
    } catch {
        $message = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $message = $_.ErrorDetails.Message
        }
        throw "manual_status_check_failed: $message"
    }
}

[pscustomobject]@{
    ok             = $true
    day_cn         = $response.day_cn
    status         = $response.status
    ready          = $response.ready
    source_type    = $response.source_type
    manual_locked  = $response.manual_locked
    manual_by      = $response.manual_by
    manual_at      = $response.manual_at
    content_present = $response.content_present
    content_valid   = $response.content_valid
    item_count     = $response.item_count
    should_publish = -not ($response.manual_locked -eq $true -and $response.source_type -eq "manual")
} | ConvertTo-Json -Depth 6
