param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$Commit = "",
    [int]$ChunkSize = 20000,
    [switch]$PackageOnly
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
    if ($exe -eq "aliyun") {
        $arguments += @(
            "--connect-timeout", "20",
            "--read-timeout", "120",
            "--retry-count", "3"
        )
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

function Get-SendFileStatus {
    param([string]$InvokeId)
    $result = Invoke-JsonCommand @(
        "aliyun", "ecs", "DescribeSendFileResults",
        "--RegionId", $RegionId,
        "--InvokeId", $InvokeId,
        "--InstanceId", $InstanceId
    )
    $invocation = $result.Invocations.Invocation[0]
    $instance = $invocation.InvokeInstances.InvokeInstance[0]
    [pscustomobject]@{
        InvokeId = $InvokeId
        Status = $instance.InvocationStatus
        ErrorCode = $instance.ErrorCode
        ErrorInfo = $instance.ErrorInfo
        Name = $invocation.Name
    }
}

function Wait-SendFile {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $status = Get-SendFileStatus $InvokeId
        if ($status.Status -eq "Success") {
            return $status
        }
        if ($status.Status -ne "Pending" -and $status.Status -ne "Running") {
            return $status
        }
    }
    throw "Timed out waiting for SendFile $InvokeId"
}

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 120; $i++) {
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

. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$serverDir = Join-Path $repoRoot "server-go"
if ([string]::IsNullOrWhiteSpace($Commit)) {
    $Commit = (& git -C $repoRoot rev-parse --short HEAD).Trim()
}

$tmpDir = Join-Path $repoRoot "tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$archive = Join-Path $tmpDir "server-go-src-$Commit.tgz"
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}

Push-Location $serverDir
try {
    & tar.exe -czf $archive go.mod go.sum assets migrations cmd internal
    if ($LASTEXITCODE -ne 0) {
        throw "tar failed"
    }
} finally {
    Pop-Location
}

$sha256 = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
$shaPrefix = $sha256.Substring(0, 12)
$archiveInfo = Get-Item -LiteralPath $archive
Write-Host "Packaged $($archiveInfo.FullName) ($($archiveInfo.Length) bytes)"
Write-Host "SHA256 $sha256"

if ($PackageOnly) {
    Write-Host "PackageOnly set; skipping upload and remote deploy."
    exit 0
}

$bytes = [IO.File]::ReadAllBytes($archive)
$partCount = [Math]::Ceiling($bytes.Length / $ChunkSize)
$targetDir = "/tmp/nongji-deploy-chunks-$Commit-$shaPrefix"
Write-Host "Uploading $partCount part(s) to $targetDir"

for ($i = 0; $i -lt $partCount; $i++) {
    $start = $i * $ChunkSize
    $length = [Math]::Min($ChunkSize, $bytes.Length - $start)
    $chunk = New-Object byte[] $length
    [Array]::Copy($bytes, $start, $chunk, 0, $length)
    $content = [Convert]::ToBase64String($chunk)
    $name = "server-go-src-$Commit.tgz.part{0:D3}" -f $i
    $send = Invoke-JsonCommand @(
        "aliyun", "ecs", "SendFile",
        "--RegionId", $RegionId,
        "--InstanceId.1", $InstanceId,
        "--Name", $name,
        "--TargetDir", $targetDir,
        "--ContentType", "Base64",
        "--Content", $content,
        "--Overwrite", "true",
        "--Timeout", "120"
    )
    $status = Wait-SendFile $send.InvokeId
    if ($status.Status -ne "Success") {
        throw "SendFile failed for ${name}: $($status.Status) $($status.ErrorCode) $($status.ErrorInfo)"
    }
    Write-Host ("Uploaded {0}/{1}: {2}" -f ($i + 1), $partCount, $name)
}

$remoteScript = @"
set -euo pipefail
lock_file='/var/lock/nongji-deploy.lock'
exec 9>"`$lock_file"
if ! flock -n 9; then
  echo 'another deploy or rollback is running' >&2
  exit 9
