param(
    [switch]$IncludeBuilds,
    [switch]$SkipAndroid,
    [switch]$SkipBackend,
    [switch]$SkipAdmin,
    [switch]$SkipCloud,
    [switch]$SkipDataBoundary,
    [switch]$SkipManualGoLiveChecklist,
    [switch]$RequireAdminSmoke,
    [switch]$ReleaseGate,
    [switch]$AppUpdateReleaseGate,
    [switch]$CheckAppUpdateReleaseMatch,
    [switch]$VerifyAppUpdateDownload,
    [int]$AppUpdatePreviousVersionCode = 0,
    [string]$AppUpdatePublicApiBaseUrl = "https://api.nongjiqiancha.cn",
    [switch]$AllowAttentionExitZero
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$results = New-Object System.Collections.Generic.List[object]

if ($ReleaseGate) {
    if ($AllowAttentionExitZero) {
        throw "-ReleaseGate cannot be combined with -AllowAttentionExitZero; a release gate must keep attention items non-zero."
    }
    if ($SkipAndroid -or $SkipBackend -or $SkipAdmin -or $SkipCloud -or $SkipDataBoundary -or $SkipManualGoLiveChecklist) {
        throw "-ReleaseGate cannot be combined with Skip* switches; use the daily gate when you need a partial report."
    }
    $IncludeBuilds = $true
    $RequireAdminSmoke = $true
}
if ($AppUpdateReleaseGate) {
    if ($AllowAttentionExitZero) {
        throw "-AppUpdateReleaseGate cannot be combined with -AllowAttentionExitZero; an APK update release gate must fail closed."
    }
    if ($SkipAndroid -or $SkipBackend -or $SkipAdmin -or $SkipCloud -or $SkipDataBoundary -or $SkipManualGoLiveChecklist) {
        throw "-AppUpdateReleaseGate cannot be combined with Skip* switches; use the daily gate when you need a partial report."
    }
    if ($AppUpdatePreviousVersionCode -le 0) {
        $envPrevious = [Environment]::GetEnvironmentVariable("NONGJI_APP_UPDATE_PREVIOUS_VERSION_CODE")
        $parsedPrevious = 0
        if (-not [string]::IsNullOrWhiteSpace($envPrevious) -and [int]::TryParse($envPrevious.Trim(), [ref]$parsedPrevious)) {
            $AppUpdatePreviousVersionCode = $parsedPrevious
        }
    }
    if ($AppUpdatePreviousVersionCode -le 0) {
        throw "-AppUpdateReleaseGate requires -AppUpdatePreviousVersionCode or NONGJI_APP_UPDATE_PREVIOUS_VERSION_CODE so the gate can prove the new APK is newer than the installed old package."
    }
    $IncludeBuilds = $true
    $RequireAdminSmoke = $true
    $CheckAppUpdateReleaseMatch = $true
    $VerifyAppUpdateDownload = $true
}

function Add-GateResult {
    param(
        [string]$Name,
        [string]$Status,
        [double]$Seconds,
        [string]$Message = ""
    )
    $results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Seconds = [math]::Round($Seconds, 1)
        Message = $Message
    }) | Out-Null
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $repoRoot
    )
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
        if ($null -ne $exitCode -and $exitCode -ne 0) {
            throw "$FilePath exited with code $exitCode"
        }
    } finally {
        Pop-Location
    }
}

function Invoke-NativeCaptured {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = $repoRoot
    )
    Push-Location $WorkingDirectory
    try {
        $output = @(& $FilePath @Arguments *>&1)
        $exitCode = $LASTEXITCODE
        $lines = @()
        foreach ($item in $output) {
            $text = $item.ToString()
            $lines += $text
            Write-Host $text
        }
        if ($null -ne $exitCode -and $exitCode -ne 0) {
            throw "$FilePath exited with code $exitCode"
        }
        return $lines
    } finally {
        Pop-Location
    }
}

function Get-CapturedValue {
    param(
        [string[]]$Lines,
        [string]$Key
    )
    $value = ""
    foreach ($line in $Lines) {
        if ($line -match "^$([regex]::Escape($Key))=(.+?)\s*$") {
            $value = $Matches[1].Trim()
        }
    }
    return $value
}

