package app.giveaway.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** C-08 acceptance: every exclusion reason, their precedence, and 50,000 comments within the spec's 3 seconds. */
class RulesFilterTest {

    private val closes = Instant.parse("2026-10-10T20:00:00Z")
    private val before = closes.minusSeconds(60)
    private val rules = Rules(
        minMentions = 2,
        requiredHashtag = "#win",
        keyword = null,
        onePerPerson = true,
        excludePastWinners = true,
        excludeBlocklist = true,
        closesAt = closes,
        winnersCount = 3,
        alternatesCount = 2,
    )
    private val context =
        FilterContext(ownerUsername = "shop", blocklist = setOf("Spammer"), pastWinners = setOf("lucky"))
    private var nextId = 0

    private fun comment(user: String, text: String, at: Instant = before) = RawComment("c${nextId++}", user, text, at)

    private fun decide(vararg comments: RawComment, r: Rules = rules, c: FilterContext = context) =
        RulesFilterV1.evaluate(comments.asSequence(), r, c).toList()

    private fun reasonOf(text: String, user: String = "ana", r: Rules = rules, at: Instant = before) =
        decide(comment(user, text, at), r = r).single().reason

    @Test
    fun eachRuleProducesItsReason() {
        val valid = "@bob @cid #win"
        val cases = listOf(
            Triple("valid entry", reasonOf(valid), null),
            Triple("late", reasonOf(valid, at = closes.plusSeconds(1)), ExclusionReason.AFTER_DEADLINE),
            Triple("own account", reasonOf(valid, user = "Shop"), ExclusionReason.OWN_ACCOUNT),
            Triple("blocklisted", reasonOf(valid, user = "spammer"), ExclusionReason.BLOCKLISTED),
            Triple("past winner", reasonOf(valid, user = "LUCKY"), ExclusionReason.PAST_WINNER),
            Triple("one mention", reasonOf("@bob #win"), ExclusionReason.TOO_FEW_MENTIONS),
            Triple("no hashtag", reasonOf("@bob @cid"), ExclusionReason.MISSING_HASHTAG),
            Triple("no keyword", reasonOf(valid, r = rules.copy(keyword = "pick me")), ExclusionReason.MISSING_KEYWORD),
        )
        cases.forEach { (name, actual, expected) -> assertEquals(name, expected, actual) }
    }

    @Test
    fun aCommentAtTheExactClosingTimeStillCounts() {
        assertNull(reasonOf("@bob @cid #win", at = closes))
    }

    @Test
    fun theFirstFailingCheckIsTheReasonShown() {
        // Late, from a past winner, with no mentions or hashtag: lateness wins.
        assertEquals(ExclusionReason.AFTER_DEADLINE, reasonOf("hi", user = "lucky", at = closes.plusSeconds(5)))
        // Blocklisted past winner with no hashtag: blocklist comes before past winner and content checks.
        val both = context.copy(pastWinners = setOf("spammer"))
        assertEquals(ExclusionReason.BLOCKLISTED, decide(comment("spammer", "hi"), c = both).single().reason)
        // Too few mentions and no hashtag: mentions first.
        assertEquals(ExclusionReason.TOO_FEW_MENTIONS, reasonOf("nothing here"))
    }

    @Test
    fun listRulesOnlyApplyWhenSwitchedOn() {
        val off = rules.copy(excludeBlocklist = false, excludePastWinners = false)
        assertNull(reasonOf("@bob @cid #win", user = "spammer", r = off))
        assertNull(reasonOf("@bob @cid #win", user = "lucky", r = off))
    }

    @Test
    fun mentionsCountDistinctOtherPeopleOnly() {
        val r = rules.copy(requiredHashtag = null)
        assertEquals("same handle twice", ExclusionReason.TOO_FEW_MENTIONS, reasonOf("@bob @BOB", r = r))
        assertEquals("self and owner don't count", ExclusionReason.TOO_FEW_MENTIONS, reasonOf("@ana @shop @bob", r = r))
        assertEquals("e-mail isn't a mention", ExclusionReason.TOO_FEW_MENTIONS, reasonOf("me@mail.com @bob", r = r))
        assertNull("trailing period ignored", reasonOf("Thanks @bob and @cid.", r = r))
        assertNull("dots and underscores allowed", reasonOf("@bob.smith @c_i_d", r = r))
        assertNull("zero required", reasonOf("no mentions", r = r.copy(minMentions = 0)))
    }

