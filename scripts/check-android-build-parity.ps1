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

function Read-SourceFile {
    param(
        [string]$Path
    )
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
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
$filePathsFile = Join-Path $RepoRoot "app/src/main/res/xml/file_paths.xml"
$backupRulesFile = Join-Path $RepoRoot "app/src/main/res/xml/backup_rules.xml"
$dataExtractionRulesFile = Join-Path $RepoRoot "app/src/main/res/xml/data_extraction_rules.xml"
$idManagerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/IdManager.kt"
$sessionApiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/SessionApi.kt"
$appUpdateInstallerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/AppUpdateInstaller.kt"
$mainActivityFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MainActivity.kt"
$privacyConsentFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PrivacyConsentStore.kt"
$pendingWorkerFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PendingChatSendWorker.kt"
$todayAgriCardUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/TodayAgriCardUi.kt"
$userMessageImageUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/UserMessageImageUi.kt"
$chatImagePreviewFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatImagePreview.kt"
$chatRecyclerViewHostFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt"
$chatScrollCoordinatorFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt"
$chatStreamingRendererFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt"
$chatComposerCoordinatorFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerCoordinator.kt"
$chatComposerPanelFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerPanel.kt"
$loginScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/LoginScreen.kt"
$chatScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$hamburgerMenuSheetFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/HamburgerMenuSheet.kt"
$debugManifestFile = Join-Path $RepoRoot "app/src/debug/AndroidManifest.xml"
$debugNetworkSecurityFile = Join-Path $RepoRoot "app/src/debug/res/xml/network_security_config.xml"
$debugBuildConfigFile = Join-Path $RepoRoot "app/build/generated/source/buildConfig/debug/com/nongjiqianwen/BuildConfig.java"
$releaseBuildConfigFile = Join-Path $RepoRoot "app/build/generated/source/buildConfig/release/com/nongjiqianwen/BuildConfig.java"

foreach ($path in @($buildFile, $manifestFile, $networkSecurityFile, $filePathsFile, $backupRulesFile, $dataExtractionRulesFile, $idManagerFile, $sessionApiFile, $appUpdateInstallerFile, $mainActivityFile, $privacyConsentFile, $pendingWorkerFile, $todayAgriCardUiFile, $userMessageImageUiFile, $chatImagePreviewFile, $chatRecyclerViewHostFile, $chatScrollCoordinatorFile, $chatStreamingRendererFile, $chatComposerCoordinatorFile, $chatComposerPanelFile, $loginScreenFile, $chatScreenFile, $hamburgerMenuSheetFile)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure $failures "Missing required file: $path"
    }
}

