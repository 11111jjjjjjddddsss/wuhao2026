param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$BackendBaseUrl = "https://api.nongjiqiancha.cn",
    [string]$TimerCalendar = "*-*-* 21:35:00 UTC",
    [switch]$RunOnce
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
            -replace '(?i)("(?:AccessKeyId|AccessKeySecret|SecurityToken|Signature|SignatureNonce|Content)"\s*:\s*")[^"]+', '${1}REDACTED'
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

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 3
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId
        )
        $item = $result.Invocation.InvocationResults.InvocationResult[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($item.Output))
            [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
            return
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

$normalizedBaseUrl = $BackendBaseUrl.Trim().TrimEnd("/")
if ($normalizedBaseUrl -ne "https://api.nongjiqiancha.cn") {
    Write-Warning "BackendBaseUrl is kept only for backward-compatible CLI shape; the ECS timer calls the local active slot via Nginx config."
}
$escapedTimerCalendar = $TimerCalendar
$runOnceFlag = if ($RunOnce) { "1" } else { "0" }

$remoteScript = @'
set -euo pipefail

cat >/usr/local/bin/nongji-generate-today-agri.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
export TZ=Asia/Shanghai
secret=$(grep -E '^(export[[:space:]]+)?DAILY_AGRI_JOB_SECRET=' /etc/nongjiqiancha/server.env 2>/dev/null | tail -n 1 | sed -E 's/^(export[[:space:]]+)?DAILY_AGRI_JOB_SECRET=//')
secret="${secret%\"}"
secret="${secret#\"}"
secret="${secret%\'}"
secret="${secret#\'}"
if [ -z "${secret:-}" ]; then
  echo 'DAILY_AGRI_JOB_SECRET is missing in /etc/nongjiqiancha/server.env' >&2
  exit 12
fi
read_active_port() {
  matches=$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' /etc/nginx/sites-available/nongjiqiancha-api 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | sort -u)
  count=$(printf '%s\n' "$matches" | sed '/^$/d' | wc -l | tr -d ' ')
  if [ "$count" != "1" ]; then
    echo "cannot determine unique active upstream port for daily agri job" >&2
    return 1
  fi
  printf '%s' "$matches"
}
active_port=$(read_active_port)
curl --silent --show-error --fail \
  --max-time 300 \
  --request POST \
  --header "X-Internal-Job-Secret: ${secret}" \
  "http://127.0.0.1:${active_port}/internal/jobs/today-agri-card/generate"
echo
EOF
chmod 0755 /usr/local/bin/nongji-generate-today-agri.sh

cat >/etc/systemd/system/nongji-daily-agri.service <<'EOF'
[Unit]
Description=Nongji Qiancha daily agri generation
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=root
Group=root
TimeoutStartSec=330
ExecStart=/usr/local/bin/nongji-generate-today-agri.sh
EOF

cat >/etc/systemd/system/nongji-daily-agri.timer <<'EOF'
[Unit]
Description=Run Nongji Qiancha daily agri generation every morning (Asia/Shanghai)

[Timer]
OnCalendar=__TIMER_CALENDAR__
Persistent=true
RandomizedDelaySec=300
Unit=nongji-daily-agri.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now nongji-daily-agri.timer
if [ '__RUN_ONCE__' = '1' ]; then
  systemctl start nongji-daily-agri.service
fi

echo '== timer =='
systemctl status nongji-daily-agri.timer --no-pager || true
echo
echo '== next triggers =='
systemctl list-timers nongji-daily-agri.timer --all --no-pager || true
echo
echo '== last service =='
systemctl status nongji-daily-agri.service --no-pager || true
'@
$remoteScript = $remoteScript.Replace("__TIMER_CALENDAR__", $escapedTimerCalendar)
$remoteScript = $remoteScript.Replace("__RUN_ONCE__", $runOnceFlag)

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-configure-daily-agri.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-configure-daily-agri.sh",
    "--Timeout", "300"
)

Write-Host "Remote configure invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote configure failed: status=$($final.Status) exit=$($final.ExitCode)"
}
