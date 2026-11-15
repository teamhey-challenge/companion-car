plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "ai.hey.feature.onboarding"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Samples and previews live in the debug source set; Paparazzi renders the debug variant only.
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false } }

// PLEOS-199: the design-diff dump tests run only when asked for (`-Pdesign-diff`), so record/verify never see them.
tasks.withType<Test>().configureEach { providers.gradleProperty("design-diff").orNull?.let { systemProperty("design-diff", it) } }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Structure, not convention: no material3 / foundation here, so raw Text/Button/Column cannot be imported.
dependencies {
    implementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(libs.compose.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.compose.ui)
    debugImplementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
