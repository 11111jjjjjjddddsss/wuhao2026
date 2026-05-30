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
echo backups
find "`$install_dir" -maxdepth 1 -type f -name 'nongji-server.bak-*' -printf '%f\n' | sort -r | head -20
echo current
ls -l "`$install_dir/nongji-server"
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
install_dir='/opt/nongjiqiancha/server'
backup="`$install_dir/$BackupName"
if [ ! -f "`$backup" ]; then
  echo "backup not found: $BackupName" >&2
  exit 20
fi
echo rollback "$BackupName"
cp -a "`$install_dir/nongji-server" "`$install_dir/nongji-server.pre-rollback-`$(date +%Y%m%d%H%M%S)"
install -m 0755 -o nongji -g nongji "`$backup" "`$install_dir/nongji-server.new"
mv "`$install_dir/nongji-server.new" "`$install_dir/nongji-server"
systemctl restart nongji-server
systemctl is-active nongji-server
nginx -t
curl -sS -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz
echo
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
