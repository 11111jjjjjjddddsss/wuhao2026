param(
    [string]$RegionId = "cn-beijing",
    [string]$ProjectName = "nongjiqiancha-prod-1159547719787456",
    [int]$Minutes = 30,
    [int]$Line = 50,
    [string]$ServerQuery = "http_request_error OR http_request_slow OR http_request",
    [string]$NginxQuery = "*"
)

$ErrorActionPreference = "Stop"

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
            -replace '(?i)(Content=)[^&\s]+', '${1}REDACTED' `
            -replace '(?<!\d)1[3-9]\d{9}(?!\d)', 'PHONE_REDACTED' `
            -replace '(?i)((?:MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEYS?|DASHSCOPE_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY(?:S|_[0-9]+)?|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content|MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEY|DASHSCOPE_API_KEYS|DASHSCOPE_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY|CHAT_PRIMARY_API_KEYS|CHAT_PRIMARY_API_KEY_[0-9]+|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)"\s*:\s*")[^"]+', '${1}REDACTED'
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

function Write-RedactedJson {
    param([object]$Value)
    if ($null -eq $Value) {
        return
    }
    $text = $Value | ConvertTo-Json -Depth 20
    $text = $text `
        -replace '(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH) ([^ ?"]+)\?[^ "]*', '${1} ${2}?REDACTED' `
        -replace '(Bearer )[A-Za-z0-9._~+\/=-]+', '${1}REDACTED' `
        -replace 'sk-[A-Za-z0-9_-]{16,}', 'sk-REDACTED' `
        -replace '([0-9]{1,3}\.[0-9]{1,3})\.[0-9]{1,3}\.[0-9]{1,3}', '${1}.*.*' `
        -replace '(?<!\d)1[3-9]\d{9}(?!\d)', 'PHONE_REDACTED' `
        -replace '(?i)(AccessKey(Id|Secret)?[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(SecurityToken[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(Signature(Nonce)?[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)((?:MYSQL_URL|MYSQL_DSN)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(REDIS_PASSWORD[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(DYPNS_ACCESS_KEY_(?:ID|SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(ALIYUN_DYPNS_ACCESS_KEY_(?:ID|SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(SMS_ACCESS_KEY_(?:ID|SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(DASHSCOPE_API_KEY(_[0-9]+)?[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(DASHSCOPE_API_KEYS[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(CHAT_PRIMARY_API_KEY(S|_[0-9]+)?[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(OSS_ACCESS_KEY_(ID|SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(APP_SECRET[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(SUPPORT_ADMIN_SECRET[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)(DAILY_AGRI_JOB_SECRET[=:][\s]*)[^, "&]+', '${1}REDACTED'
    Write-Output $text
}

if ($Minutes -le 0) {
    $Minutes = 30
}
if ($Line -le 0 -or $Line -gt 100) {
    $Line = 50
}

$to = [int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$from = $to - ($Minutes * 60)

Write-Host "== SLS server-go =="
$serverLogs = Invoke-JsonCommand @(
    "aliyun", "sls", "get-logs",
    "--region", $RegionId,
    "--project", $ProjectName,
    "--logstore", "server-go",
    "--from", "$from",
    "--to", "$to",
    "--query", $ServerQuery,
    "--line", "$Line",
    "--reverse", "true"
)
Write-RedactedJson $serverLogs

Write-Host
Write-Host "== SLS nginx-error =="
$nginxLogs = Invoke-JsonCommand @(
    "aliyun", "sls", "get-logs",
    "--region", $RegionId,
    "--project", $ProjectName,
    "--logstore", "nginx-error",
    "--from", "$from",
    "--to", "$to",
    "--query", $NginxQuery,
    "--line", "$Line",
    "--reverse", "true"
)
Write-RedactedJson $nginxLogs
