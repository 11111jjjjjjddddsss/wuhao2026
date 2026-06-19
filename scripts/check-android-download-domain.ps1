param(
    [string]$Domain = "download.nongjiqiancha.cn",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$ProbeObjectKey = "download-probes/probe.txt",
    [int]$ProbeExpiresSeconds = 600,
    [int]$CertificateExpiryAttentionDays = 30,
    [switch]$AllowAttentionExitZero
)

$ErrorActionPreference = "Stop"

$failures = New-Object System.Collections.Generic.List[string]
$attention = New-Object System.Collections.Generic.List[string]

function Add-Failure {
    param([string]$Message)
    $failures.Add($Message) | Out-Null
    Write-Host "failure=$Message"
}

function Add-Attention {
    param([string]$Message)
    $attention.Add($Message) | Out-Null
    Write-Host "attention=$Message"
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Add-Failure "required command not found: $Name"
        return $false
    }
    return $true
}

function Assert-SafeHostname {
    param([string]$Name, [string]$Value)
    if ($Value -notmatch '^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])$' -or
        $Value.Contains("..") -or
        $Value.Contains("/") -or
        $Value.Contains("\") -or
        $Value.Contains(":") -or
        $Value.Contains("@")) {
        Add-Failure "$Name must be a plain DNS hostname"
        return $false
    }
    return $true
}

function Assert-ExpectedValue {
    param([string]$Name, [string]$Value, [string]$Expected)
    if ($Value -ne $Expected) {
        Add-Failure "$Name must be '$Expected'"
        return $false
    }
    return $true
}

function Assert-SafeObjectKey {
    param([string]$Value)
    $normalized = $Value.Replace("\", "/").Trim("/")
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        Add-Failure "ProbeObjectKey must not be empty"
        return $false
    }
    foreach ($part in $normalized.Split("/")) {
        if ([string]::IsNullOrWhiteSpace($part) -or $part -eq "." -or $part -eq ".." -or $part -match '[^\w.\-]') {
            Add-Failure "ProbeObjectKey contains an unsafe path segment"
            return $false
        }
    }
    return $true
}

function Invoke-AliyunText {
    param([string[]]$CommandArgs)
    $output = & aliyun @CommandArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        $safeOutput = ($output | Out-String) `
            -replace '(?i)(AccessKeyId=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(AccessKeySecret=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(SecurityToken=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(Signature=)[^&\s]+', '${1}REDACTED'
        Add-Failure "aliyun command failed: $($CommandArgs -join ' ') :: $safeOutput"
        return ""
    }
    return ($output | Out-String)
}

function New-OssCnameSignedUrl {
    param(
        [string]$ObjectKey,
        [string]$Method = "HEAD"
    )
    $scriptPath = Join-Path $PSScriptRoot "sign-oss-cname-url.py"
    if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
        Add-Failure "OSS CNAME signing helper not found: $scriptPath"
        return ""
    }
    $output = & python $scriptPath `
        --bucket $Bucket `
        --endpoint "https://$Domain" `
        --object-key $ObjectKey `
        --expires-seconds $ProbeExpiresSeconds `
        --method $Method 2>&1
    if ($LASTEXITCODE -ne 0) {
        $safeOutput = ($output | Out-String) `
            -replace '(?i)(AccessKeyId=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(AccessKeySecret=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(SecurityToken=)[^&\s]+', '${1}REDACTED' `
            -replace '(?i)(Signature=)[^&\s]+', '${1}REDACTED'
        Add-Failure "OSS CNAME signing failed: $safeOutput"
        return ""
    }
    $url = (($output | Out-String).Trim())
    if ($url -notmatch "^https://$([regex]::Escape($Domain))/") {
        Add-Failure "OSS CNAME signing produced unexpected URL host"
        return ""
    }
    return $url
}

function Get-RemoteTlsCertificateNotAfter {
    param([string]$HostName)
    $tcpClient = $null
    $sslStream = $null
    try {
        $tcpClient = [Net.Sockets.TcpClient]::new()
        $connectTask = $tcpClient.ConnectAsync($HostName, 443)
        if (-not $connectTask.Wait([TimeSpan]::FromSeconds(10))) {
            throw "TLS connect timed out"
        }
        $sslStream = [Net.Security.SslStream]::new($tcpClient.GetStream(), $false)
        $asyncResult = $sslStream.BeginAuthenticateAsClient($HostName, $null, $null)
        if (-not $asyncResult.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds(10))) {
            throw "TLS handshake timed out"
        }
        $sslStream.EndAuthenticateAsClient($asyncResult)
        $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::new($sslStream.RemoteCertificate)
        return $cert.NotAfter
    } finally {
        if ($sslStream) { $sslStream.Dispose() }
        if ($tcpClient) { $tcpClient.Dispose() }
    }
}

Write-Host "== android download domain check =="
Write-Host "download_domain=$Domain"
Write-Host "oss_bucket=$Bucket"
Write-Host "oss_endpoint=$Endpoint"

