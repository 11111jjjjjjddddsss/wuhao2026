param(
    [string]$StartDate = (Get-Date).AddDays(-6).ToString("yyyyMMdd"),
    [string]$EndDate = (Get-Date).ToString("yyyyMMdd"),
    [string]$RegionId = "cn-hangzhou",
    [string]$SignName = "",
    [int]$PageSize = 20,
    [int]$PageIndex = 1,
    [string]$PhoneNumber = "",
    [string]$SendDate = (Get-Date).ToString("yyyyMMdd"),
    [int]$EmptyRetryCount = 1,
    [int]$RetryDelaySeconds = 2,
    [switch]$SkipResourcePackageCheck,
    [int]$ResourcePackagePageSize = 50
)

$ErrorActionPreference = "Stop"

function Invoke-AliyunJson {
    param([string[]]$ArgsList)
    if ($ArgsList.Length -eq 0) {
        throw "Command failed: empty command"
    }
    $exe = $ArgsList[0]
    $arguments = @()
    if ($ArgsList.Length -gt 1) {
        $arguments = $ArgsList[1..($ArgsList.Length - 1)]
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
            -replace '(?i)(phone-number\s+)[0-9]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|PhoneNumber|PhoneNum)"\s*:\s*")[^"]+', '${1}REDACTED'
        $safeCommand = if ($ArgsList.Length -ge 3) {
            "$($ArgsList[0]) $($ArgsList[1]) $($ArgsList[2])"
        } else {
            $ArgsList -join " "
        }
        throw "Command failed: $safeCommand`n$safeOutput"
    }
    $jsonText = $stdout | Out-String
    if ([string]::IsNullOrWhiteSpace($jsonText)) {
        return $null
    }
    return $jsonText | ConvertFrom-Json
}

function Get-JsonPropertyValue {
    param(
        $Object,
        [string]$Name
    )
    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Assert-AliyunOk {
    param(
        $Response,
        [string]$Operation
    )
    $code = Get-JsonPropertyValue $Response "Code"
    if ([string]::IsNullOrWhiteSpace([string]$code) -or $code -eq "OK" -or $code -eq "Success") {
        return
    }
    $message = Get-JsonPropertyValue $Response "Message"
    if ([string]::IsNullOrWhiteSpace([string]$message)) {
        $message = "no message"
    }
    throw "$Operation failed: Code=$code Message=$message"
}

function Get-TargetList {
    param($Stats)
    $data = Get-JsonPropertyValue $Stats "Data"
    $targetList = Get-JsonPropertyValue $data "TargetList"
    if ($null -eq $targetList) {
        return @()
    }
    return @($targetList)
}

function Get-ResourcePackageList {
    param($Response)
    $data = Get-JsonPropertyValue $Response "Data"
    $instances = Get-JsonPropertyValue $data "Instances"
    $instanceList = Get-JsonPropertyValue $instances "Instance"
    if ($null -eq $instanceList) {
        return @()
    }
    return @($instanceList)
}

function Get-IntProperty {
    param(
        $Object,
        [string]$Name
    )
    $value = Get-JsonPropertyValue $Object $Name
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
        return [int64]0
    }
    return [int64]$value
}

function Get-StringProperty {
    param(
        $Object,
        [string]$Name
    )
    $value = Get-JsonPropertyValue $Object $Name
    if ($null -eq $value) {
        return ""
    }
    return ([string]$value).Trim()
}

function Format-InlineValue {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return "-"
    }
    return (($Value -replace '[\r\n\t]+', ' ') -replace '\s{2,}', ' ').Trim()
}

function Get-ApplicableProductsText {
    param($Package)
    $applicableProducts = Get-JsonPropertyValue $Package "ApplicableProducts"
    $products = Get-JsonPropertyValue $applicableProducts "Product"
    if ($null -eq $products) {
        return ""
    }
    return (@($products) -join " ")
}

function Test-SmsResourcePackage {
    param($Package)
    $text = @(
        (Get-StringProperty $Package "CommodityCode"),
        (Get-StringProperty $Package "PackageType"),
        (Get-StringProperty $Package "Remark"),
        (Get-ApplicableProductsText $Package)
    ) -join " "
    return $text -match '(?i)sms|dysms|short\s*message|短信'
}

Write-Host "== domestic sms send statistics $StartDate-$EndDate =="
$statsArgs = @(
    "aliyun", "dysmsapi", "query-send-statistics",
    "--api-version", "2017-05-25",
    "--region", $RegionId,
    "--is-globe", "1",
    "--start-date", $StartDate,
    "--end-date", $EndDate,
    "--page-index", "$PageIndex",
    "--page-size", "$PageSize",
    "--template-type", "0"
)
if (-not [string]::IsNullOrWhiteSpace($SignName)) {
    $statsArgs += @("--sign-name", $SignName)
}

$retryLimit = [Math]::Max(0, $EmptyRetryCount)
$stats = $null
$targetList = @()
for ($attempt = 0; $attempt -le $retryLimit; $attempt++) {
    $stats = Invoke-AliyunJson $statsArgs
    Assert-AliyunOk $stats "SMS statistics query"
    $targetList = @(Get-TargetList $stats)
    if ($targetList.Count -gt 0 -or $attempt -ge $retryLimit) {
        break
    }
    Write-Warning "SMS statistics returned no rows; retrying in $RetryDelaySeconds seconds. Empty rows can mean no sends in the selected range or provider-side statistics lag."
    if ($RetryDelaySeconds -gt 0) {
        Start-Sleep -Seconds $RetryDelaySeconds
    }
}

