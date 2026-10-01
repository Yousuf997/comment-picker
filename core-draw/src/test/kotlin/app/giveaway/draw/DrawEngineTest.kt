package app.giveaway.draw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class DrawEngineTest {

    private val seed = ByteArray(32) { it.toByte() }
    private val names = listOf("ana", "bilal", "chen", "dana", "eve", "farah", "gus", "hana", "ivan", "jo")

    // --- Commit ---

    @Test
    fun commitHashIsSha256OfTheSeedInLowercaseHex() {
        val hash = Commit.commitHash(seed)
        assertEquals(64, hash.length)
        assertEquals(sha256(seed).toHex(), hash)
        assertEquals("#draw $hash", Commit.drawCode(hash))
        assertThrows(IllegalArgumentException::class.java) { Commit.commitHash(ByteArray(16)) }
    }

    @Test
    fun newSeedsAreRandom32Bytes() {
        val a = Commit.newSeed()
        val b = Commit.newSeed(SecureRandom())
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun captionCheckFindsTheCodeIgnoringCase() {
        val hash = Commit.commitHash(seed)
        assertTrue(Commit.captionContains("Win a bag! Rules below.\n#draw ${hash.uppercase()}", hash))
        assertFalse(Commit.captionContains("Win a bag! #draw 1234", hash))
        assertFalse(Commit.captionContains(null, hash))
    }

    @Test
    fun onlyTheCommittedSeedMatches() {
        val hash = Commit.commitHash(seed)
        assertTrue(Commit.matches(seed, hash.uppercase()))
        assertFalse(Commit.matches(ByteArray(32), hash))
        assertFalse(Commit.matches(ByteArray(8), hash))
    }

    @Test
    fun hexRoundTripsAndRejectsGarbage() {
        assertArrayEquals(seed, seed.toHex().hexToBytes())
        assertThrows(IllegalArgumentException::class.java) { "abc".hexToBytes() }
        assertThrows(IllegalArgumentException::class.java) { "zz".hexToBytes() }
    }

    // --- Canonical list ---

    @Test
    fun canonicalListLowercasesAndSortsByCodePoint() {
        // "𝒜" (U+1D49C) is a surrogate pair: UTF-16 order would put it before "ｚ" (U+FF5A); code point order doesn't.
        val list = CanonicalEntryList.of(listOf("ｚeta", "Zed", "𝒜lpha", "ADAM"))
        assertEquals(listOf("adam", "zed", "ｚeta", "𝒜lpha"), list.usernames)
        assertEquals("adam\nzed\nｚeta\n𝒜lpha", list.text)
        assertEquals(sha256(list.text.toByteArray(Charsets.UTF_8)).toHex(), list.hashHex)
        assertEquals(4, list.size)
    }

    @Test
    fun canonicalListKeepsDuplicatesButCountsPeopleOnce() {
        val list = CanonicalEntryList.of(listOf("Ana", "ana", "bob"))
        assertEquals(listOf("ana", "ana", "bob"), list.usernames)
        assertEquals(2, list.distinctCount)
    }

    @Test
    fun canonicalListRoundTripsThroughItsText() {
        val list = CanonicalEntryList.of(names)
        assertEquals(list.usernames, CanonicalEntryList.fromText(list.text).usernames)
        val empty = CanonicalEntryList.fromText("")
        assertEquals(0, empty.size)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty.hashHex)
    }

    @Test
    fun codePointOrderHandlesPrefixes() {
        assertTrue(CanonicalEntryList.CodePointOrder.compare("ana", "anab") < 0)
        assertTrue(CanonicalEntryList.CodePointOrder.compare("anab", "ana") > 0)
        assertEquals(0, CanonicalEntryList.CodePointOrder.compare("ana", "ana"))
    }

    // --- Random stream ---

    @Test
    fun rejectionSamplingDiscardsTheBiasedTail() {
        // For bound 3 the largest multiple of 3 below 2^32 is 2^32 - 1, so 0xFFFFFFFF must be rejected.
        val values = ArrayDeque(listOf(0xFFFFFFFFL, 5L))
        val stream = DrawStream { _ ->
            val v = values.removeFirst()
            byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        }
        assertEquals(2, stream.uniform(3))
        assertTrue("Both values were read", values.isEmpty())
    }

    @Test
    fun streamReadsAcrossBlockBoundaries() {
        // Blocks of 6 bytes: the second integer spans two blocks.
        val stream = DrawStream { k -> ByteArray(6) { (k * 6 + it).toByte() } }
        assertEquals(0x00010203L, stream.nextUInt32())
        assertEquals(0x04050607L, stream.nextUInt32())
        assertEquals(0x08090A0BL, stream.nextUInt32())
    }

    @Test
    fun uniformRejectsNonPositiveBounds() {
        assertThrows(IllegalArgumentException::class.java) { DrawStream(seed, ByteArray(32)).uniform(0) }
    }

    @Test
    fun uniformHasNoMeasurableBias() {
        val stream = DrawStream(seed, sha256("bias".toByteArray()))
        val counts = IntArray(3)
        val draws = 300_000
        repeat(draws) { counts[stream.uniform(3)]++ }
        val expected = draws / 3.0
        val chiSquare = counts.sumOf { (it - expected) * (it - expected) / expected }
        // df = 2, p = 0.001 critical value 13.82.
        assertTrue("chi-square $chiSquare for ${counts.toList()}", chiSquare < 13.82)
    }

    // --- Selection ---

    @Test
    fun identicalInputsGiveIdenticalResults() {
        val list = CanonicalEntryList.of(names)
        val again = DrawV1.select(seed.copyOf(), CanonicalEntryList.of(names.reversed()), 3, 2)
        assertEquals(DrawV1.select(seed, list, 3, 2), again)
    }

    @Test
    fun aDifferentSeedGivesADifferentResult() {
        val list = CanonicalEntryList.of(names)
        assertNotEquals(DrawV1.select(seed, list, 3, 2).picks, DrawV1.select(ByteArray(32) { 9 }, list, 3, 2).picks)
    }

    @Test
    fun winnersComeFirstThenAlternates() {
        val outcome = DrawV1.select(seed, CanonicalEntryList.of(names), 3, 2)
        assertEquals(listOf(1, 2, 3, 4, 5), outcome.picks.map { it.position })
        val roles = listOf(Role.WINNER, Role.WINNER, Role.WINNER, Role.ALTERNATE, Role.ALTERNATE)
        assertEquals(roles, outcome.picks.map { it.role })
        assertEquals(3, outcome.winners.size)
        assertEquals(2, outcome.alternates.size)
        assertEquals(5, outcome.picks.map { it.username }.toSet().size)
        assertEquals("v1", outcome.algorithmVersion)
        assertEquals(5, outcome.requested)
    }

    @Test
    fun askingForMoreThanThereArePicksEveryone() {
        val outcome = DrawV1.select(seed, CanonicalEntryList.of(listOf("a", "b", "c")), 5, 2)
        assertEquals(setOf("a", "b", "c"), outcome.picks.map { it.username }.toSet())
        assertTrue(outcome.picks.all { it.role == Role.WINNER })
        assertEquals(7, outcome.requested)
    }

    @Test
    fun singleAndEmptyLists() {
        val solo = DrawV1.select(seed, CanonicalEntryList.of(listOf("solo")), 1, 3).picks
        assertEquals(listOf(Pick(1, "solo", Role.WINNER)), solo)
        assertTrue(DrawV1.select(seed, CanonicalEntryList.of(emptyList()), 3, 2).picks.isEmpty())
        assertTrue(DrawV1.select(seed, CanonicalEntryList.of(names), 0, 0).picks.isEmpty())
    }

    @Test
    fun nobodyWinsTwiceWhenTheyHaveSeveralEntries() {
        val outcome = DrawV1.select(seed, CanonicalEntryList.of(listOf("ana", "ana", "ana", "bob", "cid")), 2, 2)
        assertEquals(listOf("ana", "bob", "cid").sorted(), outcome.picks.map { it.username }.sorted())
    }

    @Test
    fun invalidInputsAreRejected() {
        val list = CanonicalEntryList.of(names)
        assertThrows(IllegalArgumentException::class.java) { DrawV1.select(ByteArray(31), list, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { DrawV1.select(seed, list, -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { DrawV1.select(seed, list, 1, -1) }
    }

    @Test
    fun everyEntryIsEquallyLikelyToWinFirst() {
        val list = CanonicalEntryList.of(listOf("a", "b", "c", "d", "e"))
        val counts = mutableMapOf<String, Int>()
        val seeds = 5_000
        repeat(seeds) { n ->
            val s = sha256("seed-$n".toByteArray())
            val first = DrawV1.select(s, list, 1, 0).picks.single().username
            counts[first] = (counts[first] ?: 0) + 1
        }
        val expected = seeds / 5.0
        val chiSquare = counts.values.sumOf { (it - expected) * (it - expected) / expected }
        // df = 4, p = 0.001 critical value 18.47.
        assertTrue("chi-square $chiSquare for $counts", chiSquare < 18.47)
    }

    // --- Verification ---

    @Test
    fun anHonestDrawVerifies() {
        val list = CanonicalEntryList.of(names)
        val outcome = DrawV1.select(seed, list, 3, 2)
        assertEquals(VerificationResult.Ok, Verifier.verify(Commit.commitHash(seed), seed, list, 3, 2, outcome.picks))
    }

    @Test
    fun aDifferentSeedOrChangedWinnersFailVerification() {
        val list = CanonicalEntryList.of(names)
        val outcome = DrawV1.select(seed, list, 3, 2)
        val wrongSeed = Verifier.verify(Commit.commitHash(seed), ByteArray(32), list, 3, 2, outcome.picks)
        assertTrue(wrongSeed is VerificationResult.Mismatch)
        val swapped = outcome.picks.reversed().mapIndexed { i, p -> p.copy(position = i + 1) }
        val tampered = Verifier.verify(Commit.commitHash(seed), seed, list, 3, 2, swapped)
        assertTrue(tampered is VerificationResult.Mismatch)
        assertTrue((tampered as VerificationResult.Mismatch).reason.contains(outcome.picks.first().username))
    }
}
