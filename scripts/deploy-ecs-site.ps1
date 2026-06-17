param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$Domain = "nongjiqiancha.cn",
    [string]$WwwDomain = "www.nongjiqiancha.cn",
    [string]$EcsIp = "39.106.1.151",
    [int]$ChunkSize = 20000,
    [switch]$PackageOnly,
    [switch]$AllowOfficialDownloadUrl
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

function Test-OfficialAndroidApkUrl {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $false
    }
    $parsed = $null
    if (-not [System.Uri]::TryCreate($Url.Trim(), [System.UriKind]::Absolute, [ref]$parsed)) {
        return $false
    }
    if ($parsed.Scheme -ne "https") {
        return $false
    }
    return $parsed.AbsolutePath.ToLowerInvariant().EndsWith(".apk")
}

function Test-InternalAndroidApkUrlMarker {
    param([string]$Url)
    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $false
    }
    $lower = $Url.ToLowerInvariant()
    try {
        $decoded = [System.Uri]::UnescapeDataString($Url).ToLowerInvariant()
        $lower = "$lower $decoded"
    } catch {
        # Keep the raw-string check if the URL is not safely decodable.
    }
    return $lower -match "test-apks|debug|internal|staging"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$siteDir = Join-Path $repoRoot "site"
$distDir = Join-Path $siteDir "dist"
$tmpDir = Join-Path $repoRoot "tmp"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$configuredApkUrl = [string]$env:VITE_ANDROID_APK_URL
$siteEnvFiles = @(
    Get-ChildItem -LiteralPath $siteDir -Force -File -Filter ".env*" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notin @(".env.example", ".env.sample", ".env.template") }
)
if ($siteEnvFiles.Count -gt 0) {
    foreach ($envFile in $siteEnvFiles) {
        $envText = Get-Content -LiteralPath $envFile.FullName -Raw -ErrorAction Stop
        if ($envText -match '(?m)^\s*VITE_ANDROID_APK_URL\s*=') {
            if (-not $AllowOfficialDownloadUrl) {
                throw "site $($envFile.Name) contains VITE_ANDROID_APK_URL, but official download URLs are disabled by default for site deploy. Pass -AllowOfficialDownloadUrl only for a confirmed official release."
            }
            $envApkLine = ($envText -split "`r?`n" | Where-Object { $_ -match '^\s*VITE_ANDROID_APK_URL\s*=' } | Select-Object -First 1)
            $envApkUrl = (($envApkLine -split "=", 2)[1]).Trim().Trim('"').Trim("'")
            if (Test-InternalAndroidApkUrlMarker $envApkUrl) {
                throw "site $($envFile.Name) contains an internal test APK URL; do not publish test packages on the official website."
            }
            if (-not (Test-OfficialAndroidApkUrl $envApkUrl)) {
                throw "site $($envFile.Name) contains VITE_ANDROID_APK_URL, but it is not a valid https .apk URL."
            }
        }
    }
}
if (-not [string]::IsNullOrWhiteSpace($configuredApkUrl)) {
    if (-not $AllowOfficialDownloadUrl) {
        throw "VITE_ANDROID_APK_URL is set, but official download URLs are disabled by default for site deploy. Pass -AllowOfficialDownloadUrl only for a confirmed official release."
    }
    if (Test-InternalAndroidApkUrlMarker $configuredApkUrl) {
        throw "VITE_ANDROID_APK_URL looks like an internal test APK URL; do not publish test packages on the official website."
    }
    if (-not (Test-OfficialAndroidApkUrl $configuredApkUrl)) {
        throw "VITE_ANDROID_APK_URL must be a valid https .apk URL when official downloads are explicitly enabled."
    }
}

Push-Location $siteDir
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "npm run build failed"
    }
} finally {
    Pop-Location
}

