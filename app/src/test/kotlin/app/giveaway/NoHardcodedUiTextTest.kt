package app.giveaway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec: all strings in resources, no hard-coded text. Android lint's HardcodedText only covers XML layouts, so this
 * scans Compose code in the app and feature modules for string literals passed as UI text.
 */
class NoHardcodedUiTextTest {

    private val literalUiText = listOf(
        Regex("""\bText\(\s*"[^"]"""),
        Regex("""\btext\s*=\s*"[^"]"""),
        Regex("""\b(title|label|contentDescription)\s*=\s*"[^"]"""),
        Regex("""\bPlaceholderAction\(\s*"[^"]"""),
        Regex("""\b(Primary|Secondary)Button\(\s*"[^"]"""),
    )

    @Test
    fun uiTextComesFromStringResources() {
        val root = File("..").canonicalFile
        val sources = root.listFiles { file -> file.name == "app" || file.name.startsWith("feature-") }.orEmpty()
            .map { File(it, "src/main") }
            .filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue("No sources found under $root", sources.isNotEmpty())

        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                val hardcoded = literalUiText.any { it.containsMatchIn(code) }
                if (hardcoded) "${file.relativeTo(root)}:${index + 1}: $line" else null
            }
        }
        val message = "Hard-coded UI text (move it to strings.xml):\n" + offenders.joinToString("\n")
        assertTrue(message, offenders.isEmpty())
    }
}
