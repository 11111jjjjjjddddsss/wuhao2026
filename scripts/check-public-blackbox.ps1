param(
    [int]$TimeoutSec = 12,
    [switch]$SkipHttpRedirectChecks
)

$ErrorActionPreference = "Stop"

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

Add-Type -AssemblyName System.Net.Http

function Add-ErrorItem {
    param([string]$Message)
    $errors.Add($Message) | Out-Null
}

function Add-WarningItem {
    param([string]$Message)
    $warnings.Add($Message) | Out-Null
}

function New-HttpClient {
    param([bool]$AllowRedirect)
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $AllowRedirect
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd("nongjiqiancha-blackbox/1.0")
    return $client
}

function Invoke-HttpProbe {
    param(
        [string]$Name,
        [string]$Url,
        [int[]]$ExpectedStatus,
        [string[]]$RequiredBodyMarkers = @(),
        [switch]$NoRedirect,
        [string]$ExpectedLocationPrefix = ""
    )

    $client = $null
    try {
        $client = New-HttpClient -AllowRedirect:(!$NoRedirect)
        $response = $client.GetAsync($Url).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        $location = ""
        if ($null -ne $response.Headers.Location) {
            $location = [string]$response.Headers.Location
        }
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $matched = $ExpectedStatus -contains $status
        $line = "probe=$Name status=$status"
        if (-not [string]::IsNullOrWhiteSpace($location)) {
            $line += " location=$location"
        }
        Write-Host $line
        if (-not $matched) {
            Add-ErrorItem "$Name expected_status=$($ExpectedStatus -join ',') actual=$status"
            return
        }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedLocationPrefix) -and -not $location.StartsWith($ExpectedLocationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            Add-ErrorItem "$Name expected_location_prefix=$ExpectedLocationPrefix actual=$location"
        }
        foreach ($marker in $RequiredBodyMarkers) {
            if ($body -notlike "*$marker*") {
                Add-ErrorItem "$Name missing_body_marker=$marker"
            }
        }
    } catch {
        Add-ErrorItem "$Name request_failed=$($_.Exception.Message)"
    } finally {
        if ($null -ne $client) {
            $client.Dispose()
        }
    }
}

Write-Host "== public blackbox =="

Invoke-HttpProbe `
    -Name "api_https_healthz" `
    -Url "https://api.nongjiqiancha.cn/healthz" `
    -ExpectedStatus @(200) `
    -RequiredBodyMarkers @(
        '"ok":true',
        '"auth_strict":true',
        '"bailian":"ok"',
        '"sms":"ok"',
        '"dev_order_endpoints":false',
        '"redis":"ok"',
        '"upload_storage":"oss"'
    )

Invoke-HttpProbe `
    -Name "site_https_root" `
    -Url "https://nongjiqiancha.cn/" `
    -ExpectedStatus @(200) `
    -RequiredBodyMarkers @(
        "2026031728",
        "11010602202723"
    )
Invoke-HttpProbe `
    -Name "site_www_https_root" `
    -Url "https://www.nongjiqiancha.cn/" `
    -ExpectedStatus @(200) `
    -RequiredBodyMarkers @(
        "2026031728",
        "11010602202723"
    )
Invoke-HttpProbe -Name "site_user_agreement" -Url "https://nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement")
Invoke-HttpProbe -Name "site_privacy_policy" -Url "https://nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy")
Invoke-HttpProbe -Name "site_gongan_icon" -Url "https://nongjiqiancha.cn/gongan.png" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "site_www_user_agreement" -Url "https://www.nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement")
Invoke-HttpProbe -Name "site_www_privacy_policy" -Url "https://www.nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy")
Invoke-HttpProbe -Name "site_www_gongan_icon" -Url "https://www.nongjiqiancha.cn/gongan.png" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "admin_https_root" -Url "https://admin.nongjiqiancha.cn/" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "admin_https_auth_me" -Url "https://admin.nongjiqiancha.cn/admin-api/v1/auth/me" -ExpectedStatus @(401)

if (-not $SkipHttpRedirectChecks) {
    Invoke-HttpProbe -Name "api_http_redirect" -Url "http://api.nongjiqiancha.cn/healthz" -ExpectedStatus @(301, 302, 307, 308) -NoRedirect -ExpectedLocationPrefix "https://api.nongjiqiancha.cn/"
    Invoke-HttpProbe -Name "site_http_redirect" -Url "http://nongjiqiancha.cn/" -ExpectedStatus @(301, 302, 307, 308) -NoRedirect -ExpectedLocationPrefix "https://nongjiqiancha.cn/"
    Invoke-HttpProbe -Name "site_www_http_redirect" -Url "http://www.nongjiqiancha.cn/" -ExpectedStatus @(301, 302, 307, 308) -NoRedirect -ExpectedLocationPrefix "https://www.nongjiqiancha.cn/"
    Invoke-HttpProbe -Name "admin_http_redirect" -Url "http://admin.nongjiqiancha.cn/" -ExpectedStatus @(301, 302, 307, 308) -NoRedirect -ExpectedLocationPrefix "https://admin.nongjiqiancha.cn/"
}

Write-Host
Write-Host "== summary =="
Write-Host "warnings=$($warnings.Count) errors=$($errors.Count)"
foreach ($warning in $warnings) {
    Write-Host "warning=$warning"
}
foreach ($errorItem in $errors) {
    Write-Host "error=$errorItem"
}

if ($errors.Count -gt 0) {
    Write-Host "status=failed"
    exit 1
}

Write-Host "status=ready"
