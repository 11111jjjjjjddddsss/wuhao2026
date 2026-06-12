param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

function Add-Failure {
    param(
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Message
    )
    $Failures.Add($Message) | Out-Null
}

function Require-Match {
    param(
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Content,
        [string]$Pattern,
        [string]$Message
    )
    if ($Content -notmatch $Pattern) {
        Add-Failure $Failures $Message
    }
}

function Require-NoMatch {
    param(
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Content,
        [string]$Pattern,
        [string]$Message
    )
    if ($Content -match $Pattern) {
        Add-Failure $Failures $Message
    }
}

function Require-Manifest-Activity {
    param(
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Content,
        [string]$ManifestPath,
        [string]$ActivityName,
        [string]$ThemeName
    )
    $escapedActivity = [regex]::Escape($ActivityName)
    $escapedTheme = [regex]::Escape($ThemeName)
    $pattern = '<activity\b(?=[^>]*android:name="' + $escapedActivity + '")(?=[^>]*android:theme="' + $escapedTheme + '")(?=[^>]*android:exported="false")[^>]*>'
    if ($Content -notmatch $pattern) {
        Add-Failure $Failures "Merged manifest $ManifestPath must keep $ActivityName exported=false with theme $ThemeName."
    }
}

$failures = [System.Collections.Generic.List[string]]::new()

$buildFile = Join-Path $RepoRoot "app/build.gradle.kts"
$manifestFile = Join-Path $RepoRoot "app/src/main/AndroidManifest.xml"
$networkSecurityFile = Join-Path $RepoRoot "app/src/main/res/xml/network_security_config.xml"
$backupRulesFile = Join-Path $RepoRoot "app/src/main/res/xml/backup_rules.xml"
$dataExtractionRulesFile = Join-Path $RepoRoot "app/src/main/res/xml/data_extraction_rules.xml"
$idManagerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/IdManager.kt"
$sessionApiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/SessionApi.kt"
$fusionClientFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/FusionOneLoginClient.kt"
$mainActivityFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MainActivity.kt"
$privacyConsentFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PrivacyConsentGate.kt"
$pendingWorkerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PendingChatSendWorker.kt"
$loginScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/LoginScreen.kt"
$chatScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$debugManifestFile = Join-Path $RepoRoot "app/src/debug/AndroidManifest.xml"
$debugNetworkSecurityFile = Join-Path $RepoRoot "app/src/debug/res/xml/network_security_config.xml"

foreach ($path in @($buildFile, $manifestFile, $networkSecurityFile, $backupRulesFile, $dataExtractionRulesFile, $idManagerFile, $sessionApiFile, $fusionClientFile, $mainActivityFile, $privacyConsentFile, $pendingWorkerFile, $loginScreenFile, $chatScreenFile)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure $failures "Missing required file: $path"
    }
}