fi
commit='$Commit'
expected_sha='$sha256'
chunks="/tmp/nongji-deploy-chunks-$Commit-$shaPrefix"
archive="/tmp/server-go-src-$Commit.tgz"
stage="/tmp/nongji-server-src-$Commit"
install_dir='/opt/nongjiqiancha/server'
bin_tmp="/tmp/nongji-server-$Commit"
env_file='/etc/nongjiqiancha/server.env'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'
admin_nginx_site='/etc/nginx/sites-available/nongjiqiancha-admin'
legacy_service='nongji-server.service'
drain_seconds=1800

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
  grep -q '"sms":"ok"' "`$body" || { echo 'health sms is not ok' >&2; return 1; }
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
  echo 'LISTEN_ADDR or LISTEN_HOST is set; dual-port deploy requires PORT-based listen selection' >&2
  exit 12
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
switch_completed=0
installed_new_binary=0
preinstall_bin_backup=''
preinstall_assets_backup=''
preinstall_migrations_backup=''
preinstall_gomod_backup=''
preinstall_gosum_backup=''

restore_pre_switch_install() {
  if [ "`$installed_new_binary" != "1" ] || [ "`$switch_completed" = "1" ]; then
    return 0
  fi
  echo "restore pre-switch install after failed deploy" >&2
  if [ -n "`$preinstall_bin_backup" ] && [ -f "`$preinstall_bin_backup" ]; then
    cp -a "`$preinstall_bin_backup" "`$install_dir/nongji-server" || true
  fi
  if [ -n "`$preinstall_assets_backup" ] && [ -d "`$preinstall_assets_backup" ]; then
    rm -rf "`$install_dir/assets"
    cp -a "`$preinstall_assets_backup" "`$install_dir/assets" || true
  fi
  if [ -n "`$preinstall_migrations_backup" ] && [ -d "`$preinstall_migrations_backup" ]; then
    rm -rf "`$install_dir/migrations"
    cp -a "`$preinstall_migrations_backup" "`$install_dir/migrations" || true
  fi
  if [ -n "`$preinstall_gomod_backup" ] && [ -f "`$preinstall_gomod_backup" ]; then
    cp -a "`$preinstall_gomod_backup" "`$install_dir/go.mod" || true
  fi
  if [ -n "`$preinstall_gosum_backup" ] && [ -f "`$preinstall_gosum_backup" ]; then
    cp -a "`$preinstall_gosum_backup" "`$install_dir/go.sum" || true
  fi
  chown -R nongji:nongji "`$install_dir/nongji-server" "`$install_dir/assets" "`$install_dir/migrations" "`$install_dir/go.mod" "`$install_dir/go.sum" 2>/dev/null || true
  systemctl stop "`$inactive_service" 2>/dev/null || true
}

trap 'status=`$?; if [ "`$status" -ne 0 ]; then restore_pre_switch_install; fi; exit "`$status"' EXIT

echo reassemble
rm -f "`$archive"
cat "`$chunks"/server-go-src-"`$commit".tgz.part* > "`$archive"
actual_sha=`$(sha256sum "`$archive" | awk '{print `$1}')
echo sha256="`$actual_sha"
if [ "`$actual_sha" != "`$expected_sha" ]; then
  echo sha256 mismatch >&2
  exit 10
fi

echo unpack
rm -rf "`$stage"
mkdir -p "`$stage"
tar -xzf "`$archive" -C "`$stage"
cd "`$stage"
export GOPROXY="`${GOPROXY:-https://goproxy.cn,direct}"
export GOSUMDB="`${GOSUMDB:-sum.golang.google.cn}"

echo test
go test ./...

echo build
go build -buildvcs=false -o "`$bin_tmp" ./cmd/server

echo install
install -m 0755 -o nongji -g nongji "`$bin_tmp" "`$install_dir/nongji-server.new"
install_backup_suffix=`$(date +%Y%m%d%H%M%S)
if [ -f "`$install_dir/nongji-server" ]; then
  preinstall_bin_backup="`$install_dir/nongji-server.bak-`$install_backup_suffix"
  cp -a "`$install_dir/nongji-server" "`$preinstall_bin_backup"
