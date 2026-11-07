plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Version follows the number of commits: every update pushed to main bumps it (1.1, 1.2, …)
val commitCount: Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (e: Exception) {
    1
}

android {
    namespace = "com.titipong.dinofocus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.titipong.dinofocus"
        minSdk = 26
        targetSdk = 34
        versionCode = commitCount
        versionName = "1.$commitCount"
    }

    // One fixed signing key so a newer APK installs over the old one and keeps the data
    signingConfigs {
        getByName("debug") {
            storeFile = file("dinofocus.keystore")
            storePassword = "dinofocus"
            keyAlias = "dinofocus"
            keyPassword = "dinofocus"
        }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
}
