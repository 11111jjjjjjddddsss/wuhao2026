param(
    [string]$StartDate = (Get-Date).AddDays(-6).ToString("yyyyMMdd"),
    [string]$EndDate = (Get-Date).ToString("yyyyMMdd"),
    [string]$RegionId = "cn-hangzhou",
    [string]$SignName = "北京农技千问科技",
    [int]$PageSize = 20,
    [int]$PageIndex = 1,
    [string]$PhoneNumber = "",
    [string]$SendDate = (Get-Date).ToString("yyyyMMdd")
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
$stats = Invoke-AliyunJson $statsArgs
$stats | ConvertTo-Json -Depth 10

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
    $details | ConvertTo-Json -Depth 10
}

