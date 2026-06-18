param(
    [string]$BaseRef = "",
    [string]$HeadRef = "HEAD",
    [switch]$AllowHighRiskMigrations
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-GitText {
    param([string[]]$Arguments)
    $output = & git -C $repoRoot @Arguments
    $exitCode = $LASTEXITCODE
    if ($null -ne $exitCode -and $exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed with code $exitCode"
    }
    return @($output)
}

function Test-GitRef {
    param([string]$Ref)
    if ([string]::IsNullOrWhiteSpace($Ref)) {
        return $false
    }
    & git -C $repoRoot rev-parse --verify $Ref *> $null
    return $LASTEXITCODE -eq 0
}

function Add-ChangedMigration {
    param(
        [System.Collections.Generic.HashSet[string]]$Set,
        [string]$Path
    )
    $normalized = $Path.Trim().Replace("\", "/")
    if ($normalized -match '^server-go/migrations/.+\.sql$') {
        [void]$Set.Add($normalized)
    }
}

if ([string]::IsNullOrWhiteSpace($HeadRef)) {
    $HeadRef = "HEAD"
}
if ([string]::IsNullOrWhiteSpace($BaseRef) -and (Test-GitRef "$HeadRef^")) {
    $BaseRef = "$HeadRef^"
}

$changed = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
if (-not [string]::IsNullOrWhiteSpace($BaseRef) -and (Test-GitRef $BaseRef) -and (Test-GitRef $HeadRef)) {
    $names = Invoke-GitText @("diff", "--name-only", "--diff-filter=AMR", "$BaseRef..$HeadRef", "--", "server-go/migrations")
    foreach ($name in $names) {
        Add-ChangedMigration -Set $changed -Path $name
    }
} elseif (Test-GitRef $HeadRef) {
    $names = Invoke-GitText @("diff-tree", "--no-commit-id", "--name-only", "-r", "--diff-filter=AMR", $HeadRef, "--", "server-go/migrations")
    foreach ($name in $names) {
        Add-ChangedMigration -Set $changed -Path $name
    }
}

$statusLines = Invoke-GitText @("status", "--porcelain", "--", "server-go/migrations")
foreach ($line in $statusLines) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.Length -lt 4) {
        continue
    }
    $path = $line.Substring(3).Trim()
    if ($path -match ' -> ') {
        $path = ($path -split ' -> ')[-1]
    }
    Add-ChangedMigration -Set $changed -Path $path
}

$riskyFiles = New-Object System.Collections.Generic.List[string]
$riskPattern = '(?im)^\s*(ALTER|DROP|RENAME|TRUNCATE|UPDATE|DELETE|REPLACE)\b|\bMODIFY\s+(COLUMN\s+)?\b'
foreach ($relativePath in ($changed | Sort-Object)) {
    $fullPath = Join-Path $repoRoot ($relativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $fullPath)) {
        continue
    }
    $content = Get-Content -LiteralPath $fullPath -Raw
    if ($content -match $riskPattern) {
        $riskyFiles.Add($relativePath) | Out-Null
    }
}

if ($riskyFiles.Count -eq 0) {
    Write-Host "migration_risk_status=ready checked_files=$($changed.Count)"
    exit 0
}

Write-Host "migration_risk_status=blocked high_risk_files=$($riskyFiles.Count)"
foreach ($file in $riskyFiles) {
    Write-Host "high_risk_migration=$file"
}

if ($AllowHighRiskMigrations) {
    Write-Warning "High-risk migration SQL detected but -AllowHighRiskMigrations was set. Confirm backward compatibility and rollback limits before deploy."
    exit 0
}

throw "server-go migrations changed with high-risk SQL. Review DB compatibility with the currently active slot, update runbook/project memory, then rerun deploy with -AllowHighRiskMigrations if intentional."
