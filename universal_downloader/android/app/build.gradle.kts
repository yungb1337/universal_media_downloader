plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.universaldownloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.universaldownloader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Python 3.14 ships 64-bit wheels only — drop armeabi-v7a and x86
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        // Must match the Python version on this machine (3.14.x)
        version = "3.14"

        // Explicitly point to the local Python 3.14 executable so Chaquopy
        // doesn't fail auto-detection on Windows PATH configurations
        buildPython("C:/Users/Asus/AppData/Local/Python/bin/python.exe")

        // Disable pre-compilation to .pyc — avoids cross-version bytecode issues
        pyc {
            src = false
            pip = false
            stdlib = false
        }

        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    // ── Compose ──
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Activity & Lifecycle ──
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // ── Navigation ──
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── DataStore (replaces desktop .env config) ──
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ── Coroutines ──
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ── Core ──
    implementation("androidx.core:core-ktx:1.12.0")

    // ── Document File (SAF support for user-chosen download dirs) ──
    implementation("androidx.documentfile:documentfile:1.0.1")
}
