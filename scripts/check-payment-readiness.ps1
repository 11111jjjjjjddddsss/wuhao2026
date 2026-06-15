param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$BaseUrl = "https://api.nongjiqiancha.cn",
    [int]$TimeoutSec = 12,
    [switch]$SkipPublicHealth
)

$ErrorActionPreference = "Stop"

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

function Add-ErrorItem {
    param([string]$Message)
    $errors.Add($Message) | Out-Null
}

function Add-WarningItem {
    param([string]$Message)
    $warnings.Add($Message) | Out-Null
}

function Read-SourceFile {
    param([string]$Path)
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
}

function Require-Match {
    param(
        [string]$Name,
        [string]$Content,
        [string]$Pattern
    )
    if ($Content -notmatch $Pattern) {
        Add-ErrorItem "$Name missing_pattern=$Pattern"
    }
}

function Require-NoMatch {
    param(
        [string]$Name,
        [string]$Content,
        [string]$Pattern
    )
    if ($Content -match $Pattern) {
        Add-ErrorItem "$Name unexpected_pattern=$Pattern"
    }
}

Write-Host "== payment readiness =="
Write-Host "repo=$RepoRoot base_url=$BaseUrl skip_public_health=$SkipPublicHealth"

$membershipPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MembershipCenterSheet.kt"
$serverPath = Join-Path $RepoRoot "server-go/internal/app/server.go"
$paymentsRunbookPath = Join-Path $RepoRoot "docs/runbooks/payments.md"

$membership = Read-SourceFile $membershipPath
$server = Read-SourceFile $serverPath
$paymentsRunbook = Read-SourceFile $paymentsRunbookPath

Require-Match -Name "android_membership_notice_component" -Content $membership -Pattern "MembershipUnavailableNotice"
Require-Match -Name "android_topup_component" -Content $membership -Pattern "MembershipTopupCard"
Require-Match -Name "android_plan_actions_disabled" -Content $membership -Pattern "actionEnabled\s*=\s*false"
Require-Match -Name "android_topup_action_disabled" -Content $membership -Pattern "val\s+canBuy\s*=\s*false"
Require-NoMatch -Name "android_no_dev_order_api_calls" -Content $membership -Pattern "/api/(tier/renew_plus|tier/renew_pro|tier/upgrade_plus_to_pro|topup/buy)"

Require-Match -Name "server_dev_order_guard" -Content $server -Pattern "func\s+\(s \*Server\)\s+allowDevOrderEndpoint"
Require-Match -Name "server_payment_not_configured" -Content $server -Pattern "PAYMENT_NOT_CONFIGURED"
Require-Match -Name "server_dev_order_explicit_env" -Content $server -Pattern "ALLOW_DEV_ORDER_ENDPOINTS"
Require-Match -Name "server_dev_order_requires_dev_env" -Content $server -Pattern "env == ""local"" \|\| env == ""dev"" \|\| env == ""development"" \|\| env == ""test"""

Require-Match -Name "runbook_wechat_notify_url" -Content $paymentsRunbook -Pattern "https://api\.nongjiqiancha\.cn/api/payments/wechat/notify"
Require-Match -Name "runbook_alipay_notify_url" -Content $paymentsRunbook -Pattern "https://api\.nongjiqiancha\.cn/api/payments/alipay/notify"
Require-Match -Name "runbook_alipay_app_pay_api" -Content $paymentsRunbook -Pattern "alipay\.trade\.app\.pay"
Require-Match -Name "runbook_payment_not_configured" -Content $paymentsRunbook -Pattern "PAYMENT_NOT_CONFIGURED"

if (-not $SkipPublicHealth) {
    try {
        $healthUrl = ($BaseUrl.TrimEnd("/") + "/healthz")
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec $TimeoutSec -Headers @{
            "User-Agent" = "nongjiqiancha-payment-readiness/1.0"
        }
        $devOrder = $response.dev_order_endpoints
        Write-Host "public_healthz_dev_order_endpoints=$devOrder"
        if ($devOrder -ne $false) {
            Add-ErrorItem "public healthz dev_order_endpoints must be false"
        }
    } catch {
        Add-WarningItem "public healthz probe failed: $($_.Exception.Message)"
    }
}

Write-Host "payment_formal_status=not_configured"
Write-Host "wechat_notify_url=https://api.nongjiqiancha.cn/api/payments/wechat/notify"
Write-Host "alipay_notify_url=https://api.nongjiqiancha.cn/api/payments/alipay/notify"
Write-Host "safe_without_paid_iap=$($errors.Count -eq 0)"
Write-Host "next_step=apply merchant products, then wire sandbox/live credentials and implement formal order/callback tables"

if ($warnings.Count -gt 0) {
    foreach ($warning in $warnings) {
        Write-Warning $warning
    }
}

if ($errors.Count -gt 0) {
    foreach ($err in $errors) {
        Write-Error $err
    }
    Write-Host "payment_readiness_status=blocked"
    exit 1
}

Write-Host "payment_readiness_status=attention"
exit 0
