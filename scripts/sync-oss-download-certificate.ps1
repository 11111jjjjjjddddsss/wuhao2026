param(
    [string]$Domain = "download.nongjiqiancha.cn",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$EcsRegionId = "cn-beijing",
    [string]$EcsInstanceId = "i-2ze5nrem0jrchln4f0eh"
)

$ErrorActionPreference = "Stop"
$tempKeyDir = ""

trap {
    if (-not [string]::IsNullOrWhiteSpace($tempKeyDir) -and (Test-Path -LiteralPath $tempKeyDir)) {
        Remove-Item -LiteralPath $tempKeyDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    throw
}

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

function Get-MarkedBase64 {
    param([string]$Text, [string]$Name)
    $pattern = "(?s)$Name`_BEGIN\s*(?<value>[A-Za-z0-9+/=\r\n]+)\s*$Name`_END"
    $match = [regex]::Match($Text, $pattern)
    if (-not $match.Success) {
        throw "could not parse encrypted certificate payload marker: $Name"
    }
    return [Convert]::FromBase64String(($match.Groups["value"].Value -replace '\s+', ''))
}

function Get-MarkedText {
    param([string]$Text, [string]$Name)
    $pattern = "(?s)$Name`_BEGIN\r?\n(?<value>.*?)\r?\n$Name`_END"
    $match = [regex]::Match($Text, $pattern)
    if (-not $match.Success) {
        throw "could not parse decrypted certificate payload marker: $Name"
    }
    return $match.Groups["value"].Value.Trim()
}

Require-Command "aliyun"
Require-Command "python"
. (Join-Path $PSScriptRoot "cloud-assistant-safe.ps1")

if ($Domain -ne "download.nongjiqiancha.cn") {
    throw "refusing to sync unexpected OSS download certificate domain: $Domain"
}

Write-Host "== sync OSS download certificate =="
Write-Host "download_domain=$Domain"
Write-Host "oss_bucket=$Bucket"
Write-Host "oss_endpoint=$Endpoint"

$tempKeyDir = Join-Path ([IO.Path]::GetTempPath()) ("oss-cname-key-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempKeyDir | Out-Null
$privateKeyPath = Join-Path $tempKeyDir "private.pem"
$publicKeyPath = Join-Path $tempKeyDir "public.pem"
$plainPayloadPath = Join-Path $tempKeyDir "payload.txt"
$encryptedKeyPath = Join-Path $tempKeyDir "aes.key.enc"
$ivPath = Join-Path $tempKeyDir "aes.iv"
$encryptedPayloadPath = Join-Path $tempKeyDir "payload.enc"

$keygenScript = @'
import pathlib
import sys
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

private_path = pathlib.Path(sys.argv[1])
public_path = pathlib.Path(sys.argv[2])
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
private_path.write_bytes(key.private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
))
public_path.write_bytes(key.public_key().public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo,
))
'@
$keygenOutput = $keygenScript | python - $privateKeyPath $publicKeyPath 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "failed to generate local transient RSA key pair`n$($keygenOutput | Out-String)"
}
$publicKeyPem = Get-Content -LiteralPath $publicKeyPath -Raw

$remoteScript = @"
set -euo pipefail
domain='download.nongjiqiancha.cn'
cert_dir="/etc/letsencrypt/live/`$domain"
tmp_dir="`$(mktemp -d)"
trap 'rm -rf "`$tmp_dir"' EXIT
if [ ! -s "`$cert_dir/fullchain.pem" ] || [ ! -s "`$cert_dir/privkey.pem" ]; then
  echo "certificate files not found for `$domain" >&2
  exit 11
fi
openssl x509 -in "`$cert_dir/fullchain.pem" -noout -subject -issuer -dates
cat > "`$tmp_dir/public.pem" <<'PUBLIC_KEY_PEM'
$publicKeyPem
PUBLIC_KEY_PEM
{
  echo "CERT_PEM_BEGIN"
  cat "`$cert_dir/fullchain.pem"
  echo "CERT_PEM_END"
  echo "KEY_PEM_BEGIN"
  cat "`$cert_dir/privkey.pem"
  echo "KEY_PEM_END"
} > "`$tmp_dir/payload.txt"
openssl rand 32 > "`$tmp_dir/aes.key"
openssl rand 16 > "`$tmp_dir/aes.iv"
key_hex="`$(od -An -tx1 -v "`$tmp_dir/aes.key" | tr -d ' \n')"
iv_hex="`$(od -An -tx1 -v "`$tmp_dir/aes.iv" | tr -d ' \n')"
openssl enc -aes-256-cbc -K "`$key_hex" -iv "`$iv_hex" -in "`$tmp_dir/payload.txt" -out "`$tmp_dir/payload.enc"
openssl pkeyutl -encrypt -pubin -inkey "`$tmp_dir/public.pem" -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -in "`$tmp_dir/aes.key" -out "`$tmp_dir/aes.key.enc"
echo "AES_KEY_B64_BEGIN"
base64 -w0 "`$tmp_dir/aes.key.enc"
echo
echo "AES_KEY_B64_END"
echo "AES_IV_B64_BEGIN"
base64 -w0 "`$tmp_dir/aes.iv"
echo
echo "AES_IV_B64_END"
echo "PAYLOAD_B64_BEGIN"
base64 -w0 "`$tmp_dir/payload.enc"
echo
echo "PAYLOAD_B64_END"
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

$encryptedKey = Get-MarkedBase64 -Text $output -Name "AES_KEY_B64"
$ivBytes = Get-MarkedBase64 -Text $output -Name "AES_IV_B64"
$encryptedPayload = Get-MarkedBase64 -Text $output -Name "PAYLOAD_B64"
[IO.File]::WriteAllBytes($encryptedKeyPath, $encryptedKey)
[IO.File]::WriteAllBytes($ivPath, $ivBytes)
[IO.File]::WriteAllBytes($encryptedPayloadPath, $encryptedPayload)

$decryptScript = @'
import pathlib
import sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.padding import PKCS7

private_path, key_path, iv_path, payload_path, out_path = [pathlib.Path(arg) for arg in sys.argv[1:6]]
private_key = serialization.load_pem_private_key(private_path.read_bytes(), password=None)
aes_key = private_key.decrypt(
    key_path.read_bytes(),
    padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None,
    ),
)
cipher = Cipher(algorithms.AES(aes_key), modes.CBC(iv_path.read_bytes()))
decryptor = cipher.decryptor()
padded = decryptor.update(payload_path.read_bytes()) + decryptor.finalize()
unpadder = PKCS7(128).unpadder()
plain = unpadder.update(padded) + unpadder.finalize()
out_path.write_bytes(plain)
'@
$decryptOutput = $decryptScript | python - $privateKeyPath $encryptedKeyPath $ivPath $encryptedPayloadPath $plainPayloadPath 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "failed to decrypt certificate payload`n$($decryptOutput | Out-String)"
}
$plainPayload = Get-Content -LiteralPath $plainPayloadPath -Raw
$certPem = Get-MarkedText -Text $plainPayload -Name "CERT_PEM"
$keyPem = Get-MarkedText -Text $plainPayload -Name "KEY_PEM"

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
    Remove-Item -LiteralPath $tempKeyDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "oss_cname_certificate_synced=true"
