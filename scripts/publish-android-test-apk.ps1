param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$OssPrefix = "test-apks/debug",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$OssDownloadDomain = "download.nongjiqiancha.cn",
    [string]$ExpectedPackageName = "com.nongjiqiancha",
    [string]$PublicInfoPath = "",
    [int]$ExpireHours = 72,
    [switch]$NoBuild,
    [switch]$AllowDirty,
    [switch]$UseOssSignedDownload,
    [switch]$UseEcsDownloadFallback,
    [switch]$SkipEcsDownloadPublish
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "required command not found: $Name"
    }
}

function Get-GitOutput {
    param([string[]]$GitArgs)
    $output = & git -C $RepoRoot @GitArgs 2>$null
    if ($LASTEXITCODE -ne 0) {
        return ""
    }
    if ($null -eq $output) {
        return ""
    }
    return (($output | Out-String).Trim())
}

function Ensure-CleanGitTree {
    if ($AllowDirty) {
        Write-Host "git_tree_status=dirty_allowed"
        return
    }

    $status = Get-GitOutput -GitArgs @("status", "--porcelain")
    if (-not [string]::IsNullOrWhiteSpace($status)) {
        throw "git working tree is dirty; commit or stash before publishing a traceable test APK, or pass -AllowDirty intentionally"
    }
    Write-Host "git_tree_status=clean"
}

function Get-AndroidBuildInputPaths {
    return @(
        "app",
        "gradle",
        "gradlew",
        "gradlew.bat",
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties"
    )
}

function Assert-NoBuildApkFreshness {
    param([System.IO.FileInfo]$ApkItem)

    $inputPaths = Get-AndroidBuildInputPaths
    $dirty = Get-GitOutput -GitArgs (@("status", "--porcelain", "--") + $inputPaths)
    if (-not [string]::IsNullOrWhiteSpace($dirty)) {
        throw "-NoBuild cannot publish while Android build inputs have uncommitted changes; rebuild the APK without -NoBuild or commit the intended state first"
    }

    $latestCommitUnix = Get-GitOutput -GitArgs (@("log", "-1", "--format=%ct", "--") + $inputPaths)
    $latestCommitSeconds = 0L
    if ([string]::IsNullOrWhiteSpace($latestCommitUnix) -or -not [long]::TryParse($latestCommitUnix.Trim(), [ref]$latestCommitSeconds)) {
        throw "-NoBuild cannot verify debug APK freshness because the latest Android build input commit could not be resolved"
    }
    $latestCommitUtc = [DateTimeOffset]::FromUnixTimeSeconds($latestCommitSeconds).UtcDateTime
    Write-Host ("test_apk_nobuild_apk_last_write_utc={0:o}" -f $ApkItem.LastWriteTimeUtc)
    Write-Host ("test_apk_nobuild_inputs_latest_commit_utc={0:o}" -f $latestCommitUtc)
    if ($ApkItem.LastWriteTimeUtc.AddSeconds(2) -lt $latestCommitUtc) {
        throw "-NoBuild debug APK is older than the latest Android build input commit; rebuild with :app:assembleDebug before publishing"
    }
    Write-Host "test_apk_nobuild_freshness=ready"
}

function Normalize-Hex {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    return ($Value -replace '[^0-9A-Fa-f]', '').ToLowerInvariant()
}

function Assert-SafeHostname {
    param([string]$Name, [string]$Value)
    if ($Value -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])$' -or
        $Value.Contains("..") -or
        $Value.Contains("/") -or
        $Value.Contains("\") -or
        $Value.Contains(":") -or
        $Value.Contains("@")) {
        throw "$Name must be a plain DNS hostname"
    }
}

function Assert-ExpectedValue {
    param([string]$Name, [string]$Value, [string]$Expected)
    if ($Value -ne $Expected) {
        throw "$Name must be '$Expected' for nongjiqiancha test APK publishing"
    }
}

function Assert-SafeOssPrefix {
    param([string]$Value)
    $normalized = $Value.Replace("\", "/").Trim("/")
    $normalizedLower = $normalized.ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($normalized) -or ($normalizedLower -ne "test-apks/debug" -and -not $normalizedLower.StartsWith("test-apks/debug/"))) {
        throw "internal test APKs must be stored under test-apks/debug so the short lifecycle policy applies"
    }
    foreach ($part in $normalized.Split("/")) {
        if ([string]::IsNullOrWhiteSpace($part) -or $part -eq "." -or $part -eq ".." -or $part -match '[^\w.\-]') {
            throw "OssPrefix contains an unsafe path segment: $Value"
        }
    }
    return $normalized
}

