import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
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
    // Registration secret (relay HEY_PROVISION_SECRET). Release reads ~/.config/hey/provision.properties (600, outside the repo,
    // created with a random value on first use, never printed); debug uses the dev default, which only a HEY_ENV=dev relay accepts.
    val provisionFile = File(System.getProperty("user.home"), ".config/hey/provision.properties")
    fun releaseProvisionSecret(): String {
        if (!provisionFile.isFile) {
            provisionFile.parentFile.mkdirs()
            Files.createFile(provisionFile.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            provisionFile.writeText("secret=" + bytes.joinToString("") { "%02x".format(it) } + "\n")
        }
        return Properties().apply { provisionFile.inputStream().use { load(it) } }.getProperty("secret").trim()
    }
    buildTypes {
        debug {
            // Non-secret default relay; the emulator reaches the host at 10.0.2.2. Override: -PrelayUrl=...
            val relay = providers.gradleProperty("relayUrl").getOrElse("http://10.0.2.2:8787")
            buildConfigField("String", "RELAY_URL", "\"$relay\"")
            buildConfigField("String", "PROVISION_SECRET", "\"dev\"")
        }
        release {
            // Cleartext is off in release (main network_security.xml), so the relay must be https. Pass the real
            // address with -PrelayUrl=https://...; the placeholder never resolves, and D8 can still change it.
            val relay = providers.gradleProperty("relayUrl").getOrElse("https://relay.hey.invalid")
            require(relay.startsWith("https://")) { "release relayUrl must be https" }
            buildConfigField("String", "RELAY_URL", "\"$relay\"")
            buildConfigField("String", "PROVISION_SECRET", "\"${releaseProvisionSecret()}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
        // R8 proof for the field release: minified and shrunk exactly like release, but debug-signed and with plain http to the
        // e2e relay on the emulator host (res in src/releaseE2e). Never shipped; `assembleRelease` is unaffected.
        // Build: ./gradlew :app:assembleReleaseE2e [-PrelayUrl=http://10.0.2.2:8787]
        create("releaseE2e") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            val relay = providers.gradleProperty("relayUrl").getOrElse("http://10.0.2.2:8787")
            buildConfigField("String", "RELAY_URL", "\"$relay\"")
            buildConfigField("String", "PROVISION_SECRET", "\"dev\"")
            signingConfig = signingConfigs.getByName("debug")
        }
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
