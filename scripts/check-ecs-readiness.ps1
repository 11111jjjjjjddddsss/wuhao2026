param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh"
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

$remoteScript = @'
set -u
env_file='/etc/nongjiqiancha/server.env'

echo '== service =='
systemctl is-active nongji-server || true
systemctl --no-pager --full status nongji-server | sed -n '1,8p' || true

echo
echo '== nginx =='
nginx -t 2>&1 || true

echo
echo '== healthz =='
health_body='/tmp/nongji-readiness-health.json'
health_status=$(curl -sS -o "$health_body" -w '%{http_code}' -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz || true)
echo "http_status=$health_status"
cat "$health_body" 2>/dev/null || true
echo

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

for key in \
  APP_ENV AUTH_STRICT APP_SECRET MYSQL_URL MYSQL_MAX_OPEN_CONNS MYSQL_MAX_IDLE_CONNS BASE_PUBLIC_URL UPLOAD_BASE_URL \
  UPLOAD_STORAGE_BACKEND OSS_BUCKET OSS_ENDPOINT OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET \
  DASHSCOPE_API_KEY DASHSCOPE_API_KEY_1 DASHSCOPE_API_KEY_2 DASHSCOPE_API_KEY_3 DASHSCOPE_API_KEYS DASHSCOPE_KEY_COOLDOWN_SECONDS \
  DASHSCOPE_KEY_SELECTION_MODE DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS \
  DYPNS_ACCESS_KEY_ID DYPNS_ACCESS_KEY_SECRET DYPNS_FUSION_SCHEME_CODE \
  DYPNS_SMS_SIGN_NAME DYPNS_SMS_TEMPLATE_CODE \
  REDIS_ADDR REDIS_USERNAME REDIS_PASSWORD \
  SUPPORT_ADMIN_SECRET DAILY_AGRI_JOB_SECRET; do
  check_env "$key"
done

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
ss -ltnp 2>/dev/null | grep -E '(:80|:3000)[[:space:]]' || true
'@

$remoteBytes = [Text.Encoding]::UTF8.GetBytes(($remoteScript -replace "`r`n", "`n"))
$remoteBase64 = [Convert]::ToBase64String($remoteBytes)
$command = "printf '%s' '$remoteBase64' | base64 -d >/tmp/nongji-readiness-check.sh && bash /tmp/nongji-readiness-check.sh"

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $command,
    "--Timeout", "180"
)

Write-Host "Readiness check invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Readiness check failed: status=$($final.Status) exit=$($final.ExitCode)"
}