function Test-PublicDownloadUrl {
    param(
        [string]$Url,
        [long]$ExpectedSize,
        [string]$Method = "Head"
    )
    try {
        if ($Method -eq "GetRange") {
            $handler = [System.Net.Http.HttpClientHandler]::new()
            $handler.AllowAutoRedirect = $true
            $client = [System.Net.Http.HttpClient]::new($handler)
            $client.Timeout = [TimeSpan]::FromSeconds(90)
            try {
                $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $Url)
                $request.Headers.Range = [System.Net.Http.Headers.RangeHeaderValue]::new(0, 0)
                $httpResponse = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
                try {
                    $status = [int]$httpResponse.StatusCode
                    $contentLength = [string]$httpResponse.Content.Headers.ContentLength
                    $contentType = [string]$httpResponse.Content.Headers.ContentType
                    if ($status -ne 206 -and $status -ne 200) {
                        throw "unexpected HTTP status $status"
                    }
                    if ($contentType -notmatch "application/vnd\.android\.package-archive|application/octet-stream") {
                        throw "unexpected content type: $contentType"
                    }
                    Write-Host ("test_apk_download_probe=ready status={0} method=GET_RANGE content_length={1} content_type={2}" -f $status, $contentLength, $contentType)
                    return
                } finally {
                    $httpResponse.Dispose()
                }
            } finally {
                $client.Dispose()
                $handler.Dispose()
            }
        } else {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -UseBasicParsing -TimeoutSec 90
            $status = [int]$response.StatusCode
            $contentLength = [string]$response.Headers["Content-Length"]
            $contentType = [string]$response.Headers["Content-Type"]
            if ($status -ne 200) {
                throw "unexpected HTTP status $status"
            }
            if (-not [string]::IsNullOrWhiteSpace($contentLength) -and $contentLength -ne [string]$ExpectedSize) {
                throw "content length mismatch: expected=$ExpectedSize actual=$contentLength"
            }
            if ($contentType -notmatch "application/vnd\.android\.package-archive|application/octet-stream") {
                throw "unexpected content type: $contentType"
            }
            Write-Host ("test_apk_download_probe=ready status={0} content_length={1} content_type={2}" -f $status, $contentLength, $contentType)
        }
    } catch {
        throw "test APK public download probe failed for ${Url}: $($_.Exception.Message)"
    }
}

function New-OssCnameSignedUrl {
    param(
        [string]$ObjectKey,
        [int]$ExpiresSeconds,
        [string]$Method = "GET"
    )
    $scriptPath = Join-Path $PSScriptRoot "sign-oss-cname-url.py"
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        throw "OSS CNAME signing helper not found: $scriptPath"
    }
    $output = & python $scriptPath `
        --bucket $Bucket `
        --endpoint "https://$OssDownloadDomain" `
        --object-key $ObjectKey `
        --expires-seconds $ExpiresSeconds `
        --method $Method 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "OSS CNAME signing failed with exit code $LASTEXITCODE`n$($output | Out-String)"
    }
    $url = (($output | Out-String).Trim())
    if ($url -notmatch "^https://$([regex]::Escape($OssDownloadDomain))/") {
        throw "OSS CNAME signing produced an unexpected URL host"
    }
    return $url
}