function Invoke-GateStep {
    param(
        [string]$Name,
        [scriptblock]$ScriptBlock,
        [switch]$Optional
    )

    Write-Host
    Write-Host "== $Name =="
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        & $ScriptBlock
        $timer.Stop()
        Add-GateResult -Name $Name -Status "ready" -Seconds $timer.Elapsed.TotalSeconds
        Write-Host "step_status=ready seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
    } catch {
        $timer.Stop()
        $message = $_.Exception.Message
        if ($Optional) {
            Add-GateResult -Name $Name -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
            Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
        } else {
            Add-GateResult -Name $Name -Status "failed" -Seconds $timer.Elapsed.TotalSeconds -Message $message
            Write-Host "step_status=failed seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
        }
    }
}

function Invoke-SmsUsageGateStep {
    Write-Host
    Write-Host "== sms usage =="
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $smsArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-sms-usage.ps1"
        )
        if (Test-TruthyEnv "NONGJI_SMS_BALANCE_CONFIRMED") {
            $smsArgs += "-ConfirmSmsPackage"
        }
        $lines = Invoke-NativeCaptured -FilePath "powershell.exe" -Arguments $smsArgs
        $timer.Stop()
        $smsUsageStatus = Get-CapturedValue -Lines $lines -Key "sms_usage_status"
        $smsPackageStatus = Get-CapturedValue -Lines $lines -Key "sms_package_status"
        $attentionMessages = @()
        if ($smsUsageStatus -eq "attention") {
            $attentionMessages += "sms send statistics are empty"
        }
        if ([string]::IsNullOrWhiteSpace($smsPackageStatus) -or $smsPackageStatus -ne "confirmed") {
            $packageText = if ([string]::IsNullOrWhiteSpace($smsPackageStatus)) { "not_reported" } else { $smsPackageStatus }
            $attentionMessages += "SMS package balance/expiry still needs console confirmation ($packageText)"
        }
        if ($attentionMessages.Count -gt 0) {
            $message = $attentionMessages -join "; "
            Add-GateResult -Name "sms usage and balance" -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
            Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
            return
        }
        Add-GateResult -Name "sms usage and balance" -Status "ready" -Seconds $timer.Elapsed.TotalSeconds
        Write-Host "step_status=ready seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
    } catch {
        $timer.Stop()
        $message = $_.Exception.Message
        Add-GateResult -Name "sms usage and balance" -Status "failed" -Seconds $timer.Elapsed.TotalSeconds -Message $message
        Write-Host "step_status=failed seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
    }
}

function Invoke-AliyunCostGateStep {
    Write-Host
    Write-Host "== aliyun costs =="
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $lines = Invoke-NativeCaptured -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-aliyun-costs.ps1"
        )
        $timer.Stop()
        $costStatus = Get-CapturedValue -Lines $lines -Key "status"
        if ($costStatus -eq "ready") {
            Add-GateResult -Name "aliyun costs" -Status "ready" -Seconds $timer.Elapsed.TotalSeconds
            Write-Host "step_status=ready seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
            return
        }
        $statusText = if ([string]::IsNullOrWhiteSpace($costStatus)) { "not_reported" } else { $costStatus }
        $message = "Aliyun cost guard requires review ($statusText); check SMS package balance, model plan/resource package, abnormal monthly spend, and DYPNS/fusion only for no-new-use/no-auto-renew status"
        Add-GateResult -Name "aliyun costs" -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
        Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
    } catch {
        $timer.Stop()
        $message = $_.Exception.Message
        Add-GateResult -Name "aliyun costs" -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
        Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
    }
}

