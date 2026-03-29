plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aura.attendix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aura.attendix"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"

        buildConfigField("String", "BASE_URL", "\"https://demo.harshalabs.online\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // 16 KB page-size compliance — extracts .so at install time aligned to device page size
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Networking
    implementation("com.android.volley:volley:1.2.1")
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // CameraX — 1.4.x with 16KB-aligned .so files
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // ML Kit 17.3.0 — ships 16KB-aligned libbarhopper_v3.so
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ZXing QR Generator (Student Virtual ID)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Shimmer loading skeleton (UX improvement)
    implementation("com.facebook.shimmer:shimmer:0.5.0")
}