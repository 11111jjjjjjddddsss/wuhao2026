import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

val releaseSigningProperties = Properties()
val releaseSigningPropertiesFile = File(
    System.getProperty("user.home"),
    ".nongjiqiancha/android-release-signing.properties",
)
if (releaseSigningPropertiesFile.isFile) {
    releaseSigningPropertiesFile.inputStream().use { releaseSigningProperties.load(it) }
}

fun releaseSigningValue(name: String): String? =
    (project.findProperty(name) as String?)
        ?: System.getenv(name)
        ?: releaseSigningProperties.getProperty(name)

fun optionalBuildValue(name: String): String =
    ((project.findProperty(name) as String?) ?: System.getenv(name) ?: "").trim()

val releaseStoreFile = releaseSigningValue("NONGJI_ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("NONGJI_ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("NONGJI_ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("NONGJI_ANDROID_RELEASE_KEY_PASSWORD")
val uploadBaseUrl = optionalBuildValue("UPLOAD_BASE_URL")
val sessionApiToken = optionalBuildValue("SESSION_API_TOKEN")
val releaseSigningConfigured =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.nongjiqianwen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nongjiqiancha"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "UPLOAD_BASE_URL", "\"$uploadBaseUrl\"")
        buildConfigField("String", "SESSION_API_TOKEN", "\"$sessionApiToken\"")

        val useBackendAb = (project.findProperty("USE_BACKEND_AB") as String?)?.toBooleanStrictOrNull() ?: true
        buildConfigField("boolean", "USE_BACKEND_AB", useBackendAb.toString())
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease" || name == "packageRelease") {
        doFirst {
            if (!releaseSigningConfigured) {
                throw org.gradle.api.GradleException(
                    "Release signing is not configured. Create ~/.nongjiqiancha/android-release-signing.properties or pass NONGJI_ANDROID_RELEASE_* properties.",
                )
            }
            if (!uploadBaseUrl.startsWith("https://")) {
                throw org.gradle.api.GradleException(
                    "Release UPLOAD_BASE_URL must be configured as an https URL, for example -PUPLOAD_BASE_URL=https://api.nongjiqiancha.cn.",
                )
            }
            if (sessionApiToken.isNotEmpty()) {
                throw org.gradle.api.GradleException(
                    "Release SESSION_API_TOKEN must be empty. Static shared tokens are only allowed for local/internal debug builds.",
                )
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("me.saket.telephoto:zoomable-image-coil:0.19.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    baselineProfile(project(":baselineprofile"))
}
