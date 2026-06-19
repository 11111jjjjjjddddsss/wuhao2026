param(
    [string]$BackendBaseUrl = "https://api.nongjiqiancha.cn",
    [string]$DayCN = "",
    [string]$SecretsPath = "$env:USERPROFILE\.nongjiqiancha\prod-secrets.json",
    [int]$TimeoutSec = 30
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
