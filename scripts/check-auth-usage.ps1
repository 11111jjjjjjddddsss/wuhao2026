param(
    [string]$StartDate = (Get-Date).AddDays(-6).ToString("yyyyMMdd"),
    [string]$EndDate = (Get-Date).ToString("yyyyMMdd"),
    [string]$Month = (Get-Date).ToString("yyyyMM"),
    [string]$RegionId = "cn-hangzhou"
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
            -replace '(?i)(Content=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content)"\s*:\s*")[^"]+', '${1}REDACTED'
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
