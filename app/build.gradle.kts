import java.util.Properties

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
        // The one place for the version: hey.versionCode / hey.versionName in gradle.properties.
        versionCode = providers.gradleProperty("hey.versionCode").get().toInt()
        versionName = providers.gradleProperty("hey.versionName").get()
    }
    // Local signing only: key material stays in a 600 properties file outside the repo (no CI, nothing committed).
    val signingFile = File(System.getProperty("user.home"), ".config/hey/signing.properties")
    val releaseSigning = if (signingFile.isFile) {
        val p = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else null
    // The registration secret is NOT built into the APK (the debug APK is public at /download/latest.apk): the device reads it
    // from a 600 file at run time, see readProvisionSecret in HeyApp.kt.
    // Companion server: https only (main network_security.xml). Override with -PserverUrl=https://...
    val serverUrl = providers.gradleProperty("serverUrl").getOrElse("https://hey.lastmile.ltd/api")
    require(serverUrl.startsWith("https://")) { "serverUrl must be https" }
    buildTypes {
        debug {
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        // R8 proof for the field release: minified and shrunk exactly like release but debug-signed, with the injectable demo
        // recognizer (src/e2e). Never shipped. Build: ./gradlew :app:assembleReleaseE2e
        create("releaseE2e") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // The injectable demo recognizer (src/e2e) is compiled into debug and releaseE2e only; release gets the no-op twin in src/release.
    sourceSets {
        getByName("debug") { java.srcDir("src/e2e/kotlin"); assets.srcDir("src/e2e/assets") }
        getByName("releaseE2e") { java.srcDir("src/e2e/kotlin"); assets.srcDir("src/e2e/assets") }
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
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}

// `:app:assembleRelease` also copies the signed APK to dist/apk/<teamName>_HEY_release.apk (git-ignored).
// The team name is not known yet: -PteamName=... replaces the TEAM placeholder.
val teamName = providers.gradleProperty("teamName").getOrElse("TEAM")
val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("dist/apk"))
    rename { "${teamName}_HEY_release.apk" }
    doFirst { check(File(System.getProperty("user.home"), ".config/hey/signing.properties").isFile) { "no ~/.config/hey/signing.properties: the release APK is unsigned" } }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }
