param(
    [string]$RegionId = "cn-beijing",
    [string]$ProjectName = "nongjiqiancha-prod-1159547719787456",
    [string[]]$ExpectedLogstores = @("server-go", "nginx-error"),
    [int]$MaxLogstoreCount = 2,
    [int]$MinTtlDays = 180,
    [int]$MaxTtlDays = 180,
    [int]$ExpectedHotTtlDays = 7,
    [int]$ExpectedInfrequentAccessTtlDays = 173,
    [int]$MaxShardCount = 1,
    [switch]$FailOnWarning
)

$ErrorActionPreference = "Stop"

$warnings = New-Object System.Collections.Generic.List[string]
$errors = New-Object System.Collections.Generic.List[string]

function Add-WarningItem {
    param([string]$Message)
    $warnings.Add($Message) | Out-Null
}

function Add-ErrorItem {
    param([string]$Message)
    $errors.Add($Message) | Out-Null
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
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce)"\s*:\s*")[^"]+', '${1}REDACTED'
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

Write-Host "== SLS cost guard =="
Write-Host "project=$ProjectName region=$RegionId max_logstores=$MaxLogstoreCount min_ttl_days=$MinTtlDays max_ttl_days=$MaxTtlDays expected_hot_ttl_days=$ExpectedHotTtlDays expected_infrequent_access_ttl_days=$ExpectedInfrequentAccessTtlDays max_shards_per_logstore=$MaxShardCount"

$list = Invoke-JsonCommand @(
    "aliyun", "sls", "list-log-stores",
    "--region", $RegionId,
    "--project", $ProjectName
)

$actualLogstores = @($list.logstores | ForEach-Object { [string]$_ } | Sort-Object)
$expectedSorted = @($ExpectedLogstores | ForEach-Object { [string]$_ } | Sort-Object)

Write-Host "logstore_count=$($actualLogstores.Count)"
Write-Host "logstores=$($actualLogstores -join ',')"

if ($actualLogstores.Count -gt $MaxLogstoreCount) {
    Add-WarningItem "logstore_count_exceeds_low_cost_budget:$($actualLogstores.Count)/$MaxLogstoreCount"
}

foreach ($expected in $expectedSorted) {
    if ($actualLogstores -notcontains $expected) {
        Add-ErrorItem "missing_expected_logstore:$expected"
    }
}

foreach ($actual in $actualLogstores) {
    if ($expectedSorted -notcontains $actual) {
        Add-WarningItem "unexpected_logstore_may_add_cost:$actual"
    }
}

foreach ($logstoreName in $actualLogstores) {
    $detail = Invoke-JsonCommand @(
        "aliyun", "sls", "get-log-store",
        "--region", $RegionId,
        "--project", $ProjectName,
        "--logstore", $logstoreName
    )
    $ttl = [int]$detail.ttl
    $hotTtl = if ($detail.PSObject.Properties.Name -contains "hotTtl") { [int]$detail.hotTtl } elseif ($detail.PSObject.Properties.Name -contains "hot_ttl") { [int]$detail.hot_ttl } else { $ttl }
    $infrequentAccessTtl = if ($detail.PSObject.Properties.Name -contains "infrequentAccessTTL") { [int]$detail.infrequentAccessTTL } else { 0 }
    $shardCount = [int]$detail.shardCount
    $autoSplit = [bool]$detail.autoSplit
    $appendMeta = [bool]$detail.appendMeta
    $archiveSeconds = [int]$detail.archiveSeconds
    Write-Host "logstore=$logstoreName ttl_days=$ttl hot_ttl_days=$hotTtl infrequent_access_ttl_days=$infrequentAccessTtl shard_count=$shardCount auto_split=$autoSplit append_meta=$appendMeta archive_seconds=$archiveSeconds"
    if ($ttl -le 0) {
        Add-ErrorItem "logstore_ttl_invalid:${logstoreName}:$ttl"
    } elseif ($ttl -lt $MinTtlDays) {
        Add-ErrorItem "logstore_ttl_less_than_required_retention:${logstoreName}:$ttl/$MinTtlDays"
    } elseif ($ttl -gt $MaxTtlDays) {
        Add-WarningItem "logstore_ttl_exceeds_retention_cost_budget:${logstoreName}:$ttl/$MaxTtlDays"
    }
    if ($hotTtl -ne $ExpectedHotTtlDays) {
        Add-WarningItem "logstore_hot_ttl_not_low_cost_expected:${logstoreName}:$hotTtl/$ExpectedHotTtlDays"
    }
    if ($infrequentAccessTtl -ne $ExpectedInfrequentAccessTtlDays) {
        Add-WarningItem "logstore_infrequent_access_ttl_not_expected:${logstoreName}:$infrequentAccessTtl/$ExpectedInfrequentAccessTtlDays"
    }
    if ($shardCount -gt $MaxShardCount) {
        Add-WarningItem "logstore_shards_exceed_low_cost_budget:${logstoreName}:$shardCount"
    }
    if ($autoSplit) {
        Add-WarningItem "logstore_auto_split_enabled:${logstoreName}"
    }
    if ($appendMeta) {
        Add-WarningItem "logstore_append_meta_enabled:${logstoreName}"
    }
    if ($archiveSeconds -gt 0) {
        Add-WarningItem "logstore_archive_storage_enabled:${logstoreName}:$archiveSeconds"
    }
}

Write-Host
Write-Host "summary warnings=$($warnings.Count) errors=$($errors.Count)"
foreach ($warning in $warnings) {
    Write-Warning $warning
}
foreach ($errorItem in $errors) {
    Write-Host "ERROR: $errorItem"
}

if ($errors.Count -gt 0) {
    Write-Host "status=failed"
    exit 1
}

if ($warnings.Count -gt 0) {
    Write-Host "status=attention"
    if ($FailOnWarning) {
        exit 2
    }
    exit 0
}

Write-Host "status=ready"