function Invoke-TextCommand {
    param([string[]]$CommandArgs)
    if ($CommandArgs.Length -eq 0) {
        throw "Command failed: empty command"
    }
    $exe = $CommandArgs[0]
    $arguments = @()
    if ($CommandArgs.Length -gt 1) {
        $arguments = $CommandArgs[1..($CommandArgs.Length - 1)]
    }
    $output = & $exe @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $($CommandArgs -join ' ')`n$($output | Out-String)"
    }
    return ($output | Out-String)
}

function ConvertFrom-OssLifecycleText {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    $start = $Text.IndexOf("<?xml", [StringComparison]::OrdinalIgnoreCase)
    if ($start -lt 0) {
        $start = $Text.IndexOf("<LifecycleConfiguration", [StringComparison]::OrdinalIgnoreCase)
    }
    $endTag = "</LifecycleConfiguration>"
    $end = $Text.IndexOf($endTag, [StringComparison]::OrdinalIgnoreCase)
    if ($start -lt 0 -or $end -lt 0) {
        return $null
    }
    $xmlText = $Text.Substring($start, $end + $endTag.Length - $start)
    try {
        return [xml]$xmlText
    } catch {
        return $null
    }
}

function Get-XmlNodeText {
    param(
        [object]$Node,
        [string]$XPath
    )
    if ($null -eq $Node) {
        return ""
    }
    $found = $Node.SelectSingleNode($XPath)
    if ($null -eq $found) {
        return ""
    }
    return ([string]$found.InnerText).Trim()
}

function Assert-TestApkLifecycle {
    param(
        [string]$BucketName,
        [string]$OssEndpoint
    )
    $text = Invoke-TextCommand @("aliyun", "oss", "lifecycle", "--method", "get", "oss://$BucketName", "--endpoint", $OssEndpoint)
    $doc = ConvertFrom-OssLifecycleText $text
    if ($null -eq $doc) {
        throw "cannot verify OSS lifecycle for test-apks/ on bucket $BucketName"
    }
    $rules = @($doc.LifecycleConfiguration.Rule)
    $rule = @($rules | Where-Object { (Get-XmlNodeText $_ "Prefix") -eq "test-apks/" } | Select-Object -First 1)
    if ($rule.Count -eq 0) {
        throw "OSS lifecycle rule for test-apks/ is missing; refusing to publish a test APK that may not auto-clean"
    }
    $ruleNode = $rule[0]
    $status = Get-XmlNodeText $ruleNode "Status"
    $expirationDays = Get-XmlNodeText $ruleNode "Expiration/Days"
    $abortDays = Get-XmlNodeText $ruleNode "AbortMultipartUpload/Days"
    if ($status -ne "Enabled" -or $expirationDays -ne "3") {
        throw "OSS lifecycle rule for test-apks/ must be Enabled with Expiration/Days=3; actual status=$status expiration_days=$expirationDays"
    }
    if ($abortDays -ne "1") {
        throw "OSS lifecycle rule for test-apks/ must abort multipart uploads after 1 day; actual abort_multipart_days=$abortDays"
    }
    Write-Host "test_apk_lifecycle_status=verified prefix=test-apks/ expiration_days=3 abort_multipart_days=1"
}

function Invoke-AndroidTool {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )
    $output = & $FilePath @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($null -ne $exitCode -and $exitCode -ne 0) {
        throw "$FilePath exited with code $exitCode`n$($output | Out-String)"
    }
    return @($output)
}

