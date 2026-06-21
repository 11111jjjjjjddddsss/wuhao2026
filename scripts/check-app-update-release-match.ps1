param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$BaseUrl = "https://admin.nongjiqiancha.cn",
    [string]$Username = $(if ($env:NONGJI_ADMIN_USERNAME) { $env:NONGJI_ADMIN_USERNAME } else { $env:ADMIN_SMOKE_USERNAME }),
    [string]$Password = $(if ($env:NONGJI_ADMIN_PASSWORD) { $env:NONGJI_ADMIN_PASSWORD } else { $env:ADMIN_SMOKE_PASSWORD }),
    [int]$TimeoutSec = 30,
    [switch]$RequireEnabled,
    [switch]$SkipIfMissingCredentials,
    [string]$ExpectedApkUrl = "",
    [switch]$VerifyDownload,
    [int]$PreviousVersionCode = 0,
    [string]$PublicApiBaseUrl = "https://api.nongjiqiancha.cn",
    [switch]$ProbePreviousVersionUpdate
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

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

function Add-Failure {
    param(
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Message
    )
    $Failures.Add($Message) | Out-Null
}

function Normalize-Hex {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    return ($Value -replace '[^0-9A-Fa-f]', '').ToLowerInvariant()
}

function Get-FileSha256Hex {
    param([string]$Path)
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            return (($hash | ForEach-Object { $_.ToString("x2") }) -join "")
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Save-HttpDownload {
    param(
        [string]$Url,
        [string]$OutFile,
        [int]$TimeoutSeconds
    )
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $true
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    try {
        $response = $client.GetAsync($Url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        try {
            if (-not $response.IsSuccessStatusCode) {
                throw "download failed with HTTP $([int]$response.StatusCode)"
            }
            $finalUrl = [string]$response.RequestMessage.RequestUri.AbsoluteUri
            $inputStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
            try {
                $outputStream = [System.IO.File]::Create($OutFile)
                try {
                    $inputStream.CopyTo($outputStream)
                } finally {
                    $outputStream.Dispose()
                }
            } finally {
                $inputStream.Dispose()
            }
            return $finalUrl
        } finally {
            $response.Dispose()
        }
    } finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

function Test-AllowedReleaseApkUri {
    param(
        [System.Uri]$Uri,
        [string]$Label
    )
    if ($null -eq $Uri -or $Uri.Scheme -ne "https") {
        Add-Failure $failures "$Label URL must be absolute https"
        return
    }
    if ($Uri.Host.ToLowerInvariant() -ne "download.nongjiqiancha.cn") {
        Add-Failure $failures "$Label URL host must be download.nongjiqiancha.cn"
    }
    if (-not [string]::IsNullOrWhiteSpace($Uri.UserInfo) -or
        -not [string]::IsNullOrWhiteSpace($Uri.Query) -or
        -not [string]::IsNullOrWhiteSpace($Uri.Fragment)) {
        Add-Failure $failures "$Label URL must not contain userinfo, query strings or fragments"
    }
    $path = $Uri.AbsolutePath.ToLowerInvariant()
    $decodedPath = $path
    try {
        $decodedPath = [System.Uri]::UnescapeDataString($Uri.AbsolutePath).ToLowerInvariant()
    } catch {
        Add-Failure $failures "$Label URL path contains invalid percent-encoding"
        return
    }
    if (-not $path.StartsWith("/android/releases/") -or -not $decodedPath.StartsWith("/android/releases/")) {
        Add-Failure $failures "$Label URL path should stay under /android/releases/"
    }
    if (-not $path.EndsWith(".apk") -or -not $decodedPath.EndsWith(".apk")) {
        Add-Failure $failures "$Label URL path should end with .apk"
    }
    if ($path.Contains("..") -or $decodedPath.Contains("..")) {
        Add-Failure $failures "$Label URL path must not contain parent traversal"
    }
    if ($decodedPath -match "test-apks|debug|internal|staging") {
        Add-Failure $failures "$Label URL looks like an internal test APK URL; do not configure debug/internal/staging/test-apks links for app update"
    }
}

function Join-AdminUrl {
    param([string]$Path)
    $base = $BaseUrl.TrimEnd("/")
    $pathPart = if ($Path.StartsWith("/")) { $Path } else { "/$Path" }
    return "$base$pathPart"
}

function Join-PublicApiUrl {
    param([string]$Path)
    $base = $PublicApiBaseUrl.TrimEnd("/")
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

function Read-ReleaseArtifact {
    $artifactScript = Join-Path $PSScriptRoot "check-android-release-artifact.ps1"
    $artifactArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $artifactScript,
        "-RepoRoot",
        $RepoRoot
    )
    if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
        $artifactArgs += @("-ApkPath", $ApkPath)
    }

    $output = @(& powershell.exe @artifactArgs *>&1)
    $exitCode = $LASTEXITCODE
    $kv = @{}
    foreach ($item in $output) {
        $line = $item.ToString()
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            Write-Host $line
        }
        if ($line -match "^([A-Za-z0-9_]+)=(.*)$") {
            $kv[$Matches[1]] = $Matches[2].Trim()
        }
    }
    if ($null -ne $exitCode -and $exitCode -ne 0) {
        throw "release artifact check failed with exit code $exitCode"
    }

    foreach ($key in @("apk_version_code", "apk_version_name", "apk_size_bytes", "apk_sha256", "release_artifact_status")) {
        if (-not $kv.ContainsKey($key)) {
            throw "release artifact output is missing $key"
        }
    }
    if ($kv["release_artifact_status"] -ne "ready") {
        throw "release artifact status is not ready"
    }

    return [pscustomobject]@{
        VersionCode = [int]$kv["apk_version_code"]
        VersionName = [string]$kv["apk_version_name"]
        SizeBytes = [int64]$kv["apk_size_bytes"]
        Sha256 = Normalize-Hex $kv["apk_sha256"]
    }
}

function Read-AdminAppUpdateConfig {
    if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
        $message = "missing admin smoke credentials; set NONGJI_ADMIN_USERNAME/NONGJI_ADMIN_PASSWORD or ADMIN_SMOKE_USERNAME/ADMIN_SMOKE_PASSWORD"
        if ($SkipIfMissingCredentials) {
            Write-Host "admin_config_status=skipped reason=missing_credentials"
            return $null
        }
        throw $message
    }

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrfToken = ""
    try {
        $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
        $login = Invoke-RestMethod -Method Post -Uri (Join-AdminUrl "/admin-api/v1/auth/login") -Body $loginBody -ContentType "application/json" -WebSession $session -TimeoutSec $TimeoutSec
        $csrfToken = [string]$login.csrf_token
        if ([string]::IsNullOrWhiteSpace($csrfToken)) {
            throw "login succeeded but csrf_token is missing"
        }
        $role = [string]$login.admin_user.role
        Write-Host ("admin_login=ok role={0}" -f ($(if ($role) { $role } else { "unknown" })))
        return Invoke-RestMethod -Method Get -Uri (Join-AdminUrl "/admin-api/v1/app-update/android") -WebSession $session -TimeoutSec $TimeoutSec
    } catch {
        $body = Get-ErrorBody $_
        $bodySummary = if ([string]::IsNullOrWhiteSpace($body)) { "" } else { " body=" + (($body -replace '\s+', ' ').Substring(0, [Math]::Min(180, ($body -replace '\s+', ' ').Length))) }
        throw "admin app update config read failed: $($_.Exception.Message)$bodySummary"
    } finally {
        if (-not [string]::IsNullOrWhiteSpace($csrfToken)) {
            try {
                Invoke-RestMethod -Method Post -Uri (Join-AdminUrl "/admin-api/v1/auth/logout") -Headers @{ "X-Admin-CSRF" = $csrfToken } -WebSession $session -TimeoutSec $TimeoutSec | Out-Null
                Write-Host "admin_logout=ok"
            } catch {
                Write-Host ("admin_logout=failed error={0}" -f $_.Exception.Message)
            }
        }
    }
}

