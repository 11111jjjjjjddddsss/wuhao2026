plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nongjiqianwen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nongjiqianwen"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        var apiKeyFromLocal = ""
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.forEachLine { line ->
                val t = line.trimStart()
                if (t.startsWith("API_KEY=")) {
                    apiKeyFromLocal = line.substringAfter("=").trim().trim('"')
                }
            }
        }

        val apiKey = (
            System.getenv("BAILIAN_API_KEY")?.takeIf { it.isNotBlank() }
                ?: apiKeyFromLocal.takeIf { it.isNotBlank() }
                ?: (project.findProperty("API_KEY") as String?)?.takeIf { it.isNotBlank() }
                ?: ""
            ).trim()
        if (apiKey.isBlank() || apiKey == "your_key_here") {
            throw GradleException("API_KEY 未配置：请在 local.properties 设置 API_KEY 或设置环境变量 BAILIAN_API_KEY。")
        }
        buildConfigField("String", "API_KEY", "\"$apiKey\"")

        val uploadBaseUrl = project.findProperty("UPLOAD_BASE_URL") as String? ?: ""
        buildConfigField("String", "UPLOAD_BASE_URL", "\"$uploadBaseUrl\"")

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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}
