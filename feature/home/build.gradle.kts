plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.hey.feature.home"
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

// Structure, not convention: no material3 / foundation here, so raw Text/Button/Column cannot be imported.
// The one exception is the Rive view (a classic Android View), which is the character renderer and lives in RiveCharacter.kt.
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:vehicle"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.rive.android)
    testImplementation(libs.junit)
}
