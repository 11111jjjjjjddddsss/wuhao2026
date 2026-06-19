param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$OssPrefix = "test-apks/debug",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$OssInternalEndpoint = "oss-cn-beijing-internal.aliyuncs.com",
    [string]$DownloadDomain = "nongjiqiancha.cn",
    [string]$OssDownloadDomain = "download.nongjiqiancha.cn",
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$EcsTestApkRoot = "/var/www/nongjiqiancha-test-apks",
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

function Extract-FirstUrl {
    param([string[]]$Lines)
    foreach ($line in $Lines) {
        $text = [string]$line
        if ($text -match "https?://\S+") {
            return $Matches[0].Trim()
        }
    }
    return ""
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
        $arguments += @(
            "--connect-timeout", "20",
            "--read-timeout", "120",
            "--retry-count", "3"
        )
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
            -replace '(?i)(Content=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content)"\s*:\s*")[^"]+', '${1}REDACTED'
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
    param(
        [string]$RegionId,
        [string]$InvokeId
    )
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId
        )
        $item = $result.Invocation.InvocationResults.InvocationResult[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($item.Output))
            [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
            return
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

function Escape-BashSingleQuoted {
    param([string]$Value)
    return $Value.Replace("'", "'\''")
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

function Assert-SafeEcsTestApkRoot {
    param([string]$Value)
    $normalized = $Value.Replace("\", "/").TrimEnd("/")
    if ($normalized -ne "/var/www/nongjiqiancha-test-apks") {
        throw "refusing to use unexpected ECS test APK root: $Value"
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

if ($ExpireHours -lt 1 -or $ExpireHours -gt 168) {
    throw "ExpireHours must be between 1 and 168"
}

$normalizedOssPrefix = Assert-SafeOssPrefix -Value $OssPrefix
Assert-SafeHostname -Name "DownloadDomain" -Value $DownloadDomain
Assert-SafeHostname -Name "OssDownloadDomain" -Value $OssDownloadDomain
Assert-SafeHostname -Name "Endpoint" -Value $Endpoint
Assert-SafeHostname -Name "OssInternalEndpoint" -Value $OssInternalEndpoint
Assert-ExpectedValue -Name "DownloadDomain" -Value $DownloadDomain -Expected "nongjiqiancha.cn"
Assert-ExpectedValue -Name "OssDownloadDomain" -Value $OssDownloadDomain -Expected "download.nongjiqiancha.cn"
Assert-ExpectedValue -Name "Endpoint" -Value $Endpoint -Expected "oss-cn-beijing.aliyuncs.com"
Assert-ExpectedValue -Name "OssInternalEndpoint" -Value $OssInternalEndpoint -Expected "oss-cn-beijing-internal.aliyuncs.com"
Assert-SafeEcsTestApkRoot -Value $EcsTestApkRoot | Out-Null

Require-Command "git"
Require-Command "aliyun"
. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

if ($UseEcsDownloadFallback -and $SkipEcsDownloadPublish) {
    throw "-UseEcsDownloadFallback cannot be combined with -SkipEcsDownloadPublish"
}
if ($UseOssSignedDownload -and $SkipEcsDownloadPublish) {
    throw "-UseOssSignedDownload cannot be combined with -SkipEcsDownloadPublish"
}
$useOssSignedDownloadEffective = -not $UseEcsDownloadFallback -and -not $SkipEcsDownloadPublish
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
$sha256 = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$dateDir = Get-Date -Format "yyyyMMdd"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "nongjiqiancha-debug-internal-$stamp-$commit.apk"
$objectKey = ($normalizedOssPrefix, $dateDir, $fileName) -join "/"
$ossUrl = "oss://$Bucket/$objectKey"
$downloadRelativePath = ($normalizedOssPrefix, $dateDir, $fileName) -join "/"
$downloadPathUnderTestApks = $downloadRelativePath -replace '^test-apks/', ''
$downloadUrl = "https://$DownloadDomain/test-apks/$downloadPathUnderTestApks"

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

if (-not $SkipEcsDownloadPublish -and -not $useOssSignedDownloadEffective) {
    $internalSignOutput = @(& aliyun oss sign $ossUrl --endpoint $OssInternalEndpoint --timeout 3600 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $joined = ($internalSignOutput | ForEach-Object { $_.ToString() }) -join "`n"
        throw "aliyun oss internal sign failed with exit code $LASTEXITCODE`n$joined"
    }
    $internalSignedUrl = Extract-FirstUrl $internalSignOutput
    if ([string]::IsNullOrWhiteSpace($internalSignedUrl)) {
        $joined = ($internalSignOutput | ForEach-Object { $_.ToString() }) -join "`n"
        throw "failed to parse internal signed URL from aliyun oss sign output`n$joined"
    }
    if ($internalSignedUrl.StartsWith("http://")) {
        $internalSignedUrl = "https://" + $internalSignedUrl.Substring(7)
    }

    $remoteDownloadUrl = Escape-BashSingleQuoted $internalSignedUrl
    $remoteRoot = Escape-BashSingleQuoted ($EcsTestApkRoot.TrimEnd("/"))
    $remoteRelative = Escape-BashSingleQuoted $downloadPathUnderTestApks
    $remoteSha = Escape-BashSingleQuoted $sha256
    $remoteSize = [string]$apkItem.Length
    $remoteScript = @"
set -eu
download_url='$remoteDownloadUrl'
download_root='$remoteRoot'
relative_path='$remoteRelative'
expected_sha='$remoteSha'
expected_size='$remoteSize'
target="`$download_root/`$relative_path"
tmp="`$target.tmp.`$$"
nginx_site='/etc/nginx/sites-available/nongjiqiancha-site'
nginx_enabled='/etc/nginx/sites-enabled/nongjiqiancha-site'
mkdir -p "`$(dirname "`$target")"
curl -fsSL --retry 3 --connect-timeout 10 --max-time 300 -o "`$tmp" "`$download_url"
actual_size=`$(stat -c '%s' "`$tmp")
if [ "`$actual_size" != "`$expected_size" ]; then
  echo "apk size mismatch: expected=`$expected_size actual=`$actual_size" >&2
  rm -f "`$tmp"
  exit 20
fi
actual_sha=`$(sha256sum "`$tmp" | awk '{print `$1}')
if [ "`$actual_sha" != "`$expected_sha" ]; then
  echo "apk sha256 mismatch: expected=`$expected_sha actual=`$actual_sha" >&2
  rm -f "`$tmp"
  exit 21
fi
chmod 0644 "`$tmp"
mv -f "`$tmp" "`$target"
if [ -f "`$nginx_site" ] && ! grep -q 'location \^~ /test-apks/' "`$nginx_site"; then
  cp -f "`$nginx_site" "`$nginx_site.test-apks-bak.`$(date +%Y%m%d%H%M%S)"
  python3 - "`$nginx_site" "`$download_root" <<'PY'
import sys
path, root = sys.argv[1], sys.argv[2].rstrip("/")
with open(path, "r", encoding="utf-8") as f:
    text = f.read()
if "location ^~ /test-apks/" not in text:
    marker = "    location / {\n        try_files `$uri `$uri/ /index.html;\n    }\n"
    block = f"""    location ^~ /test-apks/ {{
        alias {root}/;
        default_type application/vnd.android.package-archive;
        add_header Cache-Control \"private, max-age=300\" always;
        add_header X-Robots-Tag \"noindex, nofollow\" always;
    }}

"""
    if marker not in text:
        raise SystemExit("site nginx marker not found")
    text = text.replace(marker, block + marker, 1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
PY
  ln -sfn "`$nginx_site" "`$nginx_enabled"
  nginx -t
  systemctl reload nginx
fi
echo "ecs_test_apk_path=`$target"
echo "ecs_test_apk_sha256=`$actual_sha"
echo "ecs_test_apk_size=`$actual_size"
"@
    $remoteScriptPath = "/tmp/nongji-test-apk-publish-$commit.sh"
    Send-CloudAssistantScriptFile -RegionId $EcsRegionId -InstanceId $EcsInstanceId -RemotePath $remoteScriptPath -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $EcsRegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $EcsInstanceId,
        "--Name", "publish-test-apk-$commit",
        "--CommandContent", "bash $remoteScriptPath",
        "--Timeout", "600"
    )
    $runResult = Wait-RunCommand -RegionId $EcsRegionId -InvokeId $run.InvokeId
    Write-Host $runResult.Output.Trim()
    if ($runResult.Status -ne "Success" -or $runResult.ExitCode -ne 0) {
        throw "ECS test APK publish failed: status=$($runResult.Status) exit=$($runResult.ExitCode)`n$($runResult.Output)"
    }
}

if ($useOssSignedDownloadEffective) {
    Test-PublicDownloadUrl -Url $ossSignedHeadUrl -ExpectedSize $apkItem.Length -Method "Head"
    Test-PublicDownloadUrl -Url $ossSignedDownloadUrl -ExpectedSize $apkItem.Length -Method "GetRange"
} elseif (-not $SkipEcsDownloadPublish) {
    Test-PublicDownloadUrl -Url $downloadUrl -ExpectedSize $apkItem.Length
}

Write-Host "test_apk_build_type=debug"
Write-Host ("test_apk_expires_hours={0}" -f $ExpireHours)
Write-Host "test_apk_cleanup=manual_only"
if ($UseOssSignedDownload) {
    Write-Host "test_apk_download_mode=oss_signed"
} elseif ($UseEcsDownloadFallback) {
    Write-Host "test_apk_download_mode=ecs_fallback"
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
} else {
    Write-Host "test_apk_status=ready"
    Write-Host "test_apk_public_status=published"
    Write-Host ("test_apk_url={0}" -f $downloadUrl)
}
