plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.paparazzi) apply false
}

// Why a scan and not structure: Kotlin has no way to make a numeric `.dp` literal or a raw `Text(`
// call uncompilable once ui-unit / ui are on the classpath, so the leftovers that the dependency
// graph cannot block (feature modules have no material3/foundation dependency) are checked here.
// Starts at zero violations; there is no baseline and no per-line exception.
val checkDesignSystemUsage = tasks.register("checkDesignSystemUsage") {
    group = "verification"
    description = "Fails when :feature:* or :app sources bypass the design system."
    val projectRoot = layout.projectDirectory.asFile
    val sources = listOf(fileTree(layout.projectDirectory) { include("app/src/**/*.kt", "feature/*/src/**/*.kt") })
    inputs.files(sources)
    doLast {
        val rules = listOf(
            "Color(0x literal" to Regex("""\bColor\(\s*0x"""),
            "numeric .dp/.sp literal" to Regex("""(?<![A-Za-z0-9_])[0-9][0-9_]*(\.[0-9]+)?f?\.(dp|sp)\b"""),
            "material3 import" to Regex("""^\s*import\s+androidx\.compose\.material3"""),
            "foundation import" to Regex("""^\s*import\s+androidx\.compose\.foundation"""),
            "Text( / Button( not prefixed by Hey" to Regex("""(?<![A-Za-z0-9_.])(Text|Button)\("""),
        )
        val violations = mutableListOf<String>()
        sources.forEach { tree ->
            tree.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val code = line.trimStart()
                    if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                    rules.forEach { (name, regex) ->
                        if (regex.containsMatchIn(line)) {
                            violations += "${file.relativeTo(projectRoot)}:${index + 1}: $name: ${line.trim()}"
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Design system bypass (${violations.size}):\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") { dependsOn(checkDesignSystemUsage) }
