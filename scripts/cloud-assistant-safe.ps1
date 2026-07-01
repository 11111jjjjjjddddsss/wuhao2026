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

function ConvertTo-CloudAssistantShellSingleQuote {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "'" + $Value.Replace("'", "'""'""'") + "'"
}

function Wait-CloudAssistantSendFile {
    param(
        [Parameter(Mandatory = $true)][string]$RegionId,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$InvokeId,
        [Parameter(Mandatory = $true)][string]$RemotePath
    )

    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeSendFileResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId,
            "--InstanceId", $InstanceId
        )
        $invocation = @($result.Invocations.Invocation)[0]
        if ($null -eq $invocation) {
            continue
        }
        $instance = @($invocation.InvokeInstances.InvokeInstance)[0]
        if ($null -eq $instance) {
            continue
        }
        if ($instance.InvocationStatus -eq "Success") {
            return
        }
        if ($instance.InvocationStatus -ne "Pending" -and $instance.InvocationStatus -ne "Running") {
            throw "SendFile failed for ${RemotePath}: $($instance.InvocationStatus) $($instance.ErrorCode) $($instance.ErrorInfo)"
        }
    }
    throw "Timed out waiting for SendFile $InvokeId"
}

function Wait-CloudAssistantRunCommand {
    param(
        [Parameter(Mandatory = $true)][string]$RegionId,
        [Parameter(Mandatory = $true)][string]$InstanceId,
        [Parameter(Mandatory = $true)][string]$InvokeId,
        [int]$MaxAttempts = 60
    )

    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        Start-Sleep -Seconds 2
        $result = Invoke-JsonCommand @(
            "aliyun", "ecs", "DescribeInvocationResults",
            "--RegionId", $RegionId,
            "--InvokeId", $InvokeId,
            "--InstanceId", $InstanceId
        )
        $invocation = $result.Invocation
        if (-not $invocation) {
            continue
        }
        $instance = @($invocation.InvocationResults.InvocationResult)[0]
        if ($instance.InvocationStatus -eq "Running" -or $instance.InvocationStatus -eq "Pending") {
            continue
        }
        if ($instance.InvocationStatus -ne "Success" -or $instance.ExitCode -ne 0) {
            $output = ""
            if (-not [string]::IsNullOrWhiteSpace($instance.Output)) {
                try {
                    $output = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($instance.Output))
                } catch {
                    $output = "<failed to decode command output>"
                }
            }
            throw "RunCommand failed for Cloud Assistant file assembly: status=$($instance.InvocationStatus) exit=$($instance.ExitCode)`n$output"
        }
        return
    }
    throw "Timed out waiting for RunCommand $InvokeId"
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
    $maxDirectBytes = 9000
    if ($scriptBytes.Length -le $maxDirectBytes) {
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
        Wait-CloudAssistantSendFile -RegionId $RegionId -InstanceId $InstanceId -InvokeId $send.InvokeId -RemotePath $RemotePath
        return [pscustomobject]@{
            InvokeId = $send.InvokeId
            RemotePath = $RemotePath
            Status = "Success"
        }
    }

    $suffix = [Guid]::NewGuid().ToString("N")
    $chunkSize = 9000
    $partCount = [Math]::Ceiling($scriptBytes.Length / [double]$chunkSize)
    $remotePartPaths = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $partCount; $i++) {
        $start = $i * $chunkSize
        $length = [Math]::Min($chunkSize, $scriptBytes.Length - $start)
        $chunk = New-Object byte[] $length
        [Array]::Copy($scriptBytes, $start, $chunk, 0, $length)
        $partName = "{0}.{1}.part{2:D3}" -f $parts.Name, $suffix, $i
        $remotePartPaths.Add(($parts.Directory.TrimEnd("/") + "/" + $partName))
        $send = Invoke-JsonCommand @(
            "aliyun", "ecs", "SendFile",
            "--RegionId", $RegionId,
            "--InstanceId.1", $InstanceId,
            "--Name", $partName,
            "--TargetDir", $parts.Directory,
            "--ContentType", "Base64",
            "--Content", [Convert]::ToBase64String($chunk),
            "--Overwrite", "true",
            "--Timeout", [string]$TimeoutSeconds
        )
        Wait-CloudAssistantSendFile -RegionId $RegionId -InstanceId $InstanceId -InvokeId $send.InvokeId -RemotePath $remotePartPaths[$i]
    }

    $quotedParts = ($remotePartPaths | ForEach-Object { ConvertTo-CloudAssistantShellSingleQuote $_ }) -join " "
    $quotedRemotePath = ConvertTo-CloudAssistantShellSingleQuote $RemotePath
    $assembleCommand = "set -e`ncat $quotedParts > $quotedRemotePath`nchmod 600 $quotedRemotePath`nrm -f $quotedParts"
    $run = Invoke-JsonCommand @(
        "aliyun", "ecs", "RunCommand",
        "--RegionId", $RegionId,
        "--Type", "RunShellScript",
        "--InstanceId.1", $InstanceId,
        "--CommandContent", $assembleCommand,
        "--Timeout", [string]$TimeoutSeconds
    )
    Wait-CloudAssistantRunCommand -RegionId $RegionId -InstanceId $InstanceId -InvokeId $run.InvokeId

    return [pscustomobject]@{
        InvokeId = $run.InvokeId
        RemotePath = $RemotePath
        Status = "Success"
        Parts = $partCount
    }
}
