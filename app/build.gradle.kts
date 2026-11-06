import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// An optional keystore.properties, not in git, signs release builds. Without one, anyone can
// still build the app: it is signed with their debug key instead.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "uz.met.teacher"
    compileSdk = 37

    defaultConfig {
        applicationId = "uz.met.teacher"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        // Phones only, unless a test on an x86_64 emulator asks otherwise with -PmetAbi.
        ndk { abiFilters += (providers.gradleProperty("metAbi").orNull ?: "arm64-v8a") }
        buildConfigField("boolean", "TEST_HOOKS", "false")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TEST_HOOKS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreProps != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        // Shrunk exactly like release, but with the test hooks, to find out what shrinking breaks.
        create("qa") {
            initWith(getByName("release"))
            buildConfigField("boolean", "TEST_HOOKS", "true")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Compressed native libraries make the APK much smaller to send and download.
        jniLibs {
            useLegacyPackaging = true
            // The app uses sherpa-onnx through its JNI library, which doesn't need these two.
            excludes += setOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.md",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("${libs.sherpa.onnx.get()}@aar")
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