    @Test
    fun hashtagMustBeAWholeTagButIgnoresCase() {
        val r = rules.copy(minMentions = 0)
        assertNull(reasonOf("Love it #WIN!", r = r))
        assertNull(reasonOf("#win", r = r.copy(requiredHashtag = "win")))
        assertEquals(ExclusionReason.MISSING_HASHTAG, reasonOf("#winning", r = r))
        assertEquals(ExclusionReason.MISSING_HASHTAG, reasonOf("win", r = r))
        assertNull(reasonOf("#CAFÉ time", r = r.copy(requiredHashtag = "#café")))
        assertNull("blank hashtag means none required", reasonOf("anything", r = r.copy(requiredHashtag = "  ")))
    }

    @Test
    fun keywordIsACaseInsensitiveSubstring() {
        val r = rules.copy(minMentions = 0, requiredHashtag = null, keyword = "Giveaway")
        assertNull(reasonOf("best GIVEAWAY ever", r = r))
        assertNull(reasonOf("no keyword needed", r = r.copy(keyword = "")))
    }

    @Test
    fun onePerPersonKeepsTheEarliestValidComment() {
        val first = comment("ana", "@bob #win")
        val second = comment("Ana", "@bob @cid #win")
        val third = comment("ANA", "@dan @eve #win")
        val decisions = decide(first, second, third)
        val expected = listOf(ExclusionReason.TOO_FEW_MENTIONS, null, ExclusionReason.DUPLICATE)
        assertEquals(expected, decisions.map { it.reason })
    }

    @Test
    fun withoutOnePerPersonEveryValidCommentIsAnEntry() {
        val r = rules.copy(onePerPerson = false)
        val decisions = decide(comment("ana", "@bob @cid #win"), comment("ana", "@dan @eve #win"), r = r)
        assertTrue(decisions.all { it.isValid })
        assertEquals(listOf("ana", "ana"), RulesFilterV1.validUsernames(decisions.asSequence()))
    }

    @Test
    fun manualExclusionCarriesTheNoteAndFreesThePersonsEntry() {
        val excluded = comment("ana", "@bob @cid #win")
        val later = comment("ana", "@dan @eve #win")
        val c = context.copy(manualExclusions = mapOf(excluded.id to "Fake account"))
        val decisions = decide(excluded, later, c = c)
        assertEquals(ExclusionReason.MANUAL, decisions[0].reason)
        assertEquals("Fake account", decisions[0].manualNote)
        assertTrue(decisions[1].isValid)
    }

    @Test
    fun automaticReasonsWinOverManualExclusion() {
        val late = comment("ana", "@bob @cid #win", closes.plusSeconds(1))
        val decision = decide(late, c = context.copy(manualExclusions = mapOf(late.id to "x"))).single()
        assertEquals(ExclusionReason.AFTER_DEADLINE, decision.reason)
        assertNull(decision.manualNote)
    }

    @Test
    fun manualInclusionOverridesOnlyContentChecks() {
        val typo = comment("ana", "@bob #wim")
        val late = comment("bob", "@cid @dan #win", closes.plusSeconds(1))
        val blocked = comment("spammer", "@cid @dan #win")
        val c = context.copy(manualInclusions = setOf(typo.id, late.id, blocked.id))
        val decisions = decide(typo, late, blocked, c = c)
        assertTrue("content check overridden", decisions[0].isValid)
        assertEquals(ExclusionReason.AFTER_DEADLINE, decisions[1].reason)
        assertEquals(ExclusionReason.BLOCKLISTED, decisions[2].reason)
    }

    @Test
    fun handlesWithAtSignsInListsStillMatch() {
        val c = context.copy(blocklist = setOf("@spammer"), ownerUsername = "@shop")
        assertEquals(ExclusionReason.BLOCKLISTED, decide(comment("spammer", "@bob @cid #win"), c = c).single().reason)
        assertEquals(ExclusionReason.OWN_ACCOUNT, decide(comment("shop", "@bob @cid #win"), c = c).single().reason)
    }

    @Test
    fun filtersFiftyThousandCommentsWithinTheSpecBudget() {
        val comments = (0 until 50_000).map { i ->
            val text = if (i % 7 == 0) "nice!" else "@friend${i % 97} @pal${i % 89} love it #win"
            RawComment("c$i", "user${i % 30_000}", text, before.plusMillis(i.toLong()))
        }
        RulesFilterV1.evaluate(comments.asSequence().take(5_000), rules, context).count() // warm up
        // Best of three, so a busy shared CI runner doesn't fail a fast implementation; the spec budget is 3 s.
        val millis = (1..3).minOf {
            val start = System.nanoTime()
            val valid = RulesFilterV1.evaluate(comments.asSequence(), rules, context).count { it.isValid }
            assertTrue(valid in 1 until 30_000)
            (System.nanoTime() - start) / 1_000_000
        }
        assertTrue("took $millis ms", millis < 3_000)
    }
}
