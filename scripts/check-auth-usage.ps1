param(
    [string]$StartDate = (Get-Date).AddDays(-6).ToString("yyyyMMdd"),
    [string]$EndDate = (Get-Date).ToString("yyyyMMdd"),
    [string]$Month = (Get-Date).ToString("yyyyMM"),
    [string]$RegionId = "cn-hangzhou"
)

$ErrorActionPreference = "Stop"

function Invoke-AliyunJson {
    param([string[]]$ArgsList)
    $raw = & $ArgsList[0] @($ArgsList[1..($ArgsList.Length - 1)])
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $($ArgsList -join ' ')"
    }
    return $raw | ConvertFrom-Json
}

function Write-Statistic {
    param(
        [string]$Name,
        [int]$AuthenticationType
    )
    Write-Host "== $Name statistic $StartDate-$EndDate =="
    $result = Invoke-AliyunJson @(
        "aliyun", "dypnsapi", "query-gate-verify-statistic-public",
        "--region", $RegionId,
        "--start-date", $StartDate,
        "--end-date", $EndDate,
        "--authentication-type", "$AuthenticationType"
    )
    $dayStats = @($result.Data.DayStatistic)
    if ($dayStats.Count -eq 0) {
        Write-Host "no_data"
        return
    }
    $dayStats | ConvertTo-Json -Depth 8
}

function Write-Billing {
    param(
        [string]$Name,
        [int]$AuthenticationType
    )
    Write-Host "== $Name billing $Month =="
    $result = Invoke-AliyunJson @(
        "aliyun", "dypnsapi", "query-gate-verify-billing-public",
        "--region", $RegionId,
        "--month", $Month,
        "--authentication-type", "$AuthenticationType"
    )
    $result | ConvertTo-Json -Depth 8
}

Write-Statistic -Name "one_click_login" -AuthenticationType 1
Write-Host
Write-Statistic -Name "sms_auth" -AuthenticationType 3
Write-Host
Write-Billing -Name "one_click_login" -AuthenticationType 1
Write-Host
Write-Billing -Name "sms_auth" -AuthenticationType 4
