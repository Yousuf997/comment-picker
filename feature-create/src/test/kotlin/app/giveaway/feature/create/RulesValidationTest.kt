package app.giveaway.feature.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** C-12 acceptance: S7 validation (past time, missing #, winners + alternates = 0). */
class RulesValidationTest {

    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val valid = RulesForm(closesAt = now.plus(Duration.ofDays(3)))

    private fun errors(form: RulesForm) = RulesValidation.validate(form, now)

    @Test
    fun theDefaultsAreValid() = assertEquals(emptySet<RulesError>(), errors(valid))

    @Test
    fun closingTimeMustBeInTheFuture() {
        assertEquals(setOf(RulesError.CLOSES_IN_PAST), errors(valid.copy(closesAt = now.minusSeconds(60))))
        assertEquals(setOf(RulesError.CLOSES_IN_PAST), errors(valid.copy(closesAt = now)))
        assertEquals(emptySet<RulesError>(), errors(valid.copy(closesAt = now.plusSeconds(60))))
    }

    @Test
    fun entriesCanCloseNow() {
        val closingNow = valid.copy(closesAt = now.minusSeconds(60), closesNow = true)
        assertEquals(emptySet<RulesError>(), errors(closingNow))
        val saved = now.plusSeconds(5)
        assertEquals("entries close as the rules are saved", saved, closingNow.toRules(saved).closesAt)
        assertEquals(valid.closesAt, valid.toRules(saved).closesAt)
    }

    @Test
    fun hashtagMustStartWithHashAndBeOneWord() {
        listOf("giveaway", "#", "#two words", "##", "#give-away").forEach { tag ->
            assertEquals(tag, setOf(RulesError.HASHTAG_FORMAT), errors(valid.copy(hashtag = tag)))
        }
        listOf("", "  ", "#giveaway", " #Giveaway2026 ", "#مسابقة", "#win_big").forEach { tag ->
            assertEquals(tag, emptySet<RulesError>(), errors(valid.copy(hashtag = tag)))
        }
    }

    @Test
    fun someoneMustBePicked() {
        val nobody = errors(valid.copy(winners = 0, alternates = 0))
        assertEquals(true, RulesError.NOBODY_TO_PICK in nobody)
    }

    @Test
    fun steppersStayInTheirRanges() {
        assertEquals(setOf(RulesError.OUT_OF_RANGE), errors(valid.copy(minMentions = 11)))
        assertEquals(setOf(RulesError.OUT_OF_RANGE), errors(valid.copy(winners = 51)))
        assertEquals(setOf(RulesError.OUT_OF_RANGE), errors(valid.copy(alternates = 21)))
    }

    @Test
    fun blankTextFieldsBecomeNoRule() {
        val rules = valid.copy(hashtag = "  ", keyword = " ").toRules(now)
        assertNull(rules.requiredHashtag)
        assertNull(rules.keyword)
        assertEquals("#win", valid.copy(hashtag = " #win ").toRules(now).requiredHashtag)
    }

    @Test
    fun titlesComeFromTheCaptionsFirstLine() {
        assertEquals("Win a tote bag!", SetRulesViewModel.titleFrom("\n  Win a tote bag!  \nTag two friends"))
        assertNull(SetRulesViewModel.titleFrom("   \n "))
        assertNull(SetRulesViewModel.titleFrom(null))
        val long = "Celebrate our fifth birthday with a giveaway of three hampers full of local treats and more"
        val title = SetRulesViewModel.titleFrom(long)!!
        assertEquals("Celebrate our fifth birthday with a giveaway of three…", title)
    }
}
