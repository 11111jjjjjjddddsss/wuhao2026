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

$appBuildPath = Join-Path $RepoRoot "app/build.gradle.kts"
$manifestPath = Join-Path $RepoRoot "app/src/main/AndroidManifest.xml"
$paymentClientPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/AlipayPaymentClient.kt"
$sessionApiPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/SessionApi.kt"
$chatScreenPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$hamburgerMenuPath = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/HamburgerMenuSheet.kt"
$adminMainPath = Join-Path $RepoRoot "admin/src/main.ts"
$adminTypesPath = Join-Path $RepoRoot "admin/src/types.ts"
$serverPath = Join-Path $RepoRoot "server-go/internal/app/server.go"
$paymentsPath = Join-Path $RepoRoot "server-go/internal/app/payments.go"
$migrationPath = Join-Path $RepoRoot "server-go/migrations/044_alipay_payment_orders.sql"
$paymentsRunbookPath = Join-Path $RepoRoot "docs/runbooks/payments.md"
$thirdPartyPagePath = Join-Path $RepoRoot "site/public/legal/third-party-sharing/index.html"

$appBuild = Read-SourceFile $appBuildPath
$manifest = Read-SourceFile $manifestPath
$paymentClient = Read-SourceFile $paymentClientPath
$sessionApi = Read-SourceFile $sessionApiPath
$chatScreen = Read-SourceFile $chatScreenPath
$hamburgerMenu = Read-SourceFile $hamburgerMenuPath
$adminMain = Read-SourceFile $adminMainPath
$adminTypes = Read-SourceFile $adminTypesPath
$server = Read-SourceFile $serverPath
$payments = Read-SourceFile $paymentsPath
$migration = Read-SourceFile $migrationPath
$paymentsRunbook = Read-SourceFile $paymentsRunbookPath
$thirdPartyPage = Read-SourceFile $thirdPartyPagePath

Require-Match -Name "android_alipay_sdk_dependency" -Content $appBuild -Pattern "alipaysdk-android"
Require-Match -Name "android_alipay_package_visibility" -Content $manifest -Pattern "com\.eg\.android\.AlipayGphone"
Require-Match -Name "android_alipay_paytask" -Content $paymentClient -Pattern "PayTask"
Require-Match -Name "android_alipay_payv2" -Content $paymentClient -Pattern "payV2"
Require-Match -Name "android_alipay_unknown_result_polling" -Content $paymentClient -Pattern '"8000",\s*"6004",\s*"6006"'
Require-Match -Name "android_create_alipay_order_api" -Content $sessionApi -Pattern "/api/payments/alipay/orders"
Require-Match -Name "android_get_payment_order_api" -Content $sessionApi -Pattern "/api/payments/orders"
Require-Match -Name "android_payment_start_log" -Content $chatScreen -Pattern "payment\.start"
Require-Match -Name "android_alipay_sync_log" -Content $chatScreen -Pattern "payment\.alipay_sync_result"
Require-Match -Name "android_payment_grant_success_log" -Content $chatScreen -Pattern "payment\.grant_success"
Require-Match -Name "android_payment_grant_failure_persistent" -Content $chatScreen -Pattern "支付已确认，权益处理异常"
Require-NoMatch -Name "android_no_order_string_client_log" -Content $chatScreen -Pattern '"order_string"|orderString\s+to|"\s*orderString\s*"\s+to'
Require-Match -Name "android_privacy_mentions_alipay" -Content $hamburgerMenu -Pattern "支付宝 APP 支付 SDK"
Require-NoMatch -Name "android_privacy_no_old_no_payment_sdk_copy" -Content $hamburgerMenu -Pattern "当前版本不接入[^。]*支付[^。]*第三方 SDK"

