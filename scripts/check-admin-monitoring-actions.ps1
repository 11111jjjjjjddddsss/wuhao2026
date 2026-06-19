param(
    [string]$BaseUrl = "https://admin.nongjiqiancha.cn",
    [string]$Username = $(if ($env:NONGJI_ADMIN_USERNAME) { $env:NONGJI_ADMIN_USERNAME } else { $env:ADMIN_SMOKE_USERNAME }),
    [string]$Password = $(if ($env:NONGJI_ADMIN_PASSWORD) { $env:NONGJI_ADMIN_PASSWORD } else { $env:ADMIN_SMOKE_PASSWORD }),
    [int]$TimeoutSec = 20,
    [switch]$FailOnWarning,
    [switch]$SkipIfMissingCredentials,
    [switch]$IncludeManualChecklist,
    [switch]$FailOnManualAttention,
    [switch]$IncludeLaunchReadiness
)

$ErrorActionPreference = "Stop"

function Join-AdminUrl {
    param([string]$Path)
    $base = $BaseUrl.TrimEnd("/")
    $pathPart = if ($Path.StartsWith("/")) { $Path } else { "/$Path" }
    return "$base$pathPart"
}

function Get-ErrorBody {
    param([object]$ErrorRecord)
    try {
        $stream = $ErrorRecord.Exception.Response.GetResponseStream()
        if ($null -eq $stream) {
            return ""
        }
        $reader = [System.IO.StreamReader]::new($stream)
        return $reader.ReadToEnd()
    } catch {
        return ""
    }
}

function Shorten-Text {
    param([string]$Value, [int]$Max = 96)
    $text = ([string]$Value) -replace '\s+', ' '
    $text = $text.Trim()
    if ($text.Length -le $Max) {
        return $text
    }
    return $text.Substring(0, $Max) + "..."
}

function Get-LocalAdminSmokeSecret {
    param([string[]]$Names)
    $secretPath = Join-Path $env:USERPROFILE ".nongjiqiancha\prod-secrets.json"
    if (-not (Test-Path $secretPath)) {
        return ""
    }
    try {
        $secrets = Get-Content -Raw -Path $secretPath | ConvertFrom-Json
        foreach ($name in $Names) {
            $property = $secrets.PSObject.Properties[$name]
            if ($null -ne $property -and -not [string]::IsNullOrWhiteSpace([string]$property.Value)) {
                return [string]$property.Value
            }
        }
    } catch {
        return ""
    }
    return ""
}

if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Get-LocalAdminSmokeSecret @("admin_smoke_username", "nongji_admin_username")
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = Get-LocalAdminSmokeSecret @("admin_smoke_password", "nongji_admin_password")
}

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    if ($SkipIfMissingCredentials) {
        Write-Host "== admin monitoring actions =="
        Write-Host "status=skipped reason=missing_credentials"
        exit 0
    }
    Write-Error "missing admin credentials; set NONGJI_ADMIN_USERNAME/NONGJI_ADMIN_PASSWORD or ADMIN_SMOKE_USERNAME/ADMIN_SMOKE_PASSWORD"
    exit 2
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrfToken = ""
$bad = 0
$warn = 0
$manualBad = 0
$manualWarn = 0
$launchWarn = 0

Write-Host "== admin monitoring actions =="
Write-Host ("base_url={0}" -f $BaseUrl)

