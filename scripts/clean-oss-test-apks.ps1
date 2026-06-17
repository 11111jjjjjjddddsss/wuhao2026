param(
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$OssPrefix = "test-apks/debug",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [int]$KeepNewest = 1,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ($Bucket -ne "nongjiqiancha-prod") {
    throw "refusing to clean unexpected OSS bucket: $Bucket"
}
if ($Endpoint -ne "oss-cn-beijing.aliyuncs.com") {
    throw "refusing to clean unexpected OSS endpoint: $Endpoint"
}

if ($KeepNewest -lt 0 -or $KeepNewest -gt 10) {
    throw "KeepNewest must be between 0 and 10"
}

$normalizedOssPrefix = $OssPrefix.Trim().Trim("/")
if ([string]::IsNullOrWhiteSpace($normalizedOssPrefix)) {
    throw "OssPrefix must not be empty"
}
$normalizedOssPrefixForCheck = $normalizedOssPrefix.Replace("\", "/").ToLowerInvariant()
if ($normalizedOssPrefixForCheck -ne "test-apks/debug" -and -not $normalizedOssPrefixForCheck.StartsWith("test-apks/debug/")) {
    throw "refusing to clean outside test-apks/debug/"
}
if ($normalizedOssPrefixForCheck.Contains("..") -or $normalizedOssPrefixForCheck.Contains("//")) {
    throw "refusing unsafe OSS prefix: $normalizedOssPrefix"
}

if (-not (Get-Command aliyun -ErrorAction SilentlyContinue)) {
    throw "required command not found: aliyun"
}

function Get-OssObjectKey {
    param([object]$Line)
    $text = [string]$Line
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    if ($text -match "(test-apks/debug/\S+?\.apk)") {
        return $Matches[1]
    }
    return $null
}

Write-Host "== android test apk oss cleanup =="
Write-Host ("oss_prefix={0}" -f $normalizedOssPrefix)
Write-Host ("keep_newest={0}" -f $KeepNewest)

$ossPrefixUrl = "oss://$Bucket/$normalizedOssPrefix/"
$listOutput = @(& aliyun oss ls $ossPrefixUrl --endpoint $Endpoint 2>&1)
if ($LASTEXITCODE -ne 0) {
    $joined = ($listOutput | ForEach-Object { $_.ToString() }) -join "`n"
    throw "aliyun oss ls failed with exit code $LASTEXITCODE`n$joined"
}

$objects = @(
    $listOutput |
        ForEach-Object { Get-OssObjectKey $_ } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object
)

$toDelete = @()
if ($objects.Count -gt $KeepNewest) {
    $toDelete = @($objects | Select-Object -First ($objects.Count - $KeepNewest))
}

Write-Host ("oss_test_apk_found={0}" -f $objects.Count)
Write-Host ("oss_test_apk_delete={0}" -f $toDelete.Count)

foreach ($key in $toDelete) {
    $normalizedKeyForCheck = $key.Replace("\", "/").ToLowerInvariant()
    if (-not $normalizedKeyForCheck.StartsWith("test-apks/debug/") -or $normalizedKeyForCheck.Contains("..") -or $normalizedKeyForCheck.Contains("//")) {
        throw "refusing unsafe OSS object key: $key"
    }
    $objectUrl = "oss://$Bucket/$key"
    if ($DryRun) {
        Write-Host ("dry_run_delete={0}" -f $objectUrl)
    } else {
        & aliyun oss rm $objectUrl --endpoint $Endpoint --force
        if ($LASTEXITCODE -ne 0) {
            throw "aliyun oss rm failed for $objectUrl with exit code $LASTEXITCODE"
        }
        Write-Host ("deleted={0}" -f $objectUrl)
    }
}

Write-Host "oss_test_apk_cleanup_status=ready"
