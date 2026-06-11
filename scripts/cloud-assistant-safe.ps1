function Get-CloudAssistantRemotePathParts {
    param([Parameter(Mandatory = $true)][string]$RemotePath)
    $normalized = $RemotePath.Replace("\", "/").Trim()
    if ([string]::IsNullOrWhiteSpace($normalized) -or $normalized -notlike "/*") {
        throw "RemotePath must be an absolute Linux path"
    }
    $slashIndex = $normalized.LastIndexOf("/")
    if ($slashIndex -lt 1 -or $slashIndex -ge ($normalized.Length - 1)) {
        throw "RemotePath must include a directory and file name"
    }
    [pscustomobject]@{
        Directory = $normalized.Substring(0, $slashIndex)
        Name = $normalized.Substring($slashIndex + 1)
    }
}

function Send-CloudAssistantScriptFile {
    param(
        [Parameter(Mandatory = $true)][string]$RegionId,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$RemotePath,
        [Parameter(Mandatory = $true)][string]$ScriptText,
        [int]$TimeoutSeconds = 120
    )

    if (-not (Get-Command Invoke-JsonCommand -ErrorAction SilentlyContinue)) {
        throw "Invoke-JsonCommand must be defined before Send-CloudAssistantScriptFile is called"
    }

    $parts = Get-CloudAssistantRemotePathParts -RemotePath $RemotePath
    $scriptBytes = [Text.Encoding]::UTF8.GetBytes(($ScriptText -replace "`r`n", "`n"))
    $content = [Convert]::ToBase64String($scriptBytes)
    $send = Invoke-JsonCommand @(
        "aliyun", "ecs", "SendFile",
        "--RegionId", $RegionId,
        "--InstanceId.1", $InstanceId,
        "--Name", $parts.Name,
        "--TargetDir", $parts.Directory,
        "--ContentType", "Base64",
        "--Content", $content,
        "--Overwrite", "true",
        "--Timeout", [string]$TimeoutSeconds
    )

    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeSendFileResults",
            "--RegionId", $RegionId,
            "--InvokeId", $send.InvokeId,
            "--InstanceId", $InstanceId
        )
        $invocation = @($result.Invocations.Invocation)[0]
        $instance = @($invocation.InvokeInstances.InvokeInstance)[0]
        if ($instance.InvocationStatus -eq "Success") {
            return [pscustomobject]@{
                InvokeId = $send.InvokeId
                RemotePath = $RemotePath
                Status = $instance.InvocationStatus
            }
        }
        if ($instance.InvocationStatus -ne "Pending" -and $instance.InvocationStatus -ne "Running") {
            throw "SendFile failed for ${RemotePath}: $($instance.InvocationStatus) $($instance.ErrorCode) $($instance.ErrorInfo)"
        }
    }
    throw "Timed out waiting for SendFile $($send.InvokeId)"
}