if (-not (Assert-SafeHostname -Name "Domain" -Value $Domain) -or
    -not (Assert-SafeHostname -Name "Endpoint" -Value $Endpoint) -or
    -not (Assert-SafeObjectKey -Value $ProbeObjectKey) -or
    -not (Assert-ExpectedValue -Name "Domain" -Value $Domain -Expected "download.nongjiqiancha.cn") -or
    -not (Assert-ExpectedValue -Name "Bucket" -Value $Bucket -Expected "nongjiqiancha-prod") -or
    -not (Assert-ExpectedValue -Name "Endpoint" -Value $Endpoint -Expected "oss-cn-beijing.aliyuncs.com")) {
    Write-Host ("status=failed failures={0} attention={1}" -f $failures.Count, $attention.Count)
    exit 1
}

$expectedTarget = "$Bucket.$Endpoint"

try {
    $cnameRecords = @(Resolve-DnsName -Name $Domain -Type CNAME -ErrorAction Stop)
    $targets = @($cnameRecords | Where-Object { $_.Type -eq "CNAME" } | ForEach-Object { ([string]$_.NameHost).TrimEnd(".") })
    if ($targets.Count -eq 0) {
        Add-Attention "download domain has no CNAME record yet"
    } elseif ($targets -notcontains $expectedTarget) {
        Add-Attention "download domain CNAME target is '$($targets -join ',')', expected '$expectedTarget'"
    } else {
        Write-Host "dns_cname=ready target=$expectedTarget"
    }
} catch {
    Add-Attention "download domain DNS is not ready: $($_.Exception.Message)"
}

try {
    $notAfter = Get-RemoteTlsCertificateNotAfter -HostName $Domain
    $daysLeft = [math]::Floor(($notAfter.ToUniversalTime() - (Get-Date).ToUniversalTime()).TotalDays)
    Write-Host ("download_tls_not_after={0:yyyy-MM-ddTHH:mm:ssZ} days_left={1}" -f $notAfter.ToUniversalTime(), $daysLeft)
    if ($daysLeft -lt $CertificateExpiryAttentionDays) {
        Add-Attention "download domain TLS certificate expires in $daysLeft days; sync OSS CNAME certificate after certbot renewal"
    }
} catch {
    Add-Attention "download domain TLS certificate could not be inspected: $($_.Exception.Message)"
}

if (Require-Command "aliyun") {
    $ossCnameText = Invoke-AliyunText -CommandArgs @("oss", "bucket-cname", "--method", "get", "oss://$Bucket", "--endpoint", $Endpoint)
    if ($ossCnameText) {
        try {
            $xmlStart = $ossCnameText.IndexOf("<")
            if ($xmlStart -gt 0) {
                $ossCnameText = $ossCnameText.Substring($xmlStart)
            }
            if ($ossCnameText -notmatch "<ListCnameResult") {
                throw "ListCnameResult XML was not returned"
            }
            $xmlEnd = $ossCnameText.IndexOf("</ListCnameResult>")
            if ($xmlEnd -ge 0) {
                $ossCnameText = $ossCnameText.Substring(0, $xmlEnd + "</ListCnameResult>".Length)
            }
            [xml]$ossCnameXml = $ossCnameText
            $cnameNodes = @($ossCnameXml.ListCnameResult.Cname)
            $matched = $false
            foreach ($node in $cnameNodes) {
                if ([string]$node.Domain -eq $Domain) {
                    $matched = $true
                    $status = [string]$node.Status
                    $certID = [string]$node.Certificate.CertId
                    Write-Host "oss_cname=ready status=$status has_certificate=$([bool]$certID)"
                    if (-not $certID) {
                        Add-Attention "OSS custom domain is bound but HTTPS certificate is not visible in ListCname"
                    }
                }
            }
            if (-not $matched) {
                Add-Attention "OSS bucket is not bound to $Domain yet"
            }
        } catch {
            Add-Failure "could not parse OSS CNAME XML: $($_.Exception.Message)"
        }
    }
}

$signedProbeUrl = New-OssCnameSignedUrl -ObjectKey $ProbeObjectKey -Method "HEAD"
if ($signedProbeUrl) {
    try {
        $response = Invoke-WebRequest -Uri $signedProbeUrl -Method Head -UseBasicParsing -TimeoutSec 20
        $statusCode = [int]$response.StatusCode
        $contentLength = [string]$response.Headers["Content-Length"]
        if ($statusCode -eq 200) {
            Write-Host "https_signed_probe=ready status=$statusCode content_length=$contentLength"
        } else {
            Add-Failure "signed HTTPS probe returned status $statusCode"
        }
    } catch {
        $statusCode = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        Add-Failure "signed HTTPS probe failed for $ProbeObjectKey status=$statusCode message=$($_.Exception.Message)"
    }
}

Write-Host "download_domain_note=Do not use *.oss-cn-beijing.aliyuncs.com APK URLs; use a verified HTTPS custom domain for APK downloads."

if ($failures.Count -gt 0) {
    Write-Host "status=failed failures=$($failures.Count) attention=$($attention.Count)"
    exit 1
}
if ($attention.Count -gt 0) {
    Write-Host "status=attention failures=0 attention=$($attention.Count)"
    if ($AllowAttentionExitZero) {
        exit 0
    }
    exit 2
}

Write-Host "status=ready failures=0 attention=0"
