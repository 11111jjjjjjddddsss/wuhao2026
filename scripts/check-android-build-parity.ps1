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
$appUpdateInstallerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/AppUpdateInstaller.kt"
$fusionClientFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/FusionOneLoginClient.kt"
$fusionProtocolActivityFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/FusionAuthProtocolActivity.kt"
$mainActivityFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MainActivity.kt"
$privacyConsentFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PrivacyConsentGate.kt"
$pendingWorkerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PendingChatSendWorker.kt"
$todayAgriCardUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/TodayAgriCardUi.kt"
$userMessageImageUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/UserMessageImageUi.kt"
$chatImagePreviewFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatImagePreview.kt"
$chatComposerCoordinatorFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerCoordinator.kt"
$chatComposerPanelFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerPanel.kt"
$loginScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/LoginScreen.kt"
$chatScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$debugManifestFile = Join-Path $RepoRoot "app/src/debug/AndroidManifest.xml"
$debugNetworkSecurityFile = Join-Path $RepoRoot "app/src/debug/res/xml/network_security_config.xml"

foreach ($path in @($buildFile, $manifestFile, $networkSecurityFile, $backupRulesFile, $dataExtractionRulesFile, $idManagerFile, $sessionApiFile, $appUpdateInstallerFile, $fusionClientFile, $fusionProtocolActivityFile, $mainActivityFile, $privacyConsentFile, $pendingWorkerFile, $todayAgriCardUiFile, $userMessageImageUiFile, $chatImagePreviewFile, $chatComposerCoordinatorFile, $chatComposerPanelFile, $loginScreenFile, $chatScreenFile)) {
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
    $appUpdateInstaller = Get-Content -LiteralPath $appUpdateInstallerFile -Raw
    $fusionClient = Get-Content -LiteralPath $fusionClientFile -Raw
    $fusionProtocolActivity = Get-Content -LiteralPath $fusionProtocolActivityFile -Raw
    $mainActivity = Get-Content -LiteralPath $mainActivityFile -Raw
    $privacyConsent = Get-Content -LiteralPath $privacyConsentFile -Raw
    $pendingWorker = Get-Content -LiteralPath $pendingWorkerFile -Raw
    $todayAgriCardUi = Get-Content -LiteralPath $todayAgriCardUiFile -Raw
    $userMessageImageUi = Get-Content -LiteralPath $userMessageImageUiFile -Raw
    $chatImagePreview = Get-Content -LiteralPath $chatImagePreviewFile -Raw
    $chatComposerCoordinator = Get-Content -LiteralPath $chatComposerCoordinatorFile -Raw
    $chatComposerPanel = Get-Content -LiteralPath $chatComposerPanelFile -Raw
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
    Require-Match $failures $build 'debug\s*\{(?s:.*?)signingConfigs\.findByName\s*\(\s*"release"\s*\)\?\.let\s*\{\s*signingConfig\s*=\s*it\s*\}(?s:.*?)buildConfigField\s*\(\s*"boolean"\s*,\s*"ENABLE_FUSION_ONE_LOGIN"\s*,\s*releaseSigningConfigured\.toString\(\)\s*\)' `
        "Debug builds must use release signing when configured and enable one-click login only under that condition."
    Require-Match $failures $build 'release\s*\{(?s:.*?)buildConfigField\s*\(\s*"boolean"\s*,\s*"ENABLE_FUSION_ONE_LOGIN"\s*,\s*"true"\s*\)' `
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
    Require-Match $failures $idManager 'saveAuthSession(?s:.*?)!normalizedUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must refuse to save a logged-in session for non-account user IDs."
    Require-Match $failures $idManager 'hasValidAuthSession(?s:.*?)authUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must treat stale non-account auth_user_id values as logged-out."
    Require-Match $failures $sessionApi 'auth\.sms_login_success' `
        "Android SMS login success should be logged for login handoff diagnostics."
    Require-Match $failures $sessionApi 'auth\.fusion_login_success' `
        "Android fusion login success should be logged for login handoff diagnostics."
    Require-Match $failures $sessionApi 'latestCode\s*>\s*BuildConfig\.VERSION_CODE(?s:.*?)normalizeAppUpdateSha256\s*\(\s*apkSha256\s*\)\s*!=\s*null(?s:.*?)sizeBytes\s*>\s*0L(?s:.*?)sizeBytes\s*<=\s*APP_UPDATE_MAX_APK_DOWNLOAD_BYTES' `
        "Android app update availability must require a newer version, SHA-256 and a positive bounded file size."
    Require-Match $failures $appUpdateInstaller 'DownloadFailureReason\.MissingReleaseMetadata' `
        "Android app update downloader must fail closed when release metadata is incomplete."
    Require-NoMatch $failures $appUpdateInstaller 'update\.fileSizeBytes\s*\?:\s*0L' `
        "Android app update downloader must not fall back to unknown APK size."
    Require-NoMatch $failures $appUpdateInstaller 'expectedSha256\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip SHA-256 verification when SHA metadata is missing or invalid."
    Require-NoMatch $failures $appUpdateInstaller 'expectedVersionCode\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip versionCode verification when version metadata is missing."
    Require-Match $failures $loginScreen 'code\.length\s*!=\s*6' `
        "SMS login must require exactly 6 verification-code digits before calling the backend."
    Require-NoMatch $failures $loginScreen 'code\.length\s*<\s*4' `
        "SMS login must not accept 4/5 digit codes and send avoidable failed backend requests."
    $loginAgreementGatePattern = "if\s*\(\s*!agreed\s*\)(?s:.*?)message\s*="
    $mainActivityPrivacyCheckPattern = "PrivacyConsentStore\.isAccepted\s*\(\s*this\s*\)"
    $mainActivityPostConsentInitPattern = "initializePostPrivacyConsentRuntime\s*\(\s*\)(?s:.*?)IdManager\.init\s*\(\s*this\s*\)(?s:.*?)AppCrashReporter\.flushPendingReport"
    $mainActivityLoginAfterPrivacyPattern = "if\s*\(\s*privacyAccepted\s*\)(?s:.*?)LoginGate"
    $privacyConsentVersionedStorePattern = "object\s+PrivacyConsentStore(?s:.*?)CURRENT_VERSION\s*=\s*1(?s:.*?)fun\s+isAccepted"
    $privacyConsentAcceptActionPattern = "onAccepted\s*\(\s*\)"
    $privacyConsentDeclineActionPattern = "onClick\s*=\s*onDeclined"
    Require-Match $failures $loginScreen $loginAgreementGatePattern `
        "Login actions must remain gated by the service agreement/privacy checkbox."
    Require-Match $failures $mainActivity $mainActivityPrivacyCheckPattern `
        "MainActivity must check first-launch privacy consent before entering login/chat."
    Require-Match $failures $mainActivity $mainActivityPostConsentInitPattern `
        "IdManager init and crash-log flushing must stay behind accepted privacy consent."
    Require-Match $failures $mainActivity $mainActivityLoginAfterPrivacyPattern `
        "MainActivity must render LoginGate only after privacy consent has been accepted."
    Require-Match $failures $privacyConsent $privacyConsentVersionedStorePattern `
        "PrivacyConsentStore must keep a versioned local consent flag."
    Require-Match $failures $privacyConsent $privacyConsentAcceptActionPattern `
        "First-launch privacy gate must provide an explicit agree-and-continue action."
    Require-Match $failures $privacyConsent $privacyConsentDeclineActionPattern `
        "First-launch privacy gate must provide an explicit decline/exit action."
    Require-Match $failures $loginScreen 'size\s*\(\s*48\.dp\s*\)(?s:.*?)clickable\s*\(\s*role\s*=\s*Role\.Checkbox' `
        "Login agreement checkbox touch target must remain at least 48dp."
    Require-Match $failures $privacyConsent 'size\s*\(\s*48\.dp\s*\)(?s:.*?)clickable\s*\(\s*role\s*=\s*Role\.Checkbox' `
        "First-launch privacy checkbox touch target must remain at least 48dp."
    Require-NoMatch $failures $chatScreen 'if\s*\(\s*ClientRegionProvider\.hasLocationPermission\s*\(\s*context\s*\)\s*\)(?s:.*?)\}\s*else\s+if\s*\(\s*!ClientRegionProvider\.wasLocationPermissionPrompted\s*\(\s*context\s*\)\s*\)' `
        "Chat screen must not prompt for location permission immediately on entry; request it in-context when sending."
    Require-Match $failures $chatScreen 'suspend\s+fun\s+refreshClientRegionForSend\s*\(\s*\)(?s:.*?)locationPermissionLauncher\.launch' `
        "Chat send path must be the place that first requests optional location permission."
    Require-Match $failures $fusionProtocolActivity 'allowedProtocolHosts\s*=\s*setOf\s*\(\s*"nongjiqiancha\.cn"\s*,\s*"www\.nongjiqiancha\.cn"\s*\)' `
        "Fusion auth protocol WebView must be limited to the official HTTPS domains."
    Require-Match $failures $fusionProtocolActivity 'uri\.scheme\.equals\s*\(\s*"https"(?s:.*?)host\s+in\s+allowedProtocolHosts' `
        "Fusion auth protocol WebView must reject non-HTTPS or non-official protocol URLs."
    Require-NoMatch $failures $fusionProtocolActivity 'uri\.scheme\.equals\s*\(\s*"http"' `
        "Fusion auth protocol WebView must not allow plain HTTP protocol URLs."
    Require-Match $failures $fusionProtocolActivity 'addProtocolFallback(?s:.*?)resolveFallbackProtocolText' `
        "Fusion auth protocol page must show an in-app fallback if the official web page cannot load."
    $privacyConsentSharedTextPattern = "HamburgerServiceAgreementContent(?s:.*?)HamburgerPrivacyPolicyContent"
    $pendingWorkerPrivacyGatePattern = "!PrivacyConsentStore\.isAccepted\s*\(\s*applicationContext\s*\)(?s:.*?)Result\.retry\(\)(?s:.*?)IdManager\.init"
    $todayAgriCardPattern = "fun\s+TodayAgriNewsCard\b"
    $todayAgriRenderablePattern = "fun\s+SessionApi\.TodayAgriCard\.isRenderableTodayAgriCard\b"
    $chatScreenTodayAgriImplementationPattern = "private\s+fun\s+TodayAgriNewsCard|private\s+fun\s+TodayAgriNewsItem|private\s+fun\s+todayAgriDateText|private\s+fun\s+uiCopyPreviewTodayAgriCard"

    Require-Match $failures $privacyConsent $privacyConsentSharedTextPattern `
        "First-launch privacy gate must let users read the same service agreement and privacy policy content as settings/login."
    Require-Match $failures $pendingWorker $pendingWorkerPrivacyGatePattern `
        "Pending background chat sends must not initialize identity or call backend before first-launch privacy consent is accepted."
    Require-Match $failures $todayAgriCardUi $todayAgriCardPattern `
        "Today agri card rendering must stay in TodayAgriCardUi.kt instead of bloating ChatScreen."
    Require-Match $failures $todayAgriCardUi $todayAgriRenderablePattern `
        "Today agri card display validation must stay near the card UI renderer."
    Require-NoMatch $failures $chatScreen $chatScreenTodayAgriImplementationPattern `
        "ChatScreen must not re-embed today agri UI rendering or preview fixtures; keep UI-only card code isolated."
    $userMessageImageStripPattern = "fun\s+UserMessageImageStrip\b"
    $userMessageExpiredPlaceholderPattern = "fun\s+UserMessageExpiredImagePlaceholder\b"
    $userMessagePreviewClosePattern = "fun\s+UserMessagePreviewCloseIcon\b"
    $chatImagePreviewCachePattern = "CHAT_IMAGE_PREVIEW_CACHE_MAX_KB\s*=\s*12\s*\*\s*1024"
    $chatRemotePreviewLimitPattern = "CHAT_REMOTE_PREVIEW_MAX_BYTES\s*=\s*2\s*\*\s*1024\s*\*\s*1024"
    $chatImageDecodePattern = "fun\s+Context\.decodeChatImagePreview\b"
    Require-Match $failures $userMessageImageUi $userMessageImageStripPattern `
        "User message image strip rendering must stay in UserMessageImageUi.kt instead of bloating ChatScreen."
    Require-Match $failures $userMessageImageUi $userMessageExpiredPlaceholderPattern `
        "Expired image placeholder UI must stay with user message image UI."
    Require-Match $failures $userMessageImageUi $userMessagePreviewClosePattern `
        "Image preview close icon must stay with user message image UI."
    Require-Match $failures $chatImagePreview $chatImagePreviewCachePattern `
        "Chat image preview cache limit must stay isolated and capped at 12MiB."
    Require-Match $failures $chatImagePreview $chatRemotePreviewLimitPattern `
        "Remote chat image preview reads must stay isolated and capped at 2MiB."
    Require-Match $failures $chatImagePreview $chatImageDecodePattern `
        "Chat image preview decoding must stay in ChatImagePreview.kt."
    $chatScreenImageImplementationPattern = "private\s+fun\s+UserMessageImageStrip|private\s+fun\s+UserMessageImageThumb|private\s+fun\s+UserMessageImagePreviewDialog|fun\s+Context\.decodeChatImagePreview|chatImagePreviewCache\s*=|CHAT_IMAGE_PREVIEW_CACHE_MAX_KB|CHAT_REMOTE_PREVIEW_MAX_BYTES"
    Require-NoMatch $failures $chatScreen $chatScreenImageImplementationPattern `
        "ChatScreen must not re-embed user image rendering or image preview cache/decode code; keep it isolated."
    $composerCollapseOverlayPattern = "composerCollapseOverlay|ChatComposerCollapseOverlay|shouldDismissComposerCollapseOverlay|resolveBottomContentReservedHeightPx\s*\(\s*overlay"
    Require-NoMatch $failures ($chatScreen + $chatComposerCoordinator + $chatComposerPanel) $composerCollapseOverlayPattern `
        "Chat UI must not restore the dead composer collapse overlay chain; keep composer collapse on the single measured bottom-bar path."
    $fusionVerifySuccessPattern = "override\s+fun\s+onVerifySuccess(?s:.*?)SessionApi\.loginWithFusionVerifyToken"
    $fusionHalfwayUnexpectedPattern = "override\s+fun\s+onHalfWayVerifySuccess(?s:.*?)auth\.fusion_halfway_unexpected(?s:.*?)verifyResult\?\.verifyResult"
    Require-Match $failures $fusionClient $fusionVerifySuccessPattern `
        "Fusion one-click login must exchange the final onVerifySuccess token through the backend login endpoint."
    Require-Match $failures $fusionClient $fusionHalfwayUnexpectedPattern `
        "Unexpected half-way callbacks must only be logged and continued, not treated as the primary login path."
    $fusionHalfwayBlockPattern = "override\s+fun\s+onHalfWayVerifySuccess(?s:.*?)override\s+fun\s+onVerifyFailed"
    $halfwayMatch = [regex]::Match(
        $fusionClient,
        $fusionHalfwayBlockPattern,
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    $fusionHalfwayForbiddenPattern = "SessionApi\.|/api/auth/fusion/login|/api/auth/fusion/verify|VerifyWithFusionAuthToken"
    if (!$halfwayMatch.Success) {
        Add-Failure $failures "Unable to locate onHalfWayVerifySuccess block for parity validation."
    } elseif ($halfwayMatch.Value -match $fusionHalfwayForbiddenPattern) {
        Add-Failure $failures "onHalfWayVerifySuccess must not call backend login/verify or consume the token."
    }
    $fusionProtocolCheckedPattern = "model\.setProtocolChecked\s*\(\s*false\s*\)"
    $fusionHiddenSwitchPattern = "\.hiddenSwtichLogin\s*\(\s*true\s*\)"
    $fusionProtocolActionPattern = "\.setProtocolAction\s*\(\s*PROTOCOL_ACTION\s*\)(?s:.*?)\.setPackageName\s*\(\s*BuildConfig\.APPLICATION_ID\s*\)"
    $fusionVpnWarningPattern = "fun\s+warningReason\(\):\s*String\?\s*=(?s:.*?)hasVpnTransport\s*->\s*`"vpn_active`""
    $fusionProxyWarningPattern = "fun\s+warningReason\(\):\s*String\?\s*=(?s:.*?)hasProxyConfigured\s*->\s*`"proxy_active`""
    $fusionLinkProxyPattern = "getLinkProperties\s*\(\s*it\s*\)\?\.httpProxy"
    $fusionLegacyProxyPattern = "android\.net\.Proxy\.getHost\s*\(\s*context\s*\)"
    $fusionAnyCellularPattern = "hasAnyCellularInternetTransport"

    Require-Match $failures $fusionClient $fusionProtocolCheckedPattern `
        "Aliyun SDK protocol checkbox must remain visible and unchecked by default."
    Require-Match $failures $fusionClient $fusionHiddenSwitchPattern `
        "Aliyun SDK built-in switch/more-login entry must stay hidden so SMS fallback remains in the app page."
    Require-Match $failures $fusionClient $fusionProtocolActionPattern `
        "Custom Aliyun protocolAction must also set packageName to the app applicationId."
    Require-NoMatch $failures $fusionClient 'hasVpnTransport\s*->\s*"vpn_active"(?s:.*?)!hasAnyCellularInternetTransport\s*->\s*"no_cellular_data"' `
        "Fusion one-click login must not hard-block VPN/proxy before checking usable cellular data."
    Require-Match $failures $fusionClient $fusionVpnWarningPattern `
        "Fusion one-click login must record VPN as a warning when cellular data is still available."
    Require-Match $failures $fusionClient $fusionProxyWarningPattern `
        "Fusion one-click login must record configured proxies as a warning when cellular data is still available."
    Require-Match $failures $fusionClient $fusionLinkProxyPattern `
        "Fusion one-click login must detect Android per-network proxy settings, not only Java proxy properties."
    Require-Match $failures $fusionClient $fusionLegacyProxyPattern `
        "Fusion one-click login must retain a legacy Android proxy fallback for ROMs that expose proxy settings outside LinkProperties."
    Require-Match $failures $fusionClient $fusionAnyCellularPattern `
        "Fusion one-click login must inspect all networks so 4G+WiFi mixed environments are not treated as WiFi-only."
    $fusionPrecheckPattern = "fun\s+precheckOneLoginEnvironment\s*\(\s*context:\s*Context\s*\):\s*String\?"
    $loginPrecheckBeforePermissionPattern = "precheckOneLoginEnvironment\s*\(\s*context\s*\)(?s:.*?)ContextCompat\.checkSelfPermission"
    $fusionBlockNoCellularPattern = "blockReason\(\):\s*String\?\s*=(?s:.*?)!hasAnyCellularInternetTransport\s*->\s*`"no_cellular_data`""
    $fusionWarnMixedNetworkPattern = "fun\s+warningReason\(\):\s*String\?\s*=(?s:.*?)!hasCellularTransport\s*&&\s*hasAnyCellularInternetTransport\s*->\s*`"wifi_with_cellular_available`""
    $fusionDebugRawErrorPattern = "if\s*\(\s*BuildConfig\.DEBUG\s*\)\s*\{(?s:.*?)getErrorMsg\(\)(?s:.*?)getInnerMsg\(\)(?s:.*?)\}"
    $chatDebugPreviewPattern = "BuildConfig\.DEBUG\s*&&\s*uiCopyPreviewVisible"
    $chatDebugPreviewClickPattern = "Modifier\.clickable\s*\{\s*uiCopyPreviewVisible\s*=\s*true\s*\}"
    $localFakeStreamPattern = "FAKE_STREAM_TEXT|fakeStreamJob|launchLocalFakeStream|recoverStreamingDraftAsCompletedSnapshot|completeStreamingImmediatelyFromBackground|LOCAL_STREAM_|takeTypewriterToken|LocalStreamFeedStep"

    Require-Match $failures $fusionClient $fusionPrecheckPattern `
        "Fusion one-click login must expose a pre-permission environment check for obvious SMS fallback cases."
    Require-Match $failures $loginScreen $loginPrecheckBeforePermissionPattern `
        "LoginScreen must check one-click environment before requesting READ_PHONE_STATE, so obvious no-SIM/no-cellular users go straight to SMS fallback."
    Require-Match $failures $fusionClient $fusionBlockNoCellularPattern `
        "Fusion one-click login must fall back to SMS when no usable cellular data path is available."
    Require-Match $failures $fusionClient $fusionWarnMixedNetworkPattern `
        "Fusion one-click login should warn, not hard-block, 4G+WiFi mixed environments."
    Require-Match $failures $sessionApi 'fusionTokenRefreshClient\.newCall\s*\(\s*request\s*\)\.enqueue' `
        "Initial fusion token requests must use the short-timeout auth client so login does not hang on weak networks."
    Require-Match $failures $fusionClient $fusionDebugRawErrorPattern `
        "Fusion release logcat must not print raw Aliyun SDK errorMsg/innerMsg; keep them debug-only."

    Require-Match $failures $chatScreen $chatDebugPreviewPattern `
        "Debug-only preview panel must stay behind BuildConfig.DEBUG."
    Require-Match $failures $chatScreen $chatDebugPreviewClickPattern `
        "Debug-only preview entry point must remain an explicit click hook."
    Require-NoMatch $failures $chatScreen $localFakeStreamPattern `
        "ChatScreen must not restore local fake streaming/fake assistant copy; remote failure should use snapshot recovery or retry state."
}

if ($failures.Count -gt 0) {
    Write-Error "Android build parity check failed:`n - $($failures -join "`n - ")"
    exit 1
}

Write-Host "Android build parity check passed."
Write-Host "Debug and release keep the same production backend, account/login path, and network security baseline."
