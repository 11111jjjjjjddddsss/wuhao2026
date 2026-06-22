param(
    [string]$BaseUrl = "https://admin.nongjiqiancha.cn",
    [string]$Username = $(if ($env:NONGJI_ADMIN_USERNAME) { $env:NONGJI_ADMIN_USERNAME } else { $env:ADMIN_SMOKE_USERNAME }),
    [string]$Password = $(if ($env:NONGJI_ADMIN_PASSWORD) { $env:NONGJI_ADMIN_PASSWORD } else { $env:ADMIN_SMOKE_PASSWORD }),
    [int]$TimeoutSec = 20,
    [switch]$RequireOwner,
    [switch]$SkipIfMissingCredentials
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
    $message = "missing admin smoke credentials; set NONGJI_ADMIN_USERNAME/NONGJI_ADMIN_PASSWORD or ADMIN_SMOKE_USERNAME/ADMIN_SMOKE_PASSWORD"
    if ($SkipIfMissingCredentials) {
        Write-Host "== admin authenticated smoke =="
        Write-Host "status=skipped reason=missing_credentials"
        exit 0
    }
    Write-Error $message
    exit 2
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrfToken = ""
$failed = 0

Write-Host "== admin authenticated smoke =="
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
    if ($RequireOwner -and $role -ne "owner") {
        Write-Error ("expected owner role for full smoke, got role={0}" -f $role)
        exit 4
    }
    Write-Host ("login=ok role={0} csrf=present" -f ($(if ($role) { $role } else { "unknown" })))

    $endpoints = @(
        @{ Name = "auth_me"; Path = "/admin-api/v1/auth/me" },
        @{ Name = "overview"; Path = "/admin-api/v1/overview" },
        @{ Name = "monitoring"; Path = "/admin-api/v1/monitoring" },
        @{ Name = "insights"; Path = "/admin-api/v1/insights" },
        @{ Name = "entitlements_summary"; Path = "/admin-api/v1/entitlements/summary" },
        @{ Name = "orders"; Path = "/admin-api/v1/orders?limit=1" },
        @{ Name = "users"; Path = "/admin-api/v1/users?limit=1" },
        @{ Name = "support_conversations"; Path = "/admin-api/v1/support/conversations?limit=1" },
        @{ Name = "app_logs"; Path = "/admin-api/v1/app-logs?limit=1&window=24h" },
        @{ Name = "audit_logs"; Path = "/admin-api/v1/audit-logs?limit=1" },
        @{ Name = "today_agri_cards"; Path = "/admin-api/v1/today-agri/cards?limit=1" },
        @{ Name = "app_update_android"; Path = "/admin-api/v1/app-update/android" },
        @{ Name = "app_update_events"; Path = "/admin-api/v1/app-update/android/events?limit=1" },
        @{ Name = "gift_card_summary"; Path = "/admin-api/v1/gift-cards/summary" },
        @{ Name = "gift_card_batches"; Path = "/admin-api/v1/gift-cards/batches?limit=1" },
        @{ Name = "gift_cards"; Path = "/admin-api/v1/gift-cards/cards?limit=1" },
        @{ Name = "gift_card_attempts"; Path = "/admin-api/v1/gift-cards/attempts?limit=1" },
        @{ Name = "account_deletion_requests"; Path = "/admin-api/v1/account-deletion-requests?limit=1" }
    )

    foreach ($endpoint in $endpoints) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri (Join-AdminUrl $endpoint.Path) -WebSession $session -TimeoutSec $TimeoutSec
            if ($null -eq $response) {
                throw "empty_response"
            }
            Write-Host ("endpoint={0} status=ok" -f $endpoint.Name)
        } catch {
            $failed += 1
            $body = Get-ErrorBody $_
            $bodySummary = if ([string]::IsNullOrWhiteSpace($body)) { "" } else { " body=" + ($body -replace '\s+', ' ').Substring(0, [Math]::Min(180, ($body -replace '\s+', ' ').Length)) }
            Write-Host ("endpoint={0} status=failed error={1}{2}" -f $endpoint.Name, $_.Exception.Message, $bodySummary)
        }
    }
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

if ($failed -gt 0) {
    Write-Host ("summary failed={0} status=failed" -f $failed)
    exit 1
}

Write-Host "summary failed=0 status=ready"
