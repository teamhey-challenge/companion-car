plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.hey.testing"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Test-only module: depended on through testImplementation by the modules that record Paparazzi snapshots.
dependencies {
    implementation(platform(libs.compose.bom))
    api(project(":core:designsystem"))
    api(libs.paparazzi.lib)
    api(libs.junit)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
