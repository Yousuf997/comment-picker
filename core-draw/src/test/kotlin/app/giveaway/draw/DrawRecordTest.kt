package app.giveaway.draw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DrawRecordTest {

    private val record = DrawRecord(
        algorithmVersion = ALGORITHM_V1,
        account = "shop",
        postId = "1789",
        title = "Win a tote bag!",
        entriesClosedAt = Instant.parse("2026-10-08T10:00:00Z"),
        commitHash = "ab".repeat(32),
        seedHex = "01".repeat(32),
        entryListHash = "cd".repeat(32),
        entryCount = 3,
        winnersRequested = 1,
        alternatesRequested = 1,
        picks = listOf(Pick(1, "zoe", Role.WINNER), Pick(2, "amy", Role.ALTERNATE)),
        drawnAt = Instant.parse("2026-10-08T11:30:00.123456Z"),
        captionCheck = "FOUND",
        integrityVerified = false,
        partialImport = false,
        manualExclusions = listOf(DrawRecord.ManualExclusion("bob", "Fake account")),
    )

    @Test
    fun theCanonicalFormIsSortedCompactJson() {
        val expected = "{" +
            "\"account\":\"shop\"," +
            "\"algorithmVersion\":\"v1\"," +
            "\"alternatesRequested\":1," +
            "\"captionCheck\":\"FOUND\"," +
            "\"commitHash\":\"${"ab".repeat(32)}\"," +
            "\"drawnAt\":\"2026-10-08T11:30:00.123Z\"," +
            "\"entriesClosedAt\":\"2026-10-08T10:00:00.000Z\"," +
            "\"entryCount\":3," +
            "\"entryListHash\":\"${"cd".repeat(32)}\"," +
            "\"integrityVerified\":false," +
            "\"manualExclusions\":[{\"reason\":\"Fake account\",\"username\":\"bob\"}]," +
            "\"partialImport\":false," +
            "\"picks\":[{\"position\":1,\"role\":\"WINNER\",\"username\":\"zoe\"}," +
            "{\"position\":2,\"role\":\"ALTERNATE\",\"username\":\"amy\"}]," +
            "\"postId\":\"1789\"," +
            "\"seedHex\":\"${"01".repeat(32)}\"," +
            "\"title\":\"Win a tote bag!\"," +
            "\"winnersRequested\":1" +
            "}"
        assertEquals(expected, record.canonicalJson())
        assertArrayEquals(expected.toByteArray(Charsets.UTF_8), record.canonicalBytes())
    }

    @Test
    fun stringsAreEscapedAndNonAsciiKeptAsUtf8() {
        val tricky = record.copy(
            title = "Say \"hi\"\\ \n\r\t\u0001 سحب",
            manualExclusions = emptyList(),
            picks = emptyList(),
        )
        val json = tricky.canonicalJson()
        assertEquals(true, json.contains("\"title\":\"Say \\\"hi\\\"\\\\ \\n\\r\\t\\u0001 سحب\""))
        assertEquals(true, json.contains("\"manualExclusions\":[]"))
        assertEquals(true, json.contains("\"picks\":[]"))
        assertEquals("سحب".toByteArray(Charsets.UTF_8).size, 6)
    }

    @Test
    fun theSameRecordAlwaysGivesTheSameBytes() {
        assertArrayEquals(record.canonicalBytes(), record.copy().canonicalBytes())
    }
}
