plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ai.hey.model"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("test").resources.srcDir(layout.buildDirectory.dir("protocol-fixtures"))
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// The fixtures are owned by packages/protocol; they are copied, never duplicated by hand, so a change
// there is parsed by this module's test on the next run.
val copyProtocolFixtures = tasks.register<Copy>("copyProtocolFixtures") {
    from(rootDir.resolve("../../packages/protocol/fixtures"))
    into(layout.buildDirectory.dir("protocol-fixtures/protocol-fixtures"))
}
tasks.matching { it.name.endsWith("UnitTestJavaRes") }.configureEach { dependsOn(copyProtocolFixtures) }

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
