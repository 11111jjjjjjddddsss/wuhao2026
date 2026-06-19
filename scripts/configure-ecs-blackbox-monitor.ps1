param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [switch]$RunOnce,
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

$runOnceFlag = if ($RunOnce) { "1" } else { "0" }

$remoteScript = @'
set -euo pipefail

cat >/usr/local/bin/nongji-public-blackbox.sh <<'EOF'
#!/usr/bin/env bash
set -u

log_file="/var/log/nongjiqiancha/server.log"
mkdir -p "$(dirname "$log_file")"

json_escape() {
  printf '%s' "${1:-}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/[[:cntrl:]]/ /g'
}

emit_failure() {
  target="$1"
  status="$2"
  error_text="$(json_escape "$3")"
  timestamp="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  printf '{"time":"%s","level":"error","event":"blackbox_probe_failed","message":"public blackbox probe failed","target":"%s","status":"%s","error":"%s"}\n' \
    "$timestamp" "$target" "$(json_escape "$status")" "$error_text" >>"$log_file"
}

probe() {
  target="$1"
  url="$2"
  shift 2
  body_file="$(mktemp)"
  err_file="$(mktemp)"
  curl_exit=0
  http_code="$(curl --silent --show-error --location --connect-timeout 5 --max-time 12 --output "$body_file" --write-out '%{http_code}' "$url" 2>"$err_file")" || curl_exit=$?
  if [ "$curl_exit" -ne 0 ]; then
    emit_failure "$target" "curl_exit_${curl_exit}" "$(cat "$err_file" 2>/dev/null)"
    rm -f "$body_file" "$err_file"
    return 0
  fi
  if [ "$http_code" != "200" ]; then
    emit_failure "$target" "$http_code" "unexpected_http_status"
    rm -f "$body_file" "$err_file"
    return 0
  fi
  for expect_regex in "$@"; do
    if ! grep -Eq "$expect_regex" "$body_file"; then
      emit_failure "$target" "$http_code" "unexpected_response_body_missing:${expect_regex}"
      rm -f "$body_file" "$err_file"
      return 0
    fi
  done
  if [ "$target" = "api_healthz" ]; then
    for expected_marker in \
      '"ok"[[:space:]]*:[[:space:]]*true' \
      '"auth_strict"[[:space:]]*:[[:space:]]*true' \
      '"bailian"[[:space:]]*:[[:space:]]*"ok"' \
      '"sms"[[:space:]]*:[[:space:]]*"ok"' \
      '"dev_order_endpoints"[[:space:]]*:[[:space:]]*false' \
      '"redis"[[:space:]]*:[[:space:]]*"ok"' \
      '"upload_storage"[[:space:]]*:[[:space:]]*"oss"'; do
      if ! grep -Eq "$expected_marker" "$body_file"; then
        emit_failure "$target" "$http_code" "unexpected_healthz_missing:${expected_marker}"
        rm -f "$body_file" "$err_file"
        return 0
      fi
    done
  fi
  rm -f "$body_file" "$err_file"
}

probe "api_healthz" "https://api.nongjiqiancha.cn/healthz"
probe "website_home" "https://nongjiqiancha.cn/"
probe "admin_home" "https://admin.nongjiqiancha.cn/"
EOF
chmod 0755 /usr/local/bin/nongji-public-blackbox.sh

cat >/etc/systemd/system/nongji-public-blackbox.service <<'EOF'
[Unit]
Description=Nongji Qiancha public HTTPS blackbox probe
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=root
Group=root
TimeoutStartSec=45
ExecStart=/usr/local/bin/nongji-public-blackbox.sh
EOF

cat >/etc/systemd/system/nongji-public-blackbox.timer <<'EOF'
[Unit]
Description=Run Nongji Qiancha public HTTPS blackbox probe

[Timer]
OnBootSec=3min
OnUnitActiveSec=5min
Persistent=true
RandomizedDelaySec=30
Unit=nongji-public-blackbox.service

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now nongji-public-blackbox.timer
if [ '__RUN_ONCE__' = '1' ]; then
  systemctl start nongji-public-blackbox.service
fi

echo '== timer =='
systemctl status nongji-public-blackbox.timer --no-pager || true
echo
echo '== next triggers =='
systemctl list-timers nongji-public-blackbox.timer --all --no-pager || true
echo
echo '== last service =='
systemctl status nongji-public-blackbox.service --no-pager || true
'@
$remoteScript = $remoteScript.Replace("__RUN_ONCE__", $runOnceFlag)

if ($DryRun) {
    Write-Host "dry_run=true instance=$InstanceId region=$RegionId run_once=$RunOnce"
    Write-Host $remoteScript
    return
}

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-configure-blackbox-monitor.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-configure-blackbox-monitor.sh",
    "--Timeout", "180"
)

Write-Host "Remote configure invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote configure failed: status=$($final.Status) exit=$($final.ExitCode)"
}
