plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.pronunciation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pronunciation"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        // Keep the model and lexicon uncompressed so they can be read/mmap'd cheaply.
        noCompress += listOf("onnx", "txt", "json")
    }

    // ONNX Runtime ships a ~17 MB native library per ABI. Bundling all four adds ~40 MB to an
    // APK that is already large because of the model, which matters when sideloading by hand.
    // Every current phone is arm64-v8a; x86_64 is here only so an emulator can run the app.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.material)

    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
}
