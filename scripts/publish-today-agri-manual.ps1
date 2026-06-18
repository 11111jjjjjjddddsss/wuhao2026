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

function Get-DefaultDayCN {
    $now = Get-Date
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
    [pscustomobject]@{
        ok            = $true
        dry_run       = $true
        day_cn        = $DayCN
        endpoint      = $uri
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

$secret = Get-RequiredSecret -Path $SecretsPath -EnvName "DAILY_AGRI_JOB_SECRET" -JsonName "daily_agri_job_secret"
$headers = @{
    "X-Internal-Job-Secret" = $secret
    "X-Admin-Actor"         = $Actor
}

try {
    $statusResponse = Invoke-RestMethod -Method Get -Uri $statusUri -Headers $headers
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
    $response = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType "application/json; charset=utf-8" -Body $bodyBytes
} catch {
    $message = $_.Exception.Message
    if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
        $message = $_.ErrorDetails.Message
    }
    throw "manual_publish_failed: $message"
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