function Invoke-PaymentReadinessGateStep {
    param([switch]$SkipPublicHealth)

    Write-Host
    Write-Host "== payment readiness =="
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $paymentArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-payment-readiness.ps1"
        )
        if ($SkipPublicHealth) {
            $paymentArgs += "-SkipPublicHealth"
        }
        $lines = Invoke-NativeCaptured -FilePath "powershell.exe" -Arguments $paymentArgs
        $timer.Stop()
        $paymentStatus = Get-CapturedValue -Lines $lines -Key "payment_readiness_status"
        $safeWithoutPaidIapRaw = (Get-CapturedValue -Lines $lines -Key "safe_without_paid_iap").Trim().ToLowerInvariant()
        $safeWithoutPaidIap = @("1", "true", "yes", "y", "ok") -contains $safeWithoutPaidIapRaw
        $hasSafePlaceholder = $paymentStatus -eq "attention" -and $safeWithoutPaidIap
        $message = ""
        $status = "ready"
        $name = "payment readiness"
        if ($hasSafePlaceholder) {
            $name = "payment closed guard"
            $status = "safe_placeholder"
            $message = "formal payment is not configured; safe non-payment placeholder only while Android purchase buttons and dev-order endpoints remain closed"
        } elseif ($paymentStatus -eq "attention") {
            $name = "payment readiness"
            $status = "skipped_or_attention"
            $message = "payment readiness needs review"
        }
        Add-GateResult -Name $name -Status $status -Seconds $timer.Elapsed.TotalSeconds -Message $message
        $line = "step_status=$status seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
        if (-not [string]::IsNullOrWhiteSpace($message)) {
            $line += " message=$message"
        }
        if ($status -eq "ready") {
            Write-Host $line
        } else {
            Write-Warning $line
        }
    } catch {
        $timer.Stop()
        $message = $_.Exception.Message
        Add-GateResult -Name "payment readiness" -Status "failed" -Seconds $timer.Elapsed.TotalSeconds -Message $message
        Write-Host "step_status=failed seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
    }
}

function Invoke-AdminMonitoringActionsGateStep {
    param(
        [switch]$FailOnWarning,
        [switch]$SkipIfMissingCredentials
    )

    Write-Host
    Write-Host "== admin monitoring actions =="
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $monitoringArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-admin-monitoring-actions.ps1"
        )
        if ($SkipIfMissingCredentials) {
            $monitoringArgs += "-SkipIfMissingCredentials"
        }
        if ($FailOnWarning) {
            $monitoringArgs += "-FailOnWarning"
        }
        $lines = Invoke-NativeCaptured -FilePath "powershell.exe" -Arguments $monitoringArgs
        $timer.Stop()
        $statusText = Get-CapturedValue -Lines $lines -Key "status"
        if ([string]::IsNullOrWhiteSpace($statusText)) {
            $statusText = "not_reported"
        }
        switch ($statusText) {
            "ready" {
                Add-GateResult -Name "admin monitoring actions" -Status "ready" -Seconds $timer.Elapsed.TotalSeconds
                Write-Host "step_status=ready seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1))"
            }
            "skipped" {
                $message = "admin monitoring action scan skipped ($statusText)"
                Add-GateResult -Name "admin monitoring actions" -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
                Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
            }
            default {
                $message = "admin monitoring actions reported $statusText"
                Add-GateResult -Name "admin monitoring actions" -Status "skipped_or_attention" -Seconds $timer.Elapsed.TotalSeconds -Message $message
                Write-Warning "step_status=skipped_or_attention seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
            }
        }
    } catch {
        $timer.Stop()
        $message = $_.Exception.Message
        Add-GateResult -Name "admin monitoring actions" -Status "failed" -Seconds $timer.Elapsed.TotalSeconds -Message $message
        Write-Host "step_status=failed seconds=$([math]::Round($timer.Elapsed.TotalSeconds, 1)) message=$message"
    }
}

function Test-TruthyEnv {
    param([string]$Name)
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $false
    }
    $normalized = $value.Trim().ToLowerInvariant()
    return @("1", "true", "yes", "y", "ok", "ready", "confirmed", "done") -contains $normalized
}

