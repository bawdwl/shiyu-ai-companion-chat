plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val stableDebugKeystore = rootProject.file("../outputs/android-toolchain/shiyu-debug.keystore")

android {
    namespace = "com.shiyu.chat"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.shiyu.chat"
        minSdk = 26
        targetSdk = 35
        versionCode = 57
        versionName = "0.19.2"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            resValue("bool", "feature_full", "true")
            resValue("string", "app_name", "拾语")
        }
        create("cloud") {
            dimension = "edition"
            applicationIdSuffix = ".cloud"
            versionNameSuffix = "-cloud"
            resValue("bool", "feature_full", "false")
            resValue("string", "app_name", "拾语云端")
        }
    }

    signingConfigs {
        if (stableDebugKeystore.isFile) {
            create("stableDebug") {
                storeFile = stableDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            resValue("bool", "show_dev_providers", "true")
            if (stableDebugKeystore.isFile) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        getByName("release") {
            resValue("bool", "show_dev_providers", "true")
            // MNN and sherpa-onnx expose JNI classes/methods by name. Keep the
            // release bytecode stable so local ASR, TTS and MNN all use the
            // exact symbols expected by their native libraries.
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            // The Dream backend is an executable packaged as a .so and must be
            // extracted to nativeLibraryDir so Android can execute it.
            useLegacyPackaging = true
        }
    }

    lint {
        // MNN, sherpa-onnx and Local Dream only ship arm64 native libraries.
        disable += "ChromeOsAbiSupport"
        // The supplied brand mark is intentionally a full-bleed rounded-square icon.
        disable += "IconLauncherShape"
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    add("fullImplementation", files("libs/sherpa-onnx-1.13.8.aar"))
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.collection:collection:1.4.4")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}

