param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$Domain = "nongjiqiancha.cn",
    [string]$AdminDomain = "admin.nongjiqiancha.cn",
    [string]$EcsIp = "39.106.1.151",
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

function Wait-SendFile {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeSendFileResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId,
            "--InstanceId", $InstanceId
        )
        $item = $result.Invocations.Invocation[0].InvokeInstances.InvokeInstance[0]
        if ($item.InvocationStatus -eq "Success") {
            return
        }
        if ($item.InvocationStatus -ne "Pending" -and $item.InvocationStatus -ne "Running") {
            throw "SendFile failed: $($item.InvocationStatus) $($item.ErrorCode) $($item.ErrorInfo)"
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

function Sync-ARecord {
    param(
        [string]$Rr,
        [string]$Value
    )
    $records = Invoke-JsonCommand @(
        "aliyun", "alidns", "DescribeDomainRecords",
        "--DomainName", $Domain,
        "--PageSize", "100"
    )
    $record = @($records.DomainRecords.Record | Where-Object { $_.RR -eq $Rr -and $_.Type -eq "A" })[0]
    if ($null -eq $record) {
        $created = Invoke-JsonCommand @(
            "aliyun", "alidns", "AddDomainRecord",
            "--DomainName", $Domain,
            "--RR", $Rr,
            "--Type", "A",
            "--Value", $Value,
            "--TTL", "600"
        )
        Write-Host "Created DNS A record ${Rr}.${Domain} -> ${Value} ($($created.RecordId))"
        return
    }
    if ($record.Value -ne $Value -or $record.Status -ne "ENABLE") {
        Invoke-JsonCommand @(
            "aliyun", "alidns", "UpdateDomainRecord",
            "--RecordId", $record.RecordId,
            "--RR", $Rr,
            "--Type", "A",
            "--Value", $Value,
            "--TTL", "600"
        ) | Out-Null
        if ($record.Status -ne "ENABLE") {
            Invoke-JsonCommand @(
                "aliyun", "alidns", "SetDomainRecordStatus",
                "--RecordId", $record.RecordId,
                "--Status", "ENABLE"
            ) | Out-Null
        }
        Write-Host "Updated DNS A record ${Rr}.${Domain} -> ${Value}"
        return
    }
    Write-Host "DNS A record ${Rr}.${Domain} already points to ${Value}"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$adminDir = Join-Path $repoRoot "admin"
$distDir = Join-Path $adminDir "dist"
$tmpDir = Join-Path $repoRoot "tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

Push-Location $adminDir
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build failed"
    }
} finally {
    Pop-Location
}

$archive = Join-Path $tmpDir "nongjiqiancha-admin.tgz"
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}
Push-Location $distDir
try {
    & tar.exe -czf $archive .
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
    Write-Host "PackageOnly set; skipping upload and remote admin deploy."
    exit 0
}

Sync-ARecord -Rr "admin" -Value $EcsIp

$bytes = [IO.File]::ReadAllBytes($archive)
$partCount = [Math]::Ceiling($bytes.Length / $ChunkSize)
$targetDir = "/tmp/nongji-admin-chunks-$shaPrefix"
Write-Host "Uploading $partCount part(s) to $targetDir"

for ($i = 0; $i -lt $partCount; $i++) {
    $start = $i * $ChunkSize
    $length = [Math]::Min($ChunkSize, $bytes.Length - $start)
    $chunk = New-Object byte[] $length
    [Array]::Copy($bytes, $start, $chunk, 0, $length)
    $content = [Convert]::ToBase64String($chunk)
    $name = "nongjiqiancha-admin.tgz.part{0:D3}" -f $i
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
    Wait-SendFile $send.InvokeId
    Write-Host ("Uploaded {0}/{1}: {2}" -f ($i + 1), $partCount, $name)
}

$remoteScript = @"
set -euo pipefail
lock_file='/var/lock/nongji-admin-deploy.lock'
exec 9>"`$lock_file"
if ! flock -n 9; then
  echo 'another admin deploy is running' >&2
  exit 9
fi

