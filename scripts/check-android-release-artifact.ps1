param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApkPath = "",
    [string]$ExpectedPackageName = "com.nongjiqiancha",
    [int]$ExpectedVersionCode = 0,
    [string]$ExpectedVersionName = "",
    [string]$PublicInfoPath = "",
    [int64]$MaxApkBytes = 209715200
)

$ErrorActionPreference = "Stop"

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
                    SdkRoot = $sdkRoot
                    Version = $versionDir.Name
                    Aapt = $aapt
                    ApkSigner = $apksigner
                }
            }
        }
    }
    throw "Android SDK build-tools with aapt.exe and apksigner.bat were not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $RepoRoot "app/build/outputs/apk/release/app-release.apk"
}
if ([string]::IsNullOrWhiteSpace($PublicInfoPath)) {
    $PublicInfoPath = Join-Path $env:USERPROFILE ".nongjiqiancha/android-release-public-info.txt"
}

$failures = [System.Collections.Generic.List[string]]::new()
$resolvedApk = $null
if (!(Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    Add-Failure $failures "Missing release APK: $ApkPath. Run ./gradlew.bat :app:assembleRelease first."
} else {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
}

if (!(Test-Path -LiteralPath $PublicInfoPath -PathType Leaf)) {
    Add-Failure $failures "Missing release public certificate info: $PublicInfoPath."
}

if ($failures.Count -eq 0) {
    $apkItem = Get-Item -LiteralPath $resolvedApk
    if ($apkItem.Length -le 0) {
        Add-Failure $failures "Release APK is empty: $resolvedApk."
    }
    if ($apkItem.Length -gt $MaxApkBytes) {
        Add-Failure $failures "Release APK is larger than the update download limit. size=$($apkItem.Length) max=$MaxApkBytes"
    }

    $tools = Find-AndroidBuildTools
    $badging = Invoke-AndroidTool -FilePath $tools.Aapt -Arguments @("dump", "badging", $resolvedApk)
    $badgingText = $badging -join "`n"
    $packageLine = $badging | Where-Object { $_ -match "^package:" } | Select-Object -First 1
    $packageNameMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bname='([^']+)'") }
    $versionCodeMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bversionCode='([^']+)'") }
    $versionNameMatch = if ($null -eq $packageLine) { $null } else { [regex]::Match($packageLine, "\bversionName='([^']*)'") }
    if ($null -eq $packageLine -or -not $packageNameMatch.Success -or -not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
        Add-Failure $failures "Could not parse package metadata from APK badging output."
    } else {
        $packageName = $packageNameMatch.Groups[1].Value
        $versionCodeText = $versionCodeMatch.Groups[1].Value
        $versionName = $versionNameMatch.Groups[1].Value
        $versionCode = 0
        if (-not [int]::TryParse($versionCodeText, [ref]$versionCode)) {
            Add-Failure $failures "APK versionCode is not an integer: $versionCodeText"
        }
        if ($packageName -ne $ExpectedPackageName) {
            Add-Failure $failures "APK package mismatch. expected=$ExpectedPackageName actual=$packageName"
        }
        if ($versionCode -le 0) {
            Add-Failure $failures "APK versionCode must be positive. actual=$versionCode"
        }
        if ($ExpectedVersionCode -gt 0 -and $versionCode -ne $ExpectedVersionCode) {
            Add-Failure $failures "APK versionCode mismatch. expected=$ExpectedVersionCode actual=$versionCode"
        }
        if (-not [string]::IsNullOrWhiteSpace($ExpectedVersionName) -and $versionName -ne $ExpectedVersionName) {
            Add-Failure $failures "APK versionName mismatch. expected=$ExpectedVersionName actual=$versionName"
        }
        Write-Host "apk_package=$packageName"
        Write-Host "apk_version_code=$versionCode"
        Write-Host "apk_version_name=$versionName"
    }

    if ($badgingText -match "application-debuggable") {
        Add-Failure $failures "Release APK must not be debuggable."
    }

    $permissions = @(
        $badging |
            ForEach-Object {
                if ($_ -match "^uses-permission: name='([^']+)'") { $Matches[1] }
            }
    ) | Where-Object { $_ } | Sort-Object -Unique
    $expectedPermissions = @(
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.INTERNET",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.WAKE_LOCK",
        "com.nongjiqiancha.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    ) | Sort-Object
    $permissionDiff = Compare-Object -ReferenceObject $expectedPermissions -DifferenceObject $permissions
    if ($permissionDiff) {
        Add-Failure $failures "Release APK permissions must match the documented allowlist. expected=[$($expectedPermissions -join ', ')] actual=[$($permissions -join ', ')]"
    }
    Write-Host "apk_permissions=$($permissions -join ',')"

    $verify = Invoke-AndroidTool -FilePath $tools.ApkSigner -Arguments @("verify", "--print-certs", $resolvedApk)
    $verifyText = $verify -join "`n"
    if ($verifyText -notmatch "Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)") {
        Add-Failure $failures "Could not parse release certificate SHA-256 from apksigner output."
    } else {
        $actualCertSha256 = Normalize-Hex $Matches[1]
        $publicInfo = Get-Content -LiteralPath $PublicInfoPath -Raw
        if ($publicInfo -notmatch "(?m)^SHA256:\s*(.+)$") {
            Add-Failure $failures "Public certificate info file does not contain a SHA256 line: $PublicInfoPath"
        } else {
            $expectedCertSha256 = Normalize-Hex $Matches[1]
            if ($actualCertSha256 -ne $expectedCertSha256) {
                Add-Failure $failures "Release certificate SHA-256 mismatch. expected=$expectedCertSha256 actual=$actualCertSha256"
            }
        }
        Write-Host "apk_cert_sha256=$actualCertSha256"
    }

    $apkSha256 = Get-FileSha256Hex $resolvedApk
    Write-Host "apk_path=$resolvedApk"
    Write-Host "apk_size_bytes=$($apkItem.Length)"
    Write-Host "apk_sha256=$apkSha256"
    Write-Host "android_build_tools=$($tools.Version)"
}

if ($failures.Count -gt 0) {
    Write-Host "release_artifact_status=failed"
    foreach ($failure in $failures) {
        Write-Host "ERROR: $failure"
    }
    exit 1
}

Write-Host "release_artifact_status=ready"
