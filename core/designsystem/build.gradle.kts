plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "ai.hey.designsystem"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    testImplementation(bom)
    // Only the design system's own composables and the Compose types in their signatures are api.
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    // foundation is an implementation detail; features never see it.
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
