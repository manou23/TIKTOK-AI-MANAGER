plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mesut.tiktokmanager"
    compileSdk = 35

    buildFeatures { buildConfig = true }

    val tiktokClientKey = providers.environmentVariable("TIKTOK_CLIENT_KEY").orElse("").get()
    val tiktokRedirectUri = providers.environmentVariable("TIKTOK_REDIRECT_URI").orElse("https://open.tiktokapis.com/callback").get()
    val tiktokWorkerUrl = providers.environmentVariable("TIKTOK_WORKER_URL").orElse("").get()

    defaultConfig {
        applicationId = "com.mesut.tiktokmanager"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "1.9.0"
        buildConfigField("String", "TIKTOK_CLIENT_KEY", "\"$tiktokClientKey\"")
        buildConfigField("String", "TIKTOK_REDIRECT_URI", "\"$tiktokRedirectUri\"")
        buildConfigField("String", "TIKTOK_WORKER_URL", "\"$tiktokWorkerUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.media3:media3-common:1.9.0")
    implementation("androidx.media3:media3-effect:1.9.0")
    implementation("androidx.media3:media3-transformer:1.9.0")
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-core:2.2.0")
    implementation("com.tiktok.open.sdk:tiktok-open-sdk-auth:2.2.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
}