domain='$AdminDomain'
expected_sha='$sha256'
sha_prefix='$shaPrefix'
chunks='/tmp/nongji-admin-chunks-$shaPrefix'
archive='/tmp/nongjiqiancha-admin.tgz'
site_base='/var/www/nongjiqiancha-admin'
release_dir="`$site_base/releases/`$sha_prefix"
current_link="`$site_base/current"
previous_current_target=`$(readlink -f "`$current_link" 2>/dev/null || true)
deploy_completed=0
api_nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-admin'
nginx_enabled='/etc/nginx/sites-enabled/nongjiqiancha-admin'
cert_root='/var/www/certbot'
cert_dir="/etc/letsencrypt/live/`$domain"
nginx_site_backup='/tmp/nongjiqiancha-admin.nginx-site.backup'
nginx_site_backup_state='/tmp/nongjiqiancha-admin.nginx-site.backup-state'

cleanup_deploy_temp() {
  rm -rf -- "`$chunks" "`$archive" /tmp/nongji-admin-deploy.sh /tmp/nongji-admin-root.html "`$nginx_site_backup" "`$nginx_site_backup_state" 2>/dev/null || true
}

prune_static_releases() {
  if [ ! -d "`$site_base/releases" ]; then
    return 0
  fi
  find "`$site_base/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null |
    sort -rn |
    awk 'NR > 8 {sub(/^[^ ]+ /, ""); print}' |
    while IFS= read -r old_release; do
      if [ -n "`$old_release" ] && [ "`$old_release" != "`$release_dir" ] && [ "`$old_release" != "`$previous_current_target" ]; then
        rm -rf -- "`$old_release" && echo "pruned_admin_release=`$(basename "`$old_release")"
      fi
    done
}

backup_nginx_site() {
  if [ -f "`$nginx_site" ]; then
    cp -f "`$nginx_site" "`$nginx_site_backup"
    echo 'present' > "`$nginx_site_backup_state"
  else
    rm -f "`$nginx_site_backup"
    echo 'missing' > "`$nginx_site_backup_state"
  fi
}

restore_nginx_site() {
  local state
  state=`$(cat "`$nginx_site_backup_state" 2>/dev/null || echo missing)
  if [ "`$state" = "present" ] && [ -f "`$nginx_site_backup" ]; then
    cp -f "`$nginx_site_backup" "`$nginx_site"
    ln -sfn "`$nginx_site" "`$nginx_enabled"
  else
    rm -f "`$nginx_site" "`$nginx_enabled"
  fi
  nginx -t && systemctl reload nginx || true
}

restore_current_link() {
  if [ -n "`$previous_current_target" ] && [ -d "`$previous_current_target" ]; then
    ln -sfn "`$previous_current_target" "`$current_link"
    chown -h www-data:www-data "`$current_link" 2>/dev/null || true
  elif [ -z "`$previous_current_target" ]; then
    rm -f "`$current_link"
  fi
}

on_deploy_exit() {
  local status=`$?
  if [ "`$status" -ne 0 ] && [ "`$deploy_completed" != "1" ]; then
    restore_current_link
    restore_nginx_site
  fi
  cleanup_deploy_temp
  exit "`$status"
}
trap on_deploy_exit EXIT

read_active_port() {
  matches=`$(grep -E '^[[:space:]]*proxy_pass[[:space:]]+http://127\.0\.0\.1:(3000|3001)[[:space:]]*;' "`$api_nginx_site" 2>/dev/null | sed -E 's/.*127\.0\.0\.1:(3000|3001)[[:space:]]*;.*/\1/' | sort -u)
  count=`$(printf '%s\n' "`$matches" | sed '/^`$/d' | wc -l | tr -d ' ')
  if [ "`$count" != "1" ]; then
    echo "cannot determine unique active upstream port from `$api_nginx_site" >&2
    return 1
  fi
  printf '%s' "`$matches"
}

write_http_nginx() {
  local active_port="`$1"
  cat > "`$nginx_site" <<EOF
server {
    listen 80;
    server_name `$domain;

    location ^~ /.well-known/acme-challenge/ {
        root `$cert_root;
        default_type "text/plain";
    }

    location / {
        return 404;
    }
}
EOF
}

