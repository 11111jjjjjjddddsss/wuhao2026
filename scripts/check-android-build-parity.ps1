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
$pendingChatSendStoreFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/PendingChatSendStore.kt"
$todayAgriCardUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/TodayAgriCardUi.kt"
$userMessageImageUiFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/UserMessageImageUi.kt"
$chatImagePreviewFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatImagePreview.kt"
$chatRecyclerViewHostFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt"
$chatScrollCoordinatorFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt"
$chatStreamingRendererFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt"
$chatComposerCoordinatorFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerCoordinator.kt"
$chatComposerPanelFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatComposerPanel.kt"
$imageUploaderFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ImageUploader.kt"
$loginScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/LoginScreen.kt"
$chatScreenFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt"
$hamburgerMenuSheetFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/HamburgerMenuSheet.kt"
$membershipCenterSheetFile = Join-Path $RepoRoot "app/src/main/kotlin/com/nongjiqianwen/MembershipCenterSheet.kt"
$chatTimelineItemsTestFile = Join-Path $RepoRoot "app/src/test/java/com/nongjiqianwen/ChatTimelineItemsTest.kt"
$chatStreamingRendererTestFile = Join-Path $RepoRoot "app/src/test/java/com/nongjiqianwen/ChatStreamingRendererTest.kt"
$debugManifestFile = Join-Path $RepoRoot "app/src/debug/AndroidManifest.xml"
$debugNetworkSecurityFile = Join-Path $RepoRoot "app/src/debug/res/xml/network_security_config.xml"
$debugBuildConfigFile = Join-Path $RepoRoot "app/build/generated/source/buildConfig/debug/com/nongjiqianwen/BuildConfig.java"
$releaseBuildConfigFile = Join-Path $RepoRoot "app/build/generated/source/buildConfig/release/com/nongjiqianwen/BuildConfig.java"