fi
if [ -d "`$install_dir/assets" ]; then
  preinstall_assets_backup="`$install_dir/assets.bak-`$install_backup_suffix"
  cp -a "`$install_dir/assets" "`$preinstall_assets_backup"
fi
if [ -d "`$install_dir/migrations" ]; then
  preinstall_migrations_backup="`$install_dir/migrations.bak-`$install_backup_suffix"
  cp -a "`$install_dir/migrations" "`$preinstall_migrations_backup"
fi
if [ -f "`$install_dir/go.mod" ]; then
  preinstall_gomod_backup="`$install_dir/go.mod.bak-`$install_backup_suffix"
  cp -a "`$install_dir/go.mod" "`$preinstall_gomod_backup"
fi
if [ -f "`$install_dir/go.sum" ]; then
  preinstall_gosum_backup="`$install_dir/go.sum.bak-`$install_backup_suffix"
  cp -a "`$install_dir/go.sum" "`$preinstall_gosum_backup"
fi
mv "`$install_dir/nongji-server.new" "`$install_dir/nongji-server"
rm -rf "`$install_dir/assets" "`$install_dir/migrations"
cp -a "`$stage/assets" "`$install_dir/assets"
cp -a "`$stage/migrations" "`$install_dir/migrations"
cp "`$stage/go.mod" "`$install_dir/go.mod"
cp "`$stage/go.sum" "`$install_dir/go.sum"
chown -R nongji:nongji "`$install_dir/assets" "`$install_dir/migrations" "`$install_dir/go.mod" "`$install_dir/go.sum"
installed_new_binary=1

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

echo start-new-slot
systemctl stop "`$inactive_service" 2>/dev/null || true
if [ "`$inactive_port" = "3000" ]; then
  systemctl stop "`$legacy_service" 2>/dev/null || true
fi
systemctl reset-failed "`$inactive_service" 2>/dev/null || true
systemctl start "`$inactive_service"
systemctl is-active "`$inactive_service"

