param(
    [string]$BillingCycle = (Get-Date).ToString("yyyy-MM"),
    [string]$PreviousBillingCycle = (Get-Date).AddMonths(-1).ToString("yyyy-MM"),
    [int]$PageSize = 300,
    [int]$DailyLookbackDays = 15,
    [int]$RecentModelAverageDays = 5,
    [double]$MonthlyAttentionYuan = 100.0,
    [double]$SlsMonthlyAttentionYuan = 5.0,
    [double]$SmsPayAsYouGoAttentionYuan = 10.0,
    [int]$MaxCommandAttempts = 3,
    [int]$RetryDelaySeconds = 2,
    [switch]$FailOnWarning
)

$ErrorActionPreference = "Stop"

$warnings = New-Object System.Collections.Generic.List[string]
$errors = New-Object System.Collections.Generic.List[string]
$DypnsFusionDisabledCutoffUtc = [DateTime]::Parse("2026-06-15T00:00:00Z").ToUniversalTime()

function Add-WarningItem {
    param([string]$Message)
    $warnings.Add($Message) | Out-Null
}

function Add-ErrorItem {
    param([string]$Message)
    $errors.Add($Message) | Out-Null
}

function Redact-SensitiveText {
    param([string]$Text)
    if ([string]::IsNullOrEmpty($Text)) {
        return $Text
    }
    return $Text `
        -replace '(?i)(AccessKeyId=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(AccessKeySecret=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(SecurityToken=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(Signature=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(Signature%3D)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(SignatureNonce=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(SignatureNonce%3D)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|OwnerId|OwnerID|BillAccountID|AccountID|AccountName|BillAccountName|OwnerName)"\s*:\s*")[^"]+', '${1}REDACTED'
}

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
    $attemptLimit = [Math]::Max(1, $MaxCommandAttempts)
    $lastSafeOutput = ""
    $safeCommand = if ($ArgsList.Length -ge 3) {
        "$($ArgsList[0]) $($ArgsList[1]) $($ArgsList[2])"
    } else {
        $ArgsList -join " "
    }
    for ($attempt = 1; $attempt -le $attemptLimit; $attempt++) {
        $stdoutPath = [IO.Path]::GetTempFileName()
        $stderrPath = [IO.Path]::GetTempFileName()
        try {
            $process = Start-Process -FilePath $exe -ArgumentList $arguments -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
            $stdoutText = ""
            $stderrText = ""
            if (Test-Path -LiteralPath $stdoutPath) {
                $stdoutText = [IO.File]::ReadAllText($stdoutPath, [Text.Encoding]::UTF8)
            }
            if (Test-Path -LiteralPath $stderrPath) {
                $stderrText = [IO.File]::ReadAllText($stderrPath, [Text.Encoding]::UTF8)
            }
            $lastSafeOutput = Redact-SensitiveText (($stdoutText + "`n" + $stderrText))
            if ($process.ExitCode -eq 0) {
                if ([string]::IsNullOrWhiteSpace($stdoutText)) {
                    return $null
                }
                try {
                    return $stdoutText | ConvertFrom-Json
                } catch {
                    throw "Command returned invalid JSON: $safeCommand"
                }
            }
            $retryable = $lastSafeOutput -match '(?i)timeout|deadline|temporar|connection reset|i/o timeout|awaiting headers'
            if ($retryable -and $attempt -lt $attemptLimit) {
                Write-Warning "Command failed transiently, retrying $attempt/$attemptLimit`: $safeCommand"
                if ($RetryDelaySeconds -gt 0) {
                    Start-Sleep -Seconds $RetryDelaySeconds
                }
                continue
            }
            throw "Command failed: $safeCommand`n$lastSafeOutput"
        } finally {
            Remove-Item -LiteralPath $stdoutPath -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
        }
    }
    throw "Command failed: $safeCommand`n$lastSafeOutput"
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

function Assert-AliyunSuccess {
    param(
        $Response,
        [string]$Operation
    )
    $code = Get-JsonPropertyValue $Response "Code"
    if ([string]::IsNullOrWhiteSpace([string]$code) -or $code -eq "OK" -or $code -eq "Success" -or [string]$code -eq "200") {
        return
    }
    $message = Get-JsonPropertyValue $Response "Message"
    if ([string]::IsNullOrWhiteSpace([string]$message)) {
        $message = "no message"
    }
    throw "$Operation failed: Code=$code Message=$message"
}

