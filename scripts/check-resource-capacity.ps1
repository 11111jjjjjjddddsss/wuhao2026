param(
    [string]$RegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$SecurityGroupId = "sg-2ze4tilwxw1h5w77lwl1",
    [string]$RdsInstanceId = "rm-2zes3vmj76p85n8g1",
    [string]$RedisInstanceId = "r-2zet46zvmoo9wu3bic",
    [string]$OssBucket = "nongjiqiancha-prod",
    [string]$DomainName = "nongjiqiancha.cn",
    [string]$SlsProjectName = "nongjiqiancha-prod-1159547719787456",
    [switch]$SkipAuthUsage,
    [switch]$RequireSlsExternalNotification,
    [switch]$RequireSlsDashboard,
    [switch]$Strict
)

$ErrorActionPreference = "Stop"

$warnings = New-Object System.Collections.Generic.List[string]
$errors = New-Object System.Collections.Generic.List[string]

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
        -replace '(?i)(SignatureNonce=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)(Content=)[^&\s]+', '${1}REDACTED' `
        -replace '(?i)((?:MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEYS?|DASHSCOPE_API_KEY_[0-9]+|DASHSCOPE_PRIMARY_API_KEYS?|DASHSCOPE_PRIMARY_API_KEY_[0-9]+|DASHSCOPE_SECONDARY_API_KEYS?|DASHSCOPE_SECONDARY_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)[=:][\s]*)[^, "&]+', '${1}REDACTED' `
        -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content|MYSQL_URL|MYSQL_DSN|REDIS_PASSWORD|DYPNS_ACCESS_KEY_ID|DYPNS_ACCESS_KEY_SECRET|ALIYUN_DYPNS_ACCESS_KEY_ID|ALIYUN_DYPNS_ACCESS_KEY_SECRET|SMS_ACCESS_KEY_ID|SMS_ACCESS_KEY_SECRET|DASHSCOPE_API_KEY|DASHSCOPE_API_KEYS|DASHSCOPE_API_KEY_[0-9]+|DASHSCOPE_PRIMARY_API_KEY|DASHSCOPE_PRIMARY_API_KEYS|DASHSCOPE_PRIMARY_API_KEY_[0-9]+|DASHSCOPE_SECONDARY_API_KEY|DASHSCOPE_SECONDARY_API_KEYS|DASHSCOPE_SECONDARY_API_KEY_[0-9]+|CHAT_PRIMARY_API_KEY|OSS_ACCESS_KEY_ID|OSS_ACCESS_KEY_SECRET|APP_SECRET|SUPPORT_ADMIN_SECRET|DAILY_AGRI_JOB_SECRET)"\s*:\s*")[^"]+', '${1}REDACTED'
}

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
        $safeOutput = Redact-SensitiveText ((($stdout | Out-String) + "`n" + $stderr))
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

function Invoke-TextCommand {
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
    try {
        $stdout = & $exe @arguments 2> $stderrPath
        $exitCode = $LASTEXITCODE
        if (Test-Path -LiteralPath $stderrPath) {
            $stderr = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
        }
    } finally {
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
    if ($exitCode -ne 0) {
        $safeOutput = Redact-SensitiveText $stderr
        $safeCommand = if ($CommandArgs.Length -ge 2) {
            "$($CommandArgs[0]) $($CommandArgs[1])"
        } else {
            $CommandArgs -join " "
        }
        throw "Command failed: $safeCommand`n$safeOutput"
    }
    return ($stdout | Out-String)
}

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId
        )
        $item = $result.Invocation.InvocationResults.InvocationResult[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($item.Output))
            return [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

function Invoke-EcsShell {
    param([string]$CommandContent)
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $RegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $EcsInstanceId,
        "--CommandContent", $CommandContent,
        "--Timeout", "120"
    )
    $final = Wait-RunCommand $run.InvokeId
    if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
        Add-WarningItem "ecs_run_command_nonzero:$($final.Status)/$($final.ExitCode)"
    }
    return $final.Output
}

function Get-DaysUntil {
    param([string]$DateText)
    if ([string]::IsNullOrWhiteSpace($DateText)) {
        return $null
    }
    try {
        $date = [DateTime]::Parse($DateText).ToUniversalTime()
        return [math]::Floor(($date - [DateTime]::UtcNow).TotalDays)
    } catch {
        return $null
    }
}

function Get-SystemdPassedAgeSeconds {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    $timerLine = ($Text -split "`n") |
        Where-Object { $_ -match 'nongji-public-blackbox\.timer\s+nongji-public-blackbox\.service' } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($timerLine) -or $timerLine -match '(?i)never') {
        return $null
    }
    if ($timerLine -match '(?i)(?:(\d+)\s+days?\s+)?(?:(\d+)h\s+)?(?:(\d+)min\s+)?(?:(\d+)s\s+)?ago') {
        $days = if ($matches[1]) { [int]$matches[1] } else { 0 }
        $hours = if ($matches[2]) { [int]$matches[2] } else { 0 }
        $minutes = if ($matches[3]) { [int]$matches[3] } else { 0 }
        $seconds = if ($matches[4]) { [int]$matches[4] } else { 0 }
        return (($days * 86400) + ($hours * 3600) + ($minutes * 60) + $seconds)
    }
    return $null
}

function Write-Expiry {
    param([string]$Name, [string]$DateText)
    $days = Get-DaysUntil $DateText
    if ($null -eq $days) {
        Write-Host "$Name expires=$DateText"
        return
    }
    Write-Host "$Name expires=$DateText days_left=$days"
    if ($days -lt 7) {
        Add-ErrorItem "$Name expires in $days days"
    } elseif ($days -lt 60) {
        Add-WarningItem "$Name expires in $days days"
    }
}