foreach ($path in @($buildFile, $manifestFile, $networkSecurityFile, $filePathsFile, $backupRulesFile, $dataExtractionRulesFile, $idManagerFile, $sessionApiFile, $appUpdateInstallerFile, $mainActivityFile, $privacyConsentFile, $pendingWorkerFile, $pendingChatSendStoreFile, $todayAgriCardUiFile, $userMessageImageUiFile, $chatImagePreviewFile, $chatRecyclerViewHostFile, $chatScrollCoordinatorFile, $chatStreamingRendererFile, $chatComposerCoordinatorFile, $chatComposerPanelFile, $imageUploaderFile, $loginScreenFile, $chatScreenFile, $hamburgerMenuSheetFile, $membershipCenterSheetFile, $chatTimelineItemsTestFile, $chatStreamingRendererTestFile)) {
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
    $pendingChatSendStore = Read-SourceFile $pendingChatSendStoreFile
    $todayAgriCardUi = Read-SourceFile $todayAgriCardUiFile
    $userMessageImageUi = Read-SourceFile $userMessageImageUiFile
    $chatImagePreview = Read-SourceFile $chatImagePreviewFile
    $chatRecyclerViewHost = Read-SourceFile $chatRecyclerViewHostFile
    $chatScrollCoordinator = Read-SourceFile $chatScrollCoordinatorFile
    $chatStreamingRenderer = Read-SourceFile $chatStreamingRendererFile
    $chatComposerCoordinator = Read-SourceFile $chatComposerCoordinatorFile
    $chatComposerPanel = Read-SourceFile $chatComposerPanelFile
    $imageUploader = Read-SourceFile $imageUploaderFile
    $loginScreen = Read-SourceFile $loginScreenFile
    $chatScreen = Read-SourceFile $chatScreenFile
    $hamburgerMenuSheet = Read-SourceFile $hamburgerMenuSheetFile
    $membershipCenterSheet = Read-SourceFile $membershipCenterSheetFile
    $chatTimelineItemsTest = Read-SourceFile $chatTimelineItemsTestFile
    $chatStreamingRendererTest = Read-SourceFile $chatStreamingRendererTestFile

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
    Require-Match $failures $sessionApi 'APP_UPDATE_OFFICIAL_APK_HOST\s*=\s*"download\.nongjiqiancha\.cn"(?s:.*?)APP_UPDATE_OFFICIAL_APK_PATH_PREFIX\s*=\s*"/android/releases/"(?s:.*?)isStableAppUpdateApkUrl' `
        "Android app update availability must be limited to the official download domain and release APK path."
    Require-Match $failures $sessionApi 'parsed\.host\.equals\(APP_UPDATE_OFFICIAL_APK_HOST,\s*ignoreCase\s*=\s*true\)(?s:.*?)encodedPath\s*=\s*parsed\.encodedPath\.lowercase\(Locale\.US\)(?s:.*?)decodedPath\s*=\s*decodeAppUpdateUrlGuardValue\(parsed\.encodedPath\)\.lowercase\(Locale\.US\)(?s:.*?)encodedPath\.startsWith\(APP_UPDATE_OFFICIAL_APK_PATH_PREFIX\)(?s:.*?)decodedPath\.startsWith\(APP_UPDATE_OFFICIAL_APK_PATH_PREFIX\)' `
        "Android app update URL validation must reject external APK hosts and encoded/decoded non-release paths."
    Require-Match $failures $sessionApi 'encodedPath\.endsWith\("\.apk"\)(?s:.*?)decodedPath\.endsWith\("\.apk"\)(?s:.*?)encodedPath\.contains\("\.\."\)(?s:.*?)decodedPath\.contains\("\.\."\)(?s:.*?)internalMarkers\s*=\s*listOf\("test-apks",\s*"debug",\s*"internal",\s*"staging"\)(?s:.*?)decodedUrl\.contains\(marker\)(?s:.*?)decodedPath\.contains\(marker\)' `
        "Android app update URL validation must reject non-APK files, path traversal and URL-encoded test/internal markers."
    Require-Match $failures $sessionApi 'signedQueryNames\s*=\s*setOf\((?s:.*?)"expires"(?s:.*?)"signature"(?s:.*?)"ossaccesskeyid"(?s:.*?)"x-oss-signature"(?s:.*?)parsed\.queryParameterNames\.none' `
        "Android app update URL validation must reject short signed APK URLs."
    Require-Match $failures $manifest '<uses-permission\s+android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"\s*/>' `
        "Android app update flow must keep REQUEST_INSTALL_PACKAGES so Android O+ can request package installation."
    Require-Match $failures $manifest '<provider\b(?=[^>]*android:authorities="\$\{applicationId\}\.fileprovider")(?=[^>]*android:grantUriPermissions="true")(?=[^>]*android:exported="false")(?s:.*?)android:resource="@xml/file_paths"' `
        "Android app update APKs must keep using the app FileProvider with grantUriPermissions."
    Require-Match $failures $filePaths '<cache-path\b(?=[^>]*name="app_updates")(?=[^>]*path="app_updates/")' `
        "Android FileProvider paths must keep cacheDir/app_updates exposed for downloaded APK install intents."
    Require-Match $failures $appUpdateInstaller 'canRequestPackageInstalls\s*\(\s*\)(?s:.*?)Settings\.ACTION_MANAGE_UNKNOWN_APP_SOURCES' `
        "Android app update flow must keep the Android O+ unknown-app-source permission check and settings handoff."
    Require-Match $failures $appUpdateInstaller 'private\s+fun\s+isAllowedReleaseApkUrl\(rawUrl:\s*String\):\s*Boolean\s*=(?s:.*?)SessionApi\.isStableAppUpdateApkUrl\(rawUrl\)' `
        "App update installer must reuse the same stable official APK URL guard as SessionApi for initial and final redirect URLs."
    Require-Match $failures $appUpdateInstaller 'Intent\.ACTION_INSTALL_PACKAGE(?s:.*?)startInstallIntent(?s:.*?)Intent\.ACTION_VIEW' `
        "Android app update install must prefer the package installer action and keep ACTION_VIEW as a compatibility fallback."
    Require-Match $failures $appUpdateInstaller 'FileProvider\.getUriForFile(?s:.*?)"\$\{BuildConfig\.APPLICATION_ID\}\.fileprovider"(?s:.*?)FLAG_GRANT_READ_URI_PERMISSION' `
        "Android app update install intents must grant read access to a FileProvider content URI, not expose raw files."
    Require-Match $failures $appUpdateInstaller 'DownloadFailureReason\.MissingReleaseMetadata' `
        "Android app update downloader must fail closed when release metadata is incomplete."
    Require-Match $failures $appUpdateInstaller 'response\.request\.url\.isHttps(?s:.*?)isAllowedReleaseApkUrl\(response\.request\.url\.toString\(\)\)' `
        "Android app update downloader must re-check the final redirected URL, not only the configured URL."
    Require-NoMatch $failures $appUpdateInstaller 'update\.fileSizeBytes\s*\?:\s*0L' `
        "Android app update downloader must not fall back to unknown APK size."
    Require-NoMatch $failures $appUpdateInstaller 'expectedSha256\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip SHA-256 verification when SHA metadata is missing or invalid."
    Require-NoMatch $failures $appUpdateInstaller 'expectedVersionCode\s*!=\s*null\s*&&' `
        "Android app update downloader must not skip versionCode verification when version metadata is missing."
    Require-Match $failures $hamburgerMenuSheet 'pendingInstallPermissionUpdate(?s:.*?)Lifecycle\.Event\.ON_RESUME(?s:.*?)canRequestInstallPackages(?s:.*?)startAppUpdate\s*\(\s*pendingUpdate\s*\)' `
        "Android app update flow must resume the same update after the user returns from unknown-app-source settings."
    Require-Match $failures $hamburgerMenuSheet 'pendingInstallPermissionUpdate\s*=\s*null(?s:.*?)updateDialogInfo\s*=\s*null' `
        "Dismissing the ordinary app update dialog must clear pending install-permission resume state."
    Require-Match $failures $hamburgerMenuSheet 'onDismissRequest\s*=\s*\{\s*if\s*\(\s*!downloading\s*\)\s*onDismiss\(\)\s*\}' `
        "Ordinary app update dialogs must stay dismissible whenever an APK download is not actively running."
    Require-Match $failures $hamburgerMenuSheet 'Text\(\s*text\s*=\s*"稍后"' `
        "Ordinary app update dialogs must always keep a visible later button."
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
    Require-NoMatch $failures $chatScreen 'waitingForStaticTimelineBottomSnap' `
        "Chat startup must not hide hydrated static messages while waiting for bottom snapping."
    Require-Match $failures $chatScreen 'internal\s+fun\s+shouldRevealChatMessageList(?s:.*?)messageCount\s*>\s*0\s*->\s*true(?s:.*?)hasStreamingItem\s*->\s*true' `
        "Chat startup must reveal real messages or streaming content as soon as they exist; bottom snapping continues as calibration."
    Require-NoMatch $failures $chatScreen 'internal\s+fun\s+shouldRevealChatMessageList(?s:.*?)hasTodayAgriCard\s*->\s*true' `
        "Today agri must not unlock an otherwise empty chat list; clean-state startup should keep the welcome shell."
    Require-Match $failures $chatScreen 'internal\s+fun\s+shouldShowChatWelcomePlaceholder(?s:.*?)startupHydrationBarrierSatisfied\s*\|\|\s*!hasStartedConversation' `
        "Clean installs must show a nonblank welcome shell while remote history is still hydrating."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriCardDoesNotOccupyEmptyWelcomeState' `
        "Today agri must have a unit test proving it does not occupy the empty welcome state."
    Require-Match $failures $chatTimelineItemsTest 'hasTodayAgriCard\s*=\s*true,\s*messageCount\s*=\s*0' `
        "Welcome placeholder behavior must be tested with today agri present and no real messages."
    Require-NoMatch $failures $chatScreen 'showWelcomePlaceholder(?s:.*?)waitingForRemoteStartupHydration' `
        "The welcome shell must not be hidden solely because remote history hydration is pending."
    Require-NoMatch $failures $chatScreen 'LaunchedEffect\s*\(\s*uiRuntimeResetKey\s*,\s*historyHydrationComplete\s*,\s*todayAgriRefreshDayKey\s*\)(?s:.*?)awaitTodayAgriCard' `
        "Today agri fetch may be ready early, but it must not be coupled to the chat history hydration effect."
    Require-Match $failures $chatTimelineItemsTest 'startupRevealWaitsOnlyWhileRemoteHistoryHasNoVisualContent' `
        "Chat startup reveal behavior must have a unit test for remote hydration returning visual content before bottom snap completes."
    Require-Match $failures $chatTimelineItemsTest 'welcomePlaceholderShowsWhileRemoteHistoryHydrates' `
        "Clean install startup must have a unit test that prevents a blank screen while remote history hydrates."
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
    Require-Match $failures $chatScreen 'fun\s+hasStaticVisualTimeline\s*\(\s*\)\s*:\s*Boolean\s*=\s*messages\.isNotEmpty\(\)' `
        "Clean-state startup must treat real chat messages, not today agri data, as the static timeline gate."
    Require-NoMatch $failures $chatScreen 'hasOnlyTodayAgriVisualTimeline|todayAgriCardOnlyTopExtraPaddingPx|TODAY_AGRI_CARD_ONLY_TOP_EXTRA_PADDING|cardOnlyTopPaddingPx' `
        "Today agri must not keep the old card-only top padding or skip-bottom-snap special case."
    Require-Match $failures $chatScreen 'fun\s+bottomAnchorIndexOrMinusOne\s*\(\s*\)\s*:\s*Int\s*=\s*(?s:.*?)if\s*\(\s*isStreaming\s*\|\|\s*hasStreamingItem\s*\)(?s:.*?)latestMessageIndexOrMinusOne\(\)(?s:.*?)latestVisualTailIndexOrMinusOne\(\)' `
        "When not streaming, bottom anchoring must target the visual tail so today agri can participate in back-to-bottom behavior when it follows real messages."
    Require-Match $failures $chatScreen 'fun\s+currentVisualTailContentBottomPx\s*\(\s*\)\s*:\s*Int(?s:.*?)ChatTimelineItem\.TodayAgriCard(?s:.*?)currentLastMessageContentBottomPx\(\)' `
        "Static bottom tolerance must measure a trailing today agri item as visual tail, while falling back to real messages when it is absent."
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
    Require-NoMatch $failures $chatScreen 'fun\s+canAttemptRemoteAssistantRecovery\s*\(\s*reason:\s*String\s*\):\s*Boolean(?s:(?!\n\s*fun\s+).)*"stream_in_progress"' `
        "Stream-in-progress interruptions must enter remote snapshot recovery instead of being dropped as an unrecoverable foreground failure."
    Require-Match $failures $chatScreen 'SessionApi\.streamChat\s*\(\s*options\s*=\s*SessionApi\.StreamOptions\s*\((?s:.*?)sessionGeneration\s*=\s*streamSessionGeneration' `
        "Foreground chat streams must pass the captured session generation explicitly, matching pending image-send recovery."
    Require-NoMatch $failures $imageUploader 'Log\.e\s*\(\s*TAG\s*,\s*"[^"]*"\s*,\s*e\s*\)|e\.message|error=\$errorMsg|上传异常:\s*\$\{e\.message\}' `
        "Image upload/compression logs must not print raw exception messages, stack traces, backend errors, image URLs, or other sensitive details."
    Require-Match $failures $chatScreen 'private\s+fun\s+Context\.clearLocalChatHistoryStateSync\s*\(\s*chatScopeId:\s*String\s*\)(?s:.*?)remove\("\$CHAT_CACHE_KEY_PREFIX\$chatScopeId"\)(?s:.*?)remove\("\$CHAT_STREAM_DRAFT_KEY_PREFIX\$chatScopeId"\)(?s:.*?)remove\("\$CHAT_COMPOSER_DRAFT_KEY_PREFIX\$chatScopeId"\)(?s:.*?)remove\("\$TODAY_AGRI_CARD_CACHE_DAY_KEY_PREFIX\$chatScopeId"\)(?s:.*?)remove\("\$TODAY_AGRI_CARD_CACHE_KEY_PREFIX\$chatScopeId"\)(?s:.*?)remove\("\$TODAY_AGRI_MAIN_SHOWN_DAY_KEY_PREFIX\$chatScopeId"\)(?s:.*?)commit\s*\(\s*\)' `
        "Chat clean-state reset must synchronously remove local window, stream draft, composer draft, today-agri cache and shown-day marker."
    Require-Match $failures $chatScreen 'fun\s+applyChatHistoryCleared\s*\(\s*\)(?s:.*?)SessionApi\.resetUiRuntimeForCleanState\s*\(\s*\)(?s:.*?)advanceChatHistoryClearEpoch\s*\(\s*\)(?s:.*?)context\.clearLocalChatHistoryStateSync\s*\(\s*chatScopeId\s*\)(?s:.*?)resetTodayAgriRuntimeAfterHistoryClear\(\)(?s:.*?)messages\.clear\s*\(\s*\)(?s:.*?)initialBottomSnapDone\s*=\s*false' `
        "Chat history clear must invalidate in-flight runtime, advance clear epoch, clear local state and restart bottom calibration."
    Require-Match $failures $chatScreen 'fun\s+resetTodayAgriRuntimeAfterHistoryClear\(\)(?s:.*?)todayAgriMainShownDay\s*=\s*currentChinaDateKey\(\)(?s:.*?)saveTodayAgriMainShownDaySync(?s:.*?)todayAgriShownThisRuntime\s*=\s*false(?s:.*?)todayAgriAutoInsertSuppressedThisRuntime\s*=\s*false(?s:.*?)todayAgriMainItem\s*=\s*null(?s:.*?)todayAgriCard\s*=\s*null(?s:.*?)todayAgriRemoteConfirmedDay\s*=\s*null' `
        "Chat history clear must remove the in-process today agri item and mark the current day handled so it does not reappear after deletion."
    Require-Match $failures $chatScreen 'LocalChatWindowSnapshotPayload\s*\((?s:.*?)sessionGeneration\s*=\s*SessionApi\.currentSessionGenerationOrNull\s*\(\s*\)' `
        "Local chat window snapshots must be stamped with the current backend session generation."
    Require-Match $failures $chatScreen 'if\s*\(\s*!\s*isStoredSessionGenerationCurrent\s*\(\s*payload\?\.sessionGeneration\s*\)\s*\)\s*\{\s*return@runCatching\s+LocalChatWindowSnapshot\s*\(\s*\)\s*\}' `
        "Local chat window snapshots from an old session generation must be ignored on startup."
    Require-Match $failures $chatScreen 'LaunchedEffect\s*\(\s*uiRuntimeResetKey\s*\)\s*\{(?s:.*?)mainHandler\.removeCallbacksAndMessages\s*\(\s*null\s*\)(?s:.*?)streamRevealJob\?\.cancel\s*\(\s*\)(?s:.*?)remoteRecoveryJob\?\.cancel\s*\(\s*\)(?s:.*?)SessionApi\.resetUiRuntimeForCleanState\s*\(\s*\)' `
        "Chat runtime reset must cancel pending UI callbacks and stale network callbacks when the UI runtime key changes."
    Require-Match $failures $chatScreen 'DisposableEffect\s*\(\s*uiRuntimeResetKey\s*\)\s*\{\s*onDispose\s*\{(?s:.*?)mainHandler\.removeCallbacksAndMessages\s*\(\s*null\s*\)(?s:.*?)streamRevealJob\?\.cancel\s*\(\s*\)(?s:.*?)remoteRecoveryJob\?\.cancel\s*\(\s*\)(?s:.*?)SessionApi\.resetUiRuntimeForCleanState\s*\(\s*\)' `
        "Chat runtime disposal must cancel stale callbacks and reset SessionApi generation."
    $settingsLabelMembership = [regex]::Escape("$([char]0x4f1a)$([char]0x5458)$([char]0x4e2d)$([char]0x5fc3)")
    $settingsLabelAccount = [regex]::Escape("$([char]0x8d26)$([char]0x53f7)$([char]0x7ba1)$([char]0x7406)")
    $settingsLabelSupport = [regex]::Escape("$([char]0x5e2e)$([char]0x52a9)$([char]0x4e0e)$([char]0x53cd)$([char]0x9988)")
    $settingsLabelTodayAgri = [regex]::Escape("$([char]0x4eca)$([char]0x65e5)$([char]0x519c)$([char]0x60c5)")
    $settingsLabelUpdate = [regex]::Escape("$([char]0x68c0)$([char]0x67e5)$([char]0x66f4)$([char]0x65b0)")
    $settingsLabelGiftCard = [regex]::Escape("$([char]0x793c)$([char]0x54c1)$([char]0x5361)")
    $settingsLabelLegal = [regex]::Escape("$([char]0x534f)$([char]0x8bae)$([char]0x4e0e)$([char]0x9690)$([char]0x79c1)")
    $settingsLabelLogout = [regex]::Escape("$([char]0x9000)$([char]0x51fa)$([char]0x767b)$([char]0x5f55)")
    $accountLabelPhone = [regex]::Escape("$([char]0x624b)$([char]0x673a)$([char]0x53f7)")
    $accountLabelClearing = [regex]::Escape("$([char]0x6e05)$([char]0x7406)$([char]0x4e2d)")
    $accountLabelClearCache = [regex]::Escape("$([char]0x6e05)$([char]0x7406)$([char]0x4e34)$([char]0x65f6)$([char]0x7f13)$([char]0x5b58)")
    $accountLabelDeleteHistory = [regex]::Escape("$([char]0x5220)$([char]0x9664)$([char]0x5386)$([char]0x53f2)$([char]0x5bf9)$([char]0x8bdd)")
    $accountLabelLoggingOut = [regex]::Escape("$([char]0x9000)$([char]0x51fa)$([char]0x4e2d)")
    $accountLabelSubmitting = [regex]::Escape("$([char]0x63d0)$([char]0x4ea4)$([char]0x4e2d)")
    $accountLabelDeleteAccount = [regex]::Escape("$([char]0x7533)$([char]0x8bf7)$([char]0x6ce8)$([char]0x9500)$([char]0x8d26)$([char]0x53f7)")

    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerMenuMainPage(?s:.*?)HamburgerMenuIcon\.Logout(?s:.*?)title\s*=\s*"' + $settingsLabelLogout + '"(?s:.*?)destructive\s*=\s*true') `
        "Settings main page must keep the default logout row in code, not depend on cached UI state."
    Require-Match $failures $hamburgerMenuSheet 'ui\.settings_main_opened' `
        "Settings main page must keep a safe client log so clean-state UI rollback reports are traceable."
    Require-Match $failures $hamburgerMenuSheet 'ui\.account_management_opened' `
        "Account management page must keep a safe client log so clean-state UI rollback reports are traceable."
    Require-Match $failures $hamburgerMenuSheet 'APP_ICP_RECORD_NUMBER\s*=\s*"京ICP备2026031728号-2A"' `
        "Settings/legal pages must keep the approved App ICP filing number visible in the APK."
    Require-Match $failures $hamburgerMenuSheet 'private\s+fun\s+AppFilingFooter(?s:.*?)App备案号：\$APP_ICP_RECORD_NUMBER' `
        "Settings main page must keep a low-noise App ICP filing footer with only the approved filing number."
    Require-Match $failures $hamburgerMenuSheet 'internal\s+fun\s+HamburgerMenuSheetPreview(?s:.*?)HamburgerMenuPreviewGroups\(\)(?s:.*?)AppFilingFooter' `
        "Debug UI preview for settings must render the same App ICP filing footer as production."
    Require-Match $failures $hamburgerMenuSheet 'internal\s+fun\s+HamburgerMenuShellPreview(?s:.*?)HamburgerMenuPreviewGroups\(\)(?s:.*?)AppFilingFooter' `
        "Debug UI shell preview for settings must render the same App ICP filing footer as production."
    Require-NoMatch $failures $hamburgerMenuSheet '备案查询' `
        "Settings footer must not show a separate filing-query row."
    Require-Match $failures $hamburgerMenuSheet 'showNotice\("当前没有可用更新"\)' `
        "Manual app-update checks must use a clear no-available-update notice instead of implying broken release materials are already latest."
    Require-Match $failures $hamburgerMenuSheet 'text\s*=\s*"发现新版本"' `
        "App update dialog must keep the mature user-facing title."
    Require-Match $failures $hamburgerMenuSheet 'saveLastPromptedUpdateVersionCode(?s:.*?)event\s*=\s*"app_update\.install_started"' `
        "App update must record the install-page attempt so completed or cancelled installs can be reconciled."
    Require-Match $failures $hamburgerMenuSheet 'APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY\s*=\s*"pending_install_version_code"' `
        "App update install attempts must persist the target version so process death during the system installer does not suppress the same version forever."
    Require-Match $failures $hamburgerMenuSheet 'savePendingInstallAttemptVersionCode(?s:.*?)saveLastPromptedUpdateVersionCode(?s:.*?)app_update\.install_started' `
        "App update must persist the pending install attempt before logging install_started."
    Require-Match $failures $hamburgerMenuSheet 'clearPendingInstallAttemptVersionCode(?s:.*?)clearLastPromptedUpdateVersionCode(?s:.*?)app_update\.install_not_completed' `
        "App update must clear prompt suppression if the system installer returns or the process restarts without installing the target version."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("更新未完成提示",\s*"系统安装取消后可继续安装",\s*UiCopyPreviewKind\.AppUpdateInstallNotCompletedHint\)' `
        "Debug UI copy preview must include the app-update install-not-completed notice."
    Require-Match $failures $chatScreen 'UiCopyPreviewKind\.AppUpdateInstallNotCompletedHint(?s:.*?)更新未完成，可稍后继续安装' `
        "Debug UI copy preview must render the actual app-update install-not-completed notice."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("更新权限大字体",\s*"1\.6x 字体下真实更新弹窗",\s*UiCopyPreviewKind\.AppUpdatePermissionLargeFont\)(?s:.*?)UiCopyPreviewItem\("账号管理大字体",\s*"1\.6x 字体下账号和危险操作",\s*UiCopyPreviewKind\.HamburgerAccountLargeFont\)' `
        "Debug UI copy preview must keep large-font coverage for app update permission and account management screens."
    Require-Match $failures $chatScreen 'UiCopyPreviewKind\.AppUpdateInstallPermissionHint(?s:.*?)HamburgerAppUpdateDialogPreview\(installPermissionPending\s*=\s*true\)' `
        "Debug UI copy preview must render the real app-update permission-pending dialog, not only a plain text hint."
    Require-Match $failures $chatScreen 'private\s+fun\s+UiCopyPreviewLargeFont(?s:.*?)LocalDensity\s+provides\s+Density\((?s:.*?)fontScale\s*=\s*1\.6f' `
        "Debug UI copy preview must include a reusable large-font wrapper for regression checks."
    Require-NoMatch $failures $hamburgerMenuSheet 'fun\s+startAppUpdate\s*\([^{]+\)\s*\{(?s:.*?)val\s+appContext\s*=\s*context\.applicationContext(?s:.*?)update\.latestVersionCode(?s:.*?)saveLastPromptedUpdateVersionCode(?s:.*?)if\s*\(\s*!AppUpdateInstaller\.canRequestInstallPackages' `
        "App update must not mark a version as prompted before permission, download, and install-intent stages have succeeded."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("礼品卡生效规则",\s*"生成即可兑换，不做预约生效",\s*UiCopyPreviewKind\.HamburgerGiftCardImmediateRule\)' `
        "Debug UI copy preview must show the current gift-card immediate-effect rule."
    Require-Match $failures $chatScreen 'valid_from 只作创建追溯，不作为预约生效门槛' `
        "Debug UI copy preview must explain that gift-card valid_from is not a future activation gate."
    Require-Match $failures $membershipCenterSheet ([regex]::Escape('加油包暂未开放。开放后仅 Plus / Pro 用户可购买；未用完次数长期保留，购买和续购以页面开放为准。')) `
        "Membership topup copy must not imply that topup balance expires with membership."
    Require-NoMatch $failures $membershipCenterSheet '有效期、使用规则' `
        "Membership topup copy must not keep the old vague validity-period wording."
    Require-Match $failures $membershipCenterSheet 'private\s+fun\s+MembershipPlanSectionTitle(?s:.*?)heightIn\(min\s*=\s*24\.dp\)' `
        "Membership section title must use a minimum height instead of a fixed height so large fonts are not clipped."
    Require-Match $failures $membershipCenterSheet 'private\s+fun\s+MembershipActionButton(?s:.*?)heightIn\(min\s*=\s*46\.dp\)(?s:.*?)maxLines\s*=\s*2' `
        "Membership action buttons must allow two-line text and avoid fixed-height clipping on large font settings."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerMenuMainPage(?s:.*?)title\s*=\s*"' + $settingsLabelMembership + '"(?s:.*?)title\s*=\s*"' + $settingsLabelAccount + '"(?s:.*?)title\s*=\s*"' + $settingsLabelSupport + '"(?s:.*?)title\s*=\s*"' + $settingsLabelTodayAgri + '"(?s:.*?)title\s*=\s*"' + $settingsLabelUpdate + '"(?s:.*?)title\s*=\s*"' + $settingsLabelGiftCard + '"(?s:.*?)title\s*=\s*"' + $settingsLabelLegal + '"(?s:.*?)title\s*=\s*"' + $settingsLabelLogout + '"') `
        "Settings main page defaults must include every production row after app data is cleared."
    Require-Match $failures $hamburgerMenuSheet 'private\s+fun\s+HamburgerLegalHubContent(?s:.*?)HamburgerLegalPageTitle\("协议与隐私"\)' `
        "Legal hub page title must match the settings entry because it also contains privacy, permissions and risk pages."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerAccountManagementContent(?s:.*?)title\s*=\s*if\s*\(\s*logoutSubmitting\s*\)\s*"' + $accountLabelLoggingOut + '"\s*else\s*"' + $settingsLabelLogout + '"') `
        "Account management page must keep its logout row in the default code path."
    Require-Match $failures $hamburgerMenuSheet ('private\s+fun\s+HamburgerAccountManagementContent(?s:.*?)title\s*=\s*"' + $accountLabelPhone + '"(?s:.*?)title\s*=\s*if\s*\(\s*cacheCleanupSubmitting\s*\)\s*"' + $accountLabelClearing + '"\s*else\s*"' + $accountLabelClearCache + '"(?s:.*?)title\s*=\s*"' + $accountLabelDeleteHistory + '"(?s:.*?)title\s*=\s*if\s*\(\s*logoutSubmitting\s*\)\s*"' + $accountLabelLoggingOut + '"\s*else\s*"' + $settingsLabelLogout + '"(?s:.*?)title\s*=\s*if\s*\(\s*accountDeletionSubmitting\s*\)\s*"' + $accountLabelSubmitting + '"\s*else\s*"' + $accountLabelDeleteAccount + '"') `
        "Account management defaults must include phone, scoped cache cleanup, history deletion, logout, and account deletion application rows after app data is cleared."
    Require-Match $failures $hamburgerMenuSheet ([regex]::Escape("将删除当前账号历史对话，并清除长期记忆。会员、礼品卡、反馈记录不受影响。")) `
        "Delete-history confirmation must keep the concise user-approved wording and mention long-term memory."
    Require-Match $failures $hamburgerMenuSheet 'title\s*=\s*"六、使用规范、责任和协议更新"(?s:.*?)请您在合法、真实、合理的范围内使用本服务' `
        "Service agreement section 6 must use the calmer formal usage-norms wording."
    Require-NoMatch $failures $hamburgerMenuSheet '攻击接口|爬虫抓取|批量撞库礼品卡|恶意消耗服务资源' `
        "Legal copy shown in-app must not revert to the harsh blacklist-style wording."
    Require-Match $failures $hamburgerMenuSheet 'private\s+fun\s+HamburgerLegalHubRow(?s:.*?)maxLines\s*=\s*2' `
        "Legal hub row titles must be able to wrap, so long legal-entry names are not ellipsized on narrow screens."
    Require-Match $failures $hamburgerMenuSheet 'private\s+fun\s+HamburgerLegalPageTitle(?s:.*?)maxLines\s*=\s*2(?s:.*?)padding\(start\s*=\s*76\.dp,\s*end\s*=\s*76\.dp' `
        "Legal page titles must leave room for the floating back button and wrap on narrow screens."
    Require-Match $failures $membershipCenterSheet '会员信息刷新失败，请检查网络后重试(?s:.*?)heightIn\(min\s*=\s*34\.dp\)(?s:.*?)text\s*=\s*"重试"' `
        "Membership refresh failure notice must use plain user-facing wording and avoid fixed-height clipping."
    Require-Match $failures $membershipCenterSheet 'width\(168\.dp\)(?s:.*?)heightIn\(min\s*=\s*40\.dp\)' `
        "Membership success confirmation button must avoid fixed-height clipping on large font settings."
    Require-Match $failures $sessionApi 'gift_card_lower_tier"\s*->\s*"当前已是更高档会员，这张礼品卡不能叠加使用"' `
        "Gift card lower-tier rejection must keep the concise formal user-facing copy."
    Require-Match $failures $hamburgerMenuSheet '正在读取反馈记录\.\.\.(?s:.*?)反馈记录加载失败' `
        "Support feedback loading/error copy must use user-facing feedback-record wording instead of technical sync wording."
    Require-NoMatch $failures $hamburgerMenuSheet 'val\s+shouldAutoPrompt\s*=(?s:.*?)info\.forceUpdate\s*==\s*true' `
        "App update auto prompt logic must not force prompt based only on force_update; current Android release treats updates as ordinary prompts."
    Require-NoMatch $failures $hamburgerMenuSheet 'private\s+fun\s+HamburgerAppUpdateDialog(?s:.*?)forceUpdate\s*=\s*update\.forceUpdate\s*==\s*true' `
        "Android update dialogs must not turn force_update into an undismissable prompt; current Android release treats updates as ordinary prompts."
    Require-NoMatch $failures $hamburgerMenuSheet 'private\s+fun\s+HamburgerAppUpdateCard(?s:.*?)if\s*\(!forceUpdate\)' `
        "Android update dialogs must keep the Later action available in current releases."
    $pendingWorkerPrivacyGatePattern = "!PrivacyConsentStore\.isAccepted\s*\(\s*applicationContext\s*\)(?s:.*?)Result\.retry\(\)(?s:.*?)IdManager\.init"
    $todayAgriCardPattern = "fun\s+TodayAgriNewsText\b(?s:.*?)SelectionContainer(?s:.*?)TodayAgriNewsItem"
    $todayAgriRenderablePattern = "fun\s+SessionApi\.TodayAgriCard\.isRenderableTodayAgriCard\b"
    $chatScreenTodayAgriImplementationPattern = "private\s+fun\s+TodayAgriNewsText|private\s+fun\s+TodayAgriNewsCard|private\s+fun\s+TodayAgriNewsItem|private\s+fun\s+todayAgriDateText|private\s+fun\s+uiCopyPreviewTodayAgriCard"

    Require-Match $failures $loginScreen $loginSharedTextPattern `
        "Login agreement links must let users read the same service agreement and privacy policy content as settings."
    Require-Match $failures $loginScreen 'var\s+agreed\s+by\s+remember\(context\)\s*\{\s*mutableStateOf\(PrivacyConsentStore\.isAccepted\(context\)\)\s*\}' `
        "Login agreement checkbox must initialize from persisted privacy consent, so UI and runtime consent state stay aligned."
    Require-NoMatch $failures $loginScreen 'onCheckedChange\s*=\s*\{(?s:.*?)acceptAgreementIfNeeded\(\)' `
        "Login agreement checkbox must not persist consent immediately; consent should be recorded only when the user submits SMS send or login."
    Require-Match $failures $loginScreen 'private\s+fun\s+LoginLegalDialog(?s:.*?)DialogProperties\(usePlatformDefaultWidth\s*=\s*false\)(?s:.*?)WindowInsets\.safeDrawing\.only\(WindowInsetsSides\.Vertical\)(?s:.*?)widthIn\(max\s*=\s*360\.dp\)' `
        "Login legal dialog must keep the safe-area, narrow-screen friendly shell instead of reverting to the platform default dialog width."
    Require-Match $failures $loginScreen 'text\s*=\s*"农技千查"(?s:.*?)maxLines\s*=\s*2' `
        "Login brand title must not be forced into one-line ellipsis on narrow screens or large font settings."
    Require-Match $failures $pendingWorker $pendingWorkerPrivacyGatePattern `
        "Pending background chat sends must not initialize identity or call backend before first-launch privacy consent is accepted."
    Require-Match $failures $todayAgriCardUi $todayAgriCardPattern `
        "Today agri rendering must stay in TodayAgriCardUi.kt and use the ordinary selectable text style."
    Require-Match $failures $todayAgriCardUi $todayAgriRenderablePattern `
        "Today agri card display validation must stay near the card UI renderer."
    Require-NoMatch $failures $todayAgriCardUi 'BorderStroke\s*\(' `
        "Today agri main-chat item must stay as ordinary text, not a framed card."
    Require-Match $failures $todayAgriCardUi 'fontWeight\s*=\s*FontWeight\.SemiBold(?s:.*?)TodayAgriNewsItem' `
        "Today agri title must stay visually emphasized without becoming a separate card surface."
    Require-Match $failures $todayAgriCardUi 'HorizontalDivider(?s:.*?)Color\(0xFFE7E9ED\)(?s:.*?)items\.forEachIndexed' `
        "Today agri title must keep a light divider before the news items, without turning the item back into a framed card."
    Require-Match $failures $todayAgriCardUi '"一、"(?s:.*?)"二、"(?s:.*?)"三、"' `
        "Today agri item labels must keep the current Chinese sequence marker style."
    Require-Match $failures $todayAgriCardUi 'LocalTextToolbar\s+provides\s+textToolbar' `
        "Today agri selection must be able to use the same copy/full-copy toolbar as assistant text."
    Require-Match $failures $todayAgriCardUi 'fun\s+SessionApi\.TodayAgriCard\.toTodayAgriPlainText\b' `
        "Today agri full-copy text must stay with the isolated today-agri renderer."
    Require-Match $failures $hamburgerMenuSheet 'HamburgerTodayAgriHistoryDaySection(?s:.*?)HamburgerTodayAgriHistoryCard\((?s:.*?)dateText\s*=\s*dateText(?s:.*?)private\s+fun\s+HamburgerTodayAgriHistoryCard\((?s:.*?)dateText:\s*String(?s:.*?)HorizontalDivider' `
        "Today agri history must keep the date inside each card with one light internal divider, not as a floating external date label."
    Require-Match $failures $chatScreen 'ChatTimelineItem\.TodayAgriCard(?s:.*?)buildMessageSelectionTextToolbar(?s:.*?)TodayAgriNewsText' `
        "Today agri main-chat copy menu must stay aligned with assistant text copy/full-copy behavior."
    Require-Match $failures $chatScreen 'resolveTodayAgriContextDayForTimeline(?s:.*?)userMessagesAfterAnchor\s*<\s*3' `
        "Today agri context must be scoped to the next three user sends after its visual timeline anchor."
    Require-Match $failures $chatScreen 'resolveTodayAgriContextDayForTimeline(?s:.*?)failedUserMessageIds' `
        "Today agri context counting must ignore local failed user sends so a failed send does not prematurely end the three-send window."
    Require-Match $failures $chatScreen 'requestedAfterMessageId\s*!=\s*null\s*&&\s*hiddenRoundCount\s*>\s*0(?s:.*?)ChatTimelineItem\.HistoryNotice' `
        "If today agri's saved anchor is outside the visible trimmed window, it must fall back after the history notice instead of being reattached to the latest assistant answer."
    Require-Match $failures $chatScreen 'todayAgriContextDayForNextSend(?s:.*?)remoteConfirmedDay\s*=\s*todayAgriRemoteConfirmedDay' `
        "Today agri temporary context must wait for same-day remote confirmation; cached visual content alone must not enter model context."
    Require-Match $failures $chatScreen 'awaitTodayAgriCard\(\)(?s:.*?)todayAgriRemoteConfirmedDay\s*=\s*refreshDayKey' `
        "Today agri remote confirmation day must be set only after the backend returns a same-day renderable card."
    Require-NoMatch $failures $chatScreen 'saveTodayAgriCardAnchorSync|TODAY_AGRI_CARD_ANCHOR|todayAgriCardAnchor' `
        "Today agri main-chat state must not keep the retired local anchor storage path."
    Require-Match $failures $chatScreen 'internal\s+fun\s+shouldShowTodayAgriMainCard(?s:.*?)hasSavedItem(?s:.*?)shownThisRuntime\s*\|\|\s*hasSavedItem\s*\|\|\s*hasAssistantAnswerTail(?s:.*?)cardDay\s*==\s*normalizedCurrentDay(?s:.*?)!\s*suppressedThisRuntime\s*\|\|\s*shownThisRuntime\s*\|\|\s*hasSavedItem(?s:.*?)shownThisRuntime\s*\|\|\s*hasSavedItem\s*\|\|\s*shownDayKey\s*!=\s*cardDay' `
        "Today agri main-chat visibility must restore saved same-day items, while runtime suppression still blocks late auto-insert before the card becomes visible."
    Require-Match $failures $chatScreen 'hasCompletedAssistantAnswerTail\(messages,\s*failedAssistantMessageStates\.keys\)' `
        "Today agri completed-tail detection must exclude failed assistant tails in the live chat state."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriCardDoesNotTreatFailedAssistantAsCompletedTail(?s:.*?)failedAssistantMessageIds\s*=\s*setOf' `
        "Today agri must have unit coverage proving failed assistant tails do not count as completed answers."
    Require-Match $failures $chatScreen 'fun\s+suppressPendingTodayAgriAutoInsertForUserSend\(\)(?s:.*?)!\s*todayAgriShownThisRuntime\s*&&\s*!\s*hasTodayAgriCard(?s:.*?)todayAgriUserSendEpoch\+\+(?s:.*?)todayAgriAutoInsertSuppressedThisRuntime\s*=\s*true' `
        "Today agri pending auto-insert must be suppressed and generation-guarded when the user starts a chat before the card is visible."
    Require-Match $failures $chatScreen 'val\s+saveUserSendEpoch\s*=\s*todayAgriUserSendEpoch(?s:.*?)if\s*\(\s*saveUserSendEpoch\s*!=\s*todayAgriUserSendEpoch\s*\)\s*\{(?s:.*?)return@LaunchedEffect(?s:.*?)SessionApi\.saveTodayAgriItem(?s:.*?)saveUserSendEpoch\s*==\s*todayAgriUserSendEpoch' `
        "Today agri async save callback must not visually back-insert a saved item after the user has already sent a new message."
    Require-Match $failures $chatScreen 'LaunchedEffect\((?s:.*?)remoteSnapshotHydrationComplete(?s:.*?)shouldHydrateRemoteHistory(?s:.*?)!shouldRevealMessageList(?s:.*?)shouldPersistTodayAgriShownDay\s*=(?s:.*?)canPersistTodayAgriShownDay(?s:.*?)todayAgriMainCardVisibleLogged\s*=\s*true(?s:.*?)todayAgriShownThisRuntime\s*=\s*true(?s:.*?)saveTodayAgriMainShownDaySync' `
        "Today agri should be marked shown only after it is inserted into the visible main-chat timeline, and must persist the shown day after remote snapshot hydration completes."
    Require-Match $failures $chatScreen 'todayAgriRefreshDayKey\s*!=\s*currentDay(?s:.*?)todayAgriShownThisRuntime\s*=\s*false(?s:.*?)todayAgriAutoInsertSuppressedThisRuntime\s*=\s*hasStartedConversation(?s:.*?)todayAgriMainCardLoadedLogged\s*=\s*false(?s:.*?)todayAgriMainCardVisibleLogged\s*=\s*false' `
        "Today agri same-runtime day rollover must reset shown/log gates and avoid surprise insertion if the user is already chatting."
    Require-Match $failures $chatScreen 'hasRefreshDayItem\s*=\s*currentTodayAgriMainItem\?\.day_cn\s*==\s*refreshDayKey(?s:.*?)if\s*\(\s*!\s*todayAgriShownThisRuntime\s*&&\s*todayAgriMainShownDay\s*==\s*refreshDayKey\s*&&\s*!hasRefreshDayItem\s*\)' `
        "Today agri fetch for the main chat must not let the shown-day marker block a saved same-day main item from being restored."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriMainCardShowsOncePerDayButStaysVisibleForCurrentRuntime' `
        "Today agri once-per-day main-chat visibility must have unit coverage."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriMainCardShowsOncePerDayButStaysVisibleForCurrentRuntime(?s:.*?)assertFalse\((?s:.*?)hasAssistantAnswerTail\s*=\s*false(?s:.*?)assertTrue\((?s:.*?)shownDayKey\s*=\s*""(?s:.*?)hasAssistantAnswerTail\s*=\s*true(?s:.*?)suppressedThisRuntime\s*=\s*false(?s:.*?)assertFalse\((?s:.*?)shownDayKey\s*=\s*""(?s:.*?)hasAssistantAnswerTail\s*=\s*true(?s:.*?)suppressedThisRuntime\s*=\s*true(?s:.*?)assertFalse\((?s:.*?)shownDayKey\s*=\s*"20260615"(?s:.*?)hasAssistantAnswerTail\s*=\s*true(?s:.*?)suppressedThisRuntime\s*=\s*false' `
        "Today agri visibility tests must lock the current direction: no completed assistant tail means hidden, completed tail can show once, runtime suppression blocks late auto-insert, and same-day unsaved cards do not reappear."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriMainCardShowsOncePerDayButStaysVisibleForCurrentRuntime(?s:.*?)hasSavedItem\s*=\s*true(?s:.*?)suppressedThisRuntime\s*=\s*false(?s:.*?)assertTrue\((?s:.*?)hasSavedItem\s*=\s*true(?s:.*?)suppressedThisRuntime\s*=\s*true' `
        "Today agri visibility tests must lock that saved same-day main items restore even after runtime suppression."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriCardAfterCompletedAssistantStaysInsideTimeline(?s:.*?)todayAgriAfterMessageId\s*=\s*second\.id' `
        "Today agri must anchor after a completed assistant answer in the ordinary timeline item builder."
    Require-Match $failures $chatTimelineItemsTest 'todayAgriMissingAnchorFallbackDoesNotRestartContextWindow' `
        "Today agri trimmed-anchor fallback must have unit coverage proving it does not restart the temporary context window."
    Require-Match $failures $chatScreen 'fun\s+restoredStartupWorklinePhase(?s:.*?)hasTodayAgriVisualContent\s*->\s*false' `
        "If today agri is visible after real history, it must still ignore persisted bottom-owned startup state when resetting top document flow."
    Require-Match $failures $chatScreen 'hydratedTodayAgriVisualContent\s*=\s*shouldShowTodayAgriMainCard(?s:.*?)hasAssistantAnswerTail\s*=\s*hasCompletedAssistantAnswerTail\(\s*hydratedSnapshot\.messages,\s*hydratedSnapshot\.failedAssistantMessageStates\.keys\s*\)(?s:.*?)hasTodayAgriVisualContent\s*=\s*hydratedTodayAgriVisualContent' `
        "Remote hydrate replacement must compute today-agri visual content from the hydrated message tail, not stale current UI state."
    Require-Match $failures $chatScreen 'remoteSnapshotHydrationComplete(?s:.*?)if\s*\(\s*shouldHydrateRemoteHistory\s*&&\s*!remoteSnapshotHydrationComplete\s*\)\s*return@LaunchedEffect(?s:.*?)saveTodayAgriItem' `
        "Today agri main item saving must wait for the remote snapshot, not only the local-first history hydration gate."
    Require-Match $failures $chatScreen 'TODAY_AGRI_ITEM_SAVE_MAX_ATTEMPTS(?s:.*?)todayAgriItemSaveRetryAttempt(?s:.*?)delay\(TODAY_AGRI_ITEM_SAVE_RETRY_DELAY_MS \* retryAttempt\)(?s:.*?)SessionApi\.saveTodayAgriItem' `
        "Today agri main item saving must keep a bounded retry path for transient save failures."
    Require-Match $failures $sessionApi 'fun\s+saveTodayAgriItem(?s:.*?)session_generation' `
        "Today agri main-chat item saves must include session generation."
    Require-Match $failures $chatScreen 'snapshot\.today_agri_items(?s:.*?)item\.card(?s:.*?)todayAgriMainItem\s*=\s*item' `
        "Today agri main-chat item must be restored from session snapshot with its saved card content."
    Require-Match $failures $chatScreen '"remote_snapshot_hydrated"\s+to\s+remoteSnapshotHydrationComplete' `
        "Chat startup diagnostics must distinguish local-first history hydration from remote snapshot completion."
    Require-NoMatch $failures $chatScreen 'persistedTodayAgriContextDayForUserMessage(?s:.*?)takeIf\s*\{\s*it\s*==\s*currentDay\s*\}' `
        "Persisted today_agri_context_day for an existing client_msg_id must be reused across midnight to keep replay request hashes stable."
    Require-Match $failures ($chatScreen + "`n" + $sessionApi + "`n" + $pendingWorker) 'today_agri_context_day|todayAgriContextDay' `
        "Today agri temporary context day must be carried explicitly through Android stream and pending-send paths."
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
    $forbiddenMainScrollPattern = "reverseLayout\s*=|asReversed\s*\(|dispatchRawDelta\s*\(|scrollBy\s*\(|StreamingBlockChatListItem|StreamingTextBlock|streaming_tail|streamingBrowseBlockSnapshot|activeStreamingBlockIndex|SparseBottomSpacer|BottomActiveZone|StreamingLocation|requestSendStartBottomSnap|followStreamingByDelta|streamBottomFollowActive"
    Require-NoMatch $failures $chatMainScrollSurface $forbiddenMainScrollPattern `
        "Main chat must stay on the current forward LazyColumn chain and must not restore reverse layout, raw-delta/scrollBy chasing, active-zone overlay, or split streaming items."
    Require-Match $failures $chatRecyclerViewHost 'LazyColumn\s*\((?s:.*?)verticalArrangement\s*=\s*verticalArrangement(?s:.*?)userScrollEnabled\s*=\s*true(?s:.*?)items\s*\(' `
        "ChatRecyclerViewHost must remain a single user-scrollable forward LazyColumn over the provided timeline items."
    Require-NoMatch $failures $chatRecyclerViewHost 'reverseLayout\s*=' `
        "ChatRecyclerViewHost must not opt into reverseLayout."
    Require-Match $failures $chatScreen 'shouldAnchorStreamingBottomThisFrame(?s:.*?)SideEffect\s*\{\s*requestForwardListBottomAnchor\s*\(\s*\)\s*\}' `
        "Streaming content must keep the same-frame bottom-anchor SideEffect that prevents the tail from flashing below the workline."
    Require-Match $failures $chatScreen 'STREAM_TYPEWRITER_IDLE_POLL_MS\s*=\s*20L' `
        "Streaming typewriter idle polling must keep the calmer 20ms rhythm."
    Require-Match $failures $chatScreen 'STREAM_REVEAL_FRAME_BUDGET_MS\s*=\s*40L' `
        "Streaming reveal frame budget must keep the normal faster drain."
    Require-Match $failures $chatScreen 'REMOTE_STREAM_MIN_BALL_MS\s*=\s*1800L' `
        "Remote streaming waiting ball must remain visible without holding the first text too long."
    Require-Match $failures $chatScreen 'GPT_BALL_PULSE_MS\s*=\s*700' `
        "GPT-style waiting ball pulse rhythm must stay readable at the current pace."
    Require-Match $failures $chatScreen 'assistantDisclaimerTextStyle(?s:.*?)fontWeight\s*=\s*FontWeight\.Normal' `
        "Assistant disclaimer must stay normal-weight gray text, not bold."
    Require-Match $failures $chatScreen 'val\s+userBubbleColor\s*=\s*Color\(0xFF050505\)' `
        "User messages must keep the current black bubble surface."
    Require-Match $failures $chatScreen 'SelectableRenderedUserMessageBubble(?s:.*?)buildPlainLinkedAnnotatedString\(content,\s*linkColor\s*=\s*Color\.White\)(?s:.*?)color\s*=\s*Color\.White' `
        "User message text and user-link text must remain white inside the black bubble."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("今日农情窄屏",\s*"280dp 下标题、正文和来源不互挤",\s*UiCopyPreviewKind\.TodayAgriNarrow\)' `
        "Debug UI copy preview must include a narrow today-agri layout case."
    Require-Match $failures $chatScreen 'UI_COPY_PREVIEW_ASSISTANT_MARKDOWN_SAMPLE(?s:.*?)\*\*处理建议\*\*(?s:.*?)\*\*注意事项：\*\*' `
        "Debug UI copy preview must include standalone bold heading lines so ordinary AI text dividers can be checked."
    Require-Match $failures $chatScreen 'UiCopyPreviewKind\.TodayAgriNarrow(?s:.*?)TodayAgriNewsText\((?s:.*?)horizontalPadding\s*=\s*0\.dp(?s:.*?)maxContentWidth\s*=\s*280\.dp' `
        "Today agri narrow preview must exercise the 280dp ordinary-text layout."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("检查更新",\s*"物料完整且版本更高才提示更新",\s*UiCopyPreviewKind\.HamburgerAppUpdateDialog\)' `
        "Debug UI copy preview must show that app updates only prompt when release metadata is complete and the version is newer."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\("农情上下文规则",\s*"远端确认后显示，后方三轮临时参考",\s*UiCopyPreviewKind\.TodayAgriContextRule\)' `
        "Debug UI copy preview must include the today-agri empty-state, completed-tail, remote-ready three-round temporary context, and anti-surprise-insert rule."
    Require-Match $failures $chatScreen '"无真实聊天时只显示欢迎语，今日农情不占空态"' `
        "Debug UI copy preview must explicitly show that today agri does not occupy the empty welcome state."
    Require-Match $failures $chatScreen '"如果用户本次开始问了而农情还没显示，本次运行不突然插入"' `
        "Debug UI copy preview must explicitly show that today agri is not inserted mid-chat before it has appeared."
    Require-Match $failures $chatScreen '"远端确认当天 ready 后，用户在它后面发送的后三轮会临时带当天农情标记"' `
        "Debug UI copy preview must explicitly show that today-agri context is only carried after the remote card is confirmed ready."
    Require-Match $failures $chatScreen 'UiCopyPreviewItem\(NETWORK_UNAVAILABLE_HINT_TEXT,\s*"未验证联网 / 门户 Wi-Fi / 无网络",\s*UiCopyPreviewKind\.Network\)' `
        "Debug UI copy preview must show that network unavailable covers unvalidated networks and captive portal Wi-Fi."
    Require-Match $failures $chatScreen 'MessageActionMenuButton(?s:.*?)contentDescription\s*=\s*label(?s:.*?)role\s*=\s*Role\.Button' `
        "Message action menu buttons must expose button semantics for accessibility and UI automation."
    Require-Match $failures $chatScreen 'if\s*\(\s*canAttemptRemoteAssistantRecovery\(reason\)\s*\)(?s:.*?)upsertAssistantMessagePlaceholder(?s:.*?)failedAssistantMessageStates\[finalId\]\s*=\s*FailedAssistantMessageState(?s:.*?)retryingAssistantMessageIds\[finalId\]\s*=\s*true' `
        "Recoverable stream interruptions must keep the assistant tail visible as a retrying footer while snapshot recovery runs."
    Require-Match $failures $chatScreen 'fun\s+finalizeInterruptedAssistant(?s:.*?)retryingAssistantMessageIds\.remove\(assistantMessageId\)' `
        "Assistant recovery must clear retrying footer state before settling into completed or retryable failed state."
    Require-Match $failures $chatComposerPanel 'InputActionMenuButton(?s:.*?)contentDescription\s*=\s*label(?s:.*?)role\s*=\s*Role\.Button' `
        "Input action menu buttons must expose button semantics for accessibility and UI automation."
    Require-Match $failures $chatComposerPanel 'ComposerSendActionButton(?s:.*?)contentDescription\s*=\s*"发送"(?s:.*?)role\s*=\s*Role\.Button' `
        "Composer send button must expose a hidden button label without adding visible text."
    Require-Match $failures $chatComposerPanel 'ComposerInlineAddButton(?s:.*?)contentDescription\s*=\s*"添加图片"(?s:.*?)role\s*=\s*Role\.Button' `
        "Composer add button must expose a hidden attachment label without adding visible text."
    Require-Match $failures $chatScreen 'IconButton\((?s:.*?)contentDescription\s*=\s*"会员中心"(?s:.*?)MembershipCenterLeafIcon' `
        "The top-right membership icon must keep a hidden accessibility label on the outer button."
    Require-Match $failures $chatScreen 'private\s+fun\s+MembershipCenterLeafIcon(?s:.*?)Image\((?s:.*?)contentDescription\s*=\s*null' `
        "The membership leaf graphic itself must stay decorative and avoid duplicating the outer button label."
    Require-Match $failures $chatStreamingRenderer "lastChar\s*==\s*'\\n'\s*->\s*if\s*\(\s*nextHasStructuralMarkdownPrefix\s*\)\s*92L\s*else\s*72L" `
        "Streaming newlines must keep a short readable pause to reduce heading/list reflow popping."
    Require-Match $failures $chatStreamingRenderer 'lastCodePoint\.isRendererCjkUnifiedIdeographCodePoint\(\)\s*->\s*22L' `
        "Streaming Chinese text must keep one visible character per normal-paced step."
    $onAdvanceMatch = [regex]::Match(
        $chatScreen,
        'onAdvance\s*=\s*\{\s*advance\s*->(?s:.*?streamingMessageId\s*=\s*advance\.messageId(?s:.*?)streamingRevealBuffer\s*=\s*advance\.revealBuffer(?s:.*?)streamingMessageContent\s*=\s*advance\.content.*?)\}'
    )
    if (!$onAdvanceMatch.Success) {
        Add-Failure $failures "Streaming reveal onAdvance block must stay simple and inspectable."
    } elseif ($onAdvanceMatch.Value -match 'requestForwardListBottomAnchor|requestProgrammaticForwardListBottomAnchor|scrollForwardListToBottom|scrollToItem|requestScrollToItem') {
        Add-Failure $failures "Streaming reveal onAdvance must not do pre-content scroll anchoring; anchoring belongs in the same-frame SideEffect."
    }
    Require-NoMatch $failures ($chatScreen + "`n" + $chatStreamingRenderer) 'freshSuffixEnabled|streamingFreshStart|streamingFreshEnd|streamingFreshTick|lastStreamingFreshRevealMs|currentFreshTick|lastFreshRevealMs' `
        "Streaming renderer must not restore the removed fresh-suffix highlight chain."
    Require-NoMatch $failures $chatScreen 'streamingMessageContent\s*\+\s*streamingRevealBuffer' `
        "Interrupted or background recovery paths must not flush unrevealed streaming buffer into visible assistant text; normal DONE drain owns buffer reveal."
    Require-NoMatch $failures $chatScreen 'safeDraft\.content\s*\+\s*safeDraft\.revealBuffer' `
        "Cold-start interrupted streaming draft recovery must not flush the saved unrevealed buffer into visible assistant text."
    Require-Match $failures $chatScreen 'visibleContentForInterruptedStreamingDraft(?s:.*?)return\s+normalizeAssistantText\(content\)' `
        "Cold-start interrupted streaming draft recovery must keep only the content that was already visible."
    Require-Match $failures $chatScreen 'verticalArrangement\s*=\s*if\s*\(\s*shouldUseTopArrangementForConversation\s*\(\s*\)\s*\)\s*\{(?s:.*?)Arrangement\.Top(?s:.*?)\}\s*else\s*\{(?s:.*?)Arrangement\.Bottom' `
        "Chat timeline must keep the top-only arrangement only for clean-state/top-flow cases and otherwise use the bottom workline layout."
    Require-Match $failures $chatScreen 'ChatTimelineItem\.TodayAgriCard(?s:.*?)TodayAgriNewsText' `
        "Today agri must keep rendering as a normal ChatTimelineItem in the main chat list."
    Require-Match $failures $chatStreamingRenderer 'if\s*\(\s*selectionEnabled\s*\)\s*\{\s*SelectionContainer\s*\{(?s:.*?)RendererAssistantMarkdownContentImpl' `
        "Assistant settled text must keep the message selection container even when content contains links, so copy/full-copy remains available."
    Require-Match $failures $chatStreamingRenderer 'parseRendererStandaloneBoldHeading(?s:.*?)StreamingLineModel\.Heading\(2,\s*headingText\)(?s:.*?)parseRendererStandaloneBoldHeading\(trimmed\)\s*!=\s*null' `
        "Assistant text dividers must recognize standalone bold heading lines, not only # markdown headings."
    Require-Match $failures $chatStreamingRenderer 'parseRendererActiveStandaloneBoldHeading(?s:.*?)StreamingLineModel\.Heading\(2,\s*headingText\)' `
        "Assistant text dividers must recognize an active standalone bold heading while it is still streaming."
    Require-Match $failures $chatStreamingRenderer 'heading\.level\s*(<=\s*3|>\s*3\)\s*return\s+false)' `
        "Assistant text dividers must include common level-3 Markdown headings, not only level-1/2 headings."
    Require-Match $failures $chatStreamingRenderer 'fun\s+RendererMarkdownTable\.toReadableCopyText\(\)(?s:.*?)buildRendererPlainCopyText(?s:.*?)model\.table\.toReadableCopyText\(\)' `
        "Message full-copy must convert Markdown tables into a human-readable grouped text, not raw TSV."
    Require-Match $failures $chatStreamingRenderer 'TextButton\((?s:.*?)onClick\s*=\s*\{(?s:.*?)table\.toPlainCopyText\(\)(?s:.*?)text\s*=\s*"复制表格"(?s:.*?)RendererMarkdownTableCardImpl' `
        "Markdown table UI must keep the mobile grouped table cards and the TSV copy-table button."
    Require-Match $failures $chatStreamingRenderer 'if\s*\(\s*rawRows\.isEmpty\(\)\s*\)\s*return\s+null' `
        "Incomplete Markdown table headers must stay plain text while streaming, instead of rendering a half-empty table."
    Require-NoMatch $failures $chatStreamingRenderer 'horizontalScroll\s*\(' `
        "Assistant Markdown table rendering must not revert to the old horizontal-scroll wide table on mobile."
    Require-Match $failures $chatStreamingRenderer 'text\.startsWith\("\*\*",\s*startIndex\s*=\s*cursor\)' `
        "Streaming typewriter pacing must treat a following standalone bold heading as a structural prefix."
    Require-Match $failures $chatStreamingRenderer 'previous\s+!is\s+StreamingLineModel\.Heading' `
        "Assistant text dividers must not stack between consecutive heading lines."
    Require-Match $failures $chatStreamingRenderer 'LinkInteractionListener(?s:.*?)uriHandler\.openUri\s*\(\s*url\s*\)(?s:.*?)withLink\s*\((?s:.*?)LinkAnnotation\.Url' `
        "Assistant Markdown links and bare URLs must keep real URL annotations that open through the system URI handler."
    Require-Match $failures $chatStreamingRenderer 'ui\.link_open_failed(?s:.*?)substringBefore\(":"(?s:.*?)exception' `
        "Assistant link open failures must remain user-visible and logged only as a safe summary, not as full URLs."
    Require-Match $failures $chatScreen 'hasActiveNetworkConnection(?s:.*?)NET_CAPABILITY_INTERNET(?s:.*?)NET_CAPABILITY_VALIDATED(?s:.*?)NET_CAPABILITY_CAPTIVE_PORTAL(?s:.*?)hasInternetCapability\s*&&\s*hasValidatedConnection\s*&&\s*!isCaptivePortal' `
        "Chat offline precheck must require a validated internet connection and reject captive-portal networks instead of treating every INTERNET-capable network as usable."
    Require-Match $failures $pendingWorker 'hasActiveNetworkConnection\(\)(?s:.*?)Result\.retry\(\)' `
        "Pending chat send worker must wait for a validated internet connection before uploading or streaming a background retry."
    Require-Match $failures $hamburgerMenuSheet 'BackHandler\s*\(\s*enabled\s*=\s*visible\s*\)(?s:.*?)handleBackClick\s*\(\s*\)' `
        "Settings shell must let Android back close the main settings page as well as nested pages."
    Require-Match $failures $hamburgerMenuSheet 'DisposableEffect\s*\(\s*Unit\s*\)(?s:.*?)onDispose\s*\{(?s:.*?)imagesForCleanup\.forEach\s*\(\s*contextForCleanup::deleteComposerImageAttachment\s*\)(?s:.*?)cleanupPendingComposerCameraImage' `
        "Support feedback must clean temporary selected images on page disposal, including interrupted sending states."
    Require-NoMatch $failures $hamburgerMenuSheet 'if\s*\(\s*!\s*sending\s*&&\s*selectedImages\.isNotEmpty\s*\(\s*\)\s*\)' `
        "Support feedback disposal cleanup must not skip temporary images while a send is in progress."
    Require-Match $failures $hamburgerMenuSheet 'rememberLazyListState\s*\((?s:.*?)initialFirstVisibleItemIndex\s*=\s*messages\.lastIndex\.coerceAtLeast\(0\)' `
        "Support feedback with cached messages must initially open at the latest message instead of flashing the top before scrolling down."
    Require-Match $failures $hamburgerMenuSheet 'if\s*\(\s*page\s*==\s*HamburgerMenuPage\.Support\s*&&\s*supportFeedbackSending\s*\)(?s:.*?)showNotice\("正在发送，请稍后"\)' `
        "Support feedback must block back/close while a send is in progress so the page coroutine and selected images are not torn down mid-send."
    Require-Match $failures $pendingChatSendStore 'pending\.sessionGeneration\s*\?:\s*return\s+false' `
        "Pending sends without a stored session generation must not be treated as stale after app restart; unknown generation should be retried/reconciled."
    Require-Match $failures $hamburgerMenuSheet 'DisposableEffect\s*\(\s*lifecycleOwner,\s*pendingInstallPermissionUpdate,\s*pendingInstallAttemptUpdate,\s*updateDownloading\s*\)(?s:.*?)ON_RESUME(?s:.*?)val\s+pendingUpdate\s*=\s*pendingInstallPermissionUpdate(?s:.*?)startAppUpdate\s*\(\s*pendingUpdate\s*\)' `
        "App update permission resume handling must keep working for automatic update prompts even when settings is not visible."
    Require-NoMatch $failures $hamburgerMenuSheet 'if\s*\(\s*!\s*visible\s*\|\|\s*updateDownloading\s*\)\s*return@LifecycleEventObserver' `
        "App update permission resume handling must not depend on settings sheet visibility."
    Require-Match $failures $hamburgerMenuSheet 'pendingInstallAttemptUpdate(?s:.*?)app_update\.install_completed(?s:.*?)app_update\.install_not_completed' `
        "App update install attempts must log whether the system install page actually changed the installed version."
    Require-Match $failures $hamburgerMenuSheet 'buildSupportLinkedText(?s:.*?)LinkInteractionListener(?s:.*?)ui\.link_open_failed(?s:.*?)SelectionContainer' `
        "Support feedback links must remain tappable, selectable, and report only safe link-open failures."
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
