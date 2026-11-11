plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.hey.car"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.hey.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // Non-secret default relay; the emulator reaches the host at 10.0.2.2. Override: -PrelayUrl=https://...
        val relay = providers.gradleProperty("relayUrl").getOrElse("http://10.0.2.2:8787")
        buildConfigField("String", "RELAY_URL", "\"$relay\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:vehicle"))
    implementation(project(":feature:trip"))
    implementation(project(":feature:settings"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
}
