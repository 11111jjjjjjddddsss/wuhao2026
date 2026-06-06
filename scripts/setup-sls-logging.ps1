param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$ProjectName = "nongjiqiancha-prod-1159547719787456",
    [string]$PrivateIP = "192.168.1.237",
    [string]$PublicIP = "39.106.1.151",
    [int]$TTL = 7
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

function Test-SlsResourceExists {
    param([scriptblock]$Command)
    try {
        & $Command | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Ensure-Project {
    if (Test-SlsResourceExists { Invoke-JsonCommand @("aliyun", "sls", "get-project", "--region", $RegionId, "--project", $ProjectName) }) {
        Write-Host "project=exists:$ProjectName"
        return
    }
    Invoke-JsonCommand @(
        "aliyun", "sls", "create-project",
        "--region", $RegionId,
        "--project-name", $ProjectName,
        "--description", "Nongji Qiancha production logs",
        "--data-redundancy-type", "LRS",
        "--recycle-bin-enabled", "true"
    ) | Out-Null
    Write-Host "project=created:$ProjectName"
}

function Ensure-Logstore {
    param([string]$LogstoreName)
    if (Test-SlsResourceExists { Invoke-JsonCommand @("aliyun", "sls", "get-log-store", "--region", $RegionId, "--project", $ProjectName, "--logstore", $LogstoreName) }) {
        Write-Host "logstore=exists:$LogstoreName"
        return
    }
    Invoke-JsonCommand @(
        "aliyun", "sls", "create-log-store",
        "--region", $RegionId,
        "--project", $ProjectName,
        "--logstore-name", $LogstoreName,
        "--shard-count", "1",
        "--ttl", "$TTL",
        "--append-meta", "false",
        "--biz-mode", "standard"
    ) | Out-Null
    Write-Host "logstore=created:$LogstoreName"
}

function Ensure-Index {
    param(
        [string]$LogstoreName,
        [string]$KeysJson
    )
    if (Test-SlsResourceExists { Invoke-JsonCommand @("aliyun", "sls", "get-index", "--region", $RegionId, "--project", $ProjectName, "--logstore", $LogstoreName) }) {
        Write-Host "index=exists:$LogstoreName"
        return
    }
    $lineJson = '{"caseSensitive":false,"chn":false,"token":[","," ","''","\"",";","=", "(",")","[","]","{","}","?","@","<","&","/",":","\n","\t","\r"]}'
    Invoke-JsonCommand @(
        "aliyun", "sls", "create-index",
        "--region", $RegionId,
        "--project", $ProjectName,
        "--logstore", $LogstoreName,
        "--line", $lineJson,
        "--keys", $KeysJson
    ) | Out-Null
    Write-Host "index=created:$LogstoreName"
}

function Ensure-MachineGroup {
    $groupName = "nongjiqiancha-prod-ecs"
    if (Test-SlsResourceExists { Invoke-JsonCommand @("aliyun", "sls", "get-machine-group", "--region", $RegionId, "--project", $ProjectName, "--machine-group", $groupName) }) {
        Write-Host "machine_group=exists:$groupName"
        return $groupName
    }
    Invoke-JsonCommand @(
        "aliyun", "sls", "create-machine-group",
        "--region", $RegionId,
        "--project", $ProjectName,
        "--group-name", $groupName,
        "--machine-identify-type", "ip",
        "--machine-list", $PrivateIP, $PublicIP
    ) | Out-Null
    Write-Host "machine_group=created:$groupName"
    return $groupName
}

function Ensure-Config {
    param(
        [string]$ConfigName,
        [string]$LogstoreName,
        [string]$InputDetailJson
    )
    $outputDetailJson = "{`"endpoint`":`"$RegionId.log.aliyuncs.com`",`"logstoreName`":`"$LogstoreName`",`"region`":`"$RegionId`",`"telemetryType`":`"None`"}"
    if (Test-SlsResourceExists { Invoke-JsonCommand @("aliyun", "sls", "get-config", "--region", $RegionId, "--project", $ProjectName, "--config-name", $ConfigName) }) {
        Invoke-JsonCommand @(
            "aliyun", "sls", "update-config",
            "--region", $RegionId,
            "--project", $ProjectName,
            "--config-name", $ConfigName,
            "--input-type", "file",
            "--input-detail", $InputDetailJson,
            "--output-type", "LogService",
            "--output-detail", $outputDetailJson
        ) | Out-Null
        Write-Host "config=updated:$ConfigName"
        return
    }
    Invoke-JsonCommand @(
        "aliyun", "sls", "create-config",
        "--region", $RegionId,
        "--project", $ProjectName,
        "--config-name", $ConfigName,
        "--input-type", "file",
        "--input-detail", $InputDetailJson,
        "--output-type", "LogService",
        "--output-detail", $outputDetailJson
    ) | Out-Null
    Write-Host "config=created:$ConfigName"
}

function Apply-Config {
    param(
        [string]$ConfigName,
        [string]$MachineGroup
    )
    try {
        Invoke-JsonCommand @(
            "aliyun", "sls", "apply-config-to-machine-group",
            "--region", $RegionId,
            "--project", $ProjectName,
            "--machine-group", $MachineGroup,
            "--config-name", $ConfigName
        ) | Out-Null
        Write-Host "config_applied=$ConfigName"
    } catch {
        if ($_.Exception.Message -match "Already|already|exist|Exists") {
            Write-Host "config_applied=already:$ConfigName"
            return
        }
        throw
    }
}

function Wait-RunCommand {
    param([string]$InvokeId)
    for ($i = 0; $i -lt 50; $i++) {
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

function Ensure-RemoteLogtail {
    $remoteScript = @'
set -eu

mkdir -p /var/log/nongjiqiancha
chown nongji:nongji /var/log/nongjiqiancha
touch /var/log/nongjiqiancha/server.log
chown nongji:nongji /var/log/nongjiqiancha/server.log
chmod 0640 /var/log/nongjiqiancha/server.log

env_file=/etc/nongjiqiancha/server.env
if [ -f "$env_file" ]; then
  if grep -qE '^[[:space:]]*(export[[:space:]]+)?LOG_FILE_PATH=' "$env_file"; then
    sed -i -E 's#^[[:space:]]*(export[[:space:]]+)?LOG_FILE_PATH=.*#LOG_FILE_PATH=/var/log/nongjiqiancha/server.log#' "$env_file"
  else
    printf '\nLOG_FILE_PATH=/var/log/nongjiqiancha/server.log\n' >> "$env_file"
  fi
  if grep -qE '^[[:space:]]*(export[[:space:]]+)?ACCESS_LOG_SLOW_MS=' "$env_file"; then
    sed -i -E 's#^[[:space:]]*(export[[:space:]]+)?ACCESS_LOG_SLOW_MS=.*#ACCESS_LOG_SLOW_MS=3000#' "$env_file"
  else
    printf 'ACCESS_LOG_SLOW_MS=3000\n' >> "$env_file"
  fi
fi

cat >/etc/logrotate.d/nongjiqiancha-server <<'EOF'
/var/log/nongjiqiancha/server.log {
  daily
  rotate 7
  missingok
  notifempty
  compress
  copytruncate
  create 0640 nongji nongji
}
EOF

if [ ! -x /usr/local/ilogtail/ilogtail ]; then
  curl -fsSL https://logtail-release-cn-beijing.oss-cn-beijing.aliyuncs.com/linux64/logtail.sh -o /tmp/logtail.sh
  chmod +x /tmp/logtail.sh
  /tmp/logtail.sh install cn-beijing
fi

systemctl enable ilogtaild >/dev/null 2>&1 || true
systemctl restart ilogtaild 2>/dev/null || /etc/init.d/ilogtaild restart 2>/dev/null || true
sleep 2

echo '== ilogtail =='
ps -ef | grep -Ei 'ilogtail|logtail|loongcollector' | grep -v grep || true
systemctl is-active ilogtaild 2>/dev/null || true
echo '== log dir =='
ls -ld /var/log/nongjiqiancha
ls -l /var/log/nongjiqiancha/server.log
'@
    $remoteBytes = [Text.Encoding]::UTF8.GetBytes(($remoteScript -replace "`r`n", "`n"))
    $remoteBase64 = [Convert]::ToBase64String($remoteBytes)
    $command = "printf '%s' '$remoteBase64' | base64 -d >/tmp/nongji-setup-logtail.sh && bash /tmp/nongji-setup-logtail.sh"
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $RegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $InstanceId,
        "--CommandContent", $command,
        "--Timeout", "300"
    )
    Write-Host "Remote SLS setup invoke: $($run.InvokeId)"
    $final = Wait-RunCommand $run.InvokeId
    Write-Host $final.Output
    if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
        throw "Remote SLS setup failed: status=$($final.Status) exit=$($final.ExitCode)"
    }
}

Ensure-Project
Ensure-Logstore "server-go"
Ensure-Logstore "nginx-error"

$textField = '{"doc_value":true,"alias":"","type":"text","caseSensitive":false,"chn":false,"token":[","," ","''","\"",";","=","(",")","[","]","{","}","?","@","<","&","/",":","\n","\t","\r"]}'
$serverKeys = "{`"level`":$textField,`"msg`":$textField,`"request_id`":$textField,`"method`":$textField,`"path`":$textField,`"status`":{`"doc_value`":true,`"alias`":`"`",`"type`":`"long`"},`"duration_ms`":{`"doc_value`":true,`"alias`":`"`",`"type`":`"long`"},`"user_id`":$textField,`"auth_mode`":$textField,`"masked_ip`":$textField}"
$nginxKeys = "{`"content`":$textField}"
Ensure-Index "server-go" $serverKeys
Ensure-Index "nginx-error" $nginxKeys

$machineGroup = Ensure-MachineGroup

$serverInput = '{"logType":"json_log","logPath":"/var/log/nongjiqiancha","filePattern":"server.log","topicFormat":"none","discardUnmatch":true,"enableRawLog":false,"localStorage":true,"tailExisted":true,"timeKey":"","timeFormat":""}'
$nginxInput = '{"logType":"common_reg_log","logPath":"/var/log/nginx","filePattern":"error.log","regex":"(.*)","key":["content"],"topicFormat":"none","localStorage":true,"tailExisted":true,"timeKey":"","timeFormat":""}'
Ensure-Config "server-go-json" "server-go" $serverInput
Ensure-Config "nginx-error-raw" "nginx-error" $nginxInput
Apply-Config "server-go-json" $machineGroup
Apply-Config "nginx-error-raw" $machineGroup
Ensure-RemoteLogtail

Write-Host
Write-Host "== connected machines =="
try {
    Invoke-JsonCommand @("aliyun", "sls", "list-machines", "--region", $RegionId, "--project", $ProjectName, "--machine-group", $machineGroup) | ConvertTo-Json -Depth 20
} catch {
    Write-Host "list-machines failed; Logtail may need a few minutes to register."
    Write-Host $_.Exception.Message
}
