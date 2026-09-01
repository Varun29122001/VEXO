plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vexo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.vexo"
        // 33 is the floor for RuntimeShader / AGSL, which renders the assistant orb.
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // The sherpa-onnx native libraries dominate the APK (~30 MB per ABI, most of it
    // libonnxruntime.so), so ship one APK per ABI rather than one fat APK carrying four copies.
    // Only 64-bit ABIs are listed: every device on minSdk 33 is 64-bit. A release should ship an
    // App Bundle, which performs this split automatically.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sherpa.onnx)
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
}
