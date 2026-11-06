import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Compile against the JDK 17 API: newer ones, such as List.removeLast, crash on older Android.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(libs.anthropic.java)
    api(libs.coroutines.core)
    api(libs.serialization.json)
    // The SDK pulls an older kotlin-reflect through Jackson; keep it in step with the stdlib.
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
}
