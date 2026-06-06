param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$BackupName = "",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

function Invoke-JsonCommand {
    param([string[]]$CommandArgs)
    $raw = & $CommandArgs[0] @($CommandArgs[1..($CommandArgs.Length - 1)])
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $($CommandArgs -join ' ')"
    }
    return $raw | ConvertFrom-Json
}

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
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

if ([string]::IsNullOrWhiteSpace($BackupName)) {
    $remoteScript = @"
set -euo pipefail
install_dir='/opt/nongjiqiancha/server'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'
read_active_port() {
  matches=`$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' "`$nginx_site" 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | sort -u)
  count=`$(printf '%s\n' "`$matches" | sed '/^`$/d' | wc -l | tr -d ' ')
  if [ "`$count" != "1" ]; then
    printf '%s' unknown
    return 0
  fi
  printf '%s' "`$matches"
}
echo backups
find "`$install_dir" -maxdepth 1 -type f -name 'nongji-server.bak-*' -printf '%f\n' | sort -r | head -20
echo current
ls -l "`$install_dir/nongji-server"
active_port=`$(read_active_port)
echo active_upstream_port="`$active_port"
"@
} else {
    if ($BackupName -notmatch '^nongji-server\.bak-[0-9]{14}$') {
        throw "BackupName must look like nongji-server.bak-YYYYMMDDHHMMSS"
    }
    if (-not $Apply) {
        throw "Refusing to rollback without -Apply. To list backups, omit -BackupName."
    }
    $remoteScript = @"
set -euo pipefail
lock_file='/var/lock/nongji-deploy.lock'
exec 9>"`$lock_file"
if ! flock -n 9; then
  echo 'another deploy or rollback is running' >&2
  exit 9
fi
install_dir='/opt/nongjiqiancha/server'
env_file='/etc/nongjiqiancha/server.env'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'
legacy_service='nongji-server.service'
drain_seconds=1800
backup="`$install_dir/$BackupName"
if [ ! -f "`$backup" ]; then
  echo "backup not found: $BackupName" >&2
  exit 20
fi

read_active_port() {
  matches=`$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' "`$nginx_site" 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | sort -u)
  count=`$(printf '%s\n' "`$matches" | sed '/^`$/d' | wc -l | tr -d ' ')
  if [ "`$count" != "1" ]; then
    echo "cannot determine unique nginx upstream port from `$nginx_site" >&2
    return 1
  fi
  printf '%s' "`$matches"
}

require_production_health() {
  body="`$1"
  grep -q '"auth_strict":true' "`$body" || { echo 'health auth_strict is not true' >&2; return 1; }
  grep -q '"bailian":"ok"' "`$body" || { echo 'health bailian is not ok' >&2; return 1; }
  grep -q '"dypns":"ok"' "`$body" || { echo 'health dypns is not ok' >&2; return 1; }
  grep -q '"dypns_fusion":"ok"' "`$body" || { echo 'health dypns_fusion is not ok' >&2; return 1; }
  grep -q '"dypns_sms":"ok"' "`$body" || { echo 'health dypns_sms is not ok' >&2; return 1; }
  grep -q '"redis":"ok"' "`$body" || { echo 'health redis is not ok' >&2; return 1; }
  grep -q '"upload_storage":"oss"' "`$body" || { echo 'health upload_storage is not oss' >&2; return 1; }
  grep -q '"dev_order_endpoints":false' "`$body" || { echo 'health dev_order_endpoints is not false' >&2; return 1; }
}

cancel_stale_drains() {
  systemctl list-units 'nongji-drain-stop-*' --all --no-legend --plain 2>/dev/null | awk '{print `$1}' | while read -r unit; do
    [ -n "`$unit" ] && systemctl stop "`$unit" 2>/dev/null || true
  done
}

if grep -Eq '^[[:space:]]*(export[[:space:]]+)?(LISTEN_ADDR|LISTEN_HOST)=' "`$env_file" 2>/dev/null; then
  echo 'LISTEN_ADDR or LISTEN_HOST is set; dual-port rollback requires PORT-based listen selection' >&2
  exit 22
fi
active_port=`$(read_active_port)
if [ "`$active_port" = "3000" ]; then
  inactive_port=3001
else
  inactive_port=3000
fi
active_service="nongji-server-`$active_port.service"
inactive_service="nongji-server-`$inactive_port.service"
echo "active_port=`$active_port inactive_port=`$inactive_port"
cancel_stale_drains

echo rollback "$BackupName"
cp -a "`$install_dir/nongji-server" "`$install_dir/nongji-server.pre-rollback-`$(date +%Y%m%d%H%M%S)"
install -m 0755 -o nongji -g nongji "`$backup" "`$install_dir/nongji-server.new"
mv "`$install_dir/nongji-server.new" "`$install_dir/nongji-server"

write_slot_unit() {
  port="`$1"
  cat > "/etc/systemd/system/nongji-server-`$port.service" <<EOF
[Unit]
Description=Nongji Qiancha Go API (`$port)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=nongji
Group=nongji
WorkingDirectory=/opt/nongjiqiancha/server
EnvironmentFile=/etc/nongjiqiancha/server.env
ExecStart=/usr/bin/env PORT=`$port /opt/nongjiqiancha/server/nongji-server
Restart=on-failure
RestartSec=3
LimitNOFILE=65535
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/var/lib/nongjiqiancha /var/log/nongjiqiancha

[Install]
WantedBy=multi-user.target
EOF
}

write_slot_unit 3000
write_slot_unit 3001
systemctl daemon-reload

systemctl stop "`$inactive_service" 2>/dev/null || true
if [ "`$inactive_port" = "3000" ]; then
  systemctl stop "`$legacy_service" 2>/dev/null || true
fi
systemctl reset-failed "`$inactive_service" 2>/dev/null || true
systemctl start "`$inactive_service"
systemctl is-active "`$inactive_service"

upstream_body='/tmp/nongji-rollback-upstream-health.json'
upstream_status=''
for i in `$(seq 1 20); do
  upstream_status=`$(curl -sS -o "`$upstream_body" -w '%{http_code}' http://127.0.0.1:`$inactive_port/healthz || true)
  if [ "`$upstream_status" = "200" ]; then
    break
  fi
  echo "rollback upstream not ready: `$upstream_status" >&2
  sleep 1
done
if [ "`$upstream_status" != "200" ]; then
  cat "`$upstream_body" || true
  exit 29
fi
require_production_health "`$upstream_body"

nginx_backup="`$nginx_site.rollback-bak-`$(date +%Y%m%d%H%M%S)"
cp -a "`$nginx_site" "`$nginx_backup"
before_count=`$(grep -Ec "^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:`$active_port[[:space:]]*;" "`$nginx_site" || true)
if [ "`$before_count" -lt 1 ]; then
  echo "nginx upstream port `$active_port was not found in non-comment proxy_pass lines" >&2
  exit 30
fi
sed -i -E "s#(^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:)(3000|3001)([[:space:]]*;)#\1`$inactive_port\3#g" "`$nginx_site"
new_port=`$(read_active_port)
if [ "`$new_port" != "`$inactive_port" ]; then
  cp -a "`$nginx_backup" "`$nginx_site"
  echo "nginx upstream switch verification failed: expected `$inactive_port got `${new_port:-unknown}" >&2
  exit 30
fi
if ! nginx -t; then
  cp -a "`$nginx_backup" "`$nginx_site"
  nginx -t || true
  exit 30
fi
systemctl reload nginx

restore_nginx_after_switch() {
  if [ -f "`$nginx_backup" ]; then
    cp -a "`$nginx_backup" "`$nginx_site"
    nginx -t && systemctl reload nginx || true
  fi
  systemctl stop "`$inactive_service" 2>/dev/null || true
}

health_body='/tmp/nongji-rollback-health.json'
health_status=`$(curl -sS -o "`$health_body" -w '%{http_code}' -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz || true)
cat "`$health_body" || true
echo
if [ "`$health_status" != "200" ]; then
  restore_nginx_after_switch
  exit 30
fi
if ! require_production_health "`$health_body"; then
  restore_nginx_after_switch
  exit 31
fi
systemctl enable "`$inactive_service" >/dev/null
systemctl disable "`$active_service" >/dev/null 2>&1 || true
systemctl disable "`$legacy_service" >/dev/null 2>&1 || true
drain_unit="nongji-drain-stop-`$active_port-`$(date +%s)"
systemd-run --unit="`$drain_unit" --on-active="`${drain_seconds}s" /bin/sh -c "systemctl stop '`$active_service' '`$legacy_service' 2>/dev/null || true" >/dev/null 2>&1 || {
  echo "failed to schedule old slot drain stop; leaving old slot running" >&2
}
systemctl is-active "`$inactive_service"
"@
}

$remoteBytes = [Text.Encoding]::UTF8.GetBytes(($remoteScript -replace "`r`n", "`n"))
$remoteBase64 = [Convert]::ToBase64String($remoteBytes)
$command = "printf '%s' '$remoteBase64' | base64 -d >/tmp/nongji-rollback.sh && bash /tmp/nongji-rollback.sh"
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $command,
    "--Timeout", "180"
)

Write-Host "Remote rollback invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Rollback command failed: status=$($final.Status) exit=$($final.ExitCode)"
}
