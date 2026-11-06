import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("uz.met.bench.MainKt")
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit)
}

// A stand-in for the Claude API, for testing the app on an emulator without spending anything.
tasks.register<JavaExec>("mockClaude") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("uz.met.bench.MockClaudeServer")
}
