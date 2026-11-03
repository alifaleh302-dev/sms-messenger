package com.sms.messages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every user-visible string must exist in Arabic too, otherwise the Arabic UI silently
 * falls back to English for that label.
 */
class LocalizationTest {

    private val names = Regex("""<string name="([a-zA-Z0-9_]+)"""")

    private fun resourceFile(qualifier: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/res/$qualifier/strings.xml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("strings.xml not found for '$qualifier'")
    }

    private fun stringNames(qualifier: String): Set<String> =
        names.findAll(resourceFile(qualifier).readText()).map { it.groupValues[1] }.toSet()

    @Test
    fun `arabic resources cover every english string`() {
        val english = stringNames("values")
        val arabic = stringNames("values-ar")
        assertTrue("no strings parsed from values/", english.isNotEmpty())
        val missing = english - arabic
        assertTrue("missing Arabic translations: $missing", missing.isEmpty())
    }

    @Test
    fun `arabic resources do not define strings that english lacks`() {
        val extra = stringNames("values-ar") - stringNames("values")
        assertTrue("Arabic-only strings: $extra", extra.isEmpty())
    }

    @Test
    fun `placeholders match between english and arabic`() {
        val english = resourceFile("values").readText()
        val arabic = resourceFile("values-ar").readText()
        val placeholders = Regex("""<string name="([a-zA-Z0-9_]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val englishArgs = placeholders.findAll(english).associate {
            it.groupValues[1] to Regex("""%[0-9]+\$[sd]""").findAll(it.groupValues[2]).map { m -> m.value }.toSet()
        }
        placeholders.findAll(arabic).forEach { match ->
            val name = match.groupValues[1]
            val expected = englishArgs[name] ?: return@forEach
            val actual = Regex("""%[0-9]+\$[sd]""").findAll(match.groupValues[2]).map { m -> m.value }.toSet()
            assertTrue("placeholder mismatch in '$name': expected $expected, found $actual", expected == actual)
        }
    }
}