Write-Host "== app update release match =="
Write-Host ("base_url={0}" -f $BaseUrl)
if ($PreviousVersionCode -gt 0) {
    Write-Host ("previous_version_code={0}" -f $PreviousVersionCode)
}

$artifact = Read-ReleaseArtifact
Write-Host ("local_apk_version_code={0}" -f $artifact.VersionCode)
Write-Host ("local_apk_version_name={0}" -f $artifact.VersionName)
Write-Host ("local_apk_size_bytes={0}" -f $artifact.SizeBytes)
Write-Host ("local_apk_sha256={0}" -f $artifact.Sha256)

$config = Read-AdminAppUpdateConfig
if ($null -eq $config) {
    Write-Host "release_match_status=skipped"
    exit 0
}

$failures = [System.Collections.Generic.List[string]]::new()
$adminSha256 = Normalize-Hex ([string]$config.apk_sha256)
$adminVersionCode = [int]$config.latest_version_code
$adminVersionName = [string]$config.latest_version_name
$adminSizeBytes = [int64]$config.file_size_bytes
$adminApkUrl = [string]$config.apk_url
$adminEnabled = [bool]$config.enabled
$adminSource = [string]$config.source
$adminArtifactsComplete = [bool]$config.download_artifacts_complete
$adminConfigValid = [bool]$config.config_valid
$adminForceUpdate = [bool]$config.force_update

