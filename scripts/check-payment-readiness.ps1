param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$BaseUrl = "https://api.nongjiqiancha.cn",
    [int]$TimeoutSec = 12,
    [switch]$SkipPublicHealth,
    [switch]$StrictPublicHealth,
    [switch]$RunPublicNotifyProbe,
    [switch]$AllowPublicPayment,
    [switch]$AllowTestAmount
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
Write-Host "repo=$RepoRoot base_url=$BaseUrl skip_public_health=$SkipPublicHealth strict_public_health=$StrictPublicHealth run_public_notify_probe=$RunPublicNotifyProbe allow_public_payment=$AllowPublicPayment allow_test_amount=$AllowTestAmount"

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
$testMetadataMigrationPath = Join-Path $RepoRoot "server-go/migrations/045_payment_order_test_metadata.sql"
$opsClosureMigrationPath = Join-Path $RepoRoot "server-go/migrations/046_payment_order_ops_closure.sql"
$paymentsRunbookPath = Join-Path $RepoRoot "docs/runbooks/payments.md"
$agentsPath = Join-Path $RepoRoot "AGENTS.md"
$currentStatusPath = Join-Path $RepoRoot "docs/project-state/current-status.md"
$openRisksPath = Join-Path $RepoRoot "docs/project-state/open-risks.md"
$pendingDecisionsPath = Join-Path $RepoRoot "docs/project-state/pending-decisions.md"
$recentChangesPath = Join-Path $RepoRoot "docs/project-state/recent-changes.md"
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
$testMetadataMigration = Read-SourceFile $testMetadataMigrationPath
$opsClosureMigration = Read-SourceFile $opsClosureMigrationPath
$paymentsRunbook = Read-SourceFile $paymentsRunbookPath
$agents = Read-SourceFile $agentsPath
$currentPaymentDocs = @(
    $agents,
    (Read-SourceFile $currentStatusPath),
    (Read-SourceFile $openRisksPath),
    $paymentsRunbook
) -join "`n"
$projectPaymentDocs = @(
    (Read-SourceFile $currentStatusPath),
    (Read-SourceFile $openRisksPath),
    (Read-SourceFile $pendingDecisionsPath),
    (Read-SourceFile $recentChangesPath),
    $paymentsRunbook
) -join "`n"
$thirdPartyPage = Read-SourceFile $thirdPartyPagePath

Require-Match -Name "android_alipay_sdk_dependency" -Content $appBuild -Pattern "alipaysdk-android"
Require-Match -Name "android_alipay_package_visibility" -Content $manifest -Pattern "com\.eg\.android\.AlipayGphone"
Require-Match -Name "android_alipay_paytask" -Content $paymentClient -Pattern "PayTask"
Require-Match -Name "android_alipay_payv2" -Content $paymentClient -Pattern "payV2"
Require-Match -Name "android_alipay_unknown_result_polling" -Content $paymentClient -Pattern '"8000",\s*"6004",\s*"6006"'
Require-Match -Name "android_create_alipay_order_api" -Content $sessionApi -Pattern "/api/payments/alipay/orders"
Require-Match -Name "android_create_alipay_order_build_type" -Content $sessionApi -Pattern "client_build_type"
Require-Match -Name "android_create_alipay_order_fail_closed_provider" -Content $sessionApi -Pattern 'order\.provider\.equals\("alipay",\s*ignoreCase\s*=\s*true\)'
Require-Match -Name "android_create_alipay_order_fail_closed_amount" -Content $sessionApi -Pattern '\(order\.amountCents\s*\?:\s*0\)\s*>\s*0'
Require-Match -Name "android_get_payment_order_api" -Content $sessionApi -Pattern "/api/payments/orders"
Require-Match -Name "android_get_payment_order_ok_required" -Content $sessionApi -Pattern 'status\s*->\s*status\.ok\s*==\s*true'
Require-Match -Name "android_payment_start_log" -Content $chatScreen -Pattern "payment\.start"
Require-Match -Name "android_payment_confirmation_log" -Content $chatScreen -Pattern "payment\.confirmation_shown"
Require-Match -Name "android_payment_order_poll_fail_closed" -Content $chatScreen -Pattern 'payment[.]order_poll_invalid'
Require-Match -Name "android_alipay_sync_log" -Content $chatScreen -Pattern "payment\.alipay_sync_result"
Require-Match -Name "android_payment_grant_success_log" -Content $chatScreen -Pattern "payment\.grant_success"
Require-Match -Name "android_payment_grant_failure_persistent" -Content $chatScreen -Pattern "支付已确认，权益处理异常"
Require-NoMatch -Name "android_no_order_string_client_log" -Content $chatScreen -Pattern '"order_string"|orderString\s+to|"\s*orderString\s*"\s+to'
Require-Match -Name "android_payment_preview_plus" -Content $chatScreen -Pattern "MembershipPaymentConfirmPlus"
Require-Match -Name "android_payment_preview_pro" -Content $chatScreen -Pattern "MembershipPaymentConfirmPro"
Require-Match -Name "android_payment_preview_upgrade_pro" -Content $chatScreen -Pattern "MembershipPaymentConfirmUpgradePro"
Require-Match -Name "android_payment_preview_topup" -Content $chatScreen -Pattern "MembershipPaymentConfirmTopup"
Require-Match -Name "android_payment_success_preview" -Content $chatScreen -Pattern "MembershipPurchaseSuccess"
Require-Match -Name "android_privacy_mentions_alipay" -Content $hamburgerMenu -Pattern "支付宝 APP 支付 SDK"
Require-NoMatch -Name "android_privacy_no_old_no_payment_sdk_copy" -Content $hamburgerMenu -Pattern "当前版本不接入[^。]*支付[^。]*第三方 SDK"

