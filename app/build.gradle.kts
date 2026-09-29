import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "org.kashmir.heatguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.kashmir.heatguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL",      "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProps["RELEASE_KEYSTORE_FILE"] as String?
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProps["RELEASE_KEYSTORE_PASSWORD"] as String?
                keyAlias = localProps["RELEASE_KEY_ALIAS"] as String?
                keyPassword = localProps["RELEASE_KEY_PASSWORD"] as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
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

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Wear OS Data Layer (receives watch readings from Galaxy Watch6)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Unit tests (JVM)
    testImplementation(kotlin("test"))

    // Android instrumented tests
    androidTestImplementation("androidx.test.core:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(kotlin("test"))
}
