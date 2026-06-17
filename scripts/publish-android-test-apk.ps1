param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$OssPrefix = "test-apks/debug",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$ExpectedPackageName = "com.nongjiqiancha",
    [int]$ExpireHours = 72,
    [switch]$NoBuild,
    [switch]$AllowDirty
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

Require-Command "git"
Require-Command "aliyun"

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
$objectKey = (($OssPrefix.Trim("/")), $dateDir, $fileName) -join "/"
$ossUrl = "oss://$Bucket/$objectKey"

Write-Host ("test_apk_local_path={0}" -f $ApkPath)
Write-Host ("test_apk_size_bytes={0}" -f $apkItem.Length)
Write-Host ("test_apk_sha256={0}" -f $sha256)
Write-Host ("test_apk_oss_object={0}" -f $ossUrl)
Write-Host "test_apk_retention_days=3"

& aliyun oss cp $ApkPath $ossUrl --endpoint $Endpoint --force
if ($LASTEXITCODE -ne 0) {
    throw "aliyun oss cp failed with exit code $LASTEXITCODE"
}

$timeoutSeconds = $ExpireHours * 3600
$signOutput = @(& aliyun oss sign $ossUrl --endpoint $Endpoint --timeout $timeoutSeconds 2>&1)
if ($LASTEXITCODE -ne 0) {
    $joined = ($signOutput | ForEach-Object { $_.ToString() }) -join "`n"
    throw "aliyun oss sign failed with exit code $LASTEXITCODE`n$joined"
}

$signedUrl = Extract-FirstUrl $signOutput
if ([string]::IsNullOrWhiteSpace($signedUrl)) {
    $joined = ($signOutput | ForEach-Object { $_.ToString() }) -join "`n"
    throw "failed to parse signed URL from aliyun oss sign output`n$joined"
}
if ($signedUrl.StartsWith("http://")) {
    $signedUrl = "https://" + $signedUrl.Substring(7)
}

Write-Host "test_apk_status=ready"
Write-Host "test_apk_build_type=debug"
Write-Host ("test_apk_expires_hours={0}" -f $ExpireHours)
Write-Host ("test_apk_url={0}" -f $signedUrl)