Write-Host ("admin_source={0}" -f ($(if ($adminSource) { $adminSource } else { "unknown" })))
Write-Host ("admin_enabled={0}" -f $adminEnabled)
Write-Host ("admin_config_valid={0}" -f $adminConfigValid)
Write-Host ("admin_download_artifacts_complete={0}" -f $adminArtifactsComplete)
Write-Host ("admin_force_update={0}" -f $adminForceUpdate)
Write-Host ("admin_latest_version_code={0}" -f $adminVersionCode)
Write-Host ("admin_latest_version_name={0}" -f $adminVersionName)
Write-Host ("admin_file_size_bytes={0}" -f $adminSizeBytes)
Write-Host ("admin_apk_sha256={0}" -f $adminSha256)

if ($adminForceUpdate) {
    Add-Failure $failures "admin app update force_update must stay false for the current ordinary-update release flow"
}
if ($RequireEnabled -and -not $adminEnabled) {
    Add-Failure $failures "admin app update config is not enabled"
}
if (-not $adminConfigValid) {
    Add-Failure $failures "admin app update config_valid is false"
}
if (-not $adminArtifactsComplete) {
    Add-Failure $failures "admin app update download_artifacts_complete is false"
}
if ($adminVersionCode -ne $artifact.VersionCode) {
    Add-Failure $failures "versionCode mismatch. local=$($artifact.VersionCode) admin=$adminVersionCode"
}
if ($adminVersionName -ne $artifact.VersionName) {
    Add-Failure $failures "versionName mismatch. local=$($artifact.VersionName) admin=$adminVersionName"
}
if ($adminSizeBytes -ne $artifact.SizeBytes) {
    Add-Failure $failures "file size mismatch. local=$($artifact.SizeBytes) admin=$adminSizeBytes"
}
if ($adminSha256 -ne $artifact.Sha256) {
    Add-Failure $failures "SHA-256 mismatch. local=$($artifact.Sha256) admin=$adminSha256"
}
if ($PreviousVersionCode -gt 0) {
    if ($artifact.VersionCode -le $PreviousVersionCode) {
        Add-Failure $failures "local APK versionCode must be greater than PreviousVersionCode. previous=$PreviousVersionCode local=$($artifact.VersionCode)"
    }
    if ($adminVersionCode -le $PreviousVersionCode) {
        Add-Failure $failures "admin latest_version_code must be greater than PreviousVersionCode. previous=$PreviousVersionCode admin=$adminVersionCode"
    }
}