if ($failures.Count -eq 0) {
    $build = Get-Content -LiteralPath $buildFile -Raw
    $manifest = Get-Content -LiteralPath $manifestFile -Raw
    $networkSecurity = Get-Content -LiteralPath $networkSecurityFile -Raw
    $backupRules = Get-Content -LiteralPath $backupRulesFile -Raw
    $dataExtractionRules = Get-Content -LiteralPath $dataExtractionRulesFile -Raw
    $idManager = Get-Content -LiteralPath $idManagerFile -Raw
    $sessionApi = Get-Content -LiteralPath $sessionApiFile -Raw
    $fusionClient = Get-Content -LiteralPath $fusionClientFile -Raw
    $mainActivity = Get-Content -LiteralPath $mainActivityFile -Raw
    $privacyConsent = Get-Content -LiteralPath $privacyConsentFile -Raw
    $pendingWorker = Get-Content -LiteralPath $pendingWorkerFile -Raw
    $loginScreen = Get-Content -LiteralPath $loginScreenFile -Raw
    $chatScreen = Get-Content -LiteralPath $chatScreenFile -Raw

    Require-Match $failures $build 'val\s+defaultUploadBaseUrl\s*=\s*"https://api\.nongjiqiancha\.cn"' `
        "Android default UPLOAD_BASE_URL must remain https://api.nongjiqiancha.cn."
    Require-Match $failures $build 'val\s+uploadBaseUrl\s*=\s*defaultUploadBaseUrl' `
        "Android UPLOAD_BASE_URL must not be switched by Gradle property or environment variable."
    Require-NoMatch $failures $build 'findProperty\s*\(\s*"UPLOAD_BASE_URL"\s*\)|System\.getenv\s*\(\s*"UPLOAD_BASE_URL"\s*\)' `
        "Android UPLOAD_BASE_URL must not be user-overridable for normal builds."
    Require-Match $failures $build 'applicationId\s*=\s*"com\.nongjiqiancha"' `
        "Android applicationId must remain com.nongjiqiancha."
    Require-Match $failures $build 'buildConfigField\s*\(\s*"boolean"\s*,\s*"USE_BACKEND_AB"\s*,\s*"true"\s*\)' `
        "USE_BACKEND_AB must remain enabled for debug and release."
    Require-NoMatch $failures $build 'buildConfigField\s*\(\s*"boolean"\s*,\s*"USE_BACKEND_AB"\s*,\s*[^)]*false' `
        "USE_BACKEND_AB must not be disabled in any build type."
    Require-Match $failures $build 'debug\s*\{[\s\S]*?signingConfigs\.findByName\s*\(\s*"release"\s*\)\?\.let\s*\{\s*signingConfig\s*=\s*it\s*\}[\s\S]*?buildConfigField\s*\(\s*"boolean"\s*,\s*"ENABLE_FUSION_ONE_LOGIN"\s*,\s*releaseSigningConfigured\.toString\(\)\s*\)' `
        "Debug builds must use release signing when configured and enable one-click login only under that condition."
    Require-Match $failures $build 'release\s*\{[\s\S]*?buildConfigField\s*\(\s*"boolean"\s*,\s*"ENABLE_FUSION_ONE_LOGIN"\s*,\s*"true"\s*\)' `
        "Release builds must keep one-click login enabled."
    Require-Match $failures $build 'Release signing is not configured' `
        "Release packaging must fail when release signing is missing."
    Require-Match $failures $build 'Release UPLOAD_BASE_URL must remain an https production URL' `
        "Release packaging must guard the production HTTPS backend URL."

    Require-Match $failures $manifest 'android:networkSecurityConfig="@xml/network_security_config"' `
        "Main manifest must keep the app-level networkSecurityConfig."
    Require-Match $failures $manifest 'android:allowBackup="false"' `
        "Main manifest must keep allowBackup=false so old local UI state is not restored after clear-data/reinstall."
    Require-Match $failures $manifest 'android:dataExtractionRules="@xml/data_extraction_rules"' `
        "Main manifest must keep explicit dataExtractionRules."
    Require-Match $failures $manifest 'android:fullBackupContent="@xml/backup_rules"' `
        "Main manifest must keep explicit fullBackupContent rules."
    Require-Match $failures $manifest 'android\.permission\.READ_PHONE_STATE' `
        "Manifest must declare READ_PHONE_STATE for one-click login environment checks."
    Require-Match $failures $manifest 'android\.permission\.ACCESS_NETWORK_STATE' `
        "Manifest must declare ACCESS_NETWORK_STATE for login environment checks."
    Require-Match $failures $manifest 'android\.permission\.ACCESS_WIFI_STATE' `
        "Manifest must declare ACCESS_WIFI_STATE for Aliyun SDK network checks."
    Require-Match $failures $manifest 'android\.permission\.CHANGE_NETWORK_STATE' `
        "Manifest must declare CHANGE_NETWORK_STATE for Aliyun SDK network switching compatibility."

    Require-Match $failures $networkSecurity '<base-config\s+cleartextTrafficPermitted="false"\s*/>' `
        "Network security base config must keep cleartextTrafficPermitted=false."
    Require-Match $failures $networkSecurity '<domain\s+includeSubdomains="true">enrichgw\.10010\.com</domain>' `
        "Network security config must keep the Unicom one-click-login gateway exception."
    Require-Match $failures $networkSecurity '<domain\s+includeSubdomains="true">onekey\.cmpassport\.com</domain>' `
        "Network security config must keep the China Mobile one-click-login gateway exception."
    Require-Match $failures $networkSecurity '<domain\s+includeSubdomains="true">uac\.189\.cn</domain>' `
        "Network security config must keep the China Telecom one-click-login gateway exception."
    $domains = [regex]::Matches($networkSecurity, '<domain\b[^>]*>([^<]+)</domain>') | ForEach-Object { $_.Groups[1].Value.Trim() }
    $allowedDomains = @("enrichgw.10010.com", "onekey.cmpassport.com", "uac.189.cn")
    foreach ($domain in $domains) {
        if ($allowedDomains -notcontains $domain) {
            Add-Failure $failures "Unexpected cleartext domain in network_security_config.xml: $domain"
        }
    }
    if (Test-Path -LiteralPath $debugManifestFile) {
        Add-Failure $failures "Debug manifest must not override production login/network behavior: $debugManifestFile"
    }
    if (Test-Path -LiteralPath $debugNetworkSecurityFile) {
        Add-Failure $failures "Debug network security config must not diverge from release: $debugNetworkSecurityFile"
    }
    foreach ($backupDomain in @("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")) {
        Require-Match $failures $backupRules ('<exclude\s+domain="' + [regex]::Escape($backupDomain) + '"\s+path="\."\s*/>') `
            "backup_rules.xml must exclude $backupDomain from Android backup."
    }
    foreach ($sectionName in @("cloud-backup", "device-transfer")) {
        Require-Match $failures $dataExtractionRules ('<' + $sectionName + '>') `
            "data_extraction_rules.xml must keep <$sectionName> rules."
        foreach ($backupDomain in @("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")) {
            Require-Match $failures $dataExtractionRules ('<exclude\s+domain="' + [regex]::Escape($backupDomain) + '"\s+path="\."\s*/>') `
                "data_extraction_rules.xml must exclude $backupDomain from Android $sectionName."
        }
    }

    $mergedManifestPaths = @(
        (Join-Path $RepoRoot "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
        (Join-Path $RepoRoot "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"),
        (Join-Path $RepoRoot "app/build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"),
        (Join-Path $RepoRoot "app/build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml")
    )
    foreach ($manifestPath in $mergedManifestPaths) {
        if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            Add-Failure $failures "Missing generated Android manifest for parity validation: $manifestPath. Run :app:processDebugManifest :app:processReleaseManifest :app:processDebugManifestForPackage :app:processReleaseManifestForPackage before this script."
            continue
        }
        $mergedManifest = Get-Content -LiteralPath $manifestPath -Raw
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.mobile.auth.gatewayauth.LoginAuthActivity" "@style/Theme.NongjiQianwen.FusionAuthDialog"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.mobile.auth.gatewayauth.PrivacyDialogActivity" "@style/Theme.NongjiQianwen.FusionAuthDialog"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.mobile.auth.gatewayauth.PrivacyActivity" "@style/Theme.NongjiQianwen.FusionAuthDialog"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.alicom.fusion.auth.numberauth.FusionNumberAuthActivity" "@style/Theme.NongjiQianwen.FusionNumberAuth"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.alicom.fusion.auth.smsauth.FusionSmsActivity" "@style/Theme.NongjiQianwen.FusionNumberAuth"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.alicom.fusion.auth.upsms.AlicomFusionUpSmsActivity" "@style/Theme.NongjiQianwen.FusionNumberAuth"
        Require-Manifest-Activity $failures $mergedManifest $manifestPath "com.alicom.fusion.auth.graphauth.FusionGraphAuthActivity" "@style/Theme.NongjiQianwen.FusionNumberAuth"
    }

    Require-Match $failures $sessionApi 'endpoint\s*=\s*"/api/auth/fusion/login"' `
        "Android one-click login must submit the final SDK token to /api/auth/fusion/login."
    Require-NoMatch $failures $sessionApi '/api/auth/fusion/verify' `
        "Android 100001 one-click login must not call the half-way /api/auth/fusion/verify endpoint."
    Require-Match $failures $sessionApi 'accountUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "Android login must only persist backend account IDs with the acct_ prefix."
    Require-Match $failures $sessionApi 'non_account_user_id' `
        "Android login must report and reject non-account user IDs instead of saving a legacy UUID session."
    Require-Match $failures $idManager 'saveAuthSession[\s\S]*?!normalizedUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must refuse to save a logged-in session for non-account user IDs."
    Require-Match $failures $idManager 'hasValidAuthSession[\s\S]*?authUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must treat stale non-account auth_user_id values as logged-out."
    Require-Match $failures $sessionApi 'auth\.sms_login_success' `
        "Android SMS login success should be logged for login handoff diagnostics."
    Require-Match $failures $sessionApi 'auth\.fusion_login_success' `
        "Android fusion login success should be logged for login handoff diagnostics."
    Require-Match $failures $loginScreen 'code\.length\s*!=\s*6' `
        "SMS login must require exactly 6 verification-code digits before calling the backend."
    Require-NoMatch $failures $loginScreen 'code\.length\s*<\s*4' `
        "SMS login must not accept 4/5 digit codes and send avoidable failed backend requests."
    Require-Match $failures $loginScreen 'if\s*\(\s*!agreed\s*\)[\s\S]*?请先同意服务协议和隐私政策' `
        "Login actions must remain gated by the service agreement/privacy checkbox."
    Require-Match $failures $mainActivity 'PrivacyConsentStore\.isAccepted\s*\(\s*this\s*\)' `
        "MainActivity must check first-launch privacy consent before entering login/chat."
    Require-Match $failures $mainActivity 'initializePostPrivacyConsentRuntime\s*\(\s*\)[\s\S]*?IdManager\.init\s*\(\s*this\s*\)[\s\S]*?AppCrashReporter\.flushPendingReport' `
        "IdManager init and crash-log flushing must stay behind accepted privacy consent."
    Require-Match $failures $mainActivity 'if\s*\(\s*privacyAccepted\s*\)[\s\S]*?LoginGate' `
        "MainActivity must render LoginGate only after privacy consent has been accepted."
    Require-Match $failures $privacyConsent 'object\s+PrivacyConsentStore[\s\S]*?CURRENT_VERSION\s*=\s*1[\s\S]*?fun\s+isAccepted' `
        "PrivacyConsentStore must keep a versioned local consent flag."
    Require-Match $failures $privacyConsent '同意并继续' `
        "First-launch privacy gate must provide an explicit agree-and-continue action."
    Require-Match $failures $privacyConsent '不同意，退出' `
        "First-launch privacy gate must provide an explicit decline/exit action."
    Require-Match $failures $privacyConsent 'HamburgerServiceAgreementContent[\s\S]*?HamburgerPrivacyPolicyContent' `
        "First-launch privacy gate must let users read the same service agreement and privacy policy content as settings/login."
    Require-Match $failures $pendingWorker '!PrivacyConsentStore\.isAccepted\s*\(\s*applicationContext\s*\)[\s\S]*?Result\.retry\(\)[\s\S]*?IdManager\.init' `
        "Pending background chat sends must not initialize identity or call backend before first-launch privacy consent is accepted."
    Require-Match $failures $fusionClient 'override\s+fun\s+onVerifySuccess[\s\S]*?SessionApi\.loginWithFusionVerifyToken' `
        "Fusion one-click login must exchange the final onVerifySuccess token through the backend login endpoint."
    Require-Match $failures $fusionClient 'override\s+fun\s+onHalfWayVerifySuccess[\s\S]*?auth\.fusion_halfway_unexpected[\s\S]*?verifyResult\?\.verifyResult' `
        "Unexpected half-way callbacks must only be logged and continued, not treated as the primary login path."
    $halfwayMatch = [regex]::Match(
        $fusionClient,
        'override\s+fun\s+onHalfWayVerifySuccess[\s\S]*?override\s+fun\s+onVerifyFailed',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    if (!$halfwayMatch.Success) {
        Add-Failure $failures "Unable to locate onHalfWayVerifySuccess block for parity validation."
    } elseif ($halfwayMatch.Value -match 'SessionApi\.|/api/auth/fusion/login|/api/auth/fusion/verify|VerifyWithFusionAuthToken') {
        Add-Failure $failures "onHalfWayVerifySuccess must not call backend login/verify or consume the token."
    }
    Require-Match $failures $fusionClient 'model\.setProtocolChecked\s*\(\s*false\s*\)' `
        "Aliyun SDK protocol checkbox must remain visible and unchecked by default."
    Require-Match $failures $fusionClient '\.hiddenSwtichLogin\s*\(\s*true\s*\)' `
        "Aliyun SDK built-in switch/more-login entry must stay hidden so SMS fallback remains in the app page."
    Require-Match $failures $fusionClient '\.setProtocolAction\s*\(\s*PROTOCOL_ACTION\s*\)[\s\S]*?\.setPackageName\s*\(\s*BuildConfig\.APPLICATION_ID\s*\)' `
        "Custom Aliyun protocolAction must also set packageName to the app applicationId."
    Require-Match $failures $fusionClient 'hasVpnTransport\s*->\s*"vpn_active"' `
        "Fusion one-click login must block VPN/proxy before starting the Aliyun SDK."
    Require-Match $failures $fusionClient 'hasProxyConfigured\s*->\s*"proxy_active"' `
        "Fusion one-click login must block configured system proxies before starting the Aliyun SDK."
    Require-Match $failures $fusionClient 'getLinkProperties\s*\(\s*it\s*\)\?\.httpProxy' `
        "Fusion one-click login must detect Android per-network proxy settings, not only Java proxy properties."
    Require-Match $failures $fusionClient 'android\.net\.Proxy\.getHost\s*\(\s*context\s*\)' `
        "Fusion one-click login must retain a legacy Android proxy fallback for ROMs that expose proxy settings outside LinkProperties."
    Require-Match $failures $fusionClient 'hasAnyCellularInternetTransport' `
        "Fusion one-click login must inspect all networks so 4G+WiFi mixed environments are not treated as WiFi-only."
    Require-Match $failures $fusionClient 'fun\s+precheckOneLoginEnvironment\s*\(\s*context:\s*Context\s*\):\s*String\?' `
        "Fusion one-click login must expose a pre-permission environment check for obvious SMS fallback cases."
    Require-Match $failures $loginScreen 'precheckOneLoginEnvironment\s*\(\s*context\s*\)[\s\S]*?ContextCompat\.checkSelfPermission' `
        "LoginScreen must check one-click environment before requesting READ_PHONE_STATE, so WiFi/VPN users go straight to SMS fallback."
    Require-Match $failures $fusionClient 'blockReason\(\):\s*String\?\s*=[\s\S]*?!hasAnyCellularInternetTransport\s*->\s*"no_cellular_data"' `
        "Fusion one-click login must fall back to SMS when no usable cellular data path is available."
    Require-Match $failures $fusionClient 'fun\s+warningReason\(\):\s*String\?\s*=[\s\S]*?!hasCellularTransport\s*&&\s*hasAnyCellularInternetTransport\s*->\s*"wifi_with_cellular_available"' `
        "Fusion one-click login should warn, not hard-block, 4G+WiFi mixed environments."
    Require-Match $failures $fusionClient 'if\s*\(\s*BuildConfig\.DEBUG\s*\)\s*\{[\s\S]*?getErrorMsg\(\)[\s\S]*?getInnerMsg\(\)[\s\S]*?\}' `
        "Fusion release logcat must not print raw Aliyun SDK errorMsg/innerMsg; keep them debug-only."

    Require-Match $failures $chatScreen 'BuildConfig\.DEBUG\s*&&\s*uiCopyPreviewVisible' `
        "Debug-only preview panel must stay behind BuildConfig.DEBUG."
    Require-Match $failures $chatScreen 'Modifier\.clickable\s*\{\s*uiCopyPreviewVisible\s*=\s*true\s*\}' `
        "Debug-only preview entry point must remain an explicit click hook."
    Require-NoMatch $failures $chatScreen 'FAKE_STREAM_TEXT|fakeStreamJob|launchLocalFakeStream|recoverStreamingDraftAsCompletedSnapshot|completeStreamingImmediatelyFromBackground|LOCAL_STREAM_|takeTypewriterToken|LocalStreamFeedStep' `
        "ChatScreen must not restore local fake streaming/fake assistant copy; remote failure should use snapshot recovery or retry state."
}

if ($failures.Count -gt 0) {
    Write-Error "Android build parity check failed:`n - $($failures -join "`n - ")"
    exit 1
}

Write-Host "Android build parity check passed."
Write-Host "Debug and release keep the same production backend, account/login path, and network security baseline."