echo health
health_body='/tmp/nongji-health.json'
health_status=''
upstream_body='/tmp/nongji-upstream-health.json'
upstream_status=''
echo wait-upstream
for i in `$(seq 1 20); do
  upstream_status=`$(curl -s --connect-timeout 1 --max-time 2 -o "`$upstream_body" -w '%{http_code}' http://127.0.0.1:`$inactive_port/healthz || true)
  if [ "`$upstream_status" = "200" ]; then
    break
  fi
  echo "upstream not ready: `$upstream_status (attempt `$i/20)" >&2
  sleep 1
done
if [ "`$upstream_status" != "200" ]; then
  cat "`$upstream_body" || true
  exit 20
fi
require_production_health "`$upstream_body"

echo switch-nginx
nginx_backup="`$nginx_site.bak-`$(date +%Y%m%d%H%M%S)"
cp -a "`$nginx_site" "`$nginx_backup"
admin_nginx_backup=''
if [ -f "`$admin_nginx_site" ]; then
  admin_nginx_backup="`$admin_nginx_site.bak-`$(date +%Y%m%d%H%M%S)"
  cp -a "`$admin_nginx_site" "`$admin_nginx_backup"
fi
before_count=`$(grep -Ec "^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:`$active_port[[:space:]]*;" "`$nginx_site" || true)
if [ "`$before_count" -lt 1 ]; then
  echo "nginx upstream port `$active_port was not found in non-comment proxy_pass lines" >&2
  exit 30
fi
sed -i -E "s#(^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:)(3000|3001)([[:space:]]*;)#\1`$inactive_port\3#g" "`$nginx_site"
if [ -f "`$admin_nginx_site" ]; then
  sed -i -E "s#(^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:)(3000|3001)([[:space:]]*;)#\1`$inactive_port\3#g" "`$admin_nginx_site"
fi
new_port=`$(read_active_port)
if [ "`$new_port" != "`$inactive_port" ]; then
  cp -a "`$nginx_backup" "`$nginx_site"
  if [ -n "`$admin_nginx_backup" ]; then
    cp -a "`$admin_nginx_backup" "`$admin_nginx_site"
  fi
  echo "nginx upstream switch verification failed: expected `$inactive_port got `${new_port:-unknown}" >&2
  exit 30
fi
if [ -f "`$admin_nginx_site" ]; then
  admin_port=`$(grep -oE 'proxy_pass http://127\.0\.0\.1:(3000|3001);' "`$admin_nginx_site" 2>/dev/null | head -1 | sed -E 's/.*:([0-9]+);/\1/' || true)
  if [ -z "`$admin_port" ] || [ "`$admin_port" != "`$inactive_port" ]; then
    cp -a "`$nginx_backup" "`$nginx_site"
    if [ -n "`$admin_nginx_backup" ]; then
      cp -a "`$admin_nginx_backup" "`$admin_nginx_site"
    fi
    echo "admin upstream switch verification failed: expected `$inactive_port got `${admin_port:-unknown}" >&2
    exit 30
  fi
fi
if ! nginx -t; then
  cp -a "`$nginx_backup" "`$nginx_site"
  if [ -n "`$admin_nginx_backup" ]; then
    cp -a "`$admin_nginx_backup" "`$admin_nginx_site"
  fi
  nginx -t || true
  exit 30
fi
systemctl reload nginx

restore_nginx_after_switch() {
  if [ -f "`$nginx_backup" ]; then
    cp -a "`$nginx_backup" "`$nginx_site"
    if [ -n "`$admin_nginx_backup" ]; then
      cp -a "`$admin_nginx_backup" "`$admin_nginx_site"
    fi
    nginx -t && systemctl reload nginx || true
  fi
  systemctl stop "`$inactive_service" 2>/dev/null || true
}

for i in `$(seq 1 20); do
  health_status=`$(curl -sS --resolve api.nongjiqiancha.cn:443:127.0.0.1 -o "`$health_body" -w '%{http_code}' https://api.nongjiqiancha.cn/healthz || true)
  if [ "`$health_status" = "200" ]; then
    cat "`$health_body" || true
    echo
    break
  fi
  echo "health not ready: `$health_status" >&2
  sleep 2
done
if [ "`$health_status" != "200" ]; then
  cat "`$health_body" || true
  restore_nginx_after_switch
  exit 20
fi
if ! require_production_health "`$health_body"; then
  restore_nginx_after_switch
  exit 21
fi

admin_body='/tmp/nongji-admin-auth-me.json'
admin_status=`$(curl -sS --resolve admin.nongjiqiancha.cn:443:127.0.0.1 -o "`$admin_body" -w '%{http_code}' https://admin.nongjiqiancha.cn/admin-api/v1/auth/me || true)
echo "admin_auth_me_status=`$admin_status"
cat "`$admin_body" || true
echo
if [ "`$admin_status" != "401" ]; then
  restore_nginx_after_switch
  exit 22
fi

switch_completed=1
echo drain-old-slot
systemctl enable "`$inactive_service" >/dev/null
systemctl disable "`$active_service" >/dev/null 2>&1 || true
systemctl disable "`$legacy_service" >/dev/null 2>&1 || true
drain_unit="nongji-drain-stop-`$active_port-`$(date +%s)"
systemd-run --unit="`$drain_unit" --on-active="`${drain_seconds}s" /bin/sh -c "systemctl stop '`$active_service' '`$legacy_service' 2>/dev/null || true" >/dev/null 2>&1 || {
  echo "failed to schedule old slot drain stop; leaving old slot running" >&2
}
systemctl is-active "`$inactive_service"
"@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-deploy-$Commit.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-deploy-$Commit.sh",
    "--Timeout", "600"
)

Write-Host "Remote deploy invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote deploy failed: status=$($final.Status) exit=$($final.ExitCode)"
}
