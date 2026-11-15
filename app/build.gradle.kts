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
        // Monotonic and identical for CI and local builds of the same commit: the commit count (CI checks out with fetch-depth 0).
        // -Phey.versionCode=N overrides; without git history it falls back to 1. versionName comes from gradle.properties.
        versionCode = providers.gradleProperty("hey.versionCode").map { it.toInt() }.orElse(
            providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
                .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
        ).get()
        versionName = providers.gradleProperty("hey.versionName").get()
    }
    // Release signing. CI (release-apk job) and local builds can pass HEYPARRI_STORE_FILE / HEYPARRI_STORE_PASSWORD /
    // HEYPARRI_KEY_ALIAS (key password = store password); otherwise the 600 properties file outside the repo is used.
    // Neither present: the release APK stays unsigned (debug builds and the debug CI path never touch this).
    val signingFile = File(System.getProperty("user.home"), ".config/hey/signing.properties")
    val envStore = System.getenv("HEYPARRI_STORE_FILE")
    val releaseSigning = if (envStore != null) {
        signingConfigs.create("release") {
            storeFile = file(envStore)
            storePassword = System.getenv("HEYPARRI_STORE_PASSWORD")
            keyAlias = System.getenv("HEYPARRI_KEY_ALIAS")
            keyPassword = System.getenv("HEYPARRI_STORE_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true // key rotation / lineage (Pleos: API 28+)
        }
    } else if (signingFile.isFile) {
        val p = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
            enableV2Signing = true
            enableV3Signing = true
        }
    } else null
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
    // The injectable demo override (src/e2e) is compiled into debug and releaseE2e only; release gets the no-op twin in src/release.
    sourceSets {
        getByName("debug") { java.srcDir("src/e2e/kotlin") }
        getByName("releaseE2e") { java.srcDir("src/e2e/kotlin") }
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
    implementation(project(":feature:memory"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:home"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// `:app:assembleRelease` also copies the signed APK to dist/apk/<teamName>_HEY_release.apk (git-ignored).
// The team name is not known yet: -PteamName=... replaces the TEAM placeholder.
val teamName = providers.gradleProperty("teamName").getOrElse("TEAM")
val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("dist/apk"))
    rename { "${teamName}_HEY_release.apk" }
    doFirst { check(System.getenv("HEYPARRI_STORE_FILE") != null || File(System.getProperty("user.home"), ".config/hey/signing.properties").isFile) { "no HEYPARRI_STORE_FILE and no ~/.config/hey/signing.properties: the release APK is unsigned" } }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }
