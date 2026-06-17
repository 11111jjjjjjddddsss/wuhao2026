param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$OssPrefix = "test-apks/debug",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$OssInternalEndpoint = "oss-cn-beijing-internal.aliyuncs.com",
    [string]$DownloadDomain = "nongjiqiancha.cn",
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$EcsTestApkRoot = "/var/www/nongjiqiancha-test-apks",
    [string]$ExpectedPackageName = "com.nongjiqiancha",
    [int]$ExpireHours = 72,
    [int]$KeepNewestRemote = 1,
    [switch]$NoBuild,
    [switch]$AllowDirty,
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
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GitArgs)
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

    $status = Get-GitOutput @("status", "--porcelain")
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

function Test-PublicDownloadUrl {
    param(
        [string]$Url,
        [long]$ExpectedSize
    )
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Head -UseBasicParsing -TimeoutSec 30
        $status = [int]$response.StatusCode
        $contentLength = [string]$response.Headers["Content-Length"]
        $contentType = [string]$response.Headers["Content-Type"]
        if ($status -ne 200) {
            throw "unexpected HTTP status $status"
        }
        if ($contentLength -ne [string]$ExpectedSize) {
            throw "content length mismatch: expected=$ExpectedSize actual=$contentLength"
        }
        if ($contentType -notmatch "application/vnd\.android\.package-archive|application/octet-stream") {
            throw "unexpected content type: $contentType"
        }
        Write-Host ("test_apk_download_probe=ready status={0} content_length={1} content_type={2}" -f $status, $contentLength, $contentType)
    } catch {
        throw "test APK public download probe failed for ${Url}: $($_.Exception.Message)"
    }
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

function Find-AndroidAapt {
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
            if (Test-Path -LiteralPath $aapt -PathType Leaf) {
                return $aapt
            }
        }
    }
    throw "Android SDK build-tools with aapt.exe were not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Assert-DebugApk {
    param([string]$Path)
    $aapt = Find-AndroidAapt
    $badging = Invoke-AndroidTool -FilePath $aapt -Arguments @("dump", "badging", $Path)
    $badgingText = $badging -join "`n"
    $packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
    $packageNameMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bname='([^']+)'") }
    if ($null -eq $packageLine -or -not $packageNameMatch.Success) {
        throw "could not parse package metadata from APK badging output"
    }
    $packageName = $packageNameMatch.Groups[1].Value
    if ($packageName -ne $ExpectedPackageName) {
        throw "test APK package mismatch. expected=$ExpectedPackageName actual=$packageName"
    }
    if ($badgingText -notmatch "application-debuggable") {
        throw "test APK must be a debuggable debug build; refusing to publish a non-debuggable APK as an internal test package"
    }
    Write-Host ("test_apk_package={0}" -f $packageName)
    Write-Host "test_apk_debuggable=true"
}

if ($ExpireHours -lt 1 -or $ExpireHours -gt 168) {
    throw "ExpireHours must be between 1 and 168"
}
if ($KeepNewestRemote -lt 1 -or $KeepNewestRemote -gt 10) {
    throw "KeepNewestRemote must be between 1 and 10"
}

$normalizedOssPrefix = $OssPrefix.Trim().Trim("/")
if ([string]::IsNullOrWhiteSpace($normalizedOssPrefix)) {
    throw "OssPrefix must not be empty"
}
$normalizedOssPrefixForCheck = $normalizedOssPrefix.Replace("\", "/").ToLowerInvariant()
if (-not $normalizedOssPrefixForCheck.StartsWith("test-apks/")) {
    throw "internal test APKs must be stored under test-apks/ so the short lifecycle policy applies"
}

Require-Command "git"
Require-Command "aliyun"
. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

Write-Host "== android test apk publish =="
Write-Host ("repo_root={0}" -f $RepoRoot)
Ensure-CleanGitTree

$commit = Get-GitOutput @("rev-parse", "--short=12", "HEAD")
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

if (-not $SkipEcsDownloadPublish) {
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
    $remoteExpireMinutes = [string]($ExpireHours * 60)
    $remoteScript = @"
set -euo pipefail
download_url='$remoteDownloadUrl'
download_root='$remoteRoot'
relative_path='$remoteRelative'
expected_sha='$remoteSha'
expected_size='$remoteSize'
expire_minutes='$remoteExpireMinutes'
target="`$download_root/`$relative_path"
tmp="`$target.tmp.`$$"
nginx_site='/etc/nginx/sites-available/nongjiqiancha-site'
nginx_enabled='/etc/nginx/sites-enabled/nongjiqiancha-site'
cleanup_cron='/etc/cron.d/nongjiqiancha-test-apks-clean'
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
find "`$download_root" -type f -name '*.apk' ! -path "`$target" -delete 2>/dev/null || true
find "`$download_root" -type f -name '*.apk' -mmin "+`$expire_minutes" -delete 2>/dev/null || true
find "`$download_root" -type d -empty -delete 2>/dev/null || true
cat > "`$cleanup_cron" <<EOF
# Managed by nongjiqiancha publish-android-test-apk.ps1.
# Internal debug APKs are short-lived and must not become official downloads.
17 3 * * * root find "`$download_root" -type f -name '*.apk' -mmin +`$expire_minutes -delete 2>/dev/null; find "`$download_root" -mindepth 1 -type d -empty -delete 2>/dev/null
EOF
chmod 0644 "`$cleanup_cron"
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

$cleanupScript = Join-Path $PSScriptRoot "clean-oss-test-apks.ps1"
if (Test-Path -LiteralPath $cleanupScript -PathType Leaf) {
    & $cleanupScript -Bucket $Bucket -OssPrefix $normalizedOssPrefix -Endpoint $Endpoint -KeepNewest $KeepNewestRemote
    if ($LASTEXITCODE -ne 0) {
        throw "clean-oss-test-apks.ps1 failed with exit code $LASTEXITCODE"
    }
}

if (-not $SkipEcsDownloadPublish) {
    Test-PublicDownloadUrl -Url $downloadUrl -ExpectedSize $apkItem.Length
}

Write-Host "test_apk_status=ready"
Write-Host "test_apk_build_type=debug"
Write-Host ("test_apk_expires_hours={0}" -f $ExpireHours)
Write-Host ("test_apk_remote_keep_newest={0}" -f $KeepNewestRemote)
Write-Host ("test_apk_url={0}" -f $downloadUrl)
