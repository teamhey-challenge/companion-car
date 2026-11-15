plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
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

// Snapshot samples are test-only; Paparazzi renders the debug variant only.
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false } }

// the design-diff dump tests run only when asked for (`-Pdesign-diff`), so record/verify never see them.
tasks.withType<Test>().configureEach { providers.gradleProperty("design-diff").orNull?.let { systemProperty("design-diff", it) } }

// Structure, not convention: no material3 / foundation here, so raw Text/Button/Column cannot be imported.
// The one exception is the Rive view (a classic Android View), the character renderer in RiveCharacter.kt.
dependencies {
    implementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:vehicle"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.rive.android)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
