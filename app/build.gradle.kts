plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.nongjiqianwen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nongjiqianwen"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val uploadBaseUrl = project.findProperty("UPLOAD_BASE_URL") as String? ?: ""
        buildConfigField("String", "UPLOAD_BASE_URL", "\"$uploadBaseUrl\"")
        val sessionApiToken = (project.findProperty("SESSION_API_TOKEN") as String?) ?: ""
        buildConfigField("String", "SESSION_API_TOKEN", "\"$sessionApiToken\"")

        val useBackendAb = (project.findProperty("USE_BACKEND_AB") as String?)?.toBooleanStrictOrNull() ?: true
        val useBackendEntitlement =
            (project.findProperty("USE_BACKEND_ENTITLEMENT") as String?)?.toBooleanStrictOrNull() ?: true
        buildConfigField("boolean", "USE_BACKEND_AB", useBackendAb.toString())
        buildConfigField("boolean", "USE_BACKEND_ENTITLEMENT", useBackendEntitlement.toString())
    }

    buildTypes {
        release {
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
