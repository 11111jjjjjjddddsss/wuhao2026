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

function Test-OfficialAndroidApkUrl {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $false
    }
    $parsed = $null
    if (-not [System.Uri]::TryCreate($Url.Trim(), [System.UriKind]::Absolute, [ref]$parsed)) {
        return $false
    }
    if ($parsed.Scheme -ne "https" -or
        $parsed.Host.ToLowerInvariant() -ne "download.nongjiqiancha.cn" -or
        -not ($parsed.IsDefaultPort -or $parsed.Port -eq 443) -or
        -not [string]::IsNullOrWhiteSpace($parsed.UserInfo) -or
        -not [string]::IsNullOrWhiteSpace($parsed.Query) -or
        -not [string]::IsNullOrWhiteSpace($parsed.Fragment)) {
        return $false
    }
    $path = $parsed.AbsolutePath.ToLowerInvariant()
    try {
        $decodedPath = [System.Uri]::UnescapeDataString($parsed.AbsolutePath).ToLowerInvariant()
    } catch {
        return $false
    }
    if (-not $path.StartsWith("/android/releases/") -or -not $decodedPath.StartsWith("/android/releases/")) {
        return $false
    }
    if (-not $path.EndsWith(".apk") -or -not $decodedPath.EndsWith(".apk")) {
        return $false
    }
    if ($path.Contains("..") -or $decodedPath.Contains("..")) {
        return $false
    }
    return $decodedPath -notmatch "test-apks|debug|internal|staging"
}

function Invoke-AppUpdatePublicProbe {
    param(
        [int]$ProbeVersionCode,
        [int]$ExpectedVersionCode
    )

    $client = $null
    $url = "https://api.nongjiqiancha.cn/api/app/update?platform=android&version_code=$ProbeVersionCode&version_name=blackbox"
    try {
        $client = New-HttpClient -AllowRedirect:$true
        $response = $client.GetAsync($url).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        Write-Host "probe=api_app_update_public_probe status=$status"
        if ($status -ne 200) {
            Add-ErrorItem "api_app_update_public_probe expected_status=200 actual=$status"
            return
        }
        $json = $body | ConvertFrom-Json
        if ([string]$json.platform -ne "android") {
            Add-ErrorItem "api_app_update_public_probe platform_not_android"
        }
        if ($ExpectedVersionCode -gt 0) {
            if ($json.has_update -ne $true) {
                Add-ErrorItem "api_app_update_public_probe expected_has_update=true"
            }
            if ([int]$json.latest_version_code -ne $ExpectedVersionCode) {
                Add-ErrorItem "api_app_update_public_probe expected_latest_version_code=$ExpectedVersionCode actual=$($json.latest_version_code)"
            }
            if (-not (Test-OfficialAndroidApkUrl ([string]$json.apk_url))) {
                Add-ErrorItem "api_app_update_public_probe invalid_official_apk_url"
            }
            if ([string]$json.apk_sha256 -notmatch '^[0-9a-fA-F]{64}$') {
                Add-ErrorItem "api_app_update_public_probe invalid_apk_sha256"
            }
            if ([int64]$json.file_size_bytes -le 0) {
                Add-ErrorItem "api_app_update_public_probe invalid_file_size_bytes"
            }
        } elseif ($ProbeVersionCode -gt 0) {
            if ($json.has_update -ne $true) {
                Add-ErrorItem "api_app_update_public_probe expected_has_update=true"
            }
        } else {
            Write-Host "api_app_update_public_probe_has_update=$($json.has_update)"
        }
    } catch {
        Add-ErrorItem "api_app_update_public_probe request_failed=$($_.Exception.Message)"
    } finally {
        if ($null -ne $client) {
            $client.Dispose()
        }
    }
}

