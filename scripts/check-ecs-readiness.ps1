param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$ExpectedRevision = ""
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
    for ($i = 0; $i -lt 36; $i++) {
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

$remoteScript = @'
set -u
env_file='/etc/nongjiqiancha/server.env'
nginx_site='/etc/nginx/sites-available/nongjiqiancha-api'
admin_nginx_site='/etc/nginx/sites-available/nongjiqiancha-admin'

echo '== service =='
for svc in nongji-server nongji-server-3000 nongji-server-3001; do
  state=$(systemctl is-active "$svc" 2>/dev/null || true)
  enabled=$(systemctl is-enabled "$svc" 2>/dev/null || true)
  echo "$svc state=${state:-unknown} enabled=${enabled:-unknown}"
done

echo
echo '== nginx =='
if ! nginx -t 2>&1; then
  echo 'nginx configuration test failed' >&2
  exit 17
fi
extract_unique_upstream_port() {
  site="$1"
  label="$2"
  ports=$(grep -oE 'proxy_pass http://127\.0\.0\.1:(3000|3001);' "$site" 2>/dev/null | sed -E 's/.*:([0-9]+);/\1/' | sort -u || true)
  count=$(printf '%s\n' "$ports" | sed '/^$/d' | wc -l | tr -d ' ')
  if [ "$count" = "0" ]; then
    printf 'unknown'
    return 0
  fi
  if [ "$count" != "1" ]; then
    echo "${label} has mixed upstream ports: $(printf '%s' "$ports" | tr '\n' ' ')" >&2
    exit 13
  fi
  printf '%s' "$ports" | sed '/^$/d'
}
active_port=$(extract_unique_upstream_port "$nginx_site" "api nginx")
if [ -z "$active_port" ]; then
  active_port=unknown
fi
echo "active_upstream_port=$active_port"
if [ "$active_port" != "unknown" ]; then
  active_service="nongji-server-${active_port}"
  active_state=$(systemctl is-active "$active_service" 2>/dev/null || true)
  echo "active_upstream_service=$active_service state=${active_state:-unknown}"
  if [ "$active_state" != "active" ]; then
    echo "active upstream service is not active: $active_service" >&2
    exit 10
  fi
fi
if [ -f "$admin_nginx_site" ]; then
  admin_port=$(extract_unique_upstream_port "$admin_nginx_site" "admin nginx")
  if [ -z "$admin_port" ]; then
    admin_port=unknown
  fi
  echo "admin_upstream_port=$admin_port"
  if [ "$active_port" != "unknown" ] && [ "$admin_port" != "$active_port" ]; then
    echo "admin upstream port does not match api active port: admin=$admin_port api=$active_port" >&2
    exit 13
  fi
fi

echo
echo '== nginx proxy headers =='
check_proxy_headers() {
  site="$1"
  label="$2"
  if [ ! -f "$site" ]; then
    echo "${label}=missing"
    return 0
  fi
  real_ip_count=$(grep -Ec '^[[:space:]]*proxy_set_header[[:space:]]+X-Real-IP[[:space:]]+\$remote_addr[[:space:]]*;' "$site" || true)
  xff_remote_count=$(grep -Ec '^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-For[[:space:]]+\$remote_addr[[:space:]]*;' "$site" || true)
  xff_append_count=$(grep -Ec '^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-For[[:space:]]+\$proxy_add_x_forwarded_for[[:space:]]*;' "$site" || true)
  echo "${label}_x_real_ip_remote_addr=$real_ip_count"
  echo "${label}_x_forwarded_for_remote_addr=$xff_remote_count"
  echo "${label}_x_forwarded_for_append=$xff_append_count"
  if [ "$real_ip_count" -lt 1 ]; then
    echo "${label} missing proxy_set_header X-Real-IP \$remote_addr" >&2
    exit 18
  fi
  if [ "$xff_remote_count" -lt 1 ]; then
    echo "${label} missing proxy_set_header X-Forwarded-For \$remote_addr" >&2
    exit 18
  fi
  if [ "$xff_append_count" -ne 0 ]; then
    echo "${label} must not use proxy_add_x_forwarded_for in current single-ECS direct-public topology" >&2
    exit 18
  fi
}
check_proxy_headers "$nginx_site" "api"
if [ -f "$admin_nginx_site" ]; then
  check_proxy_headers "$admin_nginx_site" "admin"
fi

echo
echo '== nginx traffic guards =='
rate_conf='/etc/nginx/conf.d/nongjiqiancha-rate-limit.conf'
require_nginx_pattern() {
  file="$1"
  label="$2"
  pattern="$3"
  if [ ! -f "$file" ]; then
    echo "${label}=missing_file:$file" >&2
    exit 19
  fi
  count=$(grep -Ec "$pattern" "$file" || true)
  echo "${label}=$count"
  if [ "$count" -lt 1 ]; then
    echo "${label} missing expected nginx guard pattern: $pattern" >&2
    exit 19
  fi
}
require_nginx_pattern "$rate_conf" "nginx_rate_zone_api_60rpm" 'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=nongji_api:10m[[:space:]]+rate=60r/m;'
require_nginx_pattern "$rate_conf" "nginx_rate_zone_chat_60rpm" 'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=nongji_chat:10m[[:space:]]+rate=60r/m;'
require_nginx_pattern "$rate_conf" "nginx_rate_zone_upload_20rpm" 'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=nongji_upload:10m[[:space:]]+rate=20r/m;'
require_nginx_pattern "$rate_conf" "nginx_conn_zone" 'limit_conn_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=nongji_conn:10m;'
require_nginx_pattern "$nginx_site" "nginx_client_max_body_2m" 'client_max_body_size[[:space:]]+2m;'
require_nginx_pattern "$nginx_site" "nginx_chat_limit_req" 'limit_req[[:space:]]+zone=nongji_chat[[:space:]]+burst=80[[:space:]]+nodelay;'
require_nginx_pattern "$nginx_site" "nginx_upload_limit_req" 'limit_req[[:space:]]+zone=nongji_upload[[:space:]]+burst=8[[:space:]]+nodelay;'
require_nginx_pattern "$nginx_site" "nginx_upload_limit_conn" 'limit_conn[[:space:]]+nongji_conn[[:space:]]+4;'
require_nginx_pattern "$nginx_site" "nginx_api_limit_req" 'limit_req[[:space:]]+zone=nongji_api[[:space:]]+burst=80[[:space:]]+nodelay;'
require_nginx_pattern "$nginx_site" "nginx_api_limit_conn" 'limit_conn[[:space:]]+nongji_conn[[:space:]]+20;'
require_nginx_pattern "$nginx_site" "nginx_chat_proxy_buffering_off" 'proxy_buffering[[:space:]]+off;'
require_nginx_pattern "$nginx_site" "nginx_chat_read_timeout_600s" 'proxy_read_timeout[[:space:]]+600s;'
chat_block=$(awk '/^[[:space:]]*location[[:space:]]+\/api\/chat\/stream[[:space:]]*\{/{flag=1} flag{print} flag && /^[[:space:]]*\}/{exit}' "$nginx_site" 2>/dev/null || true)
chat_limit_conn_count=$(printf '%s\n' "$chat_block" | grep -Ec 'limit_conn[[:space:]]+' || true)
echo "nginx_chat_limit_conn_count=$chat_limit_conn_count"
if [ "$chat_limit_conn_count" -ne 0 ]; then
  echo 'chat stream location must not use limit_conn in current shared-network friendly policy' >&2
  exit 19
fi

echo
echo '== healthz =='
health_body='/tmp/nongji-readiness-health.json'
health_status=$(curl -sS --resolve api.nongjiqiancha.cn:443:127.0.0.1 -o "$health_body" -w '%{http_code}' https://api.nongjiqiancha.cn/healthz || true)
echo "https_status=$health_status"
cat "$health_body" 2>/dev/null || true
echo
if [ "$health_status" != "200" ]; then
  echo "healthz is not ready: $health_status" >&2
  exit 11
fi
for expected in \
  '"ok":true' \
  '"auth_strict":true' \
  '"bailian":"ok"' \
  '"sms":"ok"' \
  '"dev_order_endpoints":false' \
  '"redis":"ok"' \
  '"upload_storage":"oss"'; do
  if ! grep -q "$expected" "$health_body" 2>/dev/null; then
    echo "healthz missing expected marker: $expected" >&2
    exit 12
  fi
done
server_revision=$(sed -nE 's/.*"revision":"([^"]*)".*/\1/p' "$health_body" 2>/dev/null | head -n 1)
if [ -z "$server_revision" ]; then
  echo 'server_revision=missing'
  echo 'healthz revision is missing' >&2
  exit 12
else
  echo "server_revision=$server_revision"
fi

echo
echo '== admin-api =='
admin_body='/tmp/nongji-readiness-admin-auth-me.json'
admin_status=$(curl -sS --resolve admin.nongjiqiancha.cn:443:127.0.0.1 -o "$admin_body" -w '%{http_code}' https://admin.nongjiqiancha.cn/admin-api/v1/auth/me || true)
echo "admin_auth_me_status=$admin_status"
cat "$admin_body" 2>/dev/null || true
echo
if [ "$admin_status" != "401" ]; then
  echo "admin auth/me expected 401 but got $admin_status" >&2
  exit 14
fi

echo
echo '== env readiness (values redacted) =='
check_env() {
  key="$1"
  if [ ! -f "$env_file" ]; then
    echo "$key=missing_env_file"
    return
  fi
  line=$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$env_file" | tail -n 1 || true)
  if [ -z "$line" ]; then
    echo "$key=missing"
    return
  fi
  value=$(printf '%s' "$line" | sed -E "s/^[[:space:]]*(export[[:space:]]+)?${key}=//" | sed -E "s/^['\"]|['\"]$//g")
  if [ -z "$value" ]; then
    echo "$key=empty"
  else
    echo "$key=set"
  fi
}

env_value() {
  key="$1"
  if [ ! -f "$env_file" ]; then
    return 1
  fi
  line=$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$env_file" | tail -n 1 || true)
  if [ -z "$line" ]; then
    return 1
  fi
  printf '%s' "$line" | sed -E "s/^[[:space:]]*(export[[:space:]]+)?${key}=//" | sed -E "s/^['\"]|['\"]$//g"
}

is_truthy() {
  value=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | xargs)
  [ "$value" = "1" ] || [ "$value" = "true" ] || [ "$value" = "yes" ] || [ "$value" = "on" ]
}

is_falsey() {
  value=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | xargs)
  [ "$value" = "0" ] || [ "$value" = "false" ] || [ "$value" = "no" ] || [ "$value" = "off" ]
}

for key in \
  APP_ENV ENV GO_ENV ADMIN_COOKIE_SECURE AUTH_STRICT AUTH_ALLOW_LEGACY_TOKEN AUTH_ALLOW_UNPROVEN_LEGACY_UUID APP_SECRET MYSQL_URL MYSQL_MAX_OPEN_CONNS MYSQL_MAX_IDLE_CONNS BASE_PUBLIC_URL UPLOAD_BASE_URL \
  LOG_FILE_PATH ACCESS_LOG_SLOW_MS \
  IP2REGION_V4_XDB_PATH IP2REGION_V6_XDB_PATH IP2REGION_XDB_PATH \
  LISTEN_ADDR LISTEN_HOST PORT \
  ALLOW_DEV_ORDER_ENDPOINTS \
  UPLOAD_STORAGE_BACKEND OSS_BUCKET OSS_ENDPOINT OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET \
  DASHSCOPE_API_KEY DASHSCOPE_API_KEY_1 DASHSCOPE_API_KEY_2 DASHSCOPE_API_KEY_3 DASHSCOPE_API_KEYS DASHSCOPE_KEY_COOLDOWN_SECONDS \
  DASHSCOPE_KEY_SELECTION_MODE DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS DASHSCOPE_AUTO_ROUND_ROBIN_TOKEN_THRESHOLD DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS \
  APP_ANDROID_UPDATE_ENABLED APP_UPDATE_ALLOW_FORCE_UPDATE APP_ANDROID_LATEST_VERSION_CODE APP_ANDROID_APK_URL APP_ANDROID_APK_SHA256 APP_ANDROID_FILE_SIZE_BYTES \
  DYPNS_ACCESS_KEY_ID DYPNS_ACCESS_KEY_SECRET DYPNS_FUSION_SCHEME_CODE \
  AUTH_FUSION_COMPAT_ENABLED \
  SMS_ACCESS_KEY_ID SMS_ACCESS_KEY_SECRET SMS_SIGN_NAME SMS_TEMPLATE_CODE \
  DYPNS_SMS_SIGN_NAME DYPNS_SMS_TEMPLATE_CODE \
  REDIS_ADDR REDIS_USERNAME REDIS_PASSWORD \
  SUPPORT_ADMIN_SECRET DAILY_AGRI_JOB_SECRET \
  ADMIN_BOOTSTRAP_USERNAME ADMIN_BOOTSTRAP_PASSWORD ADMIN_BOOTSTRAP_ROLE ADMIN_BOOTSTRAP_DISPLAY_NAME ADMIN_BOOTSTRAP_UPDATE_EXISTING; do
  check_env "$key"
done

auth_allow_legacy_token=$(env_value AUTH_ALLOW_LEGACY_TOKEN || true)
if is_truthy "$auth_allow_legacy_token"; then
  echo 'AUTH_ALLOW_LEGACY_TOKEN must not be enabled in production readiness' >&2
  exit 15
fi

auth_allow_unproven_legacy_uuid=$(env_value AUTH_ALLOW_UNPROVEN_LEGACY_UUID || true)
if is_truthy "$auth_allow_unproven_legacy_uuid"; then
  echo 'AUTH_ALLOW_UNPROVEN_LEGACY_UUID must not be enabled in production readiness' >&2
  exit 15
fi

allow_dev_order_endpoints=$(env_value ALLOW_DEV_ORDER_ENDPOINTS || true)
if is_truthy "$allow_dev_order_endpoints"; then
  echo 'ALLOW_DEV_ORDER_ENDPOINTS must not be enabled in production readiness' >&2
  exit 16
fi

auth_fusion_compat_enabled=$(env_value AUTH_FUSION_COMPAT_ENABLED || true)
if is_truthy "$auth_fusion_compat_enabled"; then
  echo 'AUTH_FUSION_COMPAT_ENABLED must not be enabled in production readiness' >&2
  exit 16
fi

app_android_update_enabled=$(env_value APP_ANDROID_UPDATE_ENABLED || true)
if is_truthy "$app_android_update_enabled"; then
  echo 'APP_ANDROID_UPDATE_ENABLED must stay disabled in normal production readiness; use the dedicated release-match script only after an explicit release command' >&2
  exit 16
fi

app_update_allow_force_update=$(env_value APP_UPDATE_ALLOW_FORCE_UPDATE || true)
if is_truthy "$app_update_allow_force_update"; then
  echo 'APP_UPDATE_ALLOW_FORCE_UPDATE must not be enabled in production readiness' >&2
  exit 16
fi

runtime_env=$(printf '%s' "$(env_value APP_ENV || env_value ENV || env_value GO_ENV || true)" | tr '[:upper:]' '[:lower:]' | xargs)
admin_cookie_secure=$(env_value ADMIN_COOKIE_SECURE || true)
if is_falsey "$admin_cookie_secure"; then
  echo 'ADMIN_COOKIE_SECURE is explicitly false; production admin cookies must stay Secure over HTTPS' >&2
  exit 16
fi
if [ -n "$admin_cookie_secure" ] && ! is_truthy "$admin_cookie_secure"; then
  echo 'ADMIN_COOKIE_SECURE has an invalid value; use true/yes/on/1 or leave it unset in APP_ENV=production' >&2
  exit 16
fi
if [ "$runtime_env" != "prod" ] && [ "$runtime_env" != "production" ] && ! is_truthy "$admin_cookie_secure"; then
  echo 'Admin cookies must be Secure in production readiness; set APP_ENV=production with ADMIN_COOKIE_SECURE unset, or set ADMIN_COOKIE_SECURE=true' >&2
  exit 16
fi

for key in ADMIN_BOOTSTRAP_USERNAME ADMIN_BOOTSTRAP_PASSWORD ADMIN_BOOTSTRAP_ROLE ADMIN_BOOTSTRAP_DISPLAY_NAME ADMIN_BOOTSTRAP_UPDATE_EXISTING; do
  value=$(env_value "$key" || true)
  if [ -n "$value" ]; then
    echo "${key} must be removed after production admin bootstrap" >&2
    exit 17
  fi
done

echo
echo '== ip2region data =='
ip2region_v4_path=''
if [ -f "$env_file" ]; then
  ip2region_v4_line=$(grep -E '^[[:space:]]*(export[[:space:]]+)?(IP2REGION_V4_XDB_PATH|IP2REGION_XDB_PATH)=' "$env_file" | tail -n 1 || true)
  if [ -n "$ip2region_v4_line" ]; then
    ip2region_v4_path=$(printf '%s' "$ip2region_v4_line" | sed -E 's/^[[:space:]]*(export[[:space:]]+)?(IP2REGION_V4_XDB_PATH|IP2REGION_XDB_PATH)=//' | sed -E "s/^['\"]|['\"]$//g")
  fi
fi
if [ -z "$ip2region_v4_path" ]; then
  echo 'v4_xdb=missing_path'
elif [ -r "$ip2region_v4_path" ]; then
  size=$(wc -c < "$ip2region_v4_path" 2>/dev/null || true)
  echo "v4_xdb=present readable=true bytes=${size:-unknown}"
else
  echo 'v4_xdb=present readable=false'
fi

echo
echo '== local upload dir =='
if [ -d /var/lib/nongjiqiancha/uploads ]; then
  find /var/lib/nongjiqiancha/uploads -maxdepth 1 -type f | wc -l | awk '{print "files="$1}'
  du -sh /var/lib/nongjiqiancha/uploads 2>/dev/null || true
else
  echo 'missing'
fi

echo
echo '== ports =='
ss -ltnp 2>/dev/null | grep -E '(:80|:443|:3000|:3001)[[:space:]]' || true
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-readiness-check.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-readiness-check.sh",
    "--Timeout", "180"
)

Write-Host "Readiness check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Readiness check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
if (-not [string]::IsNullOrWhiteSpace($ExpectedRevision)) {
    $actualRevision = [regex]::Match($final.Output, "(?m)^server_revision=([^\s]+)\s*$").Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($actualRevision)) {
        throw "Readiness check did not report server_revision; expected $ExpectedRevision"
    }
    if ($actualRevision -ne $ExpectedRevision) {
        throw "Readiness revision mismatch: expected=$ExpectedRevision actual=$actualRevision"
    }
    Write-Host "expected_revision_match=true"
}
