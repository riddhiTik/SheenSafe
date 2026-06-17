plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.kashmir.heatguard.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.kashmir.heatguard.wear"
        minSdk = 30   // Wear OS 3; Galaxy Watch4+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint { abortOnError = false }
}

dependencies {
    // Samsung Health Sensor SDK — drop the .aar into wear/libs/ before building
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Wearable Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines + Task<T>.await() extension
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Wear OS UI
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
