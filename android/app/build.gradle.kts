plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.abtin.tglass"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.abtin.tglass"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"

        // Optional: bake Telegram API credentials into the build (otherwise they are entered in the app).
        val apiId = (project.findProperty("TG_API_ID") as String?) ?: System.getenv("TG_API_ID")
        val apiHash = (project.findProperty("TG_API_HASH") as String?) ?: System.getenv("TG_API_HASH")
        buildConfigField("int", "TG_API_ID", apiId?.toIntOrNull()?.toString() ?: "0")
        buildConfigField("String", "TG_API_HASH", "\"${apiHash.orEmpty()}\"")

        ndk {
            // TDLib natives: phones (arm64/armv7) and the x86_64 emulator used for screenshots.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK is installable for personal use.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Compress TDLib's large .so files inside the APK (smaller download).
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += arrayOf("META-INF/*.version", "META-INF/**/LICENSE.txt", "kotlin-tooling-metadata.json")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.lottie.compose)
    implementation(libs.tdl.coroutines)
}