Require-Match -Name "server_create_alipay_order_route" -Content $server -Pattern 'POST /api/payments/alipay/orders'
Require-Match -Name "server_get_payment_order_route" -Content $server -Pattern 'GET /api/payments/orders'
Require-Match -Name "server_alipay_notify_route" -Content $server -Pattern 'POST /api/payments/alipay/notify'
Require-Match -Name "server_admin_payment_grant_route" -Content $server -Pattern 'POST /admin-api/v1/orders/grant'
Require-Match -Name "server_admin_payment_query_route" -Content $server -Pattern 'POST /admin-api/v1/orders/query'
Require-Match -Name "server_admin_payment_refund_route" -Content $server -Pattern 'POST /admin-api/v1/orders/refund'
Require-Match -Name "server_admin_payment_close_expired_route" -Content $server -Pattern 'POST /admin-api/v1/orders/close-expired'
Require-Match -Name "server_admin_payment_reconciliation_route" -Content $server -Pattern 'GET /admin-api/v1/orders/reconciliation'
Require-Match -Name "server_healthz_alipay" -Content $server -Pattern '"alipay"\s*:\s*s\.alipay\.HealthStatus\(\)'
Require-Match -Name "server_healthz_alipay_payment_gate" -Content $server -Pattern '"alipay_payment_gate"\s*:\s*alipayPaymentOrderGateStatus\(\)'
Require-Match -Name "server_alipay_app_pay_api" -Content $payments -Pattern "alipay\.trade\.app\.pay"
Require-Match -Name "server_alipay_trade_query_api" -Content $payments -Pattern "alipay\.trade\.query"
Require-Match -Name "server_alipay_trade_refund_api" -Content $payments -Pattern "alipay\.trade\.refund"
Require-Match -Name "server_alipay_bill_download_api" -Content $payments -Pattern "alipay\.data\.dataservice\.bill\.downloadurl\.query"
Require-Match -Name "server_alipay_openapi_response_sign_required" -Content $payments -Pattern "alipay response sign missing"
Require-Match -Name "server_alipay_refund_fund_change_required" -Content $payments -Pattern "fund_change"
Require-Match -Name "server_alipay_order_format_json" -Content $payments -Pattern 'format"\s*,\s*"json'
Require-Match -Name "server_alipay_order_biz_seller_id" -Content $payments -Pattern 'SellerID:\s*c\.sellerID'
Require-Match -Name "server_alipay_order_sign_keeps_sign_type" -Content $payments -Pattern 'BuildAppPayOrder(?s:.*?)alipaySignContent\(params,\s*"sign"\)'
Require-Match -Name "server_alipay_payment_gate_default_closed" -Content $payments -Pattern "ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES"
Require-Match -Name "server_alipay_payment_gate_requires_user_and_build" -Content $payments -Pattern "len\(allowedUsers\)\s*==\s*0\s*\|\|\s*len\(allowedBuildTypes\)\s*==\s*0"
Require-Match -Name "server_alipay_payment_limited_debug_only" -Content $payments -Pattern 'alipayPaymentOrderGateAllows(?s:.*?)normalizedBuildType\s*!=\s*"debug"'
Require-Match -Name "server_alipay_payment_limited_status_requires_debug" -Content $payments -Pattern 'alipayPaymentOrderGateStatus(?s:.*?)allowedBuildTypes\["debug"\]'
Require-Match -Name "server_alipay_payment_public_switch" -Content $payments -Pattern "ALIPAY_PAYMENT_PUBLIC_ENABLED"
Require-Match -Name "server_alipay_payment_test_amount_guard" -Content $payments -Pattern "ALIPAY_PAYMENT_TEST_AMOUNT_CENTS"
Require-Match -Name "server_alipay_payment_test_amount_debug_only" -Content $payments -Pattern 'normalizedBuildType\s*!=\s*"debug"'
Require-Match -Name "server_alipay_test_order_metadata" -Content $payments -Pattern "is_test_order"
Require-Match -Name "server_alipay_sign_type_rsa2" -Content $payments -Pattern 'sign_type"\s*,\s*"RSA2'
Require-Match -Name "server_alipay_seller_required" -Content $payments -Pattern 'sellerID\s*==\s*""'
Require-Match -Name "server_alipay_enabled_requires_seller" -Content $payments -Pattern "c\.sellerID\s*!="
Require-Match -Name "server_alipay_grant_retry_guard" -Content $payments -Pattern "PAYMENT_GRANT_IN_PROGRESS"
Require-Match -Name "server_alipay_grant_claim_timestamp" -Content $payments -Pattern "COALESCE\(grant_claimed_at,\s*updated_at\)"
Require-Match -Name "server_manual_payment_grant_uses_same_grant_path" -Content $payments -Pattern "manuallyGrantPaidPaymentOrder(?s:.*?)grantPaidPaymentOrder"
Require-Match -Name "server_payment_order_create_gate" -Content $payments -Pattern "WithPaymentOrderCreateGate(?s:.*?)GET_LOCK"
Require-Match -Name "server_payment_order_busy_code" -Content $payments -Pattern "PAYMENT_ORDER_BUSY"
Require-Match -Name "server_payment_pending_order_auto_close_guard" -Content $payments -Pattern "CloseUnpaidPendingPaymentOrdersForProduct(?s:.*?)LOCAL_REPLACED"
Require-NoMatch -Name "server_payment_no_invisible_pending_order_block" -Content $payments -Pattern "PAYMENT_PENDING_ORDER_EXISTS"
Require-NoMatch -Name "payment_docs_no_old_pending_block_copy" -Content $projectPaymentDocs -Pattern "近 30 分钟.?同类待支付|已有待支付订单，请先完成|PAYMENT_PENDING_ORDER_EXISTS"
Require-Match -Name "server_plus_to_pro_prorated_amount" -Content $payments -Pattern "plusToProUpgradeAmountCents"
Require-Match -Name "server_plus_remaining_discount" -Content $payments -Pattern "plusRemainingValueDiscountCents"
Require-Match -Name "server_plus_to_pro_order_amount_resolved_before_create" -Content $payments -Pattern "ResolvePaymentProductForOrder(?s:.*?)CreatePaymentOrder"
Require-Match -Name "payment_current_docs_plus_to_pro_discount" -Content $currentPaymentDocs -Pattern "Plus 升 Pro(?s:.*?)剩余有效天数(?s:.*?)金额抵扣"
Require-Match -Name "payment_current_docs_plus_to_pro_min_delta" -Content $currentPaymentDocs -Pattern "最低补差价 10\.00 元"
Require-Match -Name "payment_current_docs_upgrade_credits_inactive" -Content $currentPaymentDocs -Pattern "补偿次数(?s:.*?)退出当前业务|当前扣次顺序(?s:.*?)每日额度(?s:.*?)加油包"
Require-NoMatch -Name "payment_current_docs_no_old_plus_to_pro_compensation_copy" -Content $currentPaymentDocs -Pattern "不做[^。\r\n]*现金[^。\r\n]*抵扣|按 Pro 开通价升级|Plus 升 Pro 补偿次数按既有账本|剩余[^。\r\n]*折成(?!金额抵扣)[^。\r\n]*补偿次数|每日额度\s*[-→]\s*历史补偿|历史补偿\s*[-→]\s*加油包"
Require-Match -Name "server_alipay_no_paid_downgrade" -Content $payments -Pattern 'order\.Status\s*==\s*paymentStatusPaid(?s:.*?)last_notify_json'
Require-Match -Name "server_alipay_refund_unknown_on_failed_response" -Content $payments -Pattern "MarkPaymentRefundUnknown"
Require-Match -Name "server_alipay_refund_order_update_tx" -Content $payments -Pattern "CreatePaymentRefundPending(?s:.*?)BeginTx(?s:.*?)UPDATE payment_orders"
Require-Match -Name "server_payment_log_suffix" -Content $payments -Pattern "paymentIDLogSuffix"
Require-NoMatch -Name "server_no_full_payment_id_logs" -Content $payments -Pattern 'logger\.(Info|Warn|Error)\([^\\r\\n]*"(outTradeNo|tradeNo)"'
Require-Match -Name "db_payment_orders" -Content $migration -Pattern "CREATE TABLE IF NOT EXISTS payment_orders"
Require-Match -Name "db_payment_notifications" -Content $migration -Pattern "CREATE TABLE IF NOT EXISTS payment_notifications"
Require-Match -Name "db_payment_orders_grant_claimed_at" -Content $migration -Pattern "grant_claimed_at BIGINT NULL"
Require-Match -Name "db_payment_orders_test_metadata" -Content $migration -Pattern "is_test_order TINYINT"
Require-Match -Name "db_payment_orders_test_metadata_upgrade" -Content $testMetadataMigration -Pattern "ALTER TABLE payment_orders ADD COLUMN is_test_order"
Require-Match -Name "db_payment_orders_test_metadata_index" -Content $testMetadataMigration -Pattern "idx_payment_orders_test_created"
Require-Match -Name "db_payment_orders_refund_status" -Content $opsClosureMigration -Pattern "refund_status"
Require-Match -Name "db_payment_orders_last_query_error" -Content $opsClosureMigration -Pattern "last_query_error"
Require-Match -Name "db_payment_refunds" -Content $opsClosureMigration -Pattern "CREATE TABLE IF NOT EXISTS payment_refunds"