$requiredDistFiles = @(
    "index.html",
    "gongan.png",
    "legal/user-agreement/index.html",
    "legal/privacy-policy/index.html"
)
foreach ($relativePath in $requiredDistFiles) {
    $distFile = Join-Path $distDir $relativePath
    if (-not (Test-Path -LiteralPath $distFile -PathType Leaf)) {
        throw "site build missing required file: $relativePath"
    }
}

$archive = Join-Path $tmpDir "nongjiqiancha-site.tgz"
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
    Write-Host "PackageOnly set; skipping upload and remote site deploy."
    exit 0
}

Sync-ARecord -Rr "@" -Value $EcsIp
Sync-ARecord -Rr "www" -Value $EcsIp

$bytes = [IO.File]::ReadAllBytes($archive)
$partCount = [Math]::Ceiling($bytes.Length / $ChunkSize)
$targetDir = "/tmp/nongji-site-chunks-$shaPrefix"
Write-Host "Uploading $partCount part(s) to $targetDir"

for ($i = 0; $i -lt $partCount; $i++) {
    $start = $i * $ChunkSize
    $length = [Math]::Min($ChunkSize, $bytes.Length - $start)
    $chunk = New-Object byte[] $length
    [Array]::Copy($bytes, $start, $chunk, 0, $length)
    $content = [Convert]::ToBase64String($chunk)
    $name = "nongjiqiancha-site.tgz.part{0:D3}" -f $i
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
lock_file='/var/lock/nongji-site-deploy.lock'
exec 9>"`$lock_file"
if ! flock -n 9; then
  echo 'another site deploy is running' >&2
  exit 9
fi

domain='$Domain'
www_domain='$WwwDomain'
expected_sha='$sha256'
sha_prefix='$shaPrefix'
chunks='/tmp/nongji-site-chunks-$shaPrefix'
archive='/tmp/nongjiqiancha-site.tgz'
site_base='/var/www/nongjiqiancha-site'
test_apks_root='/var/www/nongjiqiancha-test-apks'
release_dir="`$site_base/releases/`$sha_prefix"
current_link="`$site_base/current"
nginx_site='/etc/nginx/sites-available/nongjiqiancha-site'
nginx_enabled='/etc/nginx/sites-enabled/nongjiqiancha-site'
cert_root='/var/www/certbot'
cert_dir="/etc/letsencrypt/live/`$domain"
nginx_site_backup='/tmp/nongjiqiancha-site.nginx-site.backup'
nginx_site_backup_state='/tmp/nongjiqiancha-site.nginx-site.backup-state'

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

write_http_nginx() {
  cat > "`$nginx_site" <<EOF
server {
    listen 80;
    server_name `$domain `$www_domain;

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
  cat > "`$nginx_site" <<EOF
server {
    listen 80;
    server_name `$domain `$www_domain;

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
    server_name `$domain `$www_domain;

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
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'none'; upgrade-insecure-requests" always;
    add_header Strict-Transport-Security "max-age=15552000; includeSubDomains" always;

    location ^~ /test-apks/ {
        alias `$test_apks_root/;
        default_type application/vnd.android.package-archive;
        add_header Cache-Control "private, max-age=300" always;
        add_header X-Robots-Tag "noindex, nofollow" always;
    }

    location / {
        try_files \`$uri \`$uri/ /index.html;
    }
}
EOF
}

echo reassemble
rm -f "`$archive"
cat "`$chunks"/nongjiqiancha-site.tgz.part* > "`$archive"
actual_sha=`$(sha256sum "`$archive" | awk '{print `$1}')
echo sha256="`$actual_sha"
if [ "`$actual_sha" != "`$expected_sha" ]; then
  echo sha256 mismatch >&2
  exit 10
fi

echo install-site
rm -rf "`$release_dir"
mkdir -p "`$release_dir" "`$site_base/releases" "`$cert_root" "`$test_apks_root"
tar -xzf "`$archive" -C "`$release_dir"
ln -sfn "`$release_dir" "`$current_link"
chown -R www-data:www-data "`$site_base" "`$cert_root"

echo nginx-http
backup_nginx_site
write_http_nginx
ln -sfn "`$nginx_site" "`$nginx_enabled"
nginx -t
systemctl reload nginx

echo certbot
if command -v certbot >/dev/null 2>&1; then
  certbot certonly --webroot -w "`$cert_root" -d "`$domain" -d "`$www_domain" --non-interactive --agree-tos --register-unsafely-without-email --keep-until-expiring || true
else
  echo 'certbot not found; using existing site certificate if present' >&2
fi

if [ -f "`$cert_dir/fullchain.pem" ] && [ -f "`$cert_dir/privkey.pem" ]; then
  echo nginx-https
  write_https_nginx
  nginx -t
  systemctl reload nginx
else
  echo 'site certificate is not available; refusing to keep site over HTTP' >&2
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
expect_contains() {
  local label="`$1"
  local needle="`$2"
  shift 2
  local body
  body=`$(curl -fsS "`$@")
  if ! printf '%s' "`$body" | grep -Fq "`$needle"; then
    echo "`$label missing expected text: `$needle" >&2
    exit 21
  fi
  echo "`$label contains expected text"
}
expect_min_bytes() {
  local label="`$1"
  local min_bytes="`$2"
  shift 2
  local bytes
  bytes=`$(curl -fsS "`$@" | wc -c | awk '{print `$1}')
  echo "`$label bytes=`$bytes min=`$min_bytes"
  if [ "`$bytes" -lt "`$min_bytes" ]; then
    echo "`$label too small" >&2
    exit 22
  fi
}
if [ -f "`$cert_dir/fullchain.pem" ]; then
  expect_status "site-http-redirect" "301" -H "Host: `$domain" http://127.0.0.1/
  expect_status "site-http-legal-user-redirect" "301" -H "Host: `$domain" http://127.0.0.1/legal/user-agreement/
  expect_status "site-http-legal-privacy-redirect" "301" -H "Host: `$domain" http://127.0.0.1/legal/privacy-policy/
  expect_status "site-https-root" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/"
  expect_status "site-https-www" "200" -k --resolve "`$www_domain:443:127.0.0.1" "https://`$www_domain/"
  expect_status "site-https-gongan" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/gongan.png"
  expect_min_bytes "site-https-gongan" "100" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/gongan.png"
  expect_contains "site-root-icp" "2026031728" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/"
  expect_contains "site-root-gongan" "11010602202723" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/"
  expect_contains "site-root-mps-link" "beian.mps.gov.cn/#/query/webSearch?code=11010602202723" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/"
  expect_status "site-https-legal-user" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/user-agreement/"
  expect_status "site-https-legal-privacy" "200" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/privacy-policy/"
  expect_contains "site-legal-user-marker" "nongji-page-user-agreement" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/user-agreement/"
  expect_contains "site-legal-user-gongan" "11010602202723" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/user-agreement/"
  expect_contains "site-legal-privacy-marker" "nongji-page-privacy-policy" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/privacy-policy/"
  expect_contains "site-legal-privacy-gongan" "11010602202723" -k --resolve "`$domain:443:127.0.0.1" "https://`$domain/legal/privacy-policy/"
  expect_contains "site-www-legal-user-marker" "nongji-page-user-agreement" -k --resolve "`$www_domain:443:127.0.0.1" "https://`$www_domain/legal/user-agreement/"
  expect_contains "site-www-legal-privacy-marker" "nongji-page-privacy-policy" -k --resolve "`$www_domain:443:127.0.0.1" "https://`$www_domain/legal/privacy-policy/"
else
  echo 'site certificate missing after deploy' >&2
  exit 23
fi
"@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-site-deploy.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-site-deploy.sh",
    "--Timeout", "600"
)

Write-Host "Remote site deploy invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote site deploy failed: status=$($final.Status) exit=$($final.ExitCode)"
}
