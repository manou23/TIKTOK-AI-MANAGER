plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mesut.tiktokmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mesut.tiktokmanager"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.7.0"
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
}