Require-Match -Name "admin_orders_mentions_payment" -Content $adminMain -Pattern "支付订单"
Require-Match -Name "admin_payment_grant_button" -Content $adminMain -Pattern "grant-payment-order"
Require-Match -Name "admin_payment_grant_confirmation" -Content $adminMain -Pattern 'confirmation:\s*"补发"'
Require-Match -Name "admin_payment_query_button" -Content $adminMain -Pattern "query-payment-order"
Require-Match -Name "admin_payment_refund_button" -Content $adminMain -Pattern "refund-payment-order"
Require-Match -Name "admin_payment_test_order_refund_action" -Content $adminMain -Pattern 'row\.is_test_order === true(?s:.*?)grantStatus !== "success" \|\| isTestOrder'
Require-Match -Name "admin_payment_reconciliation_panel" -Content $adminMain -Pattern "payment-reconciliation"
Require-Match -Name "admin_payment_close_expired_panel" -Content $adminMain -Pattern "payment-close-expired"
Require-Match -Name "admin_order_types_provider_fields" -Content $adminTypes -Pattern "provider_trade_no"
Require-Match -Name "runbook_alipay_notify_url" -Content $paymentsRunbook -Pattern "https://api\.nongjiqiancha\.cn/api/payments/alipay/notify"
Require-Match -Name "runbook_alipay_app_pay_api" -Content $paymentsRunbook -Pattern "alipay\.trade\.app\.pay"
Require-Match -Name "runbook_formal_validation_gate" -Content $paymentsRunbook -Pattern "正式收费开放前"
Require-Match -Name "runbook_manual_payment_grant_scope" -Content $paymentsRunbook -Pattern "status=paid"
Require-Match -Name "site_third_party_mentions_alipay" -Content $thirdPartyPage -Pattern "支付宝 APP 支付 SDK"
Require-NoMatch -Name "site_third_party_no_old_no_payment_sdk_copy" -Content $thirdPartyPage -Pattern "当前版本不接入[^。]*支付[^。]*第三方 SDK"

