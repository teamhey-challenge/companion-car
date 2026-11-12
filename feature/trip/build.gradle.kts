plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "ai.hey.feature.trip"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

// The QR prompt in every passenger language comes from the shared catalog (hey-shared packages/copy `car.scanQr`), never typed
// here: this task copies the seven strings into a generated resource folder so R.string.trip_scan_qr_<lang> exists.
abstract class GenerateScanQr : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val copyDir: DirectoryProperty
    @get:OutputDirectory abstract val out: DirectoryProperty

    @TaskAction fun run() {
        val langs = mapOf("en" to "en", "ja" to "ja", "zh_hans" to "zh-Hans", "zh_hant" to "zh-Hant", "vi" to "vi", "th" to "th")
        val rows = langs.map { (suffix, file) ->
            val text = copyDir.file("$file.ts").get().asFile.readText()
            val m = Regex("""'car\.scanQr':\s*'((?:[^'\\]|\\.)*)'""").find(text) ?: error("car.scanQr missing in shared copy $file.ts")
            val value = m.groupValues[1].replace("\\'", "'").replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
            "    <string name=\"trip_scan_qr_$suffix\">$value</string>"
        }
        val dir = out.get().dir("values").asFile.apply { mkdirs() }
        File(dir, "scan_qr.xml").writeText("<resources>\n${rows.joinToString("\n")}\n</resources>\n")
    }
}
val generateScanQr = tasks.register<GenerateScanQr>("generateScanQrStrings") {
    copyDir.set(rootProject.layout.projectDirectory.dir("shared/packages/copy/src"))
}
androidComponents {
    onVariants { it.sources.res?.addGeneratedSourceDirectory(generateScanQr, GenerateScanQr::out) }
}

// Samples and previews live in the debug source set; Paparazzi renders the debug variant only.
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false } }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Structure, not convention: no material3 / foundation here, so raw Text/Button/Column cannot be imported.
dependencies {
    implementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    debugImplementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.compose.ui.graphics)
}
