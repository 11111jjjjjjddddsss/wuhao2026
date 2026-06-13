param(
    [string]$RegionId = "cn-beijing",
    [string]$ProjectName = "nongjiqiancha-prod-1159547719787456",
    [string]$ActionPolicyId = "",
    [string]$DashboardId = "",
    [switch]$DryRun
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

function ConvertTo-CompactJson {
    param([object]$Value)
    return ($Value | ConvertTo-Json -Depth 30 -Compress)
}

function ConvertTo-AliyunObjectArg {
    param([object]$Value)
    return (ConvertTo-CompactJson $Value) -replace '"', '\"'
}

function New-SlsAlertConfiguration {
    param(
        [string]$AlertName,
        [string]$DisplayName,
        [string]$Logstore,
        [string]$Query,
        [int]$Severity,
        [string]$Condition,
        [string]$RepeatInterval,
        [string]$Runbook
    )
    return [ordered]@{
        type = "default"
        version = "2.0"
        dashboard = $DashboardId
        threshold = 1
        noDataFire = $false
        noDataSeverity = 6
        sendResolved = $true
        muteUntil = 0
        autoAnnotation = $true
        queryList = @(
            [ordered]@{
                chartTitle = $DisplayName
                dashboardId = $DashboardId
                project = $ProjectName
                store = $Logstore
                storeType = "log"
                region = $RegionId
                query = $Query
                start = "-5m"
                end = "now"
                timeSpanType = "Custom"
                powerSqlMode = "auto"
                roleArn = ""
                ui = ""
            }
        )
        groupConfiguration = [ordered]@{
            type = "no_group"
            fields = @()
        }
        joinConfigurations = @()
        conditionConfiguration = [ordered]@{
            condition = $Condition
            countCondition = ""
        }
        severityConfigurations = @(
            [ordered]@{
                severity = $Severity
                evalCondition = [ordered]@{
                    condition = $Condition
                    countCondition = ""
                }
            }
        )
        labels = @(
            [ordered]@{ key = "app"; value = "nongjiqiancha" },
            [ordered]@{ key = "managed_by"; value = "scripts/setup-sls-alerts.ps1" }
        )
        annotations = @(
            [ordered]@{ key = "summary"; value = $DisplayName },
            [ordered]@{ key = "runbook"; value = $Runbook }
        )
        policyConfiguration = [ordered]@{
            alertPolicyId = "sls.builtin.dynamic"
            actionPolicyId = $ActionPolicyId
            repeatInterval = $RepeatInterval
        }
        sinkAlerthub = [ordered]@{
            enabled = $true
        }
        sinkCms = [ordered]@{
            enabled = $false
        }
        sinkEventStore = [ordered]@{
            enabled = $false
        }
        templateConfiguration = [ordered]@{
            type = "builtin"
            id = "sls.builtin.content"
            version = "1.0"
            lang = "cn"
            tokens = @{}
            aonotations = @{}
        }
        tags = @()
    }
}

function New-FixedRateSchedule {
    param([string]$Interval)
    return [ordered]@{
        type = "FixedRate"
        interval = $Interval
        delay = 0
        runImmediately = $false
        timeZone = "Asia/Shanghai"
        cronExpression = ""
    }
}

function Get-AlertExists {
    param([string]$AlertName)
    try {
        Invoke-JsonCommand @(
            "aliyun", "sls", "get-alert",
            "--region", $RegionId,
            "--project", $ProjectName,
            "--alert-name", $AlertName
        ) | Out-Null
        return $true
    } catch {
        if ($_.Exception.Message -match "not exist|NotExist|JobNotExist|404|NoSuch|does not exist") {
            return $false
        }
        throw
    }
}

function Ensure-SlsAlert {
    param(
        [string]$Name,
        [string]$DisplayName,
        [string]$Description,
        [string]$Logstore,
        [string]$Query,
        [int]$Severity,
        [string]$Condition = "cnt > 0",
        [string]$Interval = "5m",
        [string]$RepeatInterval = "30m",
        [string]$Runbook = "docs/runbooks/logs-sls.md"
    )
    $configuration = New-SlsAlertConfiguration `
        -AlertName $Name `
        -DisplayName $DisplayName `
        -Logstore $Logstore `
        -Query $Query `
        -Severity $Severity `
        -Condition $Condition `
        -RepeatInterval $RepeatInterval `
        -Runbook $Runbook
    $schedule = New-FixedRateSchedule -Interval $Interval
    $configurationArg = ConvertTo-AliyunObjectArg $configuration
    $scheduleArg = ConvertTo-AliyunObjectArg $schedule
    $exists = Get-AlertExists -AlertName $Name
    $action = if ($exists) { "update-alert" } else { "create-alert" }
    if ($DryRun) {
        Write-Host "alert=dry_run:$Name action=$action"
        return
    }
    if ($exists) {
        Invoke-JsonCommand @(
            "aliyun", "sls", "update-alert",
            "--region", $RegionId,
            "--project", $ProjectName,
            "--alert-name", $Name,
            "--display-name", $DisplayName,
            "--description", $Description,
            "--configuration", $configurationArg,
            "--schedule", $scheduleArg
        ) | Out-Null
        Write-Host "alert=updated:$Name"
    } else {
        Invoke-JsonCommand @(
            "aliyun", "sls", "create-alert",
            "--region", $RegionId,
            "--project", $ProjectName,
            "--name", $Name,
            "--display-name", $DisplayName,
            "--description", $Description,
            "--configuration", $configurationArg,
            "--schedule", $scheduleArg
        ) | Out-Null
        Write-Host "alert=created:$Name"
    }
}

$alerts = @(
    @{
        Name = "nongji-server-5xx";
        DisplayName = "Nongji Go 5xx";
        Description = "server-go Logstore detected Go handler 5xx requests in 5 minutes.";
        Logstore = "server-go";
        Query = "http_request_error | select count(1) as cnt";
        Severity = 8;
        RepeatInterval = "30m";
    },
    @{
        Name = "nongji-server-slow";
        DisplayName = "Nongji Go non-SSE slow requests";
        Description = "server-go Logstore detected at least 5 non-SSE slow requests in 5 minutes. Normal /api/chat/stream long connections are logged as http_sse_stream instead.";
        Logstore = "server-go";
        Query = "http_request_slow | select count(1) as cnt";
        Severity = 6;
        Condition = "cnt >= 5";
        RepeatInterval = "60m";
    },
    @{
        Name = "nongji-nginx-upstream";
        DisplayName = "Nongji Nginx upstream error";
        Description = "nginx-error Logstore detected upstream errors in 5 minutes.";
        Logstore = "nginx-error";
        Query = "upstream | select count(1) as cnt";
        Severity = 8;
        RepeatInterval = "30m";
    },
    @{
        Name = "nongji-daily-agri-failed";
        DisplayName = "Nongji daily agri failed";
        Description = "server-go Logstore detected today agri generation failures in 5 minutes.";
        Logstore = "server-go";
        Query = "generate today agri card failed | select count(1) as cnt";
        Severity = 8;
        RepeatInterval = "60m";
        Runbook = "docs/runbooks/today-agri-card.md";
    },
    @{
        Name = "nongji-model-auth-config";
        DisplayName = "Nongji model or auth config";
        Description = "server-go Logstore detected model key or SMS critical config errors in 5 minutes.";
        Logstore = "server-go";
        Query = "missing_key OR MODEL_BACKEND_NOT_CONFIGURED OR sms_auth_not_configured OR sms_send_not_configured OR sms_provider_config_invalid OR sms_cache_not_configured | select count(1) as cnt";
        Severity = 8;
        RepeatInterval = "60m";
        Runbook = "docs/runbooks/model-key-pool.md";
    }
)

foreach ($alert in $alerts) {
    Ensure-SlsAlert `
        -Name $alert.Name `
        -DisplayName $alert.DisplayName `
        -Description $alert.Description `
        -Logstore $alert.Logstore `
        -Query $alert.Query `
        -Severity $alert.Severity `
        -Condition ($(if ($alert.ContainsKey("Condition")) { $alert.Condition } else { "cnt > 0" })) `
        -RepeatInterval ($(if ($alert.ContainsKey("RepeatInterval")) { $alert.RepeatInterval } else { "30m" })) `
        -Runbook ($(if ($alert.ContainsKey("Runbook")) { $alert.Runbook } else { "docs/runbooks/logs-sls.md" }))
}

if ([string]::IsNullOrWhiteSpace($ActionPolicyId)) {
    Write-Warning "No ActionPolicyId was provided. Alerts will still enter SLS AlertHub, but external notification delivery remains unconfigured."
}
if ([string]::IsNullOrWhiteSpace($DashboardId)) {
    Write-Warning "No DashboardId was provided. Alerts are not associated with a custom SLS dashboard yet."
}

Write-Host
Write-Host "== alerts =="
Invoke-JsonCommand @(
    "aliyun", "sls", "list-alerts",
    "--region", $RegionId,
    "--project", $ProjectName,
    "--size", "50"
) | ConvertTo-Json -Depth 20
