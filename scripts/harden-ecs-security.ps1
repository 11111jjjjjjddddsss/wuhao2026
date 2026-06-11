param(
    [string]$RegionId = "cn-beijing",
    [string]$InstanceId = "i-2ze5nrem0jrchln4f0eh",
    [string]$SecurityGroupId = "sg-2ze4tilwxw1h5w77lwl1"
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
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 2
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

Write-Host "== security group =="
$sg = Invoke-JsonCommand @(
    "aliyun", "ecs", "DescribeSecurityGroupAttribute",
    "--RegionId", $RegionId,
    "--SecurityGroupId", $SecurityGroupId
)

$publicSshRules = @($sg.Permissions.Permission | Where-Object {
    $_.Direction -eq "ingress" -and
    $_.IpProtocol -eq "TCP" -and
    $_.PortRange -eq "22/22" -and
    ($_.SourceCidrIp -eq "0.0.0.0/0" -or $_.Ipv6SourceCidrIp -eq "::/0")
})

if ($publicSshRules.Count -eq 0) {
    Write-Host "public_ssh_22=closed"
} else {
    foreach ($rule in $publicSshRules) {
        if ([string]::IsNullOrWhiteSpace($rule.SecurityGroupRuleId)) {
            Write-Host "skip_public_ssh_rule_without_id"
            continue
        }
        Invoke-JsonCommand @(
            "aliyun", "ecs", "RevokeSecurityGroup",
            "--RegionId", $RegionId,
            "--SecurityGroupId", $SecurityGroupId,
            "--SecurityGroupRuleId.1", $rule.SecurityGroupRuleId
        ) | Out-Null
        Write-Host "revoked_public_ssh_rule=$($rule.SecurityGroupRuleId)"
    }
}

$remoteScript = @'
set -eu

cat >/etc/nginx/conf.d/nongjiqiancha-security.conf <<'EOF'
server_tokens off;
add_header X-Content-Type-Options nosniff always;
add_header X-Frame-Options DENY always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
add_header Strict-Transport-Security "max-age=15552000; includeSubDomains" always;
EOF

nginx -t
systemctl reload nginx

systemctl disable --now ssh 2>/dev/null || systemctl disable --now sshd 2>/dev/null || true

echo
echo '== nginx security conf =='
cat /etc/nginx/conf.d/nongjiqiancha-security.conf

echo
echo '== fail2ban =='
systemctl is-active fail2ban 2>/dev/null || true
fail2ban-client status 2>/dev/null || true
fail2ban-client status sshd 2>/dev/null || true

echo
echo '== cloud security agent =='
ps -ef | grep -Ei 'aegis|AliYunDun' | grep -v grep || true

echo
echo '== ssh service =='
systemctl is-active ssh 2>/dev/null || true
systemctl is-enabled ssh 2>/dev/null || true

echo
echo '== listening ports =='
ss -ltnp 2>/dev/null | grep -E '(:22|:80|:443|:3000|:3001)[[:space:]]' || true
'@

Send-CloudAssistantScriptFile -RegionId $RegionId -InstanceId $InstanceId -RemotePath "/tmp/nongji-security-harden.sh" -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", "bash /tmp/nongji-security-harden.sh",
    "--Timeout", "180"
)

Write-Host "Remote harden invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote harden failed: status=$($final.Status) exit=$($final.ExitCode)"
}

$sgAfter = Invoke-JsonCommand @(
    "aliyun", "ecs", "DescribeSecurityGroupAttribute",
    "--RegionId", $RegionId,
    "--SecurityGroupId", $SecurityGroupId
)
$openPorts = @($sgAfter.Permissions.Permission | Where-Object {
    $_.Direction -eq "ingress" -and $_.Policy -eq "Accept"
} | ForEach-Object {
    "$($_.IpProtocol):$($_.PortRange):$($_.SourceCidrIp)"
})

Write-Host
Write-Host "== ingress allow rules =="
$openPorts | Sort-Object | ForEach-Object { Write-Host $_ }
