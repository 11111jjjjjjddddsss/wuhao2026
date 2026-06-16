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

function Test-AnyEnvironmentVariable {
    param([string[]]$Names)

    foreach ($name in $Names) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $true
        }
    }
    return $false
}

function Write-PrereqGroupStatus {
    param(
        [string]$Name,
        [array]$Requirements
    )

    $missing = New-Object System.Collections.Generic.List[string]
    foreach ($requirement in $Requirements) {
        if (-not (Test-AnyEnvironmentVariable -Names $requirement.AnyOf)) {
            $missing.Add($requirement.Label) | Out-Null
        }
    }

    if ($missing.Count -eq 0) {
        Write-Host "$Name=ready"
    } else {
        Write-Host "$Name=missing"
        Write-Host "$($Name)_missing=$($missing -join ',')"
    }
}

Write-Host "== payment readiness =="
Write-Host "repo=$RepoRoot base_url=$BaseUrl skip_public_health=$SkipPublicHealth"

$membershipPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MembershipCenterSheet.kt"
$chatScreenPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$hamburgerMenuPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/HamburgerMenuSheet.kt"
$adminMainPath = Join-Path $RepoRoot "admin/src/main.ts"
$serverPath = Join-Path $RepoRoot "server-go/internal/app/server.go"
$paymentsRunbookPath = Join-Path $RepoRoot "docs/runbooks/payments.md"

$membership = Read-SourceFile $membershipPath
$chatScreen = Read-SourceFile $chatScreenPath
$hamburgerMenu = Read-SourceFile $hamburgerMenuPath
$adminMain = Read-SourceFile $adminMainPath
$server = Read-SourceFile $serverPath
$paymentsRunbook = Read-SourceFile $paymentsRunbookPath

Require-Match -Name "android_membership_notice_component" -Content $membership -Pattern "MembershipUnavailableNotice"
Require-Match -Name "android_topup_component" -Content $membership -Pattern "MembershipTopupCard"
Require-Match -Name "android_plan_actions_disabled" -Content $membership -Pattern "actionEnabled\s*=\s*false"
Require-Match -Name "android_topup_action_disabled" -Content $membership -Pattern "val\s+canBuy\s*=\s*false"
Require-NoMatch -Name "android_no_dev_order_api_calls" -Content $membership -Pattern "/api/(tier/renew_plus|tier/renew_pro|tier/upgrade_plus_to_pro|topup/buy)"
Require-Match -Name "android_chat_membership_payment_click_log" -Content $chatScreen -Pattern "payment\.unavailable_clicked(?s:.*?)chat_membership_sheet"
Require-Match -Name "android_settings_membership_payment_click_log" -Content $hamburgerMenu -Pattern "payment\.unavailable_clicked(?s:.*?)settings_membership_page"
Require-Match -Name "admin_orders_read_only_copy" -Content $adminMain -Pattern "订单只做只读核查(?s:.*?)不提供补发、退款或手动改权益"
Require-Match -Name "admin_orders_no_payment_simulation_copy" -Content $adminMain -Pattern "不提供支付成功模拟或手动发放入口"
Require-NoMatch -Name "admin_orders_no_write_buttons" -Content $adminMain -Pattern '<button[^>]*>\s*(补发|手动改权益|模拟支付成功|确认退款|发放权益)'

Require-Match -Name "server_dev_order_guard" -Content $server -Pattern "func\s+\(s \*Server\)\s+allowDevOrderEndpoint"
Require-Match -Name "server_payment_not_configured" -Content $server -Pattern "PAYMENT_NOT_CONFIGURED"
Require-Match -Name "server_dev_order_explicit_env" -Content $server -Pattern "ALLOW_DEV_ORDER_ENDPOINTS"
Require-Match -Name "server_dev_order_requires_dev_env" -Content $server -Pattern "env == ""local"" \|\| env == ""dev"" \|\| env == ""development"" \|\| env == ""test"""
Require-Match -Name "server_admin_orders_get_only" -Content $server -Pattern 's\.mux\.HandleFunc\("GET /admin-api/v1/orders",\s*s\.handleAdminOrders\)'
Require-NoMatch -Name "server_admin_orders_no_write_routes" -Content $server -Pattern 's\.mux\.HandleFunc\("(POST|PUT|PATCH|DELETE) /admin-api/v1/orders'

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
Write-PrereqGroupStatus -Name "alipay_sandbox_prereqs" -Requirements @(
    @{ Label = "app_id"; AnyOf = @("ALIPAY_SANDBOX_APP_ID", "NONGJI_ALIPAY_SANDBOX_APP_ID") },
    @{ Label = "app_private_key"; AnyOf = @("ALIPAY_SANDBOX_APP_PRIVATE_KEY", "ALIPAY_SANDBOX_APP_PRIVATE_KEY_FILE", "NONGJI_ALIPAY_SANDBOX_APP_PRIVATE_KEY", "NONGJI_ALIPAY_SANDBOX_APP_PRIVATE_KEY_FILE") },
    @{ Label = "alipay_public_key"; AnyOf = @("ALIPAY_SANDBOX_PUBLIC_KEY", "ALIPAY_SANDBOX_PUBLIC_KEY_FILE", "NONGJI_ALIPAY_SANDBOX_PUBLIC_KEY", "NONGJI_ALIPAY_SANDBOX_PUBLIC_KEY_FILE") }
)
Write-PrereqGroupStatus -Name "wechat_app_pay_prereqs" -Requirements @(
    @{ Label = "app_id"; AnyOf = @("WECHAT_PAY_APP_ID", "WECHATPAY_APP_ID", "NONGJI_WECHAT_PAY_APP_ID") },
    @{ Label = "mch_id"; AnyOf = @("WECHAT_PAY_MCH_ID", "WECHATPAY_MCH_ID", "NONGJI_WECHAT_PAY_MCH_ID") },
    @{ Label = "merchant_private_key"; AnyOf = @("WECHAT_PAY_MCH_PRIVATE_KEY", "WECHAT_PAY_MCH_PRIVATE_KEY_FILE", "WECHATPAY_MCH_PRIVATE_KEY", "WECHATPAY_MCH_PRIVATE_KEY_FILE", "NONGJI_WECHAT_PAY_MCH_PRIVATE_KEY", "NONGJI_WECHAT_PAY_MCH_PRIVATE_KEY_FILE") },
    @{ Label = "merchant_cert_serial_no"; AnyOf = @("WECHAT_PAY_MCH_CERT_SERIAL_NO", "WECHATPAY_MCH_CERT_SERIAL_NO", "NONGJI_WECHAT_PAY_MCH_CERT_SERIAL_NO") },
    @{ Label = "api_v3_key"; AnyOf = @("WECHAT_PAY_API_V3_KEY", "WECHATPAY_API_V3_KEY", "NONGJI_WECHAT_PAY_API_V3_KEY") }
)
Write-Host "sandbox_note=Alipay sandbox can be prepared before production approval; WeChat live App Pay calls require merchant credentials, product permission, signing, and HTTPS notify handling."
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