function Get-NumericSamples {
    param([object[]]$Values)
    $numbers = New-Object System.Collections.Generic.List[double]
    foreach ($value in $Values) {
        if ($null -eq $value) {
            continue
        }
        foreach ($part in ([string]$value -split "&")) {
            $parsed = 0.0
            if ([double]::TryParse($part, [ref]$parsed)) {
                $numbers.Add($parsed) | Out-Null
            }
        }
    }
    return @($numbers)
}

function ConvertFrom-OssLifecycleText {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    $start = $Text.IndexOf("<?xml", [StringComparison]::OrdinalIgnoreCase)
    if ($start -lt 0) {
        $start = $Text.IndexOf("<LifecycleConfiguration", [StringComparison]::OrdinalIgnoreCase)
    }
    $endTag = "</LifecycleConfiguration>"
    $end = $Text.IndexOf($endTag, [StringComparison]::OrdinalIgnoreCase)
    if ($start -lt 0 -or $end -lt 0) {
        return $null
    }
    $xmlText = $Text.Substring($start, $end + $endTag.Length - $start)
    try {
        return [xml]$xmlText
    } catch {
        return $null
    }
}

function Get-XmlNodeText {
    param(
        [object]$Node,
        [string]$XPath
    )
    if ($null -eq $Node) {
        return ""
    }
    $found = $Node.SelectSingleNode($XPath)
    if ($null -eq $found) {
        return ""
    }
    return ([string]$found.InnerText).Trim()
}

Write-Host "== ECS instance =="
$ecsList = Invoke-JsonCommand @("aliyun", "ecs", "DescribeInstances", "--RegionId", $RegionId, "--PageSize", "100")
$ecs = @($ecsList.Instances.Instance) | Where-Object { $_.InstanceId -eq $EcsInstanceId } | Select-Object -First 1
if ($null -eq $ecs) {
    Add-ErrorItem "ecs_instance_missing:$EcsInstanceId"
} else {
    $ecsDeletionProtectionApplicable = [string]$ecs.InstanceChargeType -eq "PostPaid"
    $ecsDeletionProtection = if ($ecsDeletionProtectionApplicable) { [string]$ecs.DeletionProtection } else { "not_applicable_prepaid" }
    Write-Host "instance=$($ecs.InstanceId) status=$($ecs.Status) charge_type=$($ecs.InstanceChargeType) type=$($ecs.InstanceType) cpu=$($ecs.Cpu) memory_mib=$($ecs.Memory) public_ip=$(@($ecs.PublicIpAddress.IpAddress) -join ',') bandwidth_out_mbps=$($ecs.InternetMaxBandwidthOut) deletion_protection=$ecsDeletionProtection deletion_protection_applicable=$ecsDeletionProtectionApplicable"
    Write-Expiry "ecs" $ecs.ExpiredTime
    if ($ecs.Status -ne "Running") {
        Add-ErrorItem "ecs_not_running:$($ecs.Status)"
    }
    if ($ecsDeletionProtectionApplicable -and -not [bool]$ecs.DeletionProtection) {
        Add-WarningItem "ecs_deletion_protection_disabled:$($ecs.InstanceId)"
    }
    if ([int]$ecs.InternetMaxBandwidthOut -lt 5) {
        Add-WarningItem "ecs_bandwidth_out_low:$($ecs.InternetMaxBandwidthOut)Mbps"
    }
}

Write-Host
Write-Host "== ECS runtime =="
$runtime = Invoke-EcsShell "set -u; uptime; free -m; df -h /; journalctl -k --since '-7 days' 2>/dev/null | grep -Ei 'out of memory|oom-kill|killed process' | tail -20 || true"
Write-Host $runtime
if ($runtime -match '(?im)^\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+(\d+)%\s+/$') {
    $diskPct = [int]$matches[1]
    if ($diskPct -ge 80) {
        Add-ErrorItem "ecs_root_disk_usage_high:${diskPct}%"
    } elseif ($diskPct -ge 70) {
        Add-WarningItem "ecs_root_disk_usage_attention:${diskPct}%"
    }
}
if ($runtime -match '(?i)out of memory|oom-kill|killed process') {
    Add-WarningItem "ecs_oom_seen_in_last_7_days"
}