try {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri (Join-AdminUrl "/admin-api/v1/auth/login") -Body $loginBody -ContentType "application/json" -WebSession $session -TimeoutSec $TimeoutSec
    $csrfToken = [string]$login.csrf_token
    $role = [string]$login.admin_user.role
    if ([string]::IsNullOrWhiteSpace($csrfToken)) {
        Write-Error "login succeeded but csrf_token is missing"
        exit 3
    }
    Write-Host ("login=ok role={0} csrf=present" -f ($(if ($role) { $role } else { "unknown" })))

    $report = Invoke-RestMethod -Method Get -Uri (Join-AdminUrl "/admin-api/v1/monitoring") -WebSession $session -TimeoutSec $TimeoutSec
    $items = @($report.action_items)
    foreach ($item in $items) {
        $level = ([string]$item.level).Trim().ToLowerInvariant()
        if ($level -eq "bad") {
            $bad += 1
        } elseif ($level -eq "warn") {
            $warn += 1
        }
        $countText = if ($null -ne $item.count) { [string]$item.count } else { "" }
        Write-Host ("action level={0} title={1} count={2} route={3} body={4}" -f `
            ($(if ($level) { $level } else { "info" })), `
            (Shorten-Text ([string]$item.title) 48), `
            $countText, `
            (Shorten-Text ([string]$item.route) 32), `
            (Shorten-Text ([string]$item.body) 120))
    }

    $launchItems = @($report.launch_readiness)
    foreach ($item in $launchItems) {
        $status = ([string]$item.status).Trim().ToLowerInvariant()
        $isManual = $false
        try {
            $isManual = [bool]$item.manual
        } catch {
            $isManual = $false
        }
        $isBad = $status -in @("blocked", "bad", "failed")
        $isWarn = $status -in @("attention", "warn", "warning")
        if ($isManual -and -not $IncludeManualChecklist) {
            if ($isBad) {
                $manualBad += 1
                Write-Host ("manual status={0} title={1}" -f $status, (Shorten-Text ([string]$item.title) 72))
            } elseif ($isWarn) {
                $manualWarn += 1
                Write-Host ("manual status={0} title={1}" -f $status, (Shorten-Text ([string]$item.title) 72))
            }
            continue
        }
        if (-not $IncludeLaunchReadiness -and $isWarn) {
            $launchWarn += 1
            Write-Host ("launch_check status={0} title={1}" -f $status, (Shorten-Text ([string]$item.title) 72))
            continue
        }
        if ($isBad) {
            $bad += 1
            Write-Host ("launch status={0} title={1}" -f $status, (Shorten-Text ([string]$item.title) 72))
        } elseif ($isWarn) {
            $warn += 1
            Write-Host ("launch status={0} title={1}" -f $status, (Shorten-Text ([string]$item.title) 72))
        }
    }

    $queues = $report.queues
    if ($null -ne $queues) {
        Write-Host ("queues support_needs_reply={0} account_deletion_overdue={1} quota_consume_pending={2} memory_pending_users={3} memory_pending_jobs={4} app_errors={5} auth_failures={6} crash_reports={7} daily_agri_status={8}" -f `
            $queues.support_needs_reply, `
            $queues.account_deletion_overdue, `
            $queues.quota_consume_pending, `
            $queues.memory_pending_users, `
            $queues.memory_pending_jobs, `
            $queues.app_errors, `
            $queues.auth_failures, `
            $queues.crash_reports, `
            $queues.daily_agri_status)
    }
} catch {
    $body = Get-ErrorBody $_
    $bodySummary = if ([string]::IsNullOrWhiteSpace($body)) { "" } else { " body=" + (Shorten-Text $body 180) }
    Write-Host ("status=failed error={0}{1}" -f $_.Exception.Message, $bodySummary)
    exit 1
} finally {
    if (-not [string]::IsNullOrWhiteSpace($csrfToken)) {
        try {
            Invoke-RestMethod -Method Post -Uri (Join-AdminUrl "/admin-api/v1/auth/logout") -Headers @{ "X-Admin-CSRF" = $csrfToken } -WebSession $session -TimeoutSec $TimeoutSec | Out-Null
            Write-Host "logout=ok"
        } catch {
            Write-Host ("logout=failed error={0}" -f $_.Exception.Message)
        }
    }
}

Write-Host ("summary bad={0} warn={1} manual_bad={2} manual_warn={3} launch_warn={4}" -f $bad, $warn, $manualBad, $manualWarn, $launchWarn)
if ($bad -gt 0) {
    Write-Host "status=failed"
    exit 1
}
if (($manualBad -gt 0 -or $manualWarn -gt 0) -and $FailOnManualAttention) {
    Write-Host "status=manual_attention"
    exit 2
}
if ($warn -gt 0) {
    Write-Host "status=attention"
    if ($FailOnWarning) {
        exit 2
    }
    exit 0
}

Write-Host "status=ready"
