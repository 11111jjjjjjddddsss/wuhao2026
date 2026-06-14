param(
    [switch]$IncludeBuilds,
    [switch]$SkipAndroid,
    [switch]$SkipBackend,
    [switch]$SkipAdmin,
    [switch]$SkipCloud,
    [switch]$SkipDataBoundary,
    [switch]$SkipManualGoLiveChecklist,
    [switch]$RequireAdminSmoke,
    [switch]$AllowAttentionExitZero
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$results = New-Object System.Collections.Generic.List[object]

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

function Test-AdminSmokeEnv {
    $username = if ($env:NONGJI_ADMIN_USERNAME) { $env:NONGJI_ADMIN_USERNAME } else { $env:ADMIN_SMOKE_USERNAME }
    $password = if ($env:NONGJI_ADMIN_PASSWORD) { $env:NONGJI_ADMIN_PASSWORD } else { $env:ADMIN_SMOKE_PASSWORD }
    return -not [string]::IsNullOrWhiteSpace($username) -and `
        -not [string]::IsNullOrWhiteSpace($password)
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
            Key = "app_icp"
            Env = "NONGJI_APP_ICP_CONFIRMED"
            Summary = "App ICP filing has passed and the App filing number/materials are ready"
        },
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
Write-Host "repo=$repoRoot include_builds=$IncludeBuilds skip_cloud=$SkipCloud"

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
    }
}

if (-not $SkipBackend -and $IncludeBuilds) {
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
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-public-blackbox.ps1"
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
    Invoke-GateStep -Name "sms usage" -ScriptBlock {
        Invoke-Native -FilePath "powershell.exe" -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            "scripts/check-sms-usage.ps1"
        )
    }
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
    if (Test-AdminSmokeEnv) {
        Invoke-GateStep -Name "admin authenticated smoke" -ScriptBlock {
            Invoke-Native -FilePath "powershell.exe" -Arguments @(
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                "scripts/check-admin-authenticated-smoke.ps1",
                "-RequireOwner"
            )
        }
    } else {
        $optional = -not $RequireAdminSmoke
        Invoke-GateStep -Name "admin authenticated smoke" -Optional:$optional -ScriptBlock {
            throw "admin smoke credentials are not set in the current PowerShell session; set NONGJI_ADMIN_USERNAME/NONGJI_ADMIN_PASSWORD or ADMIN_SMOKE_USERNAME/ADMIN_SMOKE_PASSWORD"
        }
    }
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
foreach ($result in $results) {
    $line = "step=$($result.Name) status=$($result.Status) seconds=$($result.Seconds)"
    if (-not [string]::IsNullOrWhiteSpace($result.Message)) {
        $line += " message=$($result.Message)"
    }
    Write-Host $line
}
Write-Host "failed=$($failed.Count) attention=$($attention.Count) total=$($results.Count)"

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

Write-Host "status=ready"