function Find-AndroidBuildTools {
    $sdkCandidates = @()
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            $sdkCandidates += (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    $sdkCandidates = $sdkCandidates | Select-Object -Unique
    foreach ($sdkRoot in $sdkCandidates) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        if (!(Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
            continue
        }
        $versions = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
            Sort-Object -Property @{
                Expression = {
                    try { [version]$_.Name } catch { [version]"0.0" }
                }
                Descending = $true
        }
        foreach ($versionDir in $versions) {
            $aapt = Join-Path $versionDir.FullName "aapt.exe"
            $apksigner = Join-Path $versionDir.FullName "apksigner.bat"
            if ((Test-Path -LiteralPath $aapt -PathType Leaf) -and (Test-Path -LiteralPath $apksigner -PathType Leaf)) {
                return [pscustomobject]@{
                    Version = $versionDir.Name
                    Aapt = $aapt
                    ApkSigner = $apksigner
                }
            }
        }
    }
    throw "Android SDK build-tools with aapt.exe and apksigner.bat were not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Assert-DebugApk {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($script:PublicInfoPath)) {
        $script:PublicInfoPath = Join-Path $env:USERPROFILE ".nongjiqiancha/android-release-public-info.txt"
    }
    if (-not (Test-Path -LiteralPath $script:PublicInfoPath -PathType Leaf)) {
        throw "release public certificate info not found: $script:PublicInfoPath"
    }
    $tools = Find-AndroidBuildTools
    $badging = Invoke-AndroidTool -FilePath $tools.Aapt -Arguments @("dump", "badging", $Path)
    $badgingText = $badging -join "`n"
    $packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
    $packageNameMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bname='([^']+)'") }
    $versionCodeMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bversionCode='([^']+)'") }
    $versionNameMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bversionName='([^']*)'") }
    if ($null -eq $packageLine -or -not $packageNameMatch.Success -or -not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
        throw "could not parse package metadata from APK badging output"
    }
    $packageName = $packageNameMatch.Groups[1].Value
    $versionCode = $versionCodeMatch.Groups[1].Value
    $versionName = $versionNameMatch.Groups[1].Value
    if ($packageName -ne $ExpectedPackageName) {
        throw "test APK package mismatch. expected=$ExpectedPackageName actual=$packageName"
    }
    if ($badgingText -notmatch "application-debuggable") {
        throw "test APK must be a debuggable debug build; refusing to publish a non-debuggable APK as an internal test package"
    }
    Write-Host ("test_apk_package={0}" -f $packageName)
    Write-Host ("test_apk_version_code={0}" -f $versionCode)
    Write-Host ("test_apk_version_name={0}" -f $versionName)
    Write-Host "test_apk_debuggable=true"
    $verify = Invoke-AndroidTool -FilePath $tools.ApkSigner -Arguments @("verify", "--print-certs", $Path)
    $verifyText = $verify -join "`n"
    if ($verifyText -notmatch "Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)") {
        throw "could not parse test APK certificate SHA-256 from apksigner output"
    }
    $actualCertSha256 = Normalize-Hex $Matches[1]
    $publicInfo = Get-Content -LiteralPath $script:PublicInfoPath -Raw
    if ($publicInfo -notmatch "(?m)^SHA256:\s*(.+)$") {
        throw "release public certificate info does not contain a SHA256 line: $script:PublicInfoPath"
    }
    $expectedCertSha256 = Normalize-Hex $Matches[1]
    if ($actualCertSha256 -ne $expectedCertSha256) {
        throw "test APK certificate SHA-256 mismatch. expected=$expectedCertSha256 actual=$actualCertSha256"
    }
    Write-Host ("test_apk_cert_sha256={0}" -f $actualCertSha256)
    Write-Host "test_apk_release_cert_match=true"
    Write-Host ("android_build_tools={0}" -f $tools.Version)
}

if ($ExpireHours -lt 1 -or $ExpireHours -gt 72) {
    throw "ExpireHours must be between 1 and 72 because OSS test-apks/ lifecycle is 3 days"
}

$normalizedOssPrefix = Assert-SafeOssPrefix -Value $OssPrefix
Assert-SafeHostname -Name "OssDownloadDomain" -Value $OssDownloadDomain
Assert-SafeHostname -Name "Endpoint" -Value $Endpoint
Assert-ExpectedValue -Name "Bucket" -Value $Bucket -Expected "nongjiqiancha-prod"
Assert-ExpectedValue -Name "OssDownloadDomain" -Value $OssDownloadDomain -Expected "download.nongjiqiancha.cn"
Assert-ExpectedValue -Name "Endpoint" -Value $Endpoint -Expected "oss-cn-beijing.aliyuncs.com"

Require-Command "git"
Require-Command "aliyun"
Assert-TestApkLifecycle -BucketName $Bucket -OssEndpoint $Endpoint

if ($UseEcsDownloadFallback) {
    throw "-UseEcsDownloadFallback has been retired. Internal test APKs must use private OSS test-apks/debug/... with the 3-day lifecycle and signed download.nongjiqiancha.cn URLs."
}
if ($UseOssSignedDownload -and $SkipEcsDownloadPublish) {
    throw "-UseOssSignedDownload cannot be combined with -SkipEcsDownloadPublish"
}
$useOssSignedDownloadEffective = -not $SkipEcsDownloadPublish
if ($UseOssSignedDownload) {
    $useOssSignedDownloadEffective = $true
}

Write-Host "== android test apk publish =="
Write-Host ("repo_root={0}" -f $RepoRoot)
Ensure-CleanGitTree

$commit = Get-GitOutput -GitArgs @("rev-parse", "--short=12", "HEAD")
if ([string]::IsNullOrWhiteSpace($commit)) {
    throw "failed to resolve git commit"
}
Write-Host ("test_apk_commit={0}" -f $commit)