$stats | ConvertTo-Json -Depth 10
$data = Get-JsonPropertyValue $stats "Data"
$totalSize = Get-IntProperty $data "TotalSize"
$totalCount = [int64]0
$successCount = [int64]0
$failCount = [int64]0
$noResponseCount = [int64]0
foreach ($target in $targetList) {
    $totalCount += Get-IntProperty $target "TotalCount"
    $successCount += Get-IntProperty $target "RespondedSuccessCount"
    $failCount += Get-IntProperty $target "RespondedFailCount"
    $noResponseCount += Get-IntProperty $target "NoRespondedCount"
}
Write-Host "sms_stats_range=$StartDate-$EndDate"
Write-Host "sms_stats_rows=$($targetList.Count)"
Write-Host "sms_stats_total_size=$totalSize"
Write-Host "sms_stats_total_count=$totalCount"
Write-Host "sms_stats_success_count=$successCount"
Write-Host "sms_stats_fail_count=$failCount"
Write-Host "sms_stats_no_response_count=$noResponseCount"
Write-Host "sms_stats_empty=$($targetList.Count -eq 0)"
Write-Host "sms_stats_note=QuerySendStatistics is for send trend only; package balance and expiry still need Aliyun expense center or SMS console confirmation."
if ($targetList.Count -eq 0) {
    Write-Warning "SMS statistics are empty after retry; keep this as a trend signal, not as proof of package balance or SMS service inactivity."
}
$smsUsageStatus = if ($targetList.Count -eq 0) { "attention" } else { "ready" }
Write-Host "sms_usage_status=$smsUsageStatus"

if ($SkipResourcePackageCheck) {
    Write-Host "sms_package_status=skipped"
} else {
    Write-Host
    Write-Host "== active resource packages =="
    try {
        $resourcePackageResponse = Invoke-AliyunJson @(
            "aliyun", "bssopenapi", "QueryResourcePackageInstances",
            "--PageSize", "$ResourcePackagePageSize"
        )
        Assert-AliyunOk $resourcePackageResponse "Resource package query"
        $resourcePackageData = Get-JsonPropertyValue $resourcePackageResponse "Data"
        $resourcePackageTotalCount = Get-IntProperty $resourcePackageData "TotalCount"
        $packages = @(Get-ResourcePackageList $resourcePackageResponse)
        $smsPackages = @()
        Write-Host "resource_package_query_status=ready"
        Write-Host "resource_package_total_count=$resourcePackageTotalCount"
        Write-Host "resource_package_returned_count=$($packages.Count)"
        foreach ($package in $packages) {
            $commodityCode = Format-InlineValue (Get-StringProperty $package "CommodityCode")
            $packageType = Format-InlineValue (Get-StringProperty $package "PackageType")
            $status = Format-InlineValue (Get-StringProperty $package "Status")
            $remainingAmount = Format-InlineValue (Get-StringProperty $package "RemainingAmount")
            $remainingUnit = Format-InlineValue (Get-StringProperty $package "RemainingAmountUnit")
            $totalAmount = Format-InlineValue (Get-StringProperty $package "TotalAmount")
            $totalUnit = Format-InlineValue (Get-StringProperty $package "TotalAmountUnit")
            $expiryTime = Format-InlineValue (Get-StringProperty $package "ExpiryTime")
            $remark = Format-InlineValue (Get-StringProperty $package "Remark")
            $smsLike = Test-SmsResourcePackage $package
            if ($smsLike) {
                $smsPackages += $package
            }
            Write-Host "resource_package_item commodity=$commodityCode package_type=$packageType status=$status remaining=$remainingAmount$remainingUnit total=$totalAmount$totalUnit expiry=$expiryTime sms_like=$smsLike remark=$remark"
        }
        Write-Host "sms_package_visible=$($smsPackages.Count -gt 0)"
        Write-Host "sms_package_count=$($smsPackages.Count)"
        if ($smsPackages.Count -eq 0) {
            Write-Host "sms_package_status=not_visible_manual_required"
            Write-Warning "BssOpenAPI returned no active SMS-like package. Confirm ordinary SMS package balance, expiry, package allowance warning, and auto-renew in the SMS console."
        } else {
            Write-Host "sms_package_status=visible_not_confirmed"
            Write-Warning "BssOpenAPI returned an SMS-like package, but package allowance warning and auto-renew still need console confirmation before go-live."
        }
    } catch {
        Write-Warning "Resource package query failed: $($_.Exception.Message)"
        Write-Host "resource_package_query_status=attention"
        Write-Host "sms_package_status=manual_required"
    }
}

if (-not [string]::IsNullOrWhiteSpace($PhoneNumber)) {
    Write-Host
    Write-Host "== single phone send details $SendDate =="
    $details = Invoke-AliyunJson @(
        "aliyun", "dysmsapi", "query-send-details",
        "--api-version", "2017-05-25",
        "--region", $RegionId,
        "--phone-number", $PhoneNumber,
        "--send-date", $SendDate,
        "--current-page", "1",
        "--page-size", "20"
    )
    Assert-AliyunOk $details "SMS detail query"
    $details | ConvertTo-Json -Depth 10
}