function Invoke-AndroidDownloadDomainProbe {
    $scriptPath = Join-Path $PSScriptRoot "check-android-download-domain.ps1"
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        Add-ErrorItem "android_download_domain_probe script_missing"
        return
    }
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        Write-Host "probe=android_download_domain status=ready"
        return
    }
    $safeOutput = (($output | Out-String) -replace '\s+', ' ').Trim()
    Write-Host "probe=android_download_domain status=failed exit=$exitCode"
    Add-ErrorItem "android_download_domain_probe failed exit=$exitCode output=$safeOutput"
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
if ($ExpectedAndroidUpdateVersionCode -gt 0 -and $appUpdateProbeVersionCode -le 0) {
    Add-WarningItem "ExpectedAndroidUpdateVersionCode was set without PreviousAndroidVersionCode; app update probe uses version_code=0 and does not prove a real old installed version will see the update"
}
Invoke-AppUpdatePublicProbe -ProbeVersionCode $appUpdateProbeVersionCode -ExpectedVersionCode $ExpectedAndroidUpdateVersionCode
Invoke-AndroidDownloadDomainProbe
$internalPublicProbes = @(
    @{ Name = "api_internal_today_agri_generate_public_rejected"; Method = "POST"; Path = "/internal/jobs/today-agri-card/generate"; Body = "{}" },
    @{ Name = "api_internal_today_agri_status_public_rejected"; Method = "GET"; Path = "/internal/jobs/today-agri-card/status?day_cn=20260618"; Body = "" },
    @{ Name = "api_internal_today_agri_probe_public_rejected"; Method = "POST"; Path = "/internal/jobs/today-agri-card/probe"; Body = "{}" },
    @{ Name = "api_internal_today_agri_manual_public_rejected"; Method = "POST"; Path = "/internal/jobs/today-agri-card/manual"; Body = "{}" },
    @{ Name = "api_internal_memory_probe_public_rejected"; Method = "POST"; Path = "/internal/jobs/memory-document/probe"; Body = "{}" },
    @{ Name = "api_internal_app_logs_public_rejected"; Method = "GET"; Path = "/internal/app/logs"; Body = "" },
    @{ Name = "api_internal_admin_audit_logs_public_rejected"; Method = "GET"; Path = "/internal/admin/audit-logs"; Body = "" },
    @{ Name = "api_internal_support_conversations_public_rejected"; Method = "GET"; Path = "/internal/support/conversations"; Body = "" },
    @{ Name = "api_internal_support_messages_public_rejected"; Method = "GET"; Path = "/internal/support/messages?user_id=acct_blackbox"; Body = "" },
    @{ Name = "api_internal_support_create_message_public_rejected"; Method = "POST"; Path = "/internal/support/messages"; Body = '{"user_id":"acct_blackbox","body":"blackbox"}' }
)
foreach ($probe in $internalPublicProbes) {
    Invoke-HttpProbe `
        -Name $probe.Name `
        -Url "https://api.nongjiqiancha.cn$($probe.Path)" `
        -ExpectedStatus @(401, 403) `
        -Method $probe.Method `
        -Body $probe.Body
}
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
Invoke-HttpProbe -Name "site_user_agreement" -Url "https://nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement", 'name="nongji-legal-version" content="20260620"', 'name="nongji-legal-section" content="user-agreement-usage-norms"')
Invoke-HttpProbe -Name "site_privacy_policy" -Url "https://nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy", 'name="nongji-legal-version" content="20260620"', 'name="nongji-privacy-section" content="long-term-memory"')
Invoke-HttpProbe -Name "site_third_party_sharing" -Url "https://nongjiqiancha.cn/legal/third-party-sharing/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-third-party-sharing", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_personal_info_list" -Url "https://nongjiqiancha.cn/legal/personal-info-list/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-personal-info-list", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_app_permissions" -Url "https://nongjiqiancha.cn/legal/app-permissions/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-app-permissions", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_risk_notice" -Url "https://nongjiqiancha.cn/legal/risk-notice/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-risk-notice", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_gongan_icon" -Url "https://nongjiqiancha.cn/gongan.png" -ExpectedStatus @(200)
Invoke-HttpProbe -Name "site_test_apks_disabled" -Url "https://nongjiqiancha.cn/test-apks/" -ExpectedStatus @(404)
Invoke-HttpProbe -Name "site_www_user_agreement" -Url "https://www.nongjiqiancha.cn/legal/user-agreement/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-user-agreement", 'name="nongji-legal-version" content="20260620"', 'name="nongji-legal-section" content="user-agreement-usage-norms"')
Invoke-HttpProbe -Name "site_www_privacy_policy" -Url "https://www.nongjiqiancha.cn/legal/privacy-policy/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-privacy-policy", 'name="nongji-legal-version" content="20260620"', 'name="nongji-privacy-section" content="long-term-memory"')
Invoke-HttpProbe -Name "site_www_third_party_sharing" -Url "https://www.nongjiqiancha.cn/legal/third-party-sharing/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-third-party-sharing", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_www_personal_info_list" -Url "https://www.nongjiqiancha.cn/legal/personal-info-list/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-personal-info-list", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_www_app_permissions" -Url "https://www.nongjiqiancha.cn/legal/app-permissions/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-app-permissions", 'name="nongji-legal-version" content="20260620"')
Invoke-HttpProbe -Name "site_www_risk_notice" -Url "https://www.nongjiqiancha.cn/legal/risk-notice/" -ExpectedStatus @(200) -RequiredBodyMarkers @("nongji-page-risk-notice", 'name="nongji-legal-version" content="20260620"')
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