if (-not $NoBuild) {
    $gradle = Join-Path $RepoRoot "gradlew.bat"
    if (-not (Test-Path $gradle)) {
        throw "gradlew.bat not found at $gradle"
    }
    & $gradle ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) {
        throw "assembleDebug failed with exit code $LASTEXITCODE"
    }
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
}
$ApkPath = (Resolve-Path $ApkPath).Path
if (-not (Test-Path $ApkPath)) {
    throw "debug APK not found: $ApkPath"
}
$apkPathLower = $ApkPath.ToLowerInvariant()
$apkLeafLower = (Split-Path -Leaf $ApkPath).ToLowerInvariant()
if ($apkPathLower -notmatch "[\\/\\\\]debug[\\/\\\\]|debug" -and $apkLeafLower -notmatch "debug") {
    throw "test APK publisher only accepts debug APKs; use the official release runbook after an explicit release command for release APKs"
}
Assert-DebugApk -Path $ApkPath

$apkItem = Get-Item $ApkPath
if ($NoBuild) {
    Assert-NoBuildApkFreshness -ApkItem $apkItem
}
$sha256 = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$dateDir = Get-Date -Format "yyyyMMdd"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "nongjiqiancha-debug-internal-$stamp-$commit.apk"
$objectKey = ($normalizedOssPrefix, $dateDir, $fileName) -join "/"
$ossUrl = "oss://$Bucket/$objectKey"
$downloadRelativePath = ($normalizedOssPrefix, $dateDir, $fileName) -join "/"

Write-Host ("test_apk_local_path={0}" -f $ApkPath)
Write-Host ("test_apk_size_bytes={0}" -f $apkItem.Length)
Write-Host ("test_apk_sha256={0}" -f $sha256)
Write-Host ("test_apk_oss_object={0}" -f $ossUrl)
Write-Host ("test_apk_download_object={0}" -f $downloadRelativePath)
Write-Host "test_apk_retention_days=3"

& aliyun oss cp $ApkPath $ossUrl --endpoint $Endpoint --force
if ($LASTEXITCODE -ne 0) {
    throw "aliyun oss cp failed with exit code $LASTEXITCODE"
}

$ossSignedDownloadUrl = ""
$ossSignedHeadUrl = ""
if ($useOssSignedDownloadEffective) {
    $expiresSeconds = $ExpireHours * 3600
    $ossSignedDownloadUrl = New-OssCnameSignedUrl -ObjectKey $objectKey -ExpiresSeconds $expiresSeconds -Method "GET"
    $ossSignedHeadUrl = New-OssCnameSignedUrl -ObjectKey $objectKey -ExpiresSeconds $expiresSeconds -Method "HEAD"
    Write-Host ("test_apk_oss_download_domain={0}" -f $OssDownloadDomain)
    Write-Host "test_apk_public_status=oss_signed"
}

if ($useOssSignedDownloadEffective) {
    Test-PublicDownloadUrl -Url $ossSignedHeadUrl -ExpectedSize $apkItem.Length -Method "Head"
    Test-PublicDownloadUrl -Url $ossSignedDownloadUrl -ExpectedSize $apkItem.Length -Method "GetRange"
}

Write-Host "test_apk_build_type=debug"
Write-Host ("test_apk_expires_hours={0}" -f $ExpireHours)
Write-Host "test_apk_cleanup=oss_lifecycle_3d"
Write-Host "test_apk_cleanup_note=normal internal test APK cleanup is handled by the OSS test-apks/ lifecycle; manual cleanup is only for abnormal residue after explicit review."
if ($UseOssSignedDownload) {
    Write-Host "test_apk_download_mode=oss_signed"
} elseif ($SkipEcsDownloadPublish) {
    Write-Host "test_apk_download_mode=staged_only"
} else {
    Write-Host "test_apk_download_mode=oss_signed_default"
}
if ($useOssSignedDownloadEffective) {
    Write-Host "test_apk_status=ready"
    Write-Host "test_apk_public_status=oss_signed"
    Write-Host ("test_apk_url={0}" -f $ossSignedDownloadUrl)
    Write-Host "test_apk_note=This internal debug APK is served from private OSS through a signed download.nongjiqiancha.cn URL."
} elseif ($SkipEcsDownloadPublish) {
    Write-Host "test_apk_status=staged_only"
    Write-Host "test_apk_public_status=staged_only"
    Write-Host "test_apk_url=none"
    Write-Host "test_apk_note=No public download URL was published because -SkipEcsDownloadPublish was set."
}