Write-Host
Write-Host "== ECS public blackbox timer =="
$blackboxTimer = Invoke-EcsShell @'
set -u
timer='nongji-public-blackbox.timer'
service='nongji-public-blackbox.service'
enabled=$(systemctl is-enabled "$timer" 2>/dev/null || true)
active=$(systemctl is-active "$timer" 2>/dev/null || true)
result=$(systemctl show -p Result --value "$service" 2>/dev/null || true)
exec_status=$(systemctl show -p ExecMainStatus --value "$service" 2>/dev/null || true)
exit_ts=$(systemctl show -p ExecMainExitTimestamp --value "$service" 2>/dev/null || true)
inactive_enter_ts=$(systemctl show -p InactiveEnterTimestamp --value "$service" 2>/dev/null || true)
last_trigger_ts=$(systemctl show -p LastTriggerUSec --value "$timer" 2>/dev/null || true)
exit_mono=$(systemctl show -p ExecMainExitTimestampMonotonic --value "$service" 2>/dev/null || true)
inactive_enter_mono=$(systemctl show -p InactiveEnterTimestampMonotonic --value "$service" 2>/dev/null || true)
last_trigger_mono=$(systemctl show -p LastTriggerUSecMonotonic --value "$timer" 2>/dev/null || true)
age_seconds='unknown'
age_source='unknown'
now_mono_usec=$(awk '{printf "%.0f", $1 * 1000000}' /proc/uptime 2>/dev/null || true)
now_epoch=$(date '+%s' 2>/dev/null || true)
for candidate in "ExecMainExitTimestampMonotonic:$exit_mono" "InactiveEnterTimestampMonotonic:$inactive_enter_mono" "LastTriggerUSecMonotonic:$last_trigger_mono"; do
  if [ "$age_seconds" != "unknown" ]; then
    break
  fi
  candidate_name=${candidate%%:*}
  candidate_usec=${candidate#*:}
  if printf '%s' "$candidate_usec" | grep -Eq '^[0-9]+$' && printf '%s' "$now_mono_usec" | grep -Eq '^[0-9]+$' && [ "$candidate_usec" -gt 0 ] && [ "$now_mono_usec" -ge "$candidate_usec" ]; then
    age_seconds=$(( (now_mono_usec - candidate_usec) / 1000000 ))
    age_source=$candidate_name
  fi
done
for candidate in "ExecMainExitTimestamp:$exit_ts" "InactiveEnterTimestamp:$inactive_enter_ts" "LastTriggerUSec:$last_trigger_ts"; do
  if [ "$age_seconds" != "unknown" ]; then
    break
  fi
  candidate_name=${candidate%%:*}
  candidate_ts=${candidate#*:}
  if [ -n "$candidate_ts" ] && [ "$candidate_ts" != "n/a" ]; then
    candidate_parse_ts=$(printf '%s' "$candidate_ts" | tr -d '\r')
    if printf '%s' "$candidate_parse_ts" | grep -Eq ' CST$'; then
      candidate_parse_ts=${candidate_parse_ts% CST}
      candidate_epoch=$(TZ=Asia/Shanghai date -d "$candidate_parse_ts" '+%s' 2>/dev/null || true)
    else
      candidate_epoch=$(date -d "$candidate_parse_ts" '+%s' 2>/dev/null || true)
    fi
    if printf '%s' "$candidate_epoch" | grep -Eq '^[0-9]+$' && printf '%s' "$now_epoch" | grep -Eq '^[0-9]+$'; then
      age_seconds=$(( now_epoch - candidate_epoch ))
      age_source=$candidate_name
    fi
  fi
done
echo "timer_enabled=$enabled"
echo "timer_active=$active"
echo "service_result=$result"
echo "service_exec_status=$exec_status"
echo "service_last_exit_timestamp=$exit_ts"
echo "service_last_age_seconds=$age_seconds"
echo "service_last_age_source=$age_source"
systemctl list-timers "$timer" --all --no-pager 2>/dev/null || true
'@
Write-Host $blackboxTimer
$blackboxEnabled = ([regex]::Match($blackboxTimer, "(?m)^timer_enabled=(.+)$").Groups[1].Value).Trim()
$blackboxActive = ([regex]::Match($blackboxTimer, "(?m)^timer_active=(.+)$").Groups[1].Value).Trim()
$blackboxResult = ([regex]::Match($blackboxTimer, "(?m)^service_result=(.+)$").Groups[1].Value).Trim()
$blackboxExecStatus = ([regex]::Match($blackboxTimer, "(?m)^service_exec_status=(.+)$").Groups[1].Value).Trim()
$blackboxAgeSecondsText = ([regex]::Match($blackboxTimer, "(?m)^service_last_age_seconds=(.+)$").Groups[1].Value).Trim()
if ($blackboxEnabled -ne "enabled") {
    Add-ErrorItem "public_blackbox_timer_not_enabled:$blackboxEnabled"
}
if ($blackboxActive -ne "active") {
    Add-ErrorItem "public_blackbox_timer_not_active:$blackboxActive"
}
if (-not [string]::IsNullOrWhiteSpace($blackboxResult) -and $blackboxResult -ne "success") {
    Add-ErrorItem "public_blackbox_last_result_not_success:$blackboxResult"
}
if (-not [string]::IsNullOrWhiteSpace($blackboxExecStatus) -and $blackboxExecStatus -ne "0") {
    Add-ErrorItem "public_blackbox_last_exec_status_not_zero:$blackboxExecStatus"
}
$blackboxTimerPassedAgeSeconds = Get-SystemdPassedAgeSeconds $blackboxTimer
if ($null -ne $blackboxTimerPassedAgeSeconds) {
    Write-Host "service_last_timer_passed_seconds=$blackboxTimerPassedAgeSeconds"
}
$blackboxEffectiveAgeSeconds = $null
if ([string]::IsNullOrWhiteSpace($blackboxAgeSecondsText) -or $blackboxAgeSecondsText -eq "unknown") {
    $blackboxEffectiveAgeSeconds = $blackboxTimerPassedAgeSeconds
}
else {
    $blackboxAgeSeconds = 0
    if ([int]::TryParse($blackboxAgeSecondsText, [ref]$blackboxAgeSeconds)) {
        if ($blackboxAgeSeconds -ge 0 -and $blackboxAgeSeconds -le 1800) {
            $blackboxEffectiveAgeSeconds = $blackboxAgeSeconds
        } elseif ($null -ne $blackboxTimerPassedAgeSeconds) {
            $blackboxEffectiveAgeSeconds = $blackboxTimerPassedAgeSeconds
        } else {
            $blackboxEffectiveAgeSeconds = $blackboxAgeSeconds
        }
    } else {
        if ($null -ne $blackboxTimerPassedAgeSeconds) {
            $blackboxEffectiveAgeSeconds = $blackboxTimerPassedAgeSeconds
        } else {
            Add-ErrorItem "public_blackbox_last_age_unparseable:$blackboxAgeSecondsText"
        }
    }
}
if ($null -eq $blackboxEffectiveAgeSeconds) {
    Add-ErrorItem "public_blackbox_last_age_unknown"
} else {
    if ($blackboxEffectiveAgeSeconds -lt 0 -or $blackboxEffectiveAgeSeconds -gt 1800) {
        Add-ErrorItem "public_blackbox_last_age_stale:${blackboxEffectiveAgeSeconds}s"
    }
}

Write-Host
Write-Host "== ECS disks =="
$disks = Invoke-JsonCommand @("aliyun", "ecs", "DescribeDisks", "--RegionId", $RegionId, "--InstanceId", $EcsInstanceId)
foreach ($disk in @($disks.Disks.Disk)) {
    Write-Host "disk=$($disk.DiskId) type=$($disk.Type) size_gb=$($disk.Size) status=$($disk.Status) encrypted=$($disk.Encrypted) auto_snapshot_policy=$($disk.AutoSnapshotPolicyId) automated_snapshot=$($disk.EnableAutomatedSnapshotPolicy)"
    Write-Expiry "disk:$($disk.DiskId)" $disk.ExpiredTime
    if ([string]::IsNullOrWhiteSpace([string]$disk.AutoSnapshotPolicyId) -or -not [bool]$disk.EnableAutomatedSnapshotPolicy) {
        Add-WarningItem "ecs_disk_no_automatic_snapshot_policy:$($disk.DiskId)"
    }
}

Write-Host
Write-Host "== security group =="
$sg = Invoke-JsonCommand @("aliyun", "ecs", "DescribeSecurityGroupAttribute", "--RegionId", $RegionId, "--SecurityGroupId", $SecurityGroupId)
foreach ($permission in @($sg.Permissions.Permission)) {
    if ($permission.Direction -ne "ingress") {
        continue
    }
    Write-Host "ingress protocol=$($permission.IpProtocol) ports=$($permission.PortRange) source=$($permission.SourceCidrIp) policy=$($permission.Policy)"
    if ($permission.Policy -eq "Accept" -and $permission.IpProtocol -eq "TCP" -and $permission.SourceCidrIp -eq "0.0.0.0/0") {
        if ($permission.PortRange -notin @("80/80", "443/443")) {
            Add-ErrorItem "unexpected_public_tcp_ingress:$($permission.PortRange)"
        }
    }
}

Write-Host
Write-Host "== RDS =="
$rds = Invoke-JsonCommand @("aliyun", "rds", "DescribeDBInstanceAttribute", "--RegionId", $RegionId, "--DBInstanceId", $RdsInstanceId)
$rdsAttr = @($rds.Items.DBInstanceAttribute)[0]
if ($null -eq $rdsAttr) {
    Add-ErrorItem "rds_instance_missing:$RdsInstanceId"
} else {
    $diskUsedGb = [math]::Round(([double]$rdsAttr.DBInstanceDiskUsed / 1GB), 2)
    $diskPct = [math]::Round(([double]$rdsAttr.DBInstanceDiskUsed / ([double]$rdsAttr.DBInstanceStorage * 1GB) * 100), 2)
    $rdsDeletionProtectionApplicable = [string]$rdsAttr.PayType -match '^(Postpaid|PostPaid|Serverless)$'
    $rdsDeletionProtection = if ($rdsDeletionProtectionApplicable) { [string]$rdsAttr.DeletionProtection } else { "not_applicable_prepaid" }
    Write-Host "instance=$($rdsAttr.DBInstanceId) status=$($rdsAttr.DBInstanceStatus) pay_type=$($rdsAttr.PayType) class=$($rdsAttr.DBInstanceClass) cpu=$($rdsAttr.DBInstanceCPU) memory_mib=$($rdsAttr.DBInstanceMemory) storage_gb=$($rdsAttr.DBInstanceStorage) disk_used_gb=$diskUsedGb disk_used_pct=$diskPct max_connections=$($rdsAttr.MaxConnections) deletion_protection=$rdsDeletionProtection deletion_protection_applicable=$rdsDeletionProtectionApplicable security_ips=$($rdsAttr.SecurityIPList)"
    Write-Expiry "rds" $rdsAttr.ExpireTime
    if ($rdsAttr.DBInstanceStatus -ne "Running") {
        Add-ErrorItem "rds_not_running:$($rdsAttr.DBInstanceStatus)"
    }
    if ($diskPct -ge 80) {
        Add-ErrorItem "rds_disk_usage_high:${diskPct}%"
    } elseif ($diskPct -ge 70) {
        Add-WarningItem "rds_disk_usage_attention:${diskPct}%"
    }
    if ([string]$rdsAttr.SecurityIPList -notmatch '192\.168\.1\.237') {
        Add-WarningItem "rds_security_ip_missing_ecs_private_ip"
    }
    if ($rdsDeletionProtectionApplicable -and -not [bool]$rdsAttr.DeletionProtection) {
        Add-WarningItem "rds_deletion_protection_disabled:$RdsInstanceId"
    }
}
$rdsBackup = Invoke-JsonCommand @("aliyun", "rds", "DescribeBackupPolicy", "--RegionId", $RegionId, "--DBInstanceId", $RdsInstanceId)
Write-Host "backup_retention_days=$($rdsBackup.BackupRetentionPeriod) backup_log=$($rdsBackup.BackupLog) log_backup_retention_days=$($rdsBackup.LogBackupRetentionPeriod) preferred=$($rdsBackup.PreferredBackupPeriod) $($rdsBackup.PreferredBackupTime)"
if ([int]$rdsBackup.BackupRetentionPeriod -lt 7) {
    Add-WarningItem "rds_backup_retention_less_than_7_days"
}
if ($rdsBackup.BackupLog -ne "Enable") {
    Add-WarningItem "rds_log_backup_not_enabled"
}

Write-Host
Write-Host "== RDS 30m performance =="
$startRds = (Get-Date).ToUniversalTime().AddMinutes(-30).ToString("yyyy-MM-ddTHH:mmZ")
$endRds = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mmZ")
$rdsPerf = Invoke-JsonCommand @(
    "aliyun", "rds", "DescribeDBInstancePerformance",
    "--RegionId", $RegionId,
    "--DBInstanceId", $RdsInstanceId,
    "--StartTime", $startRds,
    "--EndTime", $endRds,
    "--Key", "MySQL_MemCpuUsage,MySQL_Sessions,MySQL_IOPS,MySQL_QPSTPS,MySQL_SpaceUsage"
)
foreach ($perfKey in @($rdsPerf.PerformanceKeys.PerformanceKey)) {
    $samples = Get-NumericSamples @(@($perfKey.Values.PerformanceValue) | ForEach-Object { $_.Value })
    if ($samples.Count -eq 0) {
        Write-Host "$($perfKey.Key)=no_data"
        continue
    }
    $max = ($samples | Measure-Object -Maximum).Maximum
    $last = $samples[-1]
    Write-Host ("{0} last={1:N2} max={2:N2}" -f $perfKey.Key, $last, $max)
}

Write-Host
Write-Host "== Redis =="
$redis = Invoke-JsonCommand @("aliyun", "r-kvstore", "DescribeInstanceAttribute", "--RegionId", $RegionId, "--InstanceId", $RedisInstanceId)
$redisAttr = @($redis.Instances.DBInstanceAttribute)[0]
if ($null -eq $redisAttr) {
    Add-ErrorItem "redis_instance_missing:$RedisInstanceId"
} else {
    Write-Host "instance=$($redisAttr.InstanceId) status=$($redisAttr.InstanceStatus) charge_type=$($redisAttr.ChargeType) class=$($redisAttr.InstanceClass) capacity_mb=$($redisAttr.Capacity) qps=$($redisAttr.QPS) connections=$($redisAttr.Connections) release_protection=$($redisAttr.InstanceReleaseProtection) security_ips=$($redisAttr.SecurityIPList)"
    Write-Expiry "redis" $redisAttr.EndTime
    if ($redisAttr.InstanceStatus -ne "Normal") {
        Add-ErrorItem "redis_not_normal:$($redisAttr.InstanceStatus)"
    }
    if ([string]$redisAttr.SecurityIPList -notmatch '192\.168\.1\.237') {
        Add-WarningItem "redis_security_ip_missing_ecs_private_ip"
    }
    if (-not [bool]$redisAttr.InstanceReleaseProtection) {
        Add-WarningItem "redis_release_protection_disabled:$RedisInstanceId"
    }
}
$startRedis = (Get-Date).ToUniversalTime().AddMinutes(-30).ToString("yyyy-MM-ddTHH:mm:ssZ")
$endRedis = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$redisPerf = Invoke-JsonCommand @(
    "aliyun", "r-kvstore", "DescribeHistoryMonitorValues",
    "--RegionId", $RegionId,
    "--InstanceId", $RedisInstanceId,
    "--StartTime", $startRedis,
    "--EndTime", $endRedis,
    "--IntervalForHistory", "01m",
    "--MonitorKeys", "UsedMemory,quotaMemory,CpuUsage,ConnectionUsage"
)
$redisSamples = @()
if (-not [string]::IsNullOrWhiteSpace([string]$redisPerf.MonitorHistory)) {
    $history = ([string]$redisPerf.MonitorHistory) | ConvertFrom-Json
    $redisSamples = @($history.PSObject.Properties | ForEach-Object { $_.Value })
}
if ($redisSamples.Count -gt 0) {
    $usedMemoryMax = (@($redisSamples | ForEach-Object { [double]$_.UsedMemory }) | Measure-Object -Maximum).Maximum
    $quotaMemoryMax = (@($redisSamples | ForEach-Object { [double]$_.quotaMemory }) | Measure-Object -Maximum).Maximum
    $cpuMax = (@($redisSamples | ForEach-Object { [double]$_.CpuUsage }) | Measure-Object -Maximum).Maximum
    $connMax = (@($redisSamples | ForEach-Object { [double]$_.connectionUsage }) | Measure-Object -Maximum).Maximum
    $memoryPct = if ($quotaMemoryMax -gt 0) { [math]::Round($usedMemoryMax / $quotaMemoryMax * 100, 2) } else { 0 }
    Write-Host ("redis_30m used_memory_mb={0:N2} quota_mb={1:N2} memory_pct={2:N2} cpu_max={3:N2} connection_usage_max={4:N2}" -f ($usedMemoryMax / 1MB), ($quotaMemoryMax / 1MB), $memoryPct, $cpuMax, $connMax)
    if ($memoryPct -ge 70) {
        Add-WarningItem "redis_memory_usage_attention:${memoryPct}%"
    }
    if ($connMax -ge 60) {
        Add-WarningItem "redis_connection_usage_attention:${connMax}%"
    }
} else {
    Write-Host "redis_30m=no_data"
}

Write-Host
Write-Host "== OSS =="
$ossStat = Invoke-TextCommand @("aliyun", "oss", "stat", "oss://$OssBucket")
Write-Host $ossStat.Trim()
if ($ossStat -notmatch '(?m)^ACL\s+:\s+private\s*$') {
    Add-ErrorItem "oss_bucket_acl_not_private:$OssBucket"
}
if ($ossStat -notmatch '(?m)^StorageClass\s+:\s+Standard\s*$') {
    Add-WarningItem "oss_bucket_storage_class_not_standard:$OssBucket"
}
$ossDu = Invoke-TextCommand @("aliyun", "oss", "du", "oss://$OssBucket", "--block-size", "MB")
Write-Host $ossDu.Trim()
if ($ossDu -match 'total du size\(MB\):([0-9.]+)') {
    $ossMb = [double]$matches[1]
    if ($ossMb -ge (100 * 1024 * 0.85)) {
        Add-WarningItem "oss_usage_over_85_percent_of_100g_resource_package"
    } elseif ($ossMb -ge (100 * 1024 * 0.70)) {
        Add-WarningItem "oss_usage_over_70_percent_of_100g_resource_package"
    }
}
$ossLifecycle = Invoke-TextCommand @("aliyun", "oss", "lifecycle", "--method", "get", "oss://$OssBucket")
Write-Host $ossLifecycle.Trim()
$ossLifecycleDoc = ConvertFrom-OssLifecycleText $ossLifecycle
if ($null -eq $ossLifecycleDoc) {
    Add-WarningItem "oss_lifecycle_parse_failed:$OssBucket"
} else {
    $rules = @($ossLifecycleDoc.LifecycleConfiguration.Rule)
    $expectedLifecycleRules = @(
        [pscustomobject]@{ Prefix = "uploads/"; ExpirationDays = 3; ExpirationWarning = "oss_uploads_lifecycle_not_3_days"; AbortWarning = "oss_uploads_abort_multipart_not_1_day" },
        [pscustomobject]@{ Prefix = "support/"; ExpirationDays = 30; ExpirationWarning = "oss_support_lifecycle_not_30_days"; AbortWarning = "oss_support_abort_multipart_not_1_day" },
        [pscustomobject]@{ Prefix = "test-apks/"; ExpirationDays = 3; ExpirationWarning = "oss_test_apks_lifecycle_not_3_days"; AbortWarning = "oss_test_apks_abort_multipart_not_1_day" }
    )
    foreach ($expectedRule in $expectedLifecycleRules) {
        $rule = @($rules | Where-Object {
            (Get-XmlNodeText $_ "Prefix") -eq $expectedRule.Prefix
        } | Select-Object -First 1)
        if ($rule.Count -eq 0) {
            Add-WarningItem "oss_lifecycle_rule_missing:$($expectedRule.Prefix)"
            continue
        }
        $ruleNode = $rule[0]
        $status = Get-XmlNodeText $ruleNode "Status"
        $expirationDaysText = Get-XmlNodeText $ruleNode "Expiration/Days"
        $abortDaysText = Get-XmlNodeText $ruleNode "AbortMultipartUpload/Days"
        Write-Host "lifecycle prefix=$($expectedRule.Prefix) status=$status expiration_days=$expirationDaysText abort_multipart_days=$abortDaysText"
        if ($status -ne "Enabled") {
            Add-WarningItem "oss_lifecycle_rule_not_enabled:$($expectedRule.Prefix)"
        }
        $expirationDays = 0
        if (-not [int]::TryParse($expirationDaysText, [ref]$expirationDays) -or $expirationDays -ne [int]$expectedRule.ExpirationDays) {
            Add-WarningItem $expectedRule.ExpirationWarning
        }
        $abortDays = 0
        if (-not [int]::TryParse($abortDaysText, [ref]$abortDays) -or $abortDays -ne 1) {
            Add-WarningItem $expectedRule.AbortWarning
        }
    }
}
try {
    $ossEncryption = Invoke-TextCommand @("aliyun", "oss", "bucket-encryption", "--method", "get", "oss://$OssBucket")
    Write-Host $ossEncryption.Trim()
} catch {
    Write-Host "server_side_encryption=not_configured"
    Add-WarningItem "oss_server_side_encryption_not_configured:$OssBucket"
}

Write-Host
Write-Host "== DNS and domain =="
$domainList = Invoke-JsonCommand @("aliyun", "domain", "QueryDomainList", "--PageNum", "1", "--PageSize", "100")
$domain = @($domainList.Data.Domain) | Where-Object { $_.DomainName -eq $DomainName } | Select-Object -First 1
if ($null -eq $domain) {
    Add-WarningItem "domain_not_found_in_query_domain_list:$DomainName"
} else {
    Write-Host "domain=$($domain.DomainName) audit=$($domain.DomainAuditStatus) dead_date=$($domain.DeadDate)"
    Write-Expiry "domain:$DomainName" $domain.DeadDate
}
$records = Invoke-JsonCommand @("aliyun", "alidns", "DescribeDomainRecords", "--DomainName", $DomainName, "--PageSize", "100")
$expectedRecords = @("@", "www", "api", "admin")
foreach ($rr in $expectedRecords) {
    $record = @($records.DomainRecords.Record) | Where-Object { $_.RR -eq $rr -and $_.Type -eq "A" -and $_.Status -eq "ENABLE" } | Select-Object -First 1
    if ($null -eq $record) {
        Add-WarningItem "dns_record_missing:$rr"
    } else {
        Write-Host "dns rr=$rr type=A value=$($record.Value) ttl=$($record.TTL) status=$($record.Status)"
        if ($record.Value -ne "39.106.1.151") {
            Add-WarningItem "dns_record_unexpected_value:${rr}:$($record.Value)"
        }
    }
}

Write-Host
Write-Host "== HTTPS certificates =="
$certs = Invoke-EcsShell "certbot certificates 2>/dev/null | sed -n '/Certificate Name:/,/Certificate Path:/p'"
Write-Host $certs
foreach ($line in ($certs -split "`n")) {
    if ($line -match 'Expiry Date:\s+([0-9-]+\s+[0-9:]+\+00:00).*\(VALID:\s+([0-9]+)\s+days\)') {
        $days = [int]$matches[2]
        if ($days -lt 30) {
            Add-ErrorItem "https_certificate_expires_soon:${days}d"
        } elseif ($days -lt 60) {
            Add-WarningItem "https_certificate_expires_in_${days}d"
        }
    }
}
$timer = Invoke-EcsShell "systemctl is-enabled certbot.timer 2>/dev/null || true; systemctl is-active certbot.timer 2>/dev/null || true"
$timerInline = $timer.Trim() -replace "`n", ","
Write-Host "certbot_timer=$timerInline"
if ($timer -notmatch 'enabled' -or $timer -notmatch 'active') {
    Add-WarningItem "certbot_timer_not_enabled_or_active"
}

Write-Host
Write-Host "== Android download domain =="
$downloadDomainScript = Join-Path $PSScriptRoot "check-android-download-domain.ps1"
if (Test-Path -LiteralPath $downloadDomainScript) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $downloadDomainScript
    if ($LASTEXITCODE -ne 0) {
        if ($Strict) {
            Add-ErrorItem "android_download_domain_check_failed"
        } else {
            Add-WarningItem "android_download_domain_check_attention"
        }
    }
} else {
    Add-WarningItem "android_download_domain_script_missing"
}

Write-Host
Write-Host "== CloudMonitor resource alerts =="
$contactGroupName = "NongjiQianchaOps"
try {
    $contactGroups = Invoke-JsonCommand @("aliyun", "cms", "DescribeContactGroupList", "--PageSize", "100")
    $contactGroup = @($contactGroups.ContactGroupList.ContactGroup) | Where-Object { $_.Name -eq $contactGroupName } | Select-Object -First 1
    if ($null -eq $contactGroup) {
        Add-WarningItem "cloudmonitor_contact_group_missing:$contactGroupName"
    } else {
        Write-Host "contact_group=$($contactGroup.Name) contacts=$(@($contactGroup.Contacts.Contact) -join ',')"
    }
} catch {
    Write-Host "contact_group_check_failed=$($_.Exception.Message)"
    Add-WarningItem "cloudmonitor_contact_group_check_failed"
}

$expectedMetricRules = @(
    [pscustomobject]@{ RuleId = "nq-ecs-cpu-high"; Namespace = "acs_ecs_dashboard"; MetricName = "CPUUtilization"; ResourceInstanceId = $EcsInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-ecs-memory-high"; Namespace = "acs_ecs_dashboard"; MetricName = "memory_usedutilization"; ResourceInstanceId = $EcsInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-rds-cpu-high"; Namespace = "acs_rds_dashboard"; MetricName = "CpuUsage"; ResourceInstanceId = $RdsInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-rds-memory-high"; Namespace = "acs_rds_dashboard"; MetricName = "MemoryUsage"; ResourceInstanceId = $RdsInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-rds-disk-high"; Namespace = "acs_rds_dashboard"; MetricName = "DiskUsage"; ResourceInstanceId = $RdsInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-rds-connection-high"; Namespace = "acs_rds_dashboard"; MetricName = "ConnectionUsage"; ResourceInstanceId = $RdsInstanceId; WarnThreshold = "60"; CriticalThreshold = "80"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-redis-cpu-high"; Namespace = "acs_kvstore"; MetricName = "StandardCpuUsage"; ResourceInstanceId = $RedisInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-redis-memory-high"; Namespace = "acs_kvstore"; MetricName = "StandardMemoryUsage"; ResourceInstanceId = $RedisInstanceId; WarnThreshold = "70"; CriticalThreshold = "85"; Times = 3; Period = 300 },
    [pscustomobject]@{ RuleId = "nq-redis-connection-high"; Namespace = "acs_kvstore"; MetricName = "StandardConnectionUsage"; ResourceInstanceId = $RedisInstanceId; WarnThreshold = "60"; CriticalThreshold = "80"; Times = 3; Period = 300 }
)
$metricRuleList = $null
try {
    $metricRuleList = Invoke-JsonCommand @("aliyun", "cms", "DescribeMetricRuleList", "--PageSize", "100")
} catch {
    Write-Host "metric_rule_list_check_failed=$($_.Exception.Message)"
    Add-WarningItem "cloudmonitor_metric_rule_list_check_failed"
}
function Get-MetricRuleResourceIds {
    param([object]$Rule)
    $raw = [string]$Rule.Resources
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    try {
        $resources = $raw | ConvertFrom-Json
        return @($resources) | ForEach-Object { [string]$_.instanceId } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    } catch {
        return @()
    }
}

foreach ($rule in $expectedMetricRules) {
    try {
        $foundRule = @($metricRuleList.Alarms.Alarm) | Where-Object { $_.RuleId -eq $rule.RuleId } | Select-Object -First 1
        if ($null -eq $foundRule) {
            Add-WarningItem "cloudmonitor_metric_rule_missing:$($rule.RuleId)"
            continue
        }
        $resourceIds = @(Get-MetricRuleResourceIds -Rule $foundRule)
        $resourceText = if ($resourceIds.Count -gt 0) { $resourceIds -join "," } else { "none" }
        Write-Host "rule=$($foundRule.RuleId) namespace=$($foundRule.Namespace) metric=$($foundRule.MetricName) resource=$resourceText warn=$($foundRule.Escalations.Warn.Threshold)/$($foundRule.Escalations.Warn.Times) critical=$($foundRule.Escalations.Critical.Threshold)/$($foundRule.Escalations.Critical.Times) period=$($foundRule.Period) alert_state=$($foundRule.AlertState) enabled=$($foundRule.EnableState) contacts=$($foundRule.ContactGroups)"
        if ([string]$foundRule.AlertState -eq "INSUFFICIENT_DATA") {
            Add-WarningItem "cloudmonitor_metric_rule_insufficient_data:$($rule.RuleId)"
        }
        if ([string]$foundRule.AlertState -eq "ALARM") {
            if ($Strict) {
                Add-ErrorItem "cloudmonitor_metric_rule_alarm:$($rule.RuleId)"
            } else {
                Add-WarningItem "cloudmonitor_metric_rule_alarm:$($rule.RuleId)"
            }
        }
        if ($foundRule.Namespace -ne $rule.Namespace -or $foundRule.MetricName -ne $rule.MetricName) {
            Add-WarningItem "cloudmonitor_metric_rule_unexpected_target:$($rule.RuleId)"
        }
        if ($resourceIds -notcontains [string]$rule.ResourceInstanceId) {
            Add-WarningItem "cloudmonitor_metric_rule_resource_mismatch:$($rule.RuleId)"
        }
        if ([string]$foundRule.Escalations.Warn.Threshold -ne [string]$rule.WarnThreshold -or [int]$foundRule.Escalations.Warn.Times -ne [int]$rule.Times) {
            Add-WarningItem "cloudmonitor_metric_rule_warn_threshold_mismatch:$($rule.RuleId)"
        }
        if ([string]$foundRule.Escalations.Critical.Threshold -ne [string]$rule.CriticalThreshold -or [int]$foundRule.Escalations.Critical.Times -ne [int]$rule.Times) {
            Add-WarningItem "cloudmonitor_metric_rule_critical_threshold_mismatch:$($rule.RuleId)"
        }
        if ([int]$foundRule.Period -ne [int]$rule.Period) {
            Add-WarningItem "cloudmonitor_metric_rule_period_mismatch:$($rule.RuleId)"
        }
        if (-not [bool]$foundRule.EnableState) {
            Add-WarningItem "cloudmonitor_metric_rule_not_enabled:$($rule.RuleId)"
        }
        if ([string]$foundRule.ContactGroups -notmatch [regex]::Escape($contactGroupName)) {
            Add-WarningItem "cloudmonitor_metric_rule_contact_group_missing:$($rule.RuleId)"
        }
    } catch {
        Write-Host "rule_check_failed=$($rule.RuleId):$($_.Exception.Message)"
        Add-WarningItem "cloudmonitor_metric_rule_check_failed:$($rule.RuleId)"
    }
}

Write-Host
Write-Host "== SLS alert readiness =="
$slsScript = Join-Path $PSScriptRoot "check-sls-alert-readiness.ps1"
if (Test-Path -LiteralPath $slsScript) {
    $slsArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $slsScript,
        "-RegionId",
        $RegionId,
        "-ProjectName",
        $SlsProjectName
    )
    $defaultSlsAttentionGate = -not $RequireSlsExternalNotification -and -not $RequireSlsDashboard -and -not $Strict
    if ($RequireSlsExternalNotification -or $Strict -or $defaultSlsAttentionGate) {
        $slsArgs += "-RequireExternalNotification"
    }
    if ($RequireSlsDashboard -or $Strict -or $defaultSlsAttentionGate) {
        $slsArgs += "-RequireDashboard"
    }
    if ($Strict -or $defaultSlsAttentionGate) {
        $slsArgs += "-FailOnWarning"
    }
    & powershell.exe @slsArgs
    if ($LASTEXITCODE -ne 0) {
        if ($RequireSlsExternalNotification -or $RequireSlsDashboard -or $Strict) {
            Add-ErrorItem "sls_alert_readiness_failed"
        } else {
            Add-WarningItem "sls_alert_external_notification_or_dashboard_may_need_attention"
        }
    }
} else {
    Add-WarningItem "sls_alert_readiness_script_missing"
}

Write-Host
Write-Host "== SLS cost guard =="
$slsCostScript = Join-Path $PSScriptRoot "check-sls-cost-guard.ps1"
if (Test-Path -LiteralPath $slsCostScript) {
    $slsCostArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $slsCostScript,
        "-RegionId",
        $RegionId,
        "-ProjectName",
        $SlsProjectName
    )
    if ($Strict) {
        $slsCostArgs += "-FailOnWarning"
    }
    & powershell.exe @slsCostArgs
    if ($LASTEXITCODE -ne 0) {
        if ($Strict) {
            Add-ErrorItem "sls_cost_guard_failed"
        } else {
            Add-WarningItem "sls_cost_guard_attention"
        }
    }
} else {
    Add-WarningItem "sls_cost_guard_script_missing"
}

