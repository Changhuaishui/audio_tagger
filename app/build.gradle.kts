plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.tagger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tagger"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only include arm64-v8a to reduce APK size
        // Most modern phones are arm64, this reduces ~100MB
        ndk {
            abiFilters += listOf("arm64-v8a")
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

    // Generate separate APKs for each ABI (optional - uncomment if needed)
    // This creates app-arm64-v8a-debug.apk (~130MB) and app-armeabi-v7a-debug.apk (~110MB)
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a", "armeabi-v7a")
    //         isUniversalApk = false
    //     }
    // }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // JAudioTagger for audio metadata (MP3, FLAC, OGG, WAV)
    implementation("net.jthink:jaudiotagger:3.0.1")

    // mp4parser for M4A/MP4 metadata (iTunes-style tags)
    // JAudioTagger has issues writing M4A, so we use mp4parser instead
    implementation("org.mp4parser:isoparser:1.9.56")

    // FFmpegKit full-gpl - community fork with all encoders (AAC, libmp3lame, FLAC, libvorbis, etc.)
    // Package: com.antonkarpenko.ffmpegkit (note: different from arthenica's package name)
    implementation("com.antonkarpenko:ffmpeg-kit-full-gpl:1.1.0")

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}