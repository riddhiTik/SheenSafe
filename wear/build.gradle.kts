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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Dedicated debug keystore, distinct from :app's. Google Play Services'
    // Wearable Data Layer resolves capability/AppKey ownership partly by signing
    // certificate; sharing the default debug keystore with :app caused message
    // routing to stay locked onto :app even after :app was uninstalled from the watch.
    signingConfigs {
        getByName("debug") {
            storeFile = file("wear-debug.keystore")
            storePassword = "android"
            keyAlias = "weardebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
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

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("com.google.android.gms:play-services-wearable:18.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
