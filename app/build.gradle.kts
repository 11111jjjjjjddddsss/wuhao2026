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
        
        // 优先从环境变量 BAILIAN_API_KEY 读取，否则从 gradle.properties 读取 API_KEY
        val apiKey = System.getenv("BAILIAN_API_KEY") 
            ?: (project.findProperty("API_KEY") as String? ?: "")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        // 图片上传：后端地址（空则不上传）。推荐 ECS 常驻服务 POST /upload -> 写入 OSS -> 返回 https URL；禁止在 APP 内写 OSS AK/SK
        val uploadBaseUrl = project.findProperty("UPLOAD_BASE_URL") as String? ?: ""
        buildConfigField("String", "UPLOAD_BASE_URL", "\"$uploadBaseUrl\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}
