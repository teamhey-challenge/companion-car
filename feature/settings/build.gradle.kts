plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.hey.feature.settings"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Structure, not convention: no material3 / foundation here, so raw Text/Button/Column cannot be imported.
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
}
