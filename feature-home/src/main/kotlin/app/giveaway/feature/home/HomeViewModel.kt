package app.giveaway.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.SignInState
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawaySummary
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** What a card's status chip says (spec: S4). */
enum class CardStatus { DRAFT, WAITING, READY_TO_IMPORT, IMPORTING, REVIEW, DRAWN, COMPLETED }

/** Where tapping a card goes, by status (spec: S4 actions). */
enum class GiveawayDestination { RULES, IMPORT, REVIEW, WINNERS, CERTIFICATE }

data class GiveawayCard(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val status: CardStatus,
    val destination: GiveawayDestination,
    val closesAt: Instant,
    val createdAt: Instant,
    val commentCount: Int,
    val validEntryCount: Int,
)

data class HomeUiState(
    val loading: Boolean = true,
    val username: String? = null,
    val signInExpired: Boolean = false,
    val showBackupReminder: Boolean = false,
    val inProgress: List<GiveawayCard> = emptyList(),
    val completed: List<GiveawayCard> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && inProgress.isEmpty() && completed.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    giveaways: GiveawayRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        accounts.observeSignInState(),
        giveaways.observeSummaries(),
        settings.observe(),
    ) { signIn, summaries, prefs ->
        val now = clock.instant()
        val cards = summaries.map { it.toCard(now) }
        val lastBackup = prefs.lastBackupAt
        HomeUiState(
            loading = false,
            username = when (signIn) {
                is SignInState.Active -> signIn.username
                is SignInState.Expired -> signIn.username
                SignInState.SignedOut -> null
            },
            signInExpired = signIn is SignInState.Expired,
            // Remind only when there is something to lose (spec: S4 backup banner).
            showBackupReminder = cards.isNotEmpty() &&
                (lastBackup == null || Duration.between(lastBackup, now) > BACKUP_REMINDER_AFTER),
            inProgress = cards.filter { it.status != CardStatus.COMPLETED },
            completed = cards.filter { it.status == CardStatus.COMPLETED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    private fun GiveawaySummary.toCard(now: Instant): GiveawayCard {
        val (status, destination) = when (giveaway.status) {
            // Only a draft left by an older version: saving its rules opens it (plan A35).
            GiveawayStatus.DRAFT -> CardStatus.DRAFT to GiveawayDestination.RULES
            GiveawayStatus.COMMITTED ->
                if (now.isBefore(giveaway.closesAt)) {
                    CardStatus.WAITING to GiveawayDestination.IMPORT
                } else {
                    CardStatus.READY_TO_IMPORT to GiveawayDestination.IMPORT
                }
            GiveawayStatus.IMPORTING -> CardStatus.IMPORTING to GiveawayDestination.IMPORT
            GiveawayStatus.REVIEW -> CardStatus.REVIEW to GiveawayDestination.REVIEW
            GiveawayStatus.DRAWN -> CardStatus.DRAWN to GiveawayDestination.WINNERS
            GiveawayStatus.ARCHIVED -> CardStatus.COMPLETED to GiveawayDestination.CERTIFICATE
        }
        return GiveawayCard(
            id = giveaway.id,
            title = giveaway.title,
            thumbnailUrl = giveaway.thumbnailUrl,
            status = status,
            destination = destination,
            closesAt = giveaway.closesAt,
            createdAt = giveaway.createdAt,
            commentCount = commentCount,
            validEntryCount = validEntryCount,
        )
    }

    private companion object {
        val BACKUP_REMINDER_AFTER: Duration = Duration.ofDays(30)
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
