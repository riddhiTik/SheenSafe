plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "org.kashmir.heatguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.kashmir.heatguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation(kotlin("test"))
}
