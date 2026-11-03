import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val sherpaVersion = "2.0.1"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar")

/**
 * sherpa-onnx не публикуется в Maven Central. Скачиваем официальный AAR
 * из GitHub Releases один раз и кладём в app/libs (файл исключён из Git).
 */
val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    description = "Downloads the official sherpa-onnx Android AAR if it is missing."
    group = "build setup"
    outputs.file(sherpaAar)

    doLast {
        val target = sherpaAar.asFile
        if (target.isFile && target.length() > 1_000_000L) return@doLast

        target.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
            "v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        logger.lifecycle("Downloading $url")
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            URI(url).toURL().openStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(temporary.length() > 1_000_000L) {
                "Downloaded sherpa-onnx AAR looks incomplete: ${temporary.length()} bytes"
            }
            check(temporary.renameTo(target)) {
                "Could not move ${temporary.name} to ${target.name}"
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a — реальные устройства, x86_64 — эмулятор Pixel 7.
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

    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
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
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Нужен для распаковки официального .tar.bz2 архива модели GigaAM.
    implementation("org.apache.commons:commons-compress:1.26.2")

    // Needed only for the XML theme Theme.Material3.DayNight.NoActionBar.
    implementation("com.google.android.material:material:1.12.0")

    // Официальный AAR sherpa-onnx (arm64-v8a + x86_64).
    implementation(files(sherpaAar))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// AAR нужен до компиляции. Регистрируем зависимость задачи, не выполняя сеть
// на этапе конфигурации.
tasks.named("preBuild").configure {
    dependsOn(downloadSherpaOnnx)
}