write_https_nginx() {
  local active_port="`$1"
  cat > "`$nginx_site" <<EOF
server {
    listen 80;
    server_name `$domain;

    location ^~ /.well-known/acme-challenge/ {
        root `$cert_root;
        default_type "text/plain";
    }

    location / {
        return 301 https://\`$host\`$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name `$domain;

    root `$current_link;
    index index.html;

    ssl_certificate `$cert_dir/fullchain.pem;
    ssl_certificate_key `$cert_dir/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=(), usb=()" always;
    add_header Content-Security-Policy "default-src 'self'; connect-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; upgrade-insecure-requests" always;
    add_header Strict-Transport-Security "max-age=15552000; includeSubDomains" always;

    location /admin-api/ {
        proxy_pass http://127.0.0.1:`$active_port;
        proxy_http_version 1.1;
        proxy_set_header Host \`$host;
        proxy_set_header X-Real-IP \`$remote_addr;
        proxy_set_header X-Forwarded-For \`$remote_addr;
        proxy_set_header X-Forwarded-Proto \`$scheme;
        proxy_read_timeout 60s;
    }

    location / {
        try_files \`$uri \`$uri/ /index.html;
    }
}
EOF
}

active_port=`$(read_active_port)
echo "active_port=`$active_port"

echo reassemble
rm -f "`$archive"
cat "`$chunks"/nongjiqiancha-admin.tgz.part* > "`$archive"
actual_sha=`$(sha256sum "`$archive" | awk '{print `$1}')
echo sha256="`$actual_sha"
if [ "`$actual_sha" != "`$expected_sha" ]; then
  echo sha256 mismatch >&2
  exit 10
fi

echo install-admin
rm -rf "`$release_dir"
mkdir -p "`$release_dir" "`$site_base/releases" "`$cert_root"
tar -xzf "`$archive" -C "`$release_dir"
ln -sfn "`$release_dir" "`$current_link"
chown -R www-data:www-data "`$site_base" "`$cert_root"

echo nginx-http
backup_nginx_site
write_http_nginx "`$active_port"
ln -sfn "`$nginx_site" "`$nginx_enabled"
nginx -t
systemctl reload nginx

echo certbot
if command -v certbot >/dev/null 2>&1; then
  certbot certonly --webroot -w "`$cert_root" -d "`$domain" --non-interactive --agree-tos --register-unsafely-without-email --keep-until-expiring || true
else
  echo 'certbot not found; using existing admin certificate if present' >&2
fi

if [ -f "`$cert_dir/fullchain.pem" ] && [ -f "`$cert_dir/privkey.pem" ]; then
  echo nginx-https
  write_https_nginx "`$active_port"
  nginx -t
  systemctl reload nginx
else
  echo 'admin certificate is not available; refusing to keep admin site or /admin-api over HTTP' >&2
  restore_nginx_site
  exit 19
fi

echo verify
sleep 2
expect_status() {
  local label="`$1"
  local expected="`$2"
  shift 2
  local status
  status=`$(curl -sS -o /dev/null -w '%{http_code}' "`$@")
  echo "`$label status=`$status expected=`$expected"
  if [ "`$status" != "`$expected" ]; then
    echo "`$label status mismatch" >&2
    exit 20
  fi
}
if [ -f "`$cert_dir/fullchain.pem" ]; then
  expect_status "admin-http-redirect" "301" -H "Host: `$domain" http://127.0.0.1/
  expect_status "admin-https-root" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/"
  admin_root_body='/tmp/nongji-admin-root.html'
  admin_root_status=`$(curl -sS -k --resolve "`$domain:443:127.0.0.1" -o "`$admin_root_body" -w '%{http_code}' "https://`$domain/" || true)
  if [ "`$admin_root_status" != "200" ]; then
    echo "admin root body probe status mismatch: `$admin_root_status" >&2
    exit 20
  fi
  grep -q 'id="app"' "`$admin_root_body" || { echo 'admin root missing app marker' >&2; exit 20; }
  grep -q '/assets/' "`$admin_root_body" || { echo 'admin root missing assets marker' >&2; exit 20; }
  first_js=`$(grep -oE 'src="[^"]*/assets/[^"]+\.js"' "`$admin_root_body" | head -1 | sed -E 's/^src="([^"]+)"/\1/' || true)
  if [ -z "`$first_js" ]; then
    echo 'admin root missing first js asset' >&2
    exit 20
  fi
  expect_status "admin-https-first-js" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain`$first_js"
  expect_status "admin-https-auth-me" "401" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/admin-api/v1/auth/me"
else
  echo 'admin certificate missing after deploy' >&2
  exit 21
fi
prune_static_releases
deploy_completed=1
"@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-admin-deploy.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-admin-deploy.sh",
    "--Timeout", "600"
)

Write-Host "Remote admin deploy invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote admin deploy failed: status=$($final.Status) exit=$($final.ExitCode)"
}