$parsedApkUrl = $null
if ([string]::IsNullOrWhiteSpace($adminApkUrl)) {
    Add-Failure $failures "admin APK URL is empty"
} elseif (-not [System.Uri]::TryCreate($adminApkUrl, [System.UriKind]::Absolute, [ref]$parsedApkUrl) -or $parsedApkUrl.Scheme -ne "https") {
	Add-Failure $failures "admin APK URL must be an absolute https URL"
} else {
	Write-Host ("admin_apk_url_host={0}" -f $parsedApkUrl.Host)
	Test-AllowedReleaseApkUri -Uri $parsedApkUrl -Label "admin APK"
	if ($parsedApkUrl.Host.ToLowerInvariant() -ne "download.nongjiqiancha.cn") {
		Add-Failure $failures "admin APK URL host must be download.nongjiqiancha.cn"
	}
    $apkUrlText = $adminApkUrl.ToLowerInvariant()
    try {
        $apkUrlText = $apkUrlText + " " + [System.Uri]::UnescapeDataString($parsedApkUrl.AbsolutePath).ToLowerInvariant()
        $apkUrlText = $apkUrlText + " " + [System.Uri]::UnescapeDataString($adminApkUrl).ToLowerInvariant()
    } catch {
        Add-Failure $failures "admin APK URL contains invalid percent-encoding"
    }
    if ($apkUrlText -match "test-apks|debug|internal|staging") {
        Add-Failure $failures "admin APK URL looks like an internal test APK URL; do not configure debug/internal/staging/test-apks links for app update"
    }
    $signedQueryKeys = @("expires", "signature", "ossaccesskeyid", "security-token", "x-oss-expires", "x-oss-signature", "x-oss-credential", "x-oss-security-token")
    foreach ($key in $parsedApkUrl.Query.TrimStart("?").Split("&", [System.StringSplitOptions]::RemoveEmptyEntries)) {
        $queryKey = ($key -split "=", 2)[0].ToLowerInvariant()
        try {
            $queryKey = [System.Uri]::UnescapeDataString($queryKey).ToLowerInvariant()
        } catch {
            Add-Failure $failures "admin APK URL query contains invalid percent-encoding"
            break
        }
        if ($signedQueryKeys -contains $queryKey) {
            Add-Failure $failures "admin APK URL looks like a short-lived signed URL; configure a stable release URL or a backend on-demand signing flow"
            break
        }
    }
    if (-not $parsedApkUrl.AbsolutePath.ToLowerInvariant().EndsWith(".apk")) {
        Add-Failure $failures "admin APK URL path should end with .apk"
    }
    if (-not $parsedApkUrl.AbsolutePath.ToLowerInvariant().StartsWith("/android/releases/")) {
        Add-Failure $failures "admin APK URL path should stay under /android/releases/"
    }
}
if (-not [string]::IsNullOrWhiteSpace($ExpectedApkUrl) -and $adminApkUrl -ne $ExpectedApkUrl) {
    Add-Failure $failures "APK URL mismatch against ExpectedApkUrl"
}

