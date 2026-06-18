param(
    [int]$TimeoutSec = 12,
    [int]$ExpectedAndroidUpdateVersionCode = 0,
    [int]$PreviousAndroidVersionCode = 0,
    [switch]$SkipHttpRedirectChecks,
    [switch]$SkipSecurityHeaderChecks
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

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
        [string]$Method = "GET",
        [string]$Body = "",
        [switch]$NoRedirect,
        [string]$ExpectedLocationPrefix = ""
    )

    $client = $null
    try {
        $client = New-HttpClient -AllowRedirect:(!$NoRedirect)
        if ($Method.ToUpperInvariant() -eq "POST") {
            $content = New-Object System.Net.Http.StringContent -ArgumentList @($Body, [System.Text.Encoding]::UTF8, "application/json")
            $response = $client.PostAsync($Url, $content).GetAwaiter().GetResult()
        } else {
            $response = $client.GetAsync($Url).GetAwaiter().GetResult()
        }
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

function Invoke-AdminAssetProbe {
    param(
        [string]$RootUrl = "https://admin.nongjiqiancha.cn/"
    )

    $client = $null
    try {
        $client = New-HttpClient -AllowRedirect:$true
        $response = $client.GetAsync($RootUrl).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        Write-Host "probe=admin_https_asset_manifest status=$status"
        if ($status -ne 200) {
            Add-ErrorItem "admin_https_asset_manifest expected_status=200 actual=$status"
            return
        }
        $match = [regex]::Match($body, 'src="(?<src>[^"]*/assets/[^"]+\.js)"')
        if (-not $match.Success) {
            Add-ErrorItem "admin_https_asset_manifest missing_first_js_asset"
            return
        }
        $src = $match.Groups["src"].Value
        $assetUrl = if ($src.StartsWith("http", [StringComparison]::OrdinalIgnoreCase)) {
            $src
        } elseif ($src.StartsWith("/")) {
            "https://admin.nongjiqiancha.cn$src"
        } else {
            "$RootUrl$src"
        }
        Invoke-HttpProbe -Name "admin_https_first_js" -Url $assetUrl -ExpectedStatus @(200)
    } catch {
        Add-ErrorItem "admin_https_asset_manifest request_failed=$($_.Exception.Message)"
    } finally {
        if ($null -ne $client) {
            $client.Dispose()
        }
    }
}

function Test-HeaderValueContains {
    param(
        [System.Net.Http.Headers.HttpResponseHeaders]$Headers,
        [System.Net.Http.Headers.HttpContentHeaders]$ContentHeaders,
        [string]$HeaderName,
        [string[]]$RequiredMarkers
    )

    $values = New-Object System.Collections.Generic.List[string]
    $headerValues = $null
    if ($Headers.TryGetValues($HeaderName, [ref]$headerValues)) {
        foreach ($value in $headerValues) {
            $values.Add([string]$value) | Out-Null
        }
    }
    $contentHeaderValues = $null
    if ($ContentHeaders.TryGetValues($HeaderName, [ref]$contentHeaderValues)) {
        foreach ($value in $contentHeaderValues) {
            $values.Add([string]$value) | Out-Null
        }
    }

    $joined = [string]::Join(", ", $values)
    if ([string]::IsNullOrWhiteSpace($joined)) {
        return [pscustomobject]@{
            Ok = $false
            Value = "<missing>"
            Missing = $RequiredMarkers -join "|"
        }
    }

    $missingMarkers = New-Object System.Collections.Generic.List[string]
    foreach ($marker in $RequiredMarkers) {
        if ($joined.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            $missingMarkers.Add($marker) | Out-Null
        }
    }

    return [pscustomobject]@{
        Ok = ($missingMarkers.Count -eq 0)
        Value = $joined
        Missing = ($missingMarkers -join "|")
    }
}

function Invoke-SecurityHeaderProbe {
    param(
        [string]$Name,
        [string]$Url,
        [hashtable]$RequiredHeaders
    )

    $client = $null
    try {
        $client = New-HttpClient -AllowRedirect:$true
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Head, $Url)
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        Write-Host "probe=$Name status=$status"
        if ($status -lt 200 -or $status -ge 400) {
            Add-ErrorItem "$Name expected_2xx_or_3xx actual=$status"
            return
        }
        foreach ($headerName in ($RequiredHeaders.Keys | Sort-Object)) {
            $markers = [string[]]$RequiredHeaders[$headerName]
            $result = Test-HeaderValueContains -Headers $response.Headers -ContentHeaders $response.Content.Headers -HeaderName $headerName -RequiredMarkers $markers
            if (-not $result.Ok) {
                Add-ErrorItem "$Name header=$headerName missing_marker=$($result.Missing) actual=$($result.Value)"
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
$appUpdateProbeVersionCode = [Math]::Max(0, $PreviousAndroidVersionCode)
$appUpdateRequiredMarkers = @(
    '"platform":"android"'
)
if ($ExpectedAndroidUpdateVersionCode -gt 0) {
    $appUpdateRequiredMarkers += '"has_update":true'
    $appUpdateRequiredMarkers += ('"latest_version_code":' + $ExpectedAndroidUpdateVersionCode)
} elseif ($PreviousAndroidVersionCode -gt 0) {
    $appUpdateRequiredMarkers += '"has_update":true'
} else {
    $appUpdateRequiredMarkers += '"has_update":false'
}
Invoke-HttpProbe `
    -Name "api_app_update_public_probe" `
    -Url "https://api.nongjiqiancha.cn/api/app/update?platform=android&version_code=$appUpdateProbeVersionCode&version_name=blackbox" `
    -ExpectedStatus @(200) `
    -RequiredBodyMarkers $appUpdateRequiredMarkers
Invoke-HttpProbe `
    -Name "api_internal_today_agri_manual_unauthorized" `
    -Url "https://api.nongjiqiancha.cn/internal/jobs/today-agri-card/manual" `
    -ExpectedStatus @(401) `
    -Method "POST" `
    -Body "{}"
Invoke-HttpProbe `
    -Name "api_upload_missing_file_probe" `
    -Url "https://api.nongjiqiancha.cn/uploads/blackbox-missing-file.jpg" `
    -ExpectedStatus @(404)

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
Invoke-HttpProbe -Name "site_user_agreement" -Url "https://nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement", 'name="nongji-legal-version" content="20260618"', 'name="nongji-legal-section" content="user-agreement-usage-norms"')
Invoke-HttpProbe -Name "site_privacy_policy" -Url "https://nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy", 'name="nongji-legal-version" content="20260618"', 'name="nongji-privacy-section" content="long-term-memory"')
Invoke-HttpProbe -Name "site_gongan_icon" -Url "https://nongjiqiancha.cn/gongan.png" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "site_test_apks_disabled" -Url "https://nongjiqiancha.cn/test-apks/" -ExpectedStatus @(404)
Invoke-HttpProbe -Name "site_www_user_agreement" -Url "https://www.nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement", 'name="nongji-legal-version" content="20260618"', 'name="nongji-legal-section" content="user-agreement-usage-norms"')
Invoke-HttpProbe -Name "site_www_privacy_policy" -Url "https://www.nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy", 'name="nongji-legal-version" content="20260618"', 'name="nongji-privacy-section" content="long-term-memory"')
Invoke-HttpProbe -Name "site_www_gongan_icon" -Url "https://www.nongjiqiancha.cn/gongan.png" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "admin_https_root" -Url "https://admin.nongjiqiancha.cn/" -ExpectedStatus @(200) -RequiredBodyMarkers @('id="app"', "/assets/")
Invoke-AdminAssetProbe
Invoke-HttpProbe -Name "admin_https_auth_me" -Url "https://admin.nongjiqiancha.cn/admin-api/v1/auth/me" -ExpectedStatus @(401)
foreach ($adminPath in @(
    "/admin-api/v1/monitoring",
    "/admin-api/v1/users",
    "/admin-api/v1/app-logs",
    "/admin-api/v1/audit-logs",
    "/admin-api/v1/support/conversations",
    "/admin-api/v1/gift-cards/summary",
    "/admin-api/v1/today-agri/cards"
)) {
    $probeName = "admin_protected_" + (
        $adminPath.Trim("/") -replace "^admin-api/v1/", "" -replace "[^A-Za-z0-9]+", "_"
    ).Trim("_")
    Invoke-HttpProbe -Name $probeName -Url "https://admin.nongjiqiancha.cn$adminPath" -ExpectedStatus @(401, 403)
}

