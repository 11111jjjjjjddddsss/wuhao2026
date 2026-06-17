param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [int]$KeepNewestPerVariant = 1,
    [int]$MaxAgeDays = 7,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ($KeepNewestPerVariant -lt 0 -or $KeepNewestPerVariant -gt 5) {
    throw "KeepNewestPerVariant must be between 0 and 5"
}
if ($MaxAgeDays -lt 1 -or $MaxAgeDays -gt 365) {
    throw "MaxAgeDays must be between 1 and 365"
}

$repoFull = (Resolve-Path -LiteralPath $RepoRoot).Path
$allowedRoots = @(
    Join-Path $repoFull "app\build\outputs\apk",
    Join-Path $repoFull "app\build\intermediates\apk",
    Join-Path $repoFull "tmp"
) | ForEach-Object {
    if (Test-Path -LiteralPath $_ -PathType Container) {
        (Resolve-Path -LiteralPath $_).Path
    }
}

function Test-IsUnderPath {
    param(
        [string]$Path,
        [string]$Root
    )
    $normalizedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    $normalizedRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\')
    return $normalizedPath.Equals($normalizedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $normalizedPath.StartsWith($normalizedRoot + "\", [StringComparison]::OrdinalIgnoreCase)
}

function Assert-AllowedApkPath {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path)
    foreach ($root in $allowedRoots) {
        if (Test-IsUnderPath -Path $full -Root $root) {
            return
        }
    }
    throw "Refusing to delete APK outside allowed generated-artifact directories: $full"
}

if ($allowedRoots.Count -eq 0) {
    Write-Host "local_apk_cleanup_status=no_generated_apk_dirs"
    exit 0
}

$allApks = foreach ($root in $allowedRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter "*.apk" -ErrorAction SilentlyContinue
}

$now = Get-Date
$delete = New-Object System.Collections.Generic.List[System.IO.FileInfo]

$intermediatesRoot = Join-Path $repoFull "app\build\intermediates\apk"
foreach ($apk in $allApks) {
    if ((Test-Path -LiteralPath $intermediatesRoot -PathType Container) -and
        (Test-IsUnderPath -Path $apk.FullName -Root $intermediatesRoot)) {
        $delete.Add($apk)
    }
}

$outputRoot = Join-Path $repoFull "app\build\outputs\apk"
if (Test-Path -LiteralPath $outputRoot -PathType Container) {
    $outputApks = $allApks | Where-Object { Test-IsUnderPath -Path $_.FullName -Root $outputRoot }
    $groups = $outputApks | Group-Object { $_.DirectoryName }
    foreach ($group in $groups) {
        $ordered = $group.Group | Sort-Object LastWriteTime -Descending
        $kept = @($ordered | Select-Object -First $KeepNewestPerVariant)
        $candidates = @($ordered | Select-Object -Skip $KeepNewestPerVariant)
        foreach ($candidate in $candidates) {
            $delete.Add($candidate)
        }
        foreach ($keptApk in $kept) {
            if (($now - $keptApk.LastWriteTime).TotalDays -gt $MaxAgeDays -and $KeepNewestPerVariant -eq 0) {
                $delete.Add($keptApk)
            }
        }
    }
}

$delete = @($delete | Sort-Object FullName -Unique)
foreach ($apk in $delete) {
    Assert-AllowedApkPath -Path $apk.FullName
}

Write-Host ("local_apk_cleanup_found={0}" -f @($allApks).Count)
Write-Host ("local_apk_cleanup_delete={0}" -f @($delete).Count)
foreach ($apk in $delete) {
    $relative = $apk.FullName.Substring($repoFull.Length).TrimStart('\')
    if ($DryRun) {
        Write-Host ("dry_run_delete={0}" -f $relative)
    } else {
        Remove-Item -LiteralPath $apk.FullName -Force
        Write-Host ("deleted={0}" -f $relative)
    }
}
Write-Host "local_apk_cleanup_status=ready"