if (-not $SkipPublicHealth) {
    try {
        $healthUrl = ($BaseUrl.TrimEnd("/") + "/healthz")
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec $TimeoutSec -Headers @{
            "User-Agent" = "nongjiqiancha-payment-readiness/1.1"
        }
        Write-Host "public_healthz_alipay=$($response.alipay)"
        if ($null -eq $response.alipay_payment_gate) {
            Add-ErrorItem "public healthz missing alipay_payment_gate; deploy backend payment gate before payment test"
        } else {
            $publicGate = [string]$response.alipay_payment_gate
            Write-Host "public_healthz_alipay_payment_gate=$publicGate"
            if (@("closed", "limited", "public") -notcontains $publicGate) {
                Add-ErrorItem "public healthz alipay_payment_gate has unexpected value: $publicGate"
            }
            if ($publicGate -eq "public" -and -not $AllowPublicPayment) {
                Add-ErrorItem "public payment gate is open; rerun with -AllowPublicPayment only for formal charging release gate"
            }
        }
        Write-Host "public_healthz_dev_order_endpoints=$($response.dev_order_endpoints)"
        if ($response.dev_order_endpoints -ne $false) {
            Add-ErrorItem "public healthz dev_order_endpoints must be false"
        }

        $orderProbeUrl = ($BaseUrl.TrimEnd("/") + "/api/payments/alipay/orders")
        try {
            Invoke-RestMethod -Method Post -Uri $orderProbeUrl -TimeoutSec $TimeoutSec -ContentType "application/json" -Body '{"product_type":"renew_plus","client_build_type":"debug"}' -Headers @{
                "User-Agent" = "nongjiqiancha-payment-readiness/1.1"
            } | Out-Null
            Add-ErrorItem "unauthenticated payment order probe unexpectedly succeeded"
        } catch {
            $statusCode = 0
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            Write-Host "public_payment_order_unauth_status=$statusCode"
            if ($statusCode -ne 401) {
                Add-ErrorItem "unauthenticated payment order probe should return 401, got $statusCode"
            }
        }

        if ($RunPublicNotifyProbe) {
            $notifyProbeUrl = ($BaseUrl.TrimEnd("/") + "/api/payments/alipay/notify")
            try {
                $notifyProbe = Invoke-WebRequest -Method Post -Uri $notifyProbeUrl -TimeoutSec $TimeoutSec -ContentType "application/x-www-form-urlencoded" -Body "out_trade_no=NJPROBE&notify_id=PROBE&sign=bad" -Headers @{
                    "User-Agent" = "nongjiqiancha-payment-readiness/1.1"
                } -UseBasicParsing
                Write-Host "public_alipay_notify_fake_status=$($notifyProbe.StatusCode)"
                if ([int]$notifyProbe.StatusCode -ne 200) {
                    Add-ErrorItem "fake alipay notify probe should return HTTP 200, got $($notifyProbe.StatusCode)"
                }
                if (($notifyProbe.Content | Out-String).Trim() -ne "failure") {
                    Add-ErrorItem "fake alipay notify probe should return failure"
                }
            } catch {
                $statusCode = 0
                if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
                Write-Host "public_alipay_notify_fake_status=$statusCode"
                Add-ErrorItem "fake alipay notify probe should return HTTP 200 + failure, got status=$statusCode"
            }
        } else {
            Write-Host "public_alipay_notify_fake_status=skipped"
        }
    } catch {
        $message = "public healthz probe failed: $($_.Exception.Message)"
        if ($StrictPublicHealth) {
            Add-ErrorItem $message
        } else {
            Add-WarningItem $message
        }
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
Write-PrereqGroupStatus -Name "alipay_test_gate_prereqs" -Requirements @(
    @{ Label = "payment_allowed_build_types"; AnyOf = @("ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES") },
    @{ Label = "payment_allowed_user_ids"; AnyOf = @("ALIPAY_PAYMENT_ALLOWED_USER_IDS") }
)
if (Test-AnyEnvironmentVariable -Names @("ALIPAY_PAYMENT_PUBLIC_ENABLED") -and -not $AllowPublicPayment) {
    Add-WarningItem "local ALIPAY_PAYMENT_PUBLIC_ENABLED is set; formal charging still requires explicit -AllowPublicPayment gate"
}
if (Test-AnyEnvironmentVariable -Names @("ALIPAY_PAYMENT_TEST_AMOUNT_CENTS") -and -not $AllowTestAmount) {
    Add-WarningItem "local ALIPAY_PAYMENT_TEST_AMOUNT_CENTS is set; remove it after 0.01 payment test"
}
Write-Host "payment_readiness_note=Code and documents are aligned for Alipay APP Pay operations, but production charging still requires live env config, callback validation, reconciliation, refund SOP, and manual acceptance."

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