function Get-BillItems {
    param(
        [string]$Cycle,
        [string]$Granularity = "MONTHLY",
        [switch]$GroupByProduct,
        [string]$BillingDate = ""
    )
    $args = @(
        "aliyun", "bssopenapi", "QueryAccountBill",
        "--BillingCycle", $Cycle,
        "--Granularity", $Granularity,
        "--PageSize", "$PageSize"
    )
    if ($GroupByProduct) {
        $args += @("--IsGroupByProduct", "true")
    }
    if (-not [string]::IsNullOrWhiteSpace($BillingDate)) {
        $args += @("--BillingDate", $BillingDate)
    }
    $response = Invoke-AliyunJson $args
    Assert-AliyunSuccess $response "QueryAccountBill $Cycle $Granularity"
    $items = Get-JsonPropertyValue (Get-JsonPropertyValue $response.Data "Items") "Item"
    if ($null -eq $items) {
        return @()
    }
    return @($items)
}

function Get-BillOverviewItems {
    param([string]$Cycle)
    $response = Invoke-AliyunJson @("aliyun", "bssopenapi", "QueryBillOverview", "--BillingCycle", $Cycle)
    Assert-AliyunSuccess $response "QueryBillOverview $Cycle"
    $items = Get-JsonPropertyValue (Get-JsonPropertyValue $response.Data "Items") "Item"
    if ($null -eq $items) {
        return @()
    }
    return @($items)
}

function Format-Money {
    param($Value)
    $number = 0.0
    if ($null -ne $Value) {
        [void][double]::TryParse([string]$Value, [ref]$number)
    }
    return ("{0:N4}" -f $number)
}

function Get-Double {
    param($Value)
    $number = 0.0
    if ($null -ne $Value) {
        [void][double]::TryParse([string]$Value, [ref]$number)
    }
    return $number
}

function Format-InlineValue {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return "-"
    }
    return (($Value -replace '[\r\n\t]+', ' ') -replace '\s{2,}', ' ').Trim()
}

function Format-SafeInstanceId {
    param([string]$Value)
    $inline = Format-InlineValue $Value
    if ($inline -match '^\d{12,}$') {
        return "account-id-redacted"
    }
    return $inline
}

function Get-DaysUntil {
    param([string]$DateText)
    if ([string]::IsNullOrWhiteSpace($DateText) -or $DateText.StartsWith("2999-")) {
        return $null
    }
    try {
        $date = [DateTime]::Parse($DateText).ToUniversalTime()
        return [math]::Floor(($date - [DateTime]::UtcNow).TotalDays)
    } catch {
        return $null
    }
}

function Write-BillSummary {
    param(
        [string]$Title,
        [object[]]$Items
    )
    Write-Host "== $Title =="
    $nonZero = @($Items | Where-Object {
        (Get-Double $_.PretaxAmount) -ne 0.0 -or
        (Get-Double $_.PretaxGrossAmount) -ne 0.0 -or
        (Get-Double $_.OutstandingAmount) -ne 0.0 -or
        (Get-Double $_.PaymentAmount) -ne 0.0
    } | Sort-Object @{ Expression = { Get-Double $_.PretaxAmount }; Descending = $true }, ProductName, SubscriptionType)
    if ($nonZero.Count -eq 0) {
        Write-Host "no_nonzero_bill_items=true"
        return
    }
    foreach ($item in $nonZero) {
        $product = Format-InlineValue ([string]$item.ProductName)
        if ($item.PSObject.Properties["ProductDetail"]) {
            $detailValue = [string]$item.ProductDetail
        } else {
            $detailValue = [string]$item.ProductCode
        }
        $detail = Format-InlineValue $detailValue
        $subscription = Format-InlineValue ([string]$item.SubscriptionType)
        $pretax = Format-Money $item.PretaxAmount
        $gross = Format-Money $item.PretaxGrossAmount
        $payment = Format-Money $item.PaymentAmount
        $outstanding = Format-Money $item.OutstandingAmount
        Write-Host "bill_item product=$product detail=$detail subscription=$subscription pretax_yuan=$pretax gross_yuan=$gross payment_yuan=$payment outstanding_yuan=$outstanding"
    }
}

function Test-InstanceInteresting {
    param($Instance)
    $subscriptionType = [string]$Instance.SubscriptionType
    $productCode = [string]$Instance.ProductCode
    $productType = [string]$Instance.ProductType
    if ($subscriptionType -eq "Subscription") {
        return $true
    }
    if ($productCode -in @("ecs", "rds", "kvstore", "oss", "dypns", "dysms", "sms", "sfm", "sls", "snapshot")) {
        return $true
    }
    if ($productType -match '(?i)dypns|dysms|sfm|sls|oss|snapshot') {
        return $true
    }
    return $false
}