function Invoke-ManualGoLiveChecklist {
    $items = @(
        [pscustomobject]@{
            Key = "app_police"
            Env = "NONGJI_APP_POLICE_CONFIRMED"
            Summary = "App public-security filing has been handled with final App information"
        },
        [pscustomobject]@{
            Key = "access_keys_rotated"
            Env = "NONGJI_ACCESS_KEYS_ROTATED_CONFIRMED"
            Summary = "exposed or main-account AccessKeys have been rotated; production uses least-privilege credentials"
        },
        [pscustomobject]@{
            Key = "final_device_regression"
            Env = "NONGJI_FINAL_DEVICE_REGRESSION_CONFIRMED"
            Summary = "final APK passed real-device regression: clean install/login/chat/images/today card/settings/member/support/update"
        },
        [pscustomobject]@{
            Key = "sls_email_delivery"
            Env = "NONGJI_SLS_EMAIL_CONFIRMED"
            Summary = "first SLS alert email was confirmed by real or test trigger"
        },
        [pscustomobject]@{
            Key = "sms_balance"
            Env = "NONGJI_SMS_BALANCE_CONFIRMED"
            Summary = "ordinary SMS package balance, expiry, and billing warning are manually confirmed"
        },
        [pscustomobject]@{
            Key = "release_artifact"
            Env = "NONGJI_RELEASE_ARTIFACT_CONFIRMED"
            Summary = "final release APK/package name/signature/versionCode/store materials are aligned"
        }
    )

    $pending = @()
    foreach ($item in $items) {
        $confirmed = Test-TruthyEnv $item.Env
        $status = if ($confirmed) { "confirmed" } else { "pending" }
        Write-Host "manual_check=$($item.Key) status=$status confirm_env=$($item.Env) summary=$($item.Summary)"
        if (-not $confirmed) {
            $pending += $item.Key
        }
    }
    Write-Host "manual_note=real payment is not a launch blocker while Android purchase buttons remain disabled and server dev-order endpoints stay closed"
    if ($pending.Count -gt 0) {
        throw "manual go-live confirmations pending: $($pending -join ', ')"
    }
}

Write-Host "== launch readiness gate =="
Write-Host "repo=$repoRoot release_gate=$ReleaseGate app_update_release_gate=$AppUpdateReleaseGate include_builds=$IncludeBuilds require_admin_smoke=$RequireAdminSmoke skip_cloud=$SkipCloud check_app_update_release_match=$CheckAppUpdateReleaseMatch app_update_previous_version_code=$AppUpdatePreviousVersionCode"

Invoke-GateStep -Name "project memory guard" -ScriptBlock {
    Invoke-Native -FilePath "python" -Arguments @("scripts/check_project_memory.py")
}

if (-not $SkipAdmin) {
    Invoke-GateStep -Name "admin surface contract" -ScriptBlock {
        Invoke-Native -FilePath "node" -Arguments @("scripts/check-admin-surface.mjs")
    }
    if ($IncludeBuilds) {
        Invoke-GateStep -Name "admin production build" -ScriptBlock {
            Invoke-Native -FilePath "npm" -Arguments @("--prefix", "admin", "run", "build")
        }
        Invoke-GateStep -Name "site production build" -ScriptBlock {
            Invoke-Native -FilePath "npm" -Arguments @("--prefix", "site", "run", "build")
        }
    }
}

if (-not $SkipBackend -and $IncludeBuilds) {
    Invoke-GateStep -Name "server migration risk guard" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-server-migration-risk.ps1"
        )
    }
    Invoke-GateStep -Name "server-go tests" -ScriptBlock {
        Invoke-Native -FilePath "go" -Arguments @("test", "./...") -WorkingDirectory (Join-Path $repoRoot "server-go")
    }
    Invoke-GateStep -Name "server-go build" -ScriptBlock {
        Invoke-Native -FilePath "go" -Arguments @("build", "./...") -WorkingDirectory (Join-Path $repoRoot "server-go")
    }
}

if (-not $SkipAndroid) {
    if ($IncludeBuilds) {
        Invoke-GateStep -Name "android debug and release build" -ScriptBlock {
            Invoke-Native -FilePath (Join-Path $repoRoot "gradlew.bat") -Arguments @(":app:assembleDebug", ":app:assembleRelease")
        }
        Invoke-GateStep -Name "android release artifact" -ScriptBlock {
            Invoke-Native -FilePath "powershell.exe" -Arguments @(
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                "scripts/check-android-release-artifact.ps1"
            )
        }
    }
    Invoke-GateStep -Name "android build parity" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-android-build-parity.ps1"
        )
    }
}

