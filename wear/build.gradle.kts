plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.kashmir.heatguard.wear"
    compileSdk = 34

    defaultConfig {
        // Must match the phone app's applicationId exactly (see app/build.gradle.kts). The
        // Wearable Data Layer routes messages by package name + signing certificate, so if
        // these drift apart the watch and phone silently stop talking to each other.
        applicationId = "com.sheensafe.riddhi"
        minSdk = 30   // Wear OS 3; Galaxy Watch4+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Health Services: heart rate on any Wear OS watch (fallback when the Samsung SDK is unavailable)
    implementation("androidx.health:health-services-client:1.0.0")
    implementation("com.google.guava:guava:33.0.0-android")

    // Wearable Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines + Task<T>.await() extension
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Wear OS UI
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("com.google.android.gms:play-services-wearable:18.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
