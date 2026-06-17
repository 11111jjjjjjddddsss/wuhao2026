param(
    [string]$Domain = "download.nongjiqiancha.cn",
    [string]$Bucket = "nongjiqiancha-prod",
    [string]$Endpoint = "oss-cn-beijing.aliyuncs.com",
    [string]$ProbeObjectKey = "download-probes/probe.txt",
    [int]$ProbeExpiresSeconds = 600,
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

Write-Host "== android download domain check =="
Write-Host "download_domain=$Domain"
Write-Host "oss_bucket=$Bucket"
Write-Host "oss_endpoint=$Endpoint"

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
