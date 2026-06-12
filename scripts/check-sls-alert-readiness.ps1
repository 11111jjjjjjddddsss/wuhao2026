param(
    [string]$RegionId = "cn-beijing",
    [string]$ProjectName = "nongjiqiancha-prod-1159547719787456",
    [switch]$RequireExternalNotification,
    [switch]$RequireDashboard,
    [switch]$FailOnWarning
)

$ErrorActionPreference = "Stop"

$expectedAlerts = @(
    @{ Name = "nongji-server-5xx"; Logstore = "server-go"; Severity = 8; Query = "http_request_error | select count(1) as cnt" },
    @{ Name = "nongji-server-slow"; Logstore = "server-go"; Severity = 6; Query = "http_request_slow | select count(1) as cnt" },
    @{ Name = "nongji-nginx-upstream"; Logstore = "nginx-error"; Severity = 8; Query = "upstream | select count(1) as cnt" },
    @{ Name = "nongji-daily-agri-failed"; Logstore = "server-go"; Severity = 8; Query = "generate today agri card failed | select count(1) as cnt" },
    @{ Name = "nongji-model-auth-config"; Logstore = "server-go"; Severity = 8; Query = "missing_key OR MODEL_BACKEND_NOT_CONFIGURED OR fusion_auth_not_configured OR sms_auth_not_configured OR sms_send_not_configured OR sms_provider_config_invalid OR sms_cache_not_configured | select count(1) as cnt" }
)

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

function Get-AnnotationValue {
    param([object]$Alert, [string]$Key)
    foreach ($annotation in @($Alert.configuration.annotations)) {
        if ($annotation.key -eq $Key) {
            return [string]$annotation.value
        }
    }
    return ""
}

function Get-FirstQuery {
    param([object]$Alert)
    $queryList = @($Alert.configuration.queryList)
    if ($queryList.Count -eq 0) {
        return $null
    }
    return $queryList[0]
}

function Get-AlertSeverity {
    param([object]$Alert)
    $configs = @($Alert.configuration.severityConfigurations)
    if ($configs.Count -eq 0) {
        return $null
    }
    return [int]$configs[0].severity
}

$response = Invoke-JsonCommand @(
    "aliyun", "sls", "list-alerts",
    "--region", $RegionId,
    "--project", $ProjectName,
    "--size", "100"
)

$alertsByName = @{}
foreach ($alert in @($response.results)) {
    $alertsByName[[string]$alert.name] = $alert
}

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$attachedActionPolicies = 0
$linkedDashboards = 0

Write-Host "== SLS alert readiness =="
Write-Host "project=$ProjectName region=$RegionId expected=$($expectedAlerts.Count)"

foreach ($expected in $expectedAlerts) {
    $name = [string]$expected.Name
    if (-not $alertsByName.ContainsKey($name)) {
        $errors.Add("missing_alert:$name")
        Write-Host "alert=$name status=missing"
        continue
    }

    $alert = $alertsByName[$name]
    $query = Get-FirstQuery -Alert $alert
    $status = [string]$alert.status
    if ([string]::IsNullOrWhiteSpace($status)) {
        $status = [string]$alert.state
    }
    $enabled = $status -in @("ENABLED", "Enabled")
    $alertHub = $false
    if ($null -ne $alert.configuration.sinkAlerthub) {
        $alertHub = [bool]$alert.configuration.sinkAlerthub.enabled
    }
    $actionPolicyId = [string]$alert.configuration.policyConfiguration.actionPolicyId
    $dashboardId = [string]$alert.configuration.dashboard
    if ([string]::IsNullOrWhiteSpace($dashboardId) -and $null -ne $query) {
        $dashboardId = [string]$query.dashboardId
    }
    $runbook = Get-AnnotationValue -Alert $alert -Key "runbook"
    $severity = Get-AlertSeverity -Alert $alert

    if (-not $enabled) {
        $errors.Add("disabled_alert:$name")
    }
    if (-not $alertHub) {
        $errors.Add("alerthub_disabled:$name")
    }
    if ($null -eq $query) {
        $errors.Add("missing_query:$name")
    } else {
        if ([string]$query.store -ne [string]$expected.Logstore) {
            $errors.Add("unexpected_logstore:${name}:$($query.store)")
        }
        if ([string]$query.query -ne [string]$expected.Query) {
            $warnings.Add("unexpected_query:${name}")
        }
    }
    if ($null -eq $severity -or $severity -ne [int]$expected.Severity) {
        $warnings.Add("unexpected_severity:${name}:$severity")
    }
    if ([string]::IsNullOrWhiteSpace($runbook)) {
        $warnings.Add("missing_runbook_annotation:$name")
    }
    if ([string]::IsNullOrWhiteSpace($actionPolicyId)) {
        $warnings.Add("external_notification_not_configured:$name")
    } else {
        $attachedActionPolicies += 1
    }
    if ([string]::IsNullOrWhiteSpace($dashboardId)) {
        $warnings.Add("dashboard_not_linked:$name")
    } else {
        $linkedDashboards += 1
    }

    Write-Host ("alert={0} enabled={1} alerthub={2} action_policy={3} dashboard={4} runbook={5}" -f `
        $name, `
        $enabled, `
        $alertHub, `
        ($(if ([string]::IsNullOrWhiteSpace($actionPolicyId)) { "none" } else { "set" })), `
        ($(if ([string]::IsNullOrWhiteSpace($dashboardId)) { "none" } else { "set" })), `
        ($(if ([string]::IsNullOrWhiteSpace($runbook)) { "none" } else { $runbook })))
}

if ($RequireExternalNotification -and $attachedActionPolicies -lt $expectedAlerts.Count) {
    $errors.Add("external_notification_required_but_missing:$attachedActionPolicies/$($expectedAlerts.Count)")
}
if ($RequireDashboard -and $linkedDashboards -lt $expectedAlerts.Count) {
    $errors.Add("dashboard_required_but_missing:$linkedDashboards/$($expectedAlerts.Count)")
}

Write-Host
Write-Host ("summary expected={0} found={1} action_policies={2}/{0} dashboards={3}/{0} warnings={4} errors={5}" -f `
    $expectedAlerts.Count, `
    $alertsByName.Count, `
    $attachedActionPolicies, `
    $linkedDashboards, `
    $warnings.Count, `
    $errors.Count)

foreach ($warning in $warnings) {
    Write-Warning $warning
}

if ($errors.Count -gt 0) {
    foreach ($errorItem in $errors) {
        Write-Host "ERROR: $errorItem"
    }
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
