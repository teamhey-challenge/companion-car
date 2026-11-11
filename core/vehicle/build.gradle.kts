plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ai.hey.vehicle"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    // android.car.jar is part of the AAOS system image; compile-only and used here exclusively.
    compileOnly(files("/home/darren/android/sdk/platforms/android-36/optional/android.car.jar"))
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
}