Require-Match -Name "server_create_alipay_order_route" -Content $server -Pattern 'POST /api/payments/alipay/orders'
Require-Match -Name "server_get_payment_order_route" -Content $server -Pattern 'GET /api/payments/orders'
Require-Match -Name "server_alipay_notify_route" -Content $server -Pattern 'POST /api/payments/alipay/notify'
Require-Match -Name "server_healthz_alipay" -Content $server -Pattern '"alipay"\s*:\s*s\.alipay\.HealthStatus\(\)'
Require-Match -Name "server_alipay_app_pay_api" -Content $payments -Pattern "alipay\.trade\.app\.pay"
Require-Match -Name "server_alipay_sign_type_rsa2" -Content $payments -Pattern 'sign_type"\s*,\s*"RSA2'
Require-Match -Name "server_alipay_seller_required" -Content $payments -Pattern 'sellerID\s*==\s*""'
Require-Match -Name "server_alipay_enabled_requires_seller" -Content $payments -Pattern "c\.sellerID\s*!="
Require-Match -Name "server_alipay_grant_retry_guard" -Content $payments -Pattern "PAYMENT_GRANT_IN_PROGRESS"
Require-Match -Name "server_alipay_grant_claim_timestamp" -Content $payments -Pattern "COALESCE\(grant_claimed_at,\s*updated_at\)"
Require-Match -Name "server_alipay_no_paid_downgrade" -Content $payments -Pattern 'order\.Status\s*==\s*paymentStatusPaid(?s:.*?)last_notify_json'
Require-Match -Name "server_payment_log_suffix" -Content $payments -Pattern "paymentIDLogSuffix"
Require-NoMatch -Name "server_no_full_payment_id_logs" -Content $payments -Pattern 'logger\.(Info|Warn|Error)\([^\\r\\n]*"(outTradeNo|tradeNo)"'
Require-Match -Name "db_payment_orders" -Content $migration -Pattern "CREATE TABLE IF NOT EXISTS payment_orders"
Require-Match -Name "db_payment_notifications" -Content $migration -Pattern "CREATE TABLE IF NOT EXISTS payment_notifications"
Require-Match -Name "db_payment_orders_grant_claimed_at" -Content $migration -Pattern "grant_claimed_at BIGINT NULL"

Require-Match -Name "admin_orders_mentions_alipay" -Content $adminMain -Pattern "支付宝订单"
Require-Match -Name "admin_order_types_provider_fields" -Content $adminTypes -Pattern "provider_trade_no"
Require-Match -Name "runbook_alipay_notify_url" -Content $paymentsRunbook -Pattern "https://api\.nongjiqiancha\.cn/api/payments/alipay/notify"
Require-Match -Name "runbook_alipay_app_pay_api" -Content $paymentsRunbook -Pattern "alipay\.trade\.app\.pay"
Require-Match -Name "runbook_formal_validation_gate" -Content $paymentsRunbook -Pattern "正式收费开放前"
Require-Match -Name "site_third_party_mentions_alipay" -Content $thirdPartyPage -Pattern "支付宝 APP 支付 SDK"
Require-NoMatch -Name "site_third_party_no_old_no_payment_sdk_copy" -Content $thirdPartyPage -Pattern "当前版本不接入[^。]*支付[^。]*第三方 SDK"

if (-not $SkipPublicHealth) {
    try {
        $healthUrl = ($BaseUrl.TrimEnd("/") + "/healthz")
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec $TimeoutSec -Headers @{
            "User-Agent" = "nongjiqiancha-payment-readiness/1.1"
        }
        Write-Host "public_healthz_alipay=$($response.alipay)"
        Write-Host "public_healthz_dev_order_endpoints=$($response.dev_order_endpoints)"
        if ($response.dev_order_endpoints -ne $false) {
            Add-ErrorItem "public healthz dev_order_endpoints must be false"
        }
    } catch {
        Add-WarningItem "public healthz probe failed: $($_.Exception.Message)"
    }
}

Write-Host "payment_formal_status=alipay_app_pay_code_ready_needs_live_validation"
Write-Host "alipay_notify_url=https://api.nongjiqiancha.cn/api/payments/alipay/notify"
Write-PrereqGroupStatus -Name "alipay_live_prereqs" -Requirements @(
    @{ Label = "app_id"; AnyOf = @("ALIPAY_APP_ID", "ALIPAY_OPEN_APP_ID") },
    @{ Label = "seller_id"; AnyOf = @("ALIPAY_SELLER_ID") },
    @{ Label = "app_private_key"; AnyOf = @("ALIPAY_APP_PRIVATE_KEY", "ALIPAY_APP_PRIVATE_KEY_FILE") },
    @{ Label = "alipay_public_key"; AnyOf = @("ALIPAY_PUBLIC_KEY", "ALIPAY_PUBLIC_KEY_FILE") }
)
Write-Host "payment_readiness_note=Code and documents are aligned for Alipay APP Pay, but production charging still requires live env config, callback validation, reconciliation, refund handling, and manual acceptance."

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
