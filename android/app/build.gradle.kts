plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.baseapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.baseapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compatible with Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Optional sherpa-onnx sources. They are compiled only when the AAR exists,
    // so the app can be built and tested without any native binaries.
    sourceSets {
        getByName("main") {
            if (project.file("libs/sherpa-onnx.aar").exists()) {
                java.srcDir("src/main/java-sherpa")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Needed only for the XML theme Theme.Material3.DayNight.NoActionBar.
    implementation("com.google.android.material:material:1.12.0")

    // sherpa-onnx is optional and is not published to Maven Central.
    // Download the Android AAR from https://github.com/k2-fsa/sherpa-onnx/releases,
    // rename it to sherpa-onnx.aar and put it into app/libs/ (see libs/README.md).
    // When the file is absent, the app still builds and runs with DemoTranscriber.
    val sherpaAar = project.file("libs/sherpa-onnx.aar")
    if (sherpaAar.exists()) {
        implementation(files(sherpaAar))
    }
}
