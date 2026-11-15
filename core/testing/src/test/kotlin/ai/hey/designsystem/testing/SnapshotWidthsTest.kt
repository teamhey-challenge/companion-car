package ai.hey.designsystem.testing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why a test and not structure: a snapshot class is a plain class name in a test source file, nothing in the type system ties
 * `FooNNNNSnapshotTest` classes together. The rule: a feature module that records a `<Name>1600SnapshotTest` also records
 * `<Name>840SnapshotTest` and `<Name>560SnapshotTest` (the Pleos half and third windows), and no feature test builds its own
 * `Paparazzi(` (that would skip the car's design density). No allowlist.
 */
class SnapshotWidthsTest {
    private val featureDir = File("../../feature").canonicalFile
    private val classRegex = Regex("""\bclass\s+([A-Za-z0-9]+?)(\d+)SnapshotTest\b""")

    private fun testSources(): List<File> =
        featureDir.listFiles { f -> f.isDirectory }.orEmpty().flatMap { module ->
            File(module, "src/test").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    @Test fun featureModulesAreFound() {
        assertTrue("feature dir not found: $featureDir", featureDir.isDirectory)
        assertTrue("no feature test sources under $featureDir", testSources().isNotEmpty())
    }

    @Test fun everyFullWidthSnapshotHasCarHalfAndThird() {
        val missing = mutableListOf<String>()
        featureDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { module ->
            val widths = File(module, "src/test").walkTopDown().filter { it.isFile && it.extension == "kt" }
                .flatMap { classRegex.findAll(it.readText()).map { m -> m.groupValues[1] to m.groupValues[2] } }
                .groupBy({ it.first }, { it.second })
            widths.forEach { (name, sizes) ->
                if ("1600" in sizes) {
                    listOf("1251", "840", "560").filter { it !in sizes }.forEach { missing += "${module.name}: ${name}${it}SnapshotTest" }
                }
            }
        }
        assertEquals("1600 snapshot classes without 1251/840/560 counterparts:\n" + missing.joinToString("\n"), emptyList<String>(), missing)
    }

    @Test fun featureSnapshotsUseCarPaparazzi() {
        val offenders = testSources().filter { Regex("""(?<![A-Za-z])Paparazzi\(""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(featureDir).path }
        assertEquals("construct CarPaparazzi, not Paparazzi, in:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }
}