foreach ($adminWritePath in @(
    "/admin-api/v1/app-update/android",
    "/admin-api/v1/gift-cards/batches",
    "/admin-api/v1/gift-cards/void",
    "/admin-api/v1/support/messages",
    "/admin-api/v1/support/conversations/status",
    "/admin-api/v1/today-agri/generate",
    "/admin-api/v1/today-agri/manual"
)) {
    $probeName = "admin_protected_post_" + (
        $adminWritePath.Trim("/") -replace "^admin-api/v1/", "" -replace "[^A-Za-z0-9]+", "_"
    ).Trim("_")
    Invoke-HttpProbe -Name $probeName -Url "https://admin.nongjiqiancha.cn$adminWritePath" -ExpectedStatus @(401, 403) -Method "POST" -Body "{}"
}

if (-not $SkipSecurityHeaderChecks) {
    $baseSecurityHeaders = @{
        "Strict-Transport-Security" = @("max-age=", "includeSubDomains")
        "X-Content-Type-Options" = @("nosniff")
        "X-Frame-Options" = @("DENY")
        "Referrer-Policy" = @("strict-origin-when-cross-origin")
    }
    $staticSiteSecurityHeaders = $baseSecurityHeaders.Clone()
    $staticSiteSecurityHeaders["Permissions-Policy"] = @("camera=()", "microphone=()", "payment=()")
    $staticSiteSecurityHeaders["Content-Security-Policy"] = @("default-src 'self'", "script-src 'self'", "object-src 'none'", "frame-ancestors 'none'")
    $adminSecurityHeaders = $staticSiteSecurityHeaders.Clone()
    $adminSecurityHeaders["Content-Security-Policy"] = @("default-src 'self'", "connect-src 'self'", "script-src 'self'", "object-src 'none'", "frame-ancestors 'none'", "form-action 'self'")

    Invoke-SecurityHeaderProbe -Name "api_security_headers" -Url "https://api.nongjiqiancha.cn/healthz" -RequiredHeaders $baseSecurityHeaders
    Invoke-SecurityHeaderProbe -Name "site_security_headers" -Url "https://nongjiqiancha.cn/" -RequiredHeaders $staticSiteSecurityHeaders
    Invoke-SecurityHeaderProbe -Name "site_www_security_headers" -Url "https://www.nongjiqiancha.cn/" -RequiredHeaders $staticSiteSecurityHeaders
    Invoke-SecurityHeaderProbe -Name "admin_security_headers" -Url "https://admin.nongjiqiancha.cn/" -RequiredHeaders $adminSecurityHeaders
}

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
