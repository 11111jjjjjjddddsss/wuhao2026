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
    $raw = & $CommandArgs[0] @($CommandArgs[1..($CommandArgs.Length - 1)])
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $($CommandArgs -join ' ')"
    }
    return $raw | ConvertFrom-Json
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
$archiveInfo = Get-Item -LiteralPath $archive
Write-Host "Packaged $($archiveInfo.FullName) ($($archiveInfo.Length) bytes)"
Write-Host "SHA256 $sha256"

if ($PackageOnly) {
    Write-Host "PackageOnly set; skipping upload and remote deploy."
    exit 0
}

$bytes = [IO.File]::ReadAllBytes($archive)
$partCount = [Math]::Ceiling($bytes.Length / $ChunkSize)
$targetDir = "/tmp/nongji-deploy-chunks-$Commit"
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
    $status = Get-SendFileStatus $send.InvokeId
    if ($status.Status -ne "Success") {
        throw "SendFile failed for ${name}: $($status.Status) $($status.ErrorCode) $($status.ErrorInfo)"
    }
    Write-Host ("Uploaded {0}/{1}: {2}" -f ($i + 1), $partCount, $name)
}

$remoteScript = @"
set -euo pipefail
commit='$Commit'
expected_sha='$sha256'
chunks="/tmp/nongji-deploy-chunks-$Commit"
archive="/tmp/server-go-src-$Commit.tgz"
stage="/tmp/nongji-server-src-$Commit"
install_dir='/opt/nongjiqiancha/server'
bin_tmp="/tmp/nongji-server-$Commit"

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

echo test
go test ./...

echo build
go build -buildvcs=false -o "`$bin_tmp" ./cmd/server

echo install
install -m 0755 -o nongji -g nongji "`$bin_tmp" "`$install_dir/nongji-server.new"
if [ -f "`$install_dir/nongji-server" ]; then
  cp -a "`$install_dir/nongji-server" "`$install_dir/nongji-server.bak-`$(date +%Y%m%d%H%M%S)"
fi
mv "`$install_dir/nongji-server.new" "`$install_dir/nongji-server"
rm -rf "`$install_dir/assets" "`$install_dir/migrations"
cp -a "`$stage/assets" "`$install_dir/assets"
cp -a "`$stage/migrations" "`$install_dir/migrations"
cp "`$stage/go.mod" "`$install_dir/go.mod"
cp "`$stage/go.sum" "`$install_dir/go.sum"
chown -R nongji:nongji "`$install_dir/assets" "`$install_dir/migrations" "`$install_dir/go.mod" "`$install_dir/go.sum"

echo restart
systemctl restart nongji-server
systemctl is-active nongji-server

echo nginx
nginx -t

echo health
curl -sS -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz
echo
"@

$remoteBytes = [Text.Encoding]::UTF8.GetBytes(($remoteScript -replace "`r`n", "`n"))
$remoteBase64 = [Convert]::ToBase64String($remoteBytes)
$command = "printf '%s' '$remoteBase64' | base64 -d >/tmp/nongji-deploy-$Commit.sh && bash /tmp/nongji-deploy-$Commit.sh"
$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $RegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $InstanceId,
    "--CommandContent", $command,
    "--Timeout", "600"
)

Write-Host "Remote deploy invoke: $($run.InvokeId)"
$final = Wait-RunCommand $run.InvokeId
Write-Host $final.Output
if ($final.Status -ne "Success" -or $final.ExitCode -ne 0) {
    throw "Remote deploy failed: status=$($final.Status) exit=$($final.ExitCode)"
}