function Test-ResourcePackageSmsLike {
    param($Package)
    $products = Get-JsonPropertyValue (Get-JsonPropertyValue $Package "ApplicableProducts") "Product"
    $text = @(
        [string]$Package.CommodityCode,
        [string]$Package.PackageType,
        [string]$Package.Remark,
        (@($products) -join " ")
    ) -join " "
    return $text -match '(?i)sms|dysms|short\s*message|短信'
}

function Test-AutoRenewStatus {
    param([string]$RenewStatus)
    $normalized = (Format-InlineValue $RenewStatus)
    if ($normalized -eq "-") {
        return $false
    }
    return $normalized -match '(?i)auto.?renew|自动续费|AutoRenewal'
}

Write-Host "== Aliyun cost guard =="
Write-Host "billing_cycle=$BillingCycle previous_billing_cycle=$PreviousBillingCycle monthly_attention_yuan=$MonthlyAttentionYuan"

try {
    $balance = Invoke-AliyunJson @("aliyun", "bssopenapi", "QueryAccountBalance")
    Assert-AliyunSuccess $balance "QueryAccountBalance"
    Write-Host "account_balance_available_yuan=$(Format-Money $balance.Data.AvailableAmount)"
    if ((Get-Double $balance.Data.AvailableAmount) -lt 100) {
        Add-WarningItem "account_balance_below_100_yuan"
    }
} catch {
    Write-Warning "Account balance query failed: $($_.Exception.Message)"
    Add-WarningItem "account_balance_query_failed"
}

