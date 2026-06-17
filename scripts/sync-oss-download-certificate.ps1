param(
    [string]$Domain = "download.nongjiqiancha.cn",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh"
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "required command not found: $Name"
    }
}

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
        $arguments += @("--connect-timeout", "20", "--read-timeout", "120", "--retry-count", "3")
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
    param(
        [string]$RegionId,
        [string]$InvokeId
    )
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId
        )
        $item = @($result.Invocation.InvocationResults.InvocationResult)[0]
        if ($item.InvokeRecordStatus -eq "Finished") {
            $decoded = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($item.Output))
            return [pscustomobject]@{
                Status = $item.InvocationStatus
                ExitCode = [int]$item.ExitCode
                Output = $decoded
            }
        }
    }
    throw "Timed out waiting for RunCommand $InvokeId"
}

function New-XmlCData {
    param([System.Xml.XmlDocument]$Document, [System.Xml.XmlElement]$Parent, [string]$Name, [string]$Value)
    $element = $Document.CreateElement($Name)
    $element.AppendChild($Document.CreateCDataSection($Value)) | Out-Null
    $Parent.AppendChild($element) | Out-Null
}

Require-Command "aliyun"
. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

Write-Host "== sync OSS download certificate =="
Write-Host "download_domain=$Domain"
Write-Host "oss_bucket=$Bucket"
Write-Host "oss_endpoint=$Endpoint"

$remoteScript = @"
set -euo pipefail
domain='$Domain'
cert_dir="/etc/letsencrypt/live/`$domain"
if [ ! -s "`$cert_dir/fullchain.pem" ] || [ ! -s "`$cert_dir/privkey.pem" ]; then
  echo "certificate files not found for `$domain" >&2
  exit 11
fi
openssl x509 -in "`$cert_dir/fullchain.pem" -noout -subject -issuer -dates
echo "CERT_B64_BEGIN"
base64 -w0 "`$cert_dir/fullchain.pem"
echo
echo "CERT_B64_END"
echo "KEY_B64_BEGIN"
base64 -w0 "`$cert_dir/privkey.pem"
echo
echo "KEY_B64_END"
"@

$remoteScriptPath = "/tmp/nongji-sync-oss-download-cert-$Domain.sh"
Send-CloudAssistantScriptFile -RegionId $EcsRegionId -InstanceId $EcsInstanceId -RemotePath $remoteScriptPath -ScriptText $remoteScript -TimeoutSeconds 120 | Out-Null

$run = Invoke-JsonCommand @(
    "aliyun", "ecs", "RunCommand",
    "--RegionId", $EcsRegionId,
    "--Type", "RunShellScript",
    "--InstanceId.1", $EcsInstanceId,
    "--Name", "sync-oss-download-cert-$Domain",
    "--CommandContent", "bash $remoteScriptPath",
    "--Timeout", "120"
)
$result = Wait-RunCommand -RegionId $EcsRegionId -InvokeId $run.InvokeId
if ($result.Status -ne "Success" -or $result.ExitCode -ne 0) {
    throw "failed to read certificate from ECS: status=$($result.Status) exit=$($result.ExitCode)"
}

$output = $result.Output
foreach ($line in ($output -split "`n")) {
    if ($line -match '^(subject=|issuer=|notBefore=|notAfter=)') {
        Write-Host ("certificate_" + $line)
    }
}

$certMatch = [regex]::Match($output, '(?s)CERT_B64_BEGIN\s*(?<value>[A-Za-z0-9+/=\r\n]+)\s*CERT_B64_END')
$keyMatch = [regex]::Match($output, '(?s)KEY_B64_BEGIN\s*(?<value>[A-Za-z0-9+/=\r\n]+)\s*KEY_B64_END')
if (-not $certMatch.Success -or -not $keyMatch.Success) {
    throw "could not parse certificate payload from ECS output"
}
$certPem = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($certMatch.Groups["value"].Value -replace '\s+', '')))
$keyPem = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($keyMatch.Groups["value"].Value -replace '\s+', '')))

$xml = New-Object System.Xml.XmlDocument
$root = $xml.CreateElement("BucketCnameConfiguration")
$xml.AppendChild($root) | Out-Null
$cname = $xml.CreateElement("Cname")
$root.AppendChild($cname) | Out-Null
$domainNode = $xml.CreateElement("Domain")
$domainNode.InnerText = $Domain
$cname.AppendChild($domainNode) | Out-Null
$certificate = $xml.CreateElement("CertificateConfiguration")
$cname.AppendChild($certificate) | Out-Null
New-XmlCData -Document $xml -Parent $certificate -Name "Certificate" -Value $certPem
New-XmlCData -Document $xml -Parent $certificate -Name "PrivateKey" -Value $keyPem
$force = $xml.CreateElement("Force")
$force.InnerText = "true"
$certificate.AppendChild($force) | Out-Null
$deleteCert = $xml.CreateElement("DeleteCertificate")
$deleteCert.InnerText = "false"
$certificate.AppendChild($deleteCert) | Out-Null

$tempPath = Join-Path ([IO.Path]::GetTempPath()) ("oss-cname-cert-" + [Guid]::NewGuid().ToString("N") + ".xml")
try {
    $xml.Save($tempPath)
    $output = & aliyun oss bucket-cname --method put --item certificate "oss://$Bucket" $tempPath --endpoint $Endpoint 2>&1
    if ($LASTEXITCODE -ne 0) {
        $safeOutput = ($output | Out-String) -replace '(?s)<PrivateKey>.*?</PrivateKey>', '<PrivateKey>REDACTED</PrivateKey>'
        throw "aliyun oss bucket-cname certificate update failed`n$safeOutput"
    }
} finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
}

Write-Host "oss_cname_certificate_synced=true"