if ($failures.Count -eq 0) {
    $build = Read-SourceFile $buildFile
    $manifest = Read-SourceFile $manifestFile
    $networkSecurity = Read-SourceFile $networkSecurityFile
    $filePaths = Read-SourceFile $filePathsFile
    $backupRules = Read-SourceFile $backupRulesFile
    $dataExtractionRules = Read-SourceFile $dataExtractionRulesFile
    $idManager = Read-SourceFile $idManagerFile
    $sessionApi = Read-SourceFile $sessionApiFile
    $appUpdateInstaller = Read-SourceFile $appUpdateInstallerFile
    $mainActivity = Read-SourceFile $mainActivityFile
    $privacyConsent = Read-SourceFile $privacyConsentFile
    $pendingWorker = Read-SourceFile $pendingWorkerFile
    $todayAgriCardUi = Read-SourceFile $todayAgriCardUiFile
    $userMessageImageUi = Read-SourceFile $userMessageImageUiFile
    $chatImagePreview = Read-SourceFile $chatImagePreviewFile
    $chatRecyclerViewHost = Read-SourceFile $chatRecyclerViewHostFile
    $chatScrollCoordinator = Read-SourceFile $chatScrollCoordinatorFile
    $chatStreamingRenderer = Read-SourceFile $chatStreamingRendererFile
    $chatComposerCoordinator = Read-SourceFile $chatComposerCoordinatorFile
    $chatComposerPanel = Read-SourceFile $chatComposerPanelFile
    $loginScreen = Read-SourceFile $loginScreenFile
    $chatScreen = Read-SourceFile $chatScreenFile
    $hamburgerMenuSheet = Read-SourceFile $hamburgerMenuSheetFile

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
    Require-Match $failures $build 'debug\s*\{(?s:.*?)signingConfigs\.findByName\s*\(\s*"release"\s*\)\?\.let\s*\{\s*signingConfig\s*=\s*it\s*\}' `
        "Debug builds must use release signing when configured so Android Studio tests match release signing."
    Require-NoMatch $failures $build 'ENABLE_FUSION|fusionauth|fileTree\s*\(\s*mapOf\s*\(\s*"dir"\s+to\s+"libs"' `
        "Android builds must not carry the removed fusion auth flag, AAR or libs fileTree dependency."
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
    Require-Match $failures $manifest 'android\.permission\.ACCESS_NETWORK_STATE' `
        "Manifest must declare ACCESS_NETWORK_STATE for normal app networking checks."
    Require-NoMatch $failures $manifest 'READ_PHONE_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|com\.alicom\.fusion|com\.mobile\.auth\.gatewayauth|FusionAuthProtocolActivity' `
        "Main manifest must not reintroduce fusion auth activities or old carrier-network permissions."

    Require-Match $failures $networkSecurity '<base-config\s+cleartextTrafficPermitted="false"\s*/>' `
        "Network security base config must keep cleartextTrafficPermitted=false."
    Require-NoMatch $failures $networkSecurity '<domain-config|<domain\b|enrichgw\.10010\.com|onekey\.cmpassport\.com|uac\.189\.cn' `
        "Network security config must not keep old carrier gateway cleartext exceptions after removing fusion auth."
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
    $generatedManifestContents = @{}
    foreach ($manifestPath in $mergedManifestPaths) {
        if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
            Add-Failure $failures "Missing generated Android manifest for parity validation: $manifestPath. Run :app:processDebugManifest :app:processReleaseManifest :app:processDebugManifestForPackage :app:processReleaseManifestForPackage before this script."
            continue
        }
        $mergedManifest = Get-Content -LiteralPath $manifestPath -Raw
        $generatedManifestContents[$manifestPath] = $mergedManifest
        Require-NoMatch $failures $mergedManifest 'android\.permission\.READ_PHONE_STATE|android\.permission\.ACCESS_WIFI_STATE|android\.permission\.CHANGE_NETWORK_STATE|com\.alicom\.fusion|com\.mobile\.auth\.gatewayauth|FusionAuthProtocolActivity' `
            "Generated manifests must not include fusion auth activities or old carrier-network permissions: $manifestPath"
    }
    foreach ($buildConfigPath in @($debugBuildConfigFile, $releaseBuildConfigFile)) {
        if (!(Test-Path -LiteralPath $buildConfigPath -PathType Leaf)) {
            Add-Failure $failures "Missing generated BuildConfig for parity validation: $buildConfigPath. Run :app:generateDebugBuildConfig :app:generateReleaseBuildConfig before this script."
            continue
        }
        $generatedBuildConfig = Get-Content -LiteralPath $buildConfigPath -Raw
        Require-Match $failures $generatedBuildConfig 'APPLICATION_ID\s*=\s*"com\.nongjiqiancha"' `
            "Generated BuildConfig must keep applicationId com.nongjiqiancha: $buildConfigPath"
        Require-Match $failures $generatedBuildConfig 'UPLOAD_BASE_URL\s*=\s*"https://api\.nongjiqiancha\.cn"' `
            "Generated BuildConfig must keep the production HTTPS backend URL: $buildConfigPath"
        Require-Match $failures $generatedBuildConfig 'USE_BACKEND_AB\s*=\s*true' `
            "Generated BuildConfig must keep USE_BACKEND_AB=true: $buildConfigPath"
    }
    if ((Test-Path -LiteralPath $debugBuildConfigFile -PathType Leaf) -and (Test-Path -LiteralPath $releaseBuildConfigFile -PathType Leaf)) {
        $debugBuildConfig = Get-Content -LiteralPath $debugBuildConfigFile -Raw
        $releaseBuildConfig = Get-Content -LiteralPath $releaseBuildConfigFile -Raw
        Require-Match $failures $debugBuildConfig 'BUILD_TYPE\s*=\s*"debug"' `
            "Generated debug BuildConfig must identify the debug build type."
        Require-Match $failures $releaseBuildConfig 'BUILD_TYPE\s*=\s*"release"' `
            "Generated release BuildConfig must identify the release build type."
    }
    $debugPackagedManifestPath = Join-Path $RepoRoot "app/build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"
    $releasePackagedManifestPath = Join-Path $RepoRoot "app/build/intermediates/packaged_manifests/release/processReleaseManifestForPackage/AndroidManifest.xml"
    if ($generatedManifestContents.ContainsKey($debugPackagedManifestPath) -and $generatedManifestContents.ContainsKey($releasePackagedManifestPath)) {
        $debugPackagedManifest = $generatedManifestContents[$debugPackagedManifestPath]
        $releasePackagedManifest = $generatedManifestContents[$releasePackagedManifestPath]
        Require-Match $failures $debugPackagedManifest 'package="com\.nongjiqiancha"' `
            "Packaged debug manifest must keep package=com.nongjiqiancha."
        Require-Match $failures $releasePackagedManifest 'package="com\.nongjiqiancha"' `
            "Packaged release manifest must keep package=com.nongjiqiancha."
        Require-Match $failures $debugPackagedManifest 'android:debuggable="true"' `
            "Packaged debug manifest should be explicitly debuggable for Android Studio diagnostics."
        Require-NoMatch $failures $releasePackagedManifest 'android:debuggable="true"' `
            "Packaged release manifest must not be debuggable."
        $debugPermissions = [regex]::Matches($debugPackagedManifest, '<uses-permission\b[^>]*android:name="([^"]+)"') |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object -Unique
        $releasePermissions = [regex]::Matches($releasePackagedManifest, '<uses-permission\b[^>]*android:name="([^"]+)"') |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object -Unique
        $debugPermissionText = $debugPermissions -join "`n"
        $releasePermissionText = $releasePermissions -join "`n"
        if ($debugPermissionText -ne $releasePermissionText) {
            Add-Failure $failures "Packaged debug and release manifests must keep the same permission set. Debug=[$($debugPermissions -join ', ')] Release=[$($releasePermissions -join ', ')]"
        }
        $expectedPackagedPermissions = @(
            'android.permission.ACCESS_COARSE_LOCATION',
            'android.permission.ACCESS_FINE_LOCATION',
            'android.permission.ACCESS_NETWORK_STATE',
            'android.permission.FOREGROUND_SERVICE',
            'android.permission.INTERNET',
            'android.permission.RECEIVE_BOOT_COMPLETED',
            'android.permission.REQUEST_INSTALL_PACKAGES',
            'android.permission.WAKE_LOCK',
            'com.nongjiqiancha.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
        ) | Sort-Object
        $expectedPackagedPermissionText = $expectedPackagedPermissions -join "`n"
        if ($debugPermissionText -ne $expectedPackagedPermissionText) {
            Add-Failure $failures "Packaged Android permissions must stay on the documented allowlist. Expected=[$($expectedPackagedPermissions -join ', ')] Actual=[$($debugPermissions -join ', ')]"
        }
        Require-Match $failures $debugPackagedManifest '<permission\b(?=[^>]*android:name="com\.nongjiqiancha\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")(?=[^>]*android:protectionLevel="signature")[^>]*>' `
            "Packaged manifest must keep the app-private dynamic receiver protection permission signature-only."
        Require-Match $failures $releasePackagedManifest '<permission\b(?=[^>]*android:name="com\.nongjiqiancha\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")(?=[^>]*android:protectionLevel="signature")[^>]*>' `
            "Release packaged manifest must keep the app-private dynamic receiver protection permission signature-only."
    }

    Require-Match $failures $sessionApi 'endpoint\s*=\s*"/api/auth/sms/login"' `
        "Android login must use the backend ordinary SMS login endpoint."
    Require-Match $failures $sessionApi '\.url\("\$base/api/auth/sms/send"\)' `
        "Android must request verification codes from the backend ordinary SMS send endpoint."
    Require-NoMatch $failures $sessionApi '/api/auth/fusion/|FusionAuthToken|loginWithFusion|requestFusion|fusionTokenRefreshClient|auth\.fusion_login_success' `
        "Android SessionApi must not call or log the removed fusion auth client path."
    Require-Match $failures $sessionApi 'accountUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "Android login must only persist backend account IDs with the acct_ prefix."
    Require-Match $failures $sessionApi 'non_account_user_id' `
        "Android login must report and reject non-account user IDs instead of saving a legacy UUID session."
    Require-Match $failures $idManager 'saveAuthSession(?s:.*?)!normalizedUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must refuse to save a logged-in session for non-account user IDs."
    Require-Match $failures $idManager 'hasValidAuthSession(?s:.*?)authUserId\.startsWith\s*\(\s*"acct_"\s*\)' `
        "IdManager must treat stale non-account auth_user_id values as logged-out."
    Require-Match $failures $sessionApi 'auth\.sms_login_success' `
        "Legacy Android SMS login success logging must remain for old-package compatibility diagnostics."
    Require-Match $failures $sessionApi 'latestCode\s*>\s*BuildConfig\.VERSION_CODE(?s:.*?)normalizeAppUpdateSha256\s*\(\s*apkSha256\s*\)\s*!=\s*null(?s:.*?)sizeBytes\s*>\s*0L(?s:.*?)sizeBytes\s*<=\s*APP_UPDATE_MAX_APK_DOWNLOAD_BYTES' `
        "Android app update availability must require a newer version, SHA-256 and a positive bounded file size."
    Require-Match $failures $manifest '<uses-permission\s+android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"\s*/>' `
        "Android app update flow must keep REQUEST_INSTALL_PACKAGES so Android O+ can request package installation."
    Require-Match $failures $manifest '<provider\b(?=[^>]*android:authorities="\$\{applicationId\}\.fileprovider")(?=[^>]*android:grantUriPermissions="true")(?=[^>]*android:exported="false")(?s:.*?)android:resource="@xml/file_paths"' `
        "Android app update APKs must keep using the app FileProvider with grantUriPermissions."
    Require-Match $failures $filePaths '<cache-path\b(?=[^>]*name="app_updates")(?=[^>]*path="app_updates/")' `
        "Android FileProvider paths must keep cacheDir/app_updates exposed for downloaded APK install intents."
    Require-Match $failures $appUpdateInstaller 'canRequestPackageInstalls\s*\(\s*\)(?s:.*?)Settings\.ACTION_MANAGE_UNKNOWN_APP_SOURCES' `
        "Android app update flow must keep the Android O+ unknown-app-source permission check and settings handoff."
    Require-Match $failures $appUpdateInstaller 'Intent\.ACTION_INSTALL_PACKAGE(?s:.*?)startInstallIntent(?s:.*?)Intent\.ACTION_VIEW' `
        "Android app update install must prefer the package installer action and keep ACTION_VIEW as a compatibility fallback."
    Require-Match $failures $appUpdateInstaller 'FileProvider\.getUriForFile(?s:.*?)"\$\{BuildConfig\.APPLICATION_ID\}\.fileprovider"(?s:.*?)FLAG_GRANT_READ_URI_PERMISSION' `
        "Android app update install intents must grant read access to a FileProvider content URI, not expose raw files."
    Require-Match $failures $appUpdateInstaller 'DownloadFailureReason\.MissingReleaseMetadata' `
        "Android app update downloader must fail closed when release metadata is incomplete."
    Require-NoMatch $failures $appUpdateInstaller 'update\.fileSizeBytes\s*\?:\s*0L' `
        "Android app update downloader must not fall back to unknown APK size."
    Require-NoMatch $failures $appUpdateInstaller 'expectedSha256\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip SHA-256 verification when SHA metadata is missing or invalid."
    Require-NoMatch $failures $appUpdateInstaller 'expectedVersionCode\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip versionCode verification when version metadata is missing."
    Require-Match $failures $hamburgerMenuSheet 'pendingInstallPermissionUpdate(?s:.*?)Lifecycle\.Event\.ON_RESUME(?s:.*?)canRequestInstallPackages(?s:.*?)startAppUpdate\s*\(\s*pendingUpdate\s*\)' `
        "Android app update flow must resume the same update after the user returns from unknown-app-source settings."
    Require-Match $failures $hamburgerMenuSheet 'if\s*\(\s*updateChecking\s*\|\|\s*updateDownloading\s*\)\s*return(?s:.*?)clickable\s*\((?s:.*?)enabled\s*=\s*!downloading' `
        "Android app update UI must prevent duplicate checks/downloads while a package is being prepared."
    Require-Match $failures $loginScreen 'SessionApi\.sendSmsCode' `
        "LoginScreen must expose the ordinary backend SMS send flow."
    Require-Match $failures $loginScreen 'SessionApi\.loginWithSms' `
        "LoginScreen must expose the ordinary backend SMS login flow."
    Require-Match $failures $loginScreen 'code\.length\s*!=\s*6' `
        "LoginScreen must require a 6-digit verification code."
    Require-Match $failures $loginScreen 'countdown\s*=\s*60' `
        "LoginScreen SMS send button must keep a 60-second cooldown."
    Require-NoMatch $failures $loginScreen 'BuildConfig\.ENABLE_FUSION|FusionOneLoginClient|rememberLauncherForActivityResult|ActivityResultContracts|ContextCompat\.checkSelfPermission|READ_PHONE_STATE|本机号码一键登录|使用备用验证码|收起备用验证码' `
        "LoginScreen must not reintroduce fusion auth, phone-state permission requests, one-click login or backup-SMS labels."
    $loginAgreementGatePattern = "if\s*\(\s*!agreed\s*\)(?s:.*?)message\s*="
    $mainActivityPrivacyCheckPattern = "PrivacyConsentStore\.isAccepted\s*\(\s*this\s*\)"
    $mainActivityExistingConsentInitPattern = "if\s*\(\s*privacyAcceptedOnCreate\s*\)\s*\{(?s:.*?)initializePostPrivacyConsentRuntime\s*\(\s*\)"
    $mainActivityAcceptCallbackPattern = "val\s+acceptPrivacyIfNeeded\s*=\s*\{(?s:.*?)PrivacyConsentStore\.accept\s*\(\s*this@MainActivity\s*\)(?s:.*?)initializePostPrivacyConsentRuntime\s*\(\s*\)"
    $mainActivityPostConsentInitPattern = "initializePostPrivacyConsentRuntime\s*\(\s*\)(?s:.*?)IdManager\.init\s*\(\s*this\s*\)(?s:.*?)AppCrashReporter\.flushPendingReport"
    $mainActivityLoginConsentCallbackPattern = "LoginGate\s*\(\s*onPrivacyAccepted\s*=\s*acceptPrivacyIfNeeded"
    $privacyConsentVersionedStorePattern = "object\s+PrivacyConsentStore(?s:.*?)CURRENT_VERSION\s*=\s*1(?s:.*?)fun\s+isAccepted"
    $loginAgreementPersistPattern = "fun\s+acceptAgreementIfNeeded\s*\(\s*\)(?s:.*?)PrivacyConsentStore\.isAccepted\s*\(\s*context\s*\)(?s:.*?)onPrivacyAccepted\s*\(\s*\)"
    $loginAgreementRequirePattern = "fun\s+requireAgreement\s*\(\s*\)(?s:.*?)if\s*\(\s*!agreed\s*\)(?s:.*?)acceptAgreementIfNeeded\s*\(\s*\)"
    $loginSharedTextPattern = "HamburgerServiceAgreementContent(?s:.*?)HamburgerPrivacyPolicyContent"
    Require-Match $failures $loginScreen $loginAgreementGatePattern `
        "Login actions must remain gated by the service agreement/privacy checkbox."
    Require-Match $failures $mainActivity $mainActivityPrivacyCheckPattern `
        "MainActivity must read first-launch privacy consent before initializing runtime."
    Require-Match $failures $mainActivity $mainActivityExistingConsentInitPattern `
        "Existing accepted privacy consent must initialize runtime before logged-in chat can load."
    Require-Match $failures $mainActivity $mainActivityAcceptCallbackPattern `
        "New privacy consent must be recorded before IdManager init and crash-log flushing."
    Require-Match $failures $mainActivity $mainActivityPostConsentInitPattern `
        "IdManager init and crash-log flushing must stay behind accepted privacy consent."
    Require-Match $failures $mainActivity $mainActivityLoginConsentCallbackPattern `
        "LoginGate must receive the consent callback instead of rendering a separate first-launch consent page."
    Require-NoMatch $failures $mainActivity 'PrivacyConsentGate' `
        "MainActivity must not show a separate first-launch privacy page; consent is handled in LoginScreen."
    Require-Match $failures $privacyConsent $privacyConsentVersionedStorePattern `
        "PrivacyConsentStore must keep a versioned local consent flag."
    Require-Match $failures $loginScreen $loginAgreementPersistPattern `
        "LoginScreen must persist first privacy consent through the same agreement checkbox."
    Require-Match $failures $loginScreen $loginAgreementRequirePattern `
        "Login actions must initialize post-consent runtime only after the agreement checkbox is checked."
    Require-Match $failures $loginScreen 'size\s*\(\s*48\.dp\s*\)(?s:.*?)clickable\s*\(\s*role\s*=\s*Role\.Checkbox' `
        "Login agreement checkbox touch target must remain at least 48dp."
    Require-NoMatch $failures $chatScreen 'if\s*\(\s*ClientRegionProvider\.hasLocationPermission\s*\(\s*context\s*\)\s*\)(?s:.*?)\}\s*else\s+if\s*\(\s*!ClientRegionProvider\.wasLocationPermissionPrompted\s*\(\s*context\s*\)\s*\)' `
        "Chat screen must not prompt for location permission immediately on entry; request it in-context when sending."
    Require-Match $failures $chatScreen 'suspend\s+fun\s+refreshClientRegionForSend\s*\(\s*\)(?s:.*?)locationPermissionLauncher\.launch' `
        "Chat send path must be the place that first requests optional location permission."
    Require-Match $failures $chatScreen 'val\s+hasStartupLocalMessages\s*=\s*initialLocalMessages\.isNotEmpty\s*\(\s*\)' `
        "Chat startup must explicitly distinguish existing local messages from completed bottom calibration."
    Require-Match $failures $chatScreen 'var\s+initialBottomSnapDone\s+by\s+remember\s*\(\s*uiRuntimeResetKey\s*\)\s*\{\s*mutableStateOf\s*\(\s*false\s*\)' `
        "Chat startup bottom calibration must always start pending, even when local messages exist."
    Require-NoMatch $failures $chatScreen 'initialBottomSnapDone\s+by\s+remember\s*\([^)]*\)\s*\{\s*mutableStateOf\s*\(\s*initialLocalMessages\.isNotEmpty\s*\(\s*\)' `
        "Chat startup must not treat local cached messages as if bottom snapping had already completed."
    Require-Match $failures $chatScreen 'waitingForStaticTimelineBottomSnap(?s:.*?)!hasStartupLocalMessages(?s:.*?)!initialBottomSnapDone' `
        "Chat startup may keep cached local messages visible, but only after separating that from the bottom-snap guard."
    Require-Match $failures $chatScreen 'ui\.chat_startup_state' `
        "Chat startup must keep a safe client log for diagnosing clean-state, hydration and reveal behavior."
    Require-Match $failures $chatScreen 'ui\.chat_startup_bottom_snap_done' `
        "Chat startup must log when initial bottom calibration completes."
    Require-Match $failures $chatScreen 'ui\.chat_startup_bottom_snap_pending' `
        "Chat startup must log a warning when bottom calibration remains pending after retries."
    Require-Match $failures $chatScreen 'today_agri\.main_card_loaded' `
        "Chat startup must log when the today agri main card is loaded for diagnostics."
    Require-Match $failures $chatScreen 'today_agri\.main_card_visible' `
        "Chat startup must log when the today agri main card is inserted into the visible timeline."
    Require-Match $failures $sessionApi 'fun\s+getTodayAgriCard\b(?s:.*?)if\s*\(\s*isRuntimeStale\(\)\s*\)\s*\{\s*onResult\s*\(\s*null\s*\)\s*return@use\s*\}' `
        "Today agri fetch must always invoke its callback when a reset makes the in-flight request stale."
    Require-Match $failures $sessionApi 'fun\s+getSnapshot\b(?s:.*?)fun\s+attempt\s*\(\s*networkRetry:\s*Int\s*\)\s*\{(?s:.*?)if\s*\(\s*isRuntimeStale\(\)\s*\)\s*\{\s*onResult\s*\(\s*null\s*\)\s*return\s*\}' `
        "Session snapshot fetch must callback on stale start so clean-state resets cannot leave hydration pending."
    Require-Match $failures $sessionApi 'fun\s+getSnapshot\b(?s:.*?)onResult\s*=\s*\{\s*response\s*->(?s:.*?)if\s*\(\s*isRuntimeStale\(\)\s*\)\s*\{\s*onResult\s*\(\s*null\s*\)\s*return@use\s*\}' `
        "Session snapshot fetch must callback when a stale HTTP response arrives after a runtime reset."
    Require-Match $failures $sessionApi 'fun\s+getSnapshot\b(?s:.*?)onFailure\s*=\s*\{\s*error\s*->(?s:.*?)if\s*\(\s*isRuntimeStale\(\)\s*\)\s*\{\s*onResult\s*\(\s*null\s*\)\s*return@enqueueWithRetry401\s*\}' `
        "Session snapshot fetch must callback when a stale failure arrives after a runtime reset."
    Require-Match $failures $sessionApi 'fun\s+getSnapshot\b(?s:.*?)postDelayed\s*\(\s*\{(?s:.*?)if\s*\(\s*isRuntimeStale\(\)\s*\)\s*\{\s*onResult\s*\(\s*null\s*\)\s*return@postDelayed\s*\}' `
        "Session snapshot delayed retry must callback when a reset happens before retry execution."
    $settingsLabelMembership = [regex]::Escape("$([char]0x4f1a)$([char]0x5458)$([char]0x4e2d)$([char]0x5fc3)")
    $settingsLabelAccount = [regex]::Escape("$([char]0x8d26)$([char]0x53f7)$([char]0x7ba1)$([char]0x7406)")
    $settingsLabelSupport = [regex]::Escape("$([char]0x5e2e)$([char]0x52a9)$([char]0x4e0e)$([char]0x53cd)$([char]0x9988)")
    $settingsLabelTodayAgri = [regex]::Escape("$([char]0x4eca)$([char]0x65e5)$([char]0x519c)$([char]0x60c5)")
    $settingsLabelUpdate = [regex]::Escape("$([char]0x68c0)$([char]0x67e5)$([char]0x66f4)$([char]0x65b0)")
    $settingsLabelGiftCard = [regex]::Escape("$([char]0x793c)$([char]0x54c1)$([char]0x5361)")
    $settingsLabelLegal = [regex]::Escape("$([char]0x670d)$([char]0x52a1)$([char]0x534f)$([char]0x8bae)")
    $settingsLabelLogout = [regex]::Escape("$([char]0x9000)$([char]0x51fa)$([char]0x767b)$([char]0x5f55)")
    $accountLabelPhone = [regex]::Escape("$([char]0x624b)$([char]0x673a)$([char]0x53f7)")
    $accountLabelClearing = [regex]::Escape("$([char]0x6e05)$([char]0x7406)$([char]0x4e2d)")
    $accountLabelClearCache = [regex]::Escape("$([char]0x6e05)$([char]0x7406)$([char]0x4e34)$([char]0x65f6)$([char]0x7f13)$([char]0x5b58)")
    $accountLabelDeleteHistory = [regex]::Escape("$([char]0x5220)$([char]0x9664)$([char]0x5386)$([char]0x53f2)$([char]0x5bf9)$([char]0x8bdd)")
    $accountLabelLoggingOut = [regex]::Escape("$([char]0x9000)$([char]0x51fa)$([char]0x4e2d)")
    $accountLabelSubmitting = [regex]::Escape("$([char]0x63d0)$([char]0x4ea4)$([char]0x4e2d)")
    $accountLabelDeleteAccount = [regex]::Escape("$([char]0x6ce8)$([char]0x9500)$([char]0x8d26)$([char]0x53f7)")

    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerMenuMainPage(?s:.*?)HamburgerMenuIcon\.Logout(?s:.*?)title\s*=\s*"' + $settingsLabelLogout + '"(?s:.*?)destructive\s*=\s*true') `
        "Settings main page must keep the default logout row in code, not depend on cached UI state."
    Require-Match $failures $hamburgerMenuSheet 'ui\.settings_main_opened' `
        "Settings main page must keep a safe client log so clean-state UI rollback reports are traceable."
    Require-Match $failures $hamburgerMenuSheet 'ui\.account_management_opened' `
        "Account management page must keep a safe client log so clean-state UI rollback reports are traceable."
    Require-Match $failures $hamburgerMenuSheet 'APP_ICP_RECORD_NUMBER\s*=\s*"京ICP备2026031728号-2A"' `
        "Settings/legal pages must keep the approved App ICP filing number visible in the APK."
    Require-Match $failures $hamburgerMenuSheet 'MIIT_BEIAN_QUERY_URL\s*=\s*"https://beian\.miit\.gov\.cn/"' `
        "Settings/legal pages must keep the MIIT filing query link for public verification."
    Require-Match $failures $hamburgerMenuSheet 'private\s+fun\s+AppFilingFooter(?s:.*?)APP_ICP_RECORD_NUMBER(?s:.*?)MIIT_BEIAN_QUERY_URL' `
        "Settings main page must keep a low-noise App ICP filing footer with a query link."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerMenuMainPage(?s:.*?)title\s*=\s*"' + $settingsLabelMembership + '"(?s:.*?)title\s*=\s*"' + $settingsLabelAccount + '"(?s:.*?)title\s*=\s*"' + $settingsLabelSupport + '"(?s:.*?)title\s*=\s*"' + $settingsLabelTodayAgri + '"(?s:.*?)title\s*=\s*"' + $settingsLabelUpdate + '"(?s:.*?)title\s*=\s*"' + $settingsLabelGiftCard + '"(?s:.*?)title\s*=\s*"' + $settingsLabelLegal + '"(?s:.*?)title\s*=\s*"' + $settingsLabelLogout + '"') `
        "Settings main page defaults must include every production row after app data is cleared."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerAccountManagementContent(?s:.*?)title\s*=\s*if\s*\(\s*logoutSubmitting\s*\)\s*"' + $accountLabelLoggingOut + '"\s*else\s*"' + $settingsLabelLogout + '"') `
        "Account management page must keep its logout row in the default code path."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerAccountManagementContent(?s:.*?)title\s*=\s*"' + $accountLabelPhone + '"(?s:.*?)title\s*=\s*if\s*\(\s*cacheCleanupSubmitting\s*\)\s*"' + $accountLabelClearing + '"\s*else\s*"' + $accountLabelClearCache + '"(?s:.*?)title\s*=\s*"' + $accountLabelDeleteHistory + '"(?s:.*?)title\s*=\s*if\s*\(\s*logoutSubmitting\s*\)\s*"' + $accountLabelLoggingOut + '"\s*else\s*"' + $settingsLabelLogout + '"(?s:.*?)title\s*=\s*if\s*\(\s*accountDeletionSubmitting\s*\)\s*"' + $accountLabelSubmitting + '"\s*else\s*"' + $accountLabelDeleteAccount + '"') `
        "Account management defaults must include phone, cache cleanup, history deletion, logout, and account deletion rows after app data is cleared."
    $pendingWorkerPrivacyGatePattern = "!PrivacyConsentStore\.isAccepted\s*\(\s*applicationContext\s*\)(?s:.*?)Result\.retry\(\)(?s:.*?)IdManager\.init"
    $todayAgriCardPattern = "fun\s+TodayAgriNewsText\b(?s:.*?)ChatStreamingRenderer"
    $todayAgriRenderablePattern = "fun\s+SessionApi\.TodayAgriCard\.isRenderableTodayAgriCard\b"
    $chatScreenTodayAgriImplementationPattern = "private\s+fun\s+TodayAgriNewsText|private\s+fun\s+TodayAgriNewsCard|private\s+fun\s+TodayAgriNewsItem|private\s+fun\s+todayAgriDateText|private\s+fun\s+uiCopyPreviewTodayAgriCard|toTodayAgriPlainText\s*\("

    Require-Match $failures $loginScreen $loginSharedTextPattern `
        "Login agreement links must let users read the same service agreement and privacy policy content as settings."
    Require-Match $failures $pendingWorker $pendingWorkerPrivacyGatePattern `
        "Pending background chat sends must not initialize identity or call backend before first-launch privacy consent is accepted."
    Require-Match $failures $todayAgriCardUi $todayAgriCardPattern `
        "Today agri rendering must stay in TodayAgriCardUi.kt and reuse the normal assistant text renderer."
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
    $chatMainScrollSurface = $chatScreen + "`n" + $chatRecyclerViewHost + "`n" + $chatScrollCoordinator + "`n" + $chatStreamingRenderer
    $forbiddenMainScrollPattern = "reverseLayout\s*=|asReversed\s*\(|dispatchRawDelta\s*\(|scrollBy\s*\(|StreamingBlockChatListItem|StreamingTextBlock|streaming_tail|SparseBottomSpacer|BottomActiveZone|StreamingLocation|requestSendStartBottomSnap|followStreamingByDelta|streamBottomFollowActive"
    Require-NoMatch $failures $chatMainScrollSurface $forbiddenMainScrollPattern `
        "Main chat must stay on the current forward LazyColumn chain and must not restore reverse layout, raw-delta/scrollBy chasing, active-zone overlay, or split streaming items."
    Require-Match $failures $chatRecyclerViewHost 'LazyColumn\s*\((?s:.*?)verticalArrangement\s*=\s*verticalArrangement(?s:.*?)userScrollEnabled\s*=\s*true(?s:.*?)items\s*\(' `
        "ChatRecyclerViewHost must remain a single user-scrollable forward LazyColumn over the provided timeline items."
    Require-NoMatch $failures $chatRecyclerViewHost 'reverseLayout\s*=' `
        "ChatRecyclerViewHost must not opt into reverseLayout."
    Require-Match $failures $chatScreen 'shouldAnchorStreamingBottomThisFrame(?s:.*?)SideEffect\s*\{\s*requestForwardListBottomAnchor\s*\(\s*\)\s*\}' `
        "Streaming content must keep the same-frame bottom-anchor SideEffect that prevents the tail from flashing below the workline."
    $onAdvanceMatch = [regex]::Match(
        $chatScreen,
        'onAdvance\s*=\s*\{\s*advance\s*->(?s:.*?lastStreamingFreshRevealMs\s*=\s*advance\.lastFreshRevealMs\s*)\}'
    )
    if (!$onAdvanceMatch.Success) {
        Add-Failure $failures "Streaming reveal onAdvance block must stay simple and inspectable."
    } elseif ($onAdvanceMatch.Value -match 'requestForwardListBottomAnchor|requestProgrammaticForwardListBottomAnchor|scrollForwardListToBottom|scrollToItem|requestScrollToItem') {
        Add-Failure $failures "Streaming reveal onAdvance must not do pre-content scroll anchoring; anchoring belongs in the same-frame SideEffect."
    }
    Require-Match $failures $chatScreen 'verticalArrangement\s*=\s*if\s*\(\s*shouldUseTopArrangementForConversation\s*\(\s*\)\s*\)\s*\{(?s:.*?)Arrangement\.Top(?s:.*?)\}\s*else\s*\{(?s:.*?)Arrangement\.Bottom' `
        "Chat timeline must keep the top-only arrangement only for clean-state/top-flow cases and otherwise use the bottom workline layout."
    Require-Match $failures $chatScreen 'ChatTimelineItem\.TodayAgriCard(?s:.*?)TodayAgriNewsText' `
        "Today agri must keep rendering as a normal ChatTimelineItem in the main chat list, using assistant-style plain text."
    Require-Match $failures $chatStreamingRenderer 'val\s+selectContent\s*=\s*selectionEnabled\s*&&\s*!rendererContainsLinkCandidate\s*\(\s*content\s*\)' `
        "Assistant settled text must not wrap link candidates in SelectionContainer, otherwise short-tap links can become plain text."
    Require-Match $failures $chatStreamingRenderer 'LinkInteractionListener(?s:.*?)uriHandler\.openUri\s*\(\s*url\s*\)(?s:.*?)withLink\s*\((?s:.*?)LinkAnnotation\.Url' `
        "Assistant Markdown links and bare URLs must keep real URL annotations that open through the system URI handler."
    Require-Match $failures $chatStreamingRenderer 'ui\.link_open_failed(?s:.*?)substringBefore\(":"(?s:.*?)exception' `
        "Assistant link open failures must remain user-visible and logged only as a safe summary, not as full URLs."
    Require-Match $failures $chatScreen 'hasActiveNetworkConnection(?s:.*?)NET_CAPABILITY_INTERNET(?s:.*?)NET_CAPABILITY_CAPTIVE_PORTAL(?s:.*?)hasInternetCapability\s*&&\s*!isCaptivePortal' `
        "Chat offline precheck must reject captive-portal networks instead of treating every INTERNET-capable network as usable."
    Require-Match $failures $hamburgerMenuSheet 'BackHandler\s*\(\s*enabled\s*=\s*visible\s*\)(?s:.*?)handleBackClick\s*\(\s*\)' `
        "Settings shell must let Android back close the main settings page as well as nested pages."
    Require-Match $failures $hamburgerMenuSheet 'DisposableEffect\s*\(\s*Unit\s*\)(?s:.*?)onDispose\s*\{(?s:.*?)imagesForCleanup\.forEach\s*\(\s*contextForCleanup::deleteComposerImageAttachment\s*\)(?s:.*?)cleanupPendingComposerCameraImage' `
        "Support feedback must clean temporary selected images on page disposal, including interrupted sending states."
    Require-NoMatch $failures $hamburgerMenuSheet 'if\s*\(\s*!\s*sending\s*&&\s*selectedImages\.isNotEmpty\s*\(\s*\)\s*\)' `
        "Support feedback disposal cleanup must not skip temporary images while a send is in progress."
    Require-Match $failures $hamburgerMenuSheet 'DisposableEffect\s*\(\s*lifecycleOwner,\s*pendingInstallPermissionUpdate,\s*updateDownloading\s*\)(?s:.*?)ON_RESUME(?s:.*?)pendingInstallPermissionUpdate\s*\?:\s*return@LifecycleEventObserver(?s:.*?)updateDownloading' `
        "App update permission resume handling must keep working for automatic update prompts even when settings is not visible."
    Require-NoMatch $failures $hamburgerMenuSheet 'if\s*\(\s*!\s*visible\s*\|\|\s*updateDownloading\s*\)\s*return@LifecycleEventObserver' `
        "App update permission resume handling must not depend on settings sheet visibility."
    Require-Match $failures $hamburgerMenuSheet 'supportContainsLinkCandidate(?s:.*?)LinkInteractionListener(?s:.*?)ui\.link_open_failed(?s:.*?)SelectionContainer' `
        "Support feedback links must remain tappable and report only safe link-open failures."
    $chatDebugPreviewPattern = "BuildConfig\.DEBUG\s*&&\s*uiCopyPreviewVisible"
    $chatDebugPreviewClickPattern = "Modifier\.clickable\s*\{\s*uiCopyPreviewVisible\s*=\s*true\s*\}"
    $localFakeStreamPattern = "FAKE_STREAM_TEXT|fakeStreamJob|launchLocalFakeStream|recoverStreamingDraftAsCompletedSnapshot|completeStreamingImmediatelyFromBackground|LOCAL_STREAM_|takeTypewriterToken|LocalStreamFeedStep"

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
Write-Host "Debug and release keep the same production backend, ordinary SMS login path, and network security baseline."