if ($CheckAppUpdateReleaseMatch) {
    Invoke-GateStep -Name "app update release match" -ScriptBlock {
        $matchArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-app-update-release-match.ps1",
            "-RequireEnabled"
        )
        if ($VerifyAppUpdateDownload) {
            $matchArgs += "-VerifyDownload"
        }
        if ($AppUpdatePreviousVersionCode -gt 0) {
            $matchArgs += @("-PreviousVersionCode", "$AppUpdatePreviousVersionCode")
        }
        if (-not [string]::IsNullOrWhiteSpace($AppUpdatePublicApiBaseUrl)) {
            $matchArgs += @("-PublicApiBaseUrl", $AppUpdatePublicApiBaseUrl)
        }
        if ($AppUpdateReleaseGate) {
            $matchArgs += "-ProbePreviousVersionUpdate"
        }
        Invoke-Native -FilePath "powershell.exe" -Arguments $matchArgs
    }
}

if (-not $SkipAndroid -and -not $SkipBackend) {
    Invoke-PaymentReadinessGateStep -SkipPublicHealth:$SkipCloud
}

if (-not $SkipCloud) {
    Invoke-GateStep -Name "ecs readiness" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-ecs-readiness.ps1"
        )
    }
    Invoke-GateStep -Name "public blackbox" -ScriptBlock {
        $blackboxArgs = @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-public-blackbox.ps1"
        )
        if ($AppUpdateReleaseGate -and $AppUpdatePreviousVersionCode -gt 0) {
            $blackboxArgs += @(
                "-PreviousAndroidVersionCode",
                "$AppUpdatePreviousVersionCode"
            )
        }
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            $blackboxArgs
        )
    }
    Invoke-GateStep -Name "sls alert readiness" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-sls-alert-readiness.ps1",
            "-RequireExternalNotification",
            "-RequireDashboard",
            "-FailOnWarning"
        )
    }
    Invoke-GateStep -Name "resource capacity" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-resource-capacity.ps1",
            "-SkipAuthUsage",
            "-Strict",
            "-RequireSlsExternalNotification",
            "-RequireSlsDashboard"
        )
    }
    Invoke-AliyunCostGateStep
    Invoke-SmsUsageGateStep
    if (-not $SkipDataBoundary) {
        Invoke-GateStep -Name "backend data boundaries" -ScriptBlock {
            Invoke-Native -FilePath "powershell.exe" -Arguments @(
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                "scripts/check-backend-data-boundaries.ps1"
            )
        }
    }
}

if (-not $SkipCloud) {
    $adminSmokeArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "scripts/check-admin-authenticated-smoke.ps1"
    )
    if ($ReleaseGate -or $AppUpdateReleaseGate -or $RequireAdminSmoke) {
        $adminSmokeArgs += "-RequireOwner"
    }
    Invoke-GateStep -Name "admin authenticated smoke" -Optional:(-not $RequireAdminSmoke) -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments $adminSmokeArgs
    }
    Invoke-AdminMonitoringActionsGateStep `
        -FailOnWarning:($ReleaseGate -or $AppUpdateReleaseGate) `
        -SkipIfMissingCredentials:(-not $RequireAdminSmoke)
}

if (-not $SkipManualGoLiveChecklist) {
    Invoke-GateStep -Name "manual go-live checklist" -Optional -ScriptBlock {
        Invoke-ManualGoLiveChecklist
    }
}

Write-Host
Write-Host "== launch readiness summary =="
$failed = @($results | Where-Object { $_.Status -eq "failed" })
$attention = @($results | Where-Object { $_.Status -eq "skipped_or_attention" })
$safePlaceholders = @($results | Where-Object { $_.Status -eq "safe_placeholder" })
foreach ($result in $results) {
    $line = "step=$($result.Name) status=$($result.Status) seconds=$($result.Seconds)"
    if (-not [string]::IsNullOrWhiteSpace($result.Message)) {
        $line += " message=$($result.Message)"
    }
    Write-Host $line
}
Write-Host "failed=$($failed.Count) attention=$($attention.Count) safe_placeholder=$($safePlaceholders.Count) total=$($results.Count)"

if ($failed.Count -gt 0) {
    Write-Host "status=failed"
    exit 1
}

if ($attention.Count -gt 0) {
    Write-Host "status=attention"
    if ($AllowAttentionExitZero) {
        exit 0
    }
    exit 2
}

if ($safePlaceholders.Count -gt 0) {
    Write-Host "status=ready_without_paid_iap"
    exit 0
}

Write-Host "status=ready"