Write-Host
Write-Host "== data retention and cost guard =="
$dataRetentionScript = Join-Path $PSScriptRoot "check-data-retention-cost.ps1"
if (Test-Path -LiteralPath $dataRetentionScript) {
    $dataRetentionArgs = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $dataRetentionScript,
        "-RegionId",
        $RegionId,
        "-InstanceId",
        $EcsInstanceId
    )
    if ($Strict) {
        $dataRetentionArgs += "-FailOnWarning"
    }
    & powershell.exe @dataRetentionArgs
    if ($LASTEXITCODE -ne 0) {
        if ($Strict) {
            Add-ErrorItem "data_retention_cost_guard_failed"
        } else {
            Add-WarningItem "data_retention_cost_guard_attention"
        }
    }
} else {
    Add-WarningItem "data_retention_cost_guard_script_missing"
}

if (-not $SkipAuthUsage) {
    Write-Host
    Write-Host "== sms usage =="
    $smsScript = Join-Path $PSScriptRoot "check-sms-usage.ps1"
    if (Test-Path -LiteralPath $smsScript) {
        $smsOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $smsScript 2>&1
        $smsExitCode = $LASTEXITCODE
        $smsLines = @($smsOutput | ForEach-Object { "$_" })
        $smsLines | ForEach-Object { Write-Host $_ }
        if ($smsExitCode -ne 0) {
            Add-WarningItem "sms_usage_check_failed"
        }
        $smsPackageStatusLine = $smsLines | Where-Object { $_ -match '^sms_package_status=' } | Select-Object -Last 1
        $smsPackageStatus = ""
        if (-not [string]::IsNullOrWhiteSpace($smsPackageStatusLine)) {
            $smsPackageStatus = ($smsPackageStatusLine -replace '^sms_package_status=', '').Trim()
        }
        if ([string]::IsNullOrWhiteSpace($smsPackageStatus)) {
            Add-WarningItem "sms_package_status_not_reported"
        } elseif ($smsPackageStatus -ne "confirmed") {
            Add-WarningItem "sms_package_status_$smsPackageStatus"
        }
    } else {
        Add-WarningItem "sms_usage_script_missing"
    }
}

Write-Host
Write-Host "== summary =="
Write-Host "warnings=$($warnings.Count) errors=$($errors.Count)"
foreach ($warning in $warnings) {
    Write-Warning $warning
}
foreach ($errorItem in $errors) {
    Write-Host "ERROR: $errorItem"
}
if ($errors.Count -gt 0) {
    exit 1
}
if ($Strict -and $warnings.Count -gt 0) {
    exit 2
}
if ($warnings.Count -gt 0) {
    Write-Host "status=attention"
} else {
    Write-Host "status=ready"
}