Write-Host
$currentItems = @(Get-BillItems -Cycle $BillingCycle -Granularity "MONTHLY" -GroupByProduct)
Write-BillSummary -Title "monthly product bill $BillingCycle" -Items $currentItems
$currentTotal = (@($currentItems | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
Write-Host "monthly_product_pretax_total_yuan=$(Format-Money $currentTotal)"
if ($currentTotal -ge $MonthlyAttentionYuan) {
    Add-WarningItem "monthly_pretax_total_reaches_attention_threshold:${BillingCycle}:$(Format-Money $currentTotal)"
}

Write-Host
$previousItems = @(Get-BillItems -Cycle $PreviousBillingCycle -Granularity "MONTHLY" -GroupByProduct)
Write-BillSummary -Title "monthly product bill $PreviousBillingCycle" -Items $previousItems
$previousTotal = (@($previousItems | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
Write-Host "previous_month_product_pretax_total_yuan=$(Format-Money $previousTotal)"

Write-Host
Write-Host "== current month detailed bill $BillingCycle =="
$overviewItems = @(Get-BillOverviewItems -Cycle $BillingCycle)
$nonZeroOverview = @($overviewItems | Where-Object {
    (Get-Double $_.PretaxAmount) -ne 0.0 -or
    (Get-Double $_.PretaxGrossAmount) -ne 0.0 -or
    (Get-Double $_.OutstandingAmount) -ne 0.0 -or
    (Get-Double $_.PaymentAmount) -ne 0.0
} | Sort-Object @{ Expression = { Get-Double $_.PretaxAmount }; Descending = $true }, ProductName, ProductDetail)
foreach ($item in $nonZeroOverview) {
    $product = Format-InlineValue ([string]$item.ProductName)
    $detail = Format-InlineValue ([string]$item.ProductDetail)
    $type = Format-InlineValue ([string]$item.ProductType)
    Write-Host "bill_detail product=$product detail=$detail type=$type subscription=$($item.SubscriptionType) pretax_yuan=$(Format-Money $item.PretaxAmount) gross_yuan=$(Format-Money $item.PretaxGrossAmount) payment_yuan=$(Format-Money $item.PaymentAmount) outstanding_yuan=$(Format-Money $item.OutstandingAmount)"
}

$dypnsCost = (@($currentItems | Where-Object { [string]$_.ProductCode -eq "dypns" -or [string]$_.ProductName -match "号码认证" } | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
if ($dypnsCost -gt 0) {
    Write-Host "dypns_fusion_cost_note=sunk_cost_new_android_unused billing_cycle=$BillingCycle pretax_yuan=$(Format-Money $dypnsCost)"
}
$slsCost = (@($currentItems | Where-Object { [string]$_.ProductCode -eq "sls" -or [string]$_.ProductName -match "日志服务" } | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
if ($slsCost -ge $SlsMonthlyAttentionYuan) {
    Add-WarningItem "sls_monthly_cost_reaches_attention_threshold:${BillingCycle}:$(Format-Money $slsCost)"
}
$smsPaygCost = (@($overviewItems | Where-Object { ([string]$_.ProductCode -match "dysms|sms" -or [string]$_.ProductName -match "短信") -and [string]$_.SubscriptionType -eq "PayAsYouGo" } | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
if ($smsPaygCost -ge $SmsPayAsYouGoAttentionYuan) {
    Add-WarningItem "sms_payg_cost_reaches_attention_threshold:${BillingCycle}:$(Format-Money $smsPaygCost)"
}

Write-Host
Write-Host "== daily model cost =="
$dailyRows = New-Object System.Collections.Generic.List[object]
$cycleDate = [DateTime]::ParseExact("$BillingCycle-01", "yyyy-MM-dd", $null)
$today = (Get-Date).Date
$cycleEnd = $cycleDate.AddMonths(1).AddDays(-1)
$lastDay = if ($cycleEnd -lt $today) { $cycleEnd } else { $today }
$firstDay = $lastDay.AddDays(-1 * ([Math]::Max(1, $DailyLookbackDays) - 1))
if ($firstDay -lt $cycleDate) {
    $firstDay = $cycleDate
}
for ($day = $firstDay; $day -le $lastDay; $day = $day.AddDays(1)) {
    $dateText = $day.ToString("yyyy-MM-dd")
    try {
        $items = @(Get-BillItems -Cycle $BillingCycle -Granularity "DAILY" -GroupByProduct -BillingDate $dateText)
        $sfmPretax = (@($items | Where-Object { [string]$_.ProductCode -eq "sfm" -or [string]$_.ProductName -match "百炼|模型" } | ForEach-Object { Get-Double $_.PretaxAmount }) | Measure-Object -Sum).Sum
        $sfmGross = (@($items | Where-Object { [string]$_.ProductCode -eq "sfm" -or [string]$_.ProductName -match "百炼|模型" } | ForEach-Object { Get-Double $_.PretaxGrossAmount }) | Measure-Object -Sum).Sum
        if ($sfmPretax -gt 0 -or $sfmGross -gt 0) {
            Write-Host "daily_model date=$dateText pretax_yuan=$(Format-Money $sfmPretax) gross_yuan=$(Format-Money $sfmGross)"
        }
        $dailyRows.Add([pscustomobject]@{ Date = $dateText; Pretax = $sfmPretax; Gross = $sfmGross }) | Out-Null
    } catch {
        Write-Warning "Daily model bill query failed for ${dateText}: $($_.Exception.Message)"
        Add-WarningItem "daily_model_bill_query_failed:$dateText"
    }
}
$recentRows = @($dailyRows | Select-Object -Last ([Math]::Max(1, $RecentModelAverageDays)))
$recentSum = (@($recentRows | ForEach-Object { $_.Pretax }) | Measure-Object -Sum).Sum
$recentAvg = if ($recentRows.Count -gt 0) { $recentSum / $recentRows.Count } else { 0 }
Write-Host "daily_model_recent_days=$($recentRows.Count) recent_pretax_total_yuan=$(Format-Money $recentSum) recent_avg_yuan_per_day=$(Format-Money $recentAvg)"

Write-Host
Write-Host "== active resource packages =="
try {
    $packageResponse = Invoke-AliyunJson @("aliyun", "bssopenapi", "QueryResourcePackageInstances", "--PageSize", "$PageSize")
    Assert-AliyunSuccess $packageResponse "QueryResourcePackageInstances"
    $packages = @(Get-JsonPropertyValue (Get-JsonPropertyValue $packageResponse.Data "Instances") "Instance")
    if ($packages.Count -eq 0) {
        Write-Host "active_resource_packages=none"
    }
    $smsLikePackages = @()
    foreach ($package in $packages) {
        $products = Get-JsonPropertyValue (Get-JsonPropertyValue $package "ApplicableProducts") "Product"
        $remaining = "$(Format-InlineValue ([string]$package.RemainingAmount))$(Format-InlineValue ([string]$package.RemainingAmountUnit))"
        $total = "$(Format-InlineValue ([string]$package.TotalAmount))$(Format-InlineValue ([string]$package.TotalAmountUnit))"
        $smsLike = Test-ResourcePackageSmsLike $package
        if ($smsLike) {
            $smsLikePackages += $package
        }
        Write-Host "resource_package commodity=$(Format-InlineValue ([string]$package.CommodityCode)) status=$(Format-InlineValue ([string]$package.Status)) remaining=$remaining total=$total effective=$($package.EffectiveTime) expiry=$($package.ExpiryTime) region=$(Format-InlineValue ([string]$package.Region)) sms_like=$smsLike remark=$(Format-InlineValue ([string]$package.Remark)) applies=$(@($products) -join ',')"
        $daysLeft = Get-DaysUntil ([string]$package.ExpiryTime)
        if ($null -ne $daysLeft -and $daysLeft -lt 60) {
            Add-WarningItem "resource_package_expires_within_60_days:$($package.CommodityCode):${daysLeft}d"
        }
        $remainingValue = Get-Double $package.RemainingAmount
        $totalValue = Get-Double $package.TotalAmount
        if ($totalValue -gt 0) {
            $remainingPct = $remainingValue / $totalValue * 100
            if ($remainingPct -lt 20) {
                Add-WarningItem "resource_package_remaining_below_20_percent:$($package.CommodityCode):$([math]::Round($remainingPct,2))%"
            }
        }
    }
    if ($smsLikePackages.Count -eq 0) {
        Write-Host "sms_package_visible_in_resource_package_api=false"
        Add-WarningItem "sms_package_not_visible_in_resource_package_api_manual_console_confirmation_required"
    } else {
        Write-Host "sms_package_visible_in_resource_package_api=true count=$($smsLikePackages.Count)"
    }
} catch {
    Write-Warning "Resource package query failed: $($_.Exception.Message)"
    Add-WarningItem "resource_package_query_failed"
}

Write-Host
Write-Host "== active billable instances =="
try {
    $instancesResponse = Invoke-AliyunJson @("aliyun", "bssopenapi", "QueryAvailableInstances", "--PageSize", "$PageSize")
    Assert-AliyunSuccess $instancesResponse "QueryAvailableInstances"
    $instances = @(Get-JsonPropertyValue $instancesResponse.Data "InstanceList")
    $interesting = @($instances | Where-Object { Test-InstanceInteresting $_ } | Sort-Object SubscriptionType, ProductCode, ProductType, InstanceID)
    Write-Host "available_instance_count=$($instances.Count) interesting_instance_count=$($interesting.Count)"
    foreach ($instance in $interesting) {
        $instanceId = Format-SafeInstanceId ([string]$instance.InstanceID)
        $productCode = Format-InlineValue ([string]$instance.ProductCode)
        $productType = Format-InlineValue ([string]$instance.ProductType)
        $subscription = Format-InlineValue ([string]$instance.SubscriptionType)
        $status = Format-InlineValue ([string]$instance.Status)
        $createTime = Format-InlineValue ([string]$instance.CreateTime)
        $endTime = Format-InlineValue ([string]$instance.EndTime)
        $renewStatus = Format-InlineValue ([string]$instance.RenewStatus)
        Write-Host "available_instance product=$productCode type=$productType subscription=$subscription status=$status renew=$renewStatus created=$createTime end=$endTime id=$instanceId"
        if ($productCode -eq "dypns" -and $subscription -eq "Subscription") {
            Write-Host "dypns_fusion_instance_note=sunk_cost_new_android_unused_no_refund_required renew=$renewStatus end=$endTime id=$instanceId"
            if (Test-AutoRenewStatus $renewStatus) {
                Add-WarningItem "active_dypns_subscription_auto_renew_review_no_new_use:$instanceId"
            }
            try {
                $createdUtc = [DateTime]::Parse([string]$instance.CreateTime).ToUniversalTime()
                if ($createdUtc -gt $DypnsFusionDisabledCutoffUtc) {
                    Add-WarningItem "new_dypns_subscription_after_fusion_auth_disabled:$instanceId"
                }
            } catch {
                Add-WarningItem "dypns_subscription_create_time_unreadable:$instanceId"
            }
        }
        $daysLeft = Get-DaysUntil ([string]$instance.EndTime)
        if ($null -ne $daysLeft -and $daysLeft -lt 60) {
            Add-WarningItem "billable_instance_expires_within_60_days:${productCode}:${instanceId}:${daysLeft}d"
        }
    }
} catch {
    Write-Warning "Available instances query failed: $($_.Exception.Message)"
    Add-WarningItem "available_instances_query_failed"
}

Write-Host
Write-Host "== interpretation =="
Write-Host "cost_note=Prepaid ECS/RDS/Redis/domain/OSS/SMS/DYPNS packages are mostly sunk or renewal costs; DYPNS/fusion is no longer used by new Android and is treated as sunk cost unless auto-renew or new purchase appears; current monthly attention should focus on model usage, SMS PayAsYouGo, SLS growth, snapshots, and real renewal decisions."
Write-Host "model_note=Early prompt tests can create spikes. Use recent daily average and resource package remaining tokens before buying more plans."
Write-Host "storage_note=Ask images stay in OSS uploads/ for 3 days, support images in support/ for 30 days, and internal test APKs in test-apks/ for 3 days; current storage cost should stay tiny unless traffic or model image analysis grows."

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