if ($VerifyDownload -and $failures.Count -eq 0) {
    $tempApk = Join-Path ([System.IO.Path]::GetTempPath()) ("nongjiqiancha-release-check-{0}.apk" -f ([guid]::NewGuid().ToString("N")))
    try {
        Write-Host "download_verify=started"
        $finalDownloadUrl = Save-HttpDownload -Url $adminApkUrl -OutFile $tempApk -TimeoutSeconds $TimeoutSec
        $parsedFinalDownloadUrl = $null
		if (-not [System.Uri]::TryCreate($finalDownloadUrl, [System.UriKind]::Absolute, [ref]$parsedFinalDownloadUrl) -or $parsedFinalDownloadUrl.Scheme -ne "https") {
			Add-Failure $failures "download final URL must remain https. final=$finalDownloadUrl"
		} else {
			Write-Host ("download_final_url_host={0}" -f $parsedFinalDownloadUrl.Host)
			Test-AllowedReleaseApkUri -Uri $parsedFinalDownloadUrl -Label "download final"
		}
        $downloadItem = Get-Item -LiteralPath $tempApk
        $downloadSha256 = Get-FileSha256Hex $tempApk
        Write-Host ("download_size_bytes={0}" -f $downloadItem.Length)
        Write-Host ("download_sha256={0}" -f $downloadSha256)
        if ($downloadItem.Length -ne $artifact.SizeBytes) {
            Add-Failure $failures "downloaded APK file size mismatch. local=$($artifact.SizeBytes) downloaded=$($downloadItem.Length)"
        }
        if ($downloadSha256 -ne $artifact.Sha256) {
            Add-Failure $failures "downloaded APK SHA-256 mismatch. local=$($artifact.Sha256) downloaded=$downloadSha256"
        }
        Write-Host "download_verify=ok"
    } finally {
        Remove-Item -LiteralPath $tempApk -Force -ErrorAction SilentlyContinue
    }
}

if ($ProbePreviousVersionUpdate -and $failures.Count -eq 0) {
    if ($PreviousVersionCode -le 0) {
        Add-Failure $failures "ProbePreviousVersionUpdate requires PreviousVersionCode > 0"
    } else {
        $probeUrl = Join-PublicApiUrl ("/api/app/update?platform=android&version_code={0}&version_name=previous" -f $PreviousVersionCode)
        Write-Host ("public_update_probe_url_host={0}" -f ([System.Uri]$probeUrl).Host)
        $probe = Invoke-RestMethod -Method Get -Uri $probeUrl -TimeoutSec $TimeoutSec
        $probeHasUpdate = [bool]$probe.has_update
        $probeLatestVersionCode = [int]$probe.latest_version_code
        $probeForceUpdate = [bool]$probe.force_update
        Write-Host ("public_update_probe_has_update={0}" -f $probeHasUpdate)
        Write-Host ("public_update_probe_latest_version_code={0}" -f $probeLatestVersionCode)
        Write-Host ("public_update_probe_force_update={0}" -f $probeForceUpdate)
        if (-not $probeHasUpdate) {
            Add-Failure $failures "public /api/app/update does not report an update for PreviousVersionCode=$PreviousVersionCode"
        }
        if ($probeLatestVersionCode -ne $artifact.VersionCode) {
            Add-Failure $failures "public /api/app/update latest version mismatch. local=$($artifact.VersionCode) public=$probeLatestVersionCode"
        }
        if ($probeForceUpdate) {
            Add-Failure $failures "public /api/app/update force_update must stay false for the current ordinary-update release flow"
        }

        $currentProbeUrl = Join-PublicApiUrl ("/api/app/update?platform=android&version_code={0}&version_name={1}" -f $artifact.VersionCode, [uri]::EscapeDataString($artifact.VersionName))
        Write-Host ("public_current_update_probe_url_host={0}" -f ([System.Uri]$currentProbeUrl).Host)
        $currentProbe = Invoke-RestMethod -Method Get -Uri $currentProbeUrl -TimeoutSec $TimeoutSec
        $currentProbeHasUpdate = [bool]$currentProbe.has_update
        Write-Host ("public_current_update_probe_has_update={0}" -f $currentProbeHasUpdate)
        if ($currentProbeHasUpdate) {
            Add-Failure $failures "public /api/app/update must not report an update for the current APK VersionCode=$($artifact.VersionCode)"
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "release_match_status=failed"
    foreach ($failure in $failures) {
        Write-Host "ERROR: $failure"
    }
    exit 1
}

Write-Host "release_match_status=ready"
