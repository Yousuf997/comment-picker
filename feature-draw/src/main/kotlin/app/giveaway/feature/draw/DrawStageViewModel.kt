package app.giveaway.feature.draw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.draw.DrawChecks
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.draw.Commit
import app.giveaway.draw.Pick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrawStageState(
    val title: String? = null,
    /** Entries in the canonical list (one per valid comment). */
    val entries: Int = 0,
    /** Different people among them; a person can only win once (plan A12). */
    val people: Int = 0,
    val winners: Int = 0,
    val alternates: Int = 0,
    /** Null while the caption is being re-read (spec: S11 checks). */
    val caption: CaptionCheck? = null,
    val recordDraw: Boolean = true,
    /** The last test draw's picks, shown in the TEST sheet. */
    val testPicks: List<Pick>? = null,
    val drawing: Boolean = false,
) {
    val checking: Boolean get() = caption == null

    /** Fewer people than winners plus alternates: everyone valid is picked (spec: Edge cases). */
    val fewerThanRequested: Boolean get() = people in 1 until winners + alternates

    val canDraw: Boolean get() = !checking && !drawing && entries > 0
}

sealed interface DrawStageEvent {
    /** The real draw is saved; the animation (S12) plays it back. */
    data class Drawn(val record: Boolean) : DrawStageEvent

    /** Opened after the draw (e.g. the app was killed during S12): go straight to the result (spec: Edge cases). */
    data object AlreadyDrawn : DrawStageEvent
}

/** S11 Draw (plan C-21): the checks before the draw, test draws, and the real draw. */
@HiltViewModel
class DrawStageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val entries: EntryRepository,
    private val instagram: InstagramRepository,
    private val draws: DrawService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<DrawRoute>().giveawayId

    private val uiState = MutableStateFlow(DrawStageState())
    val state: StateFlow<DrawStageState> = uiState.asStateFlow()

    private val eventChannel = Channel<DrawStageEvent>(Channel.BUFFERED)
    val events: Flow<DrawStageEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val giveaway = giveaways.get(giveawayId) ?: return
        if (giveaway.status == GiveawayStatus.DRAWN || giveaway.status == GiveawayStatus.ARCHIVED) {
            eventChannel.send(DrawStageEvent.AlreadyDrawn)
            return
        }
        val rules = giveaways.rules(giveawayId)
        val list = entries.canonicalList(giveawayId)
        uiState.update {
            it.copy(
                title = giveaway.title,
                entries = list.size,
                people = list.distinctCount,
                winners = rules?.winnersCount ?: 0,
                alternates = rules?.alternatesCount ?: 0,
                recordDraw = settings.get().recordDrawsByDefault,
            )
        }
        uiState.update { it.copy(caption = checkCaption(giveaway.igMediaId)) }
    }

    /** Re-reads the caption now (spec: Commit step 4); offline or a deleted post means "not checked" (plan A7). */
    private suspend fun checkCaption(mediaId: String): CaptionCheck {
        val hash = giveaways.commitment(giveawayId)?.commitHash ?: return CaptionCheck.NOT_CHECKED
        return when (val media = instagram.mediaById(mediaId)) {
            is IgResult.Ok ->
                if (Commit.captionContains(media.value.caption, hash)) CaptionCheck.FOUND else CaptionCheck.NOT_FOUND
            is IgResult.Err -> CaptionCheck.NOT_CHECKED
        }
    }

    fun onRecordDraw(record: Boolean) = uiState.update { it.copy(recordDraw = record) }

    /** A TEST draw with a fresh seed; it never counts (spec: S11). */
    fun testDraw() {
        viewModelScope.launch {
            val outcome = draws.testDraw(giveawayId)
            uiState.update { it.copy(testPicks = outcome.picks) }
        }
    }

    fun closeTest() = uiState.update { it.copy(testPicks = null) }

    /** Computes, signs and saves the real draw before any animation (plan C-23), then plays it on S12. */
    fun drawWinners() {
        val current = uiState.value
        if (!current.canDraw) return
        uiState.update { it.copy(drawing = true) }
        viewModelScope.launch {
            val checks = DrawChecks(current.caption ?: CaptionCheck.NOT_CHECKED, integrityVerified = false)
            draws.realDraw(giveawayId, checks)
            eventChannel.send(DrawStageEvent.Drawn(record = current.recordDraw))
        }
    }
}
