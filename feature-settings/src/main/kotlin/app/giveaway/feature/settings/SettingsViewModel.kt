package app.giveaway.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.cleanup.EverythingWiper
import app.giveaway.core.data.cleanup.GiveawayRemoval
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.security.lock.PinStore
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Switches the app's language (spec: S5 language); null follows the system. */
fun interface AppLanguage {
    fun set(languageTag: String?)
}

/** Per-app language through AppCompat, which also stores the choice on Android 12 and below. */
class AppCompatLanguage @Inject constructor() : AppLanguage {
    override fun set(languageTag: String?) {
        val locales = languageTag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

data class SettingsUiState(
    val settings: SettingsEntity = SettingsEntity(),
    val username: String? = null,
    /** For "Delete all giveaways" (plan A33). */
    val giveawayCount: Int = 0,
)

sealed interface SettingsEvent {
    /** Turning app lock on goes through S3. */
    data object SetUpAppLock : SettingsEvent

    /** Signed out of Instagram; go to S2. */
    data object Disconnected : SettingsEvent

    /** Everything is wiped; the app restarts at S1. */
    data object EverythingDeleted : SettingsEvent
}

/** S5 Settings (spec). Backup and restore have their own pages. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val pinStore: Lazy<PinStore>,
    private val language: AppLanguage,
    private val wiper: Lazy<EverythingWiper>,
    private val giveaways: GiveawayRemoval,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.observe(),
        accounts.observeUsername(),
        giveaways.observeCount(),
    ) { prefs, user, count ->
        SettingsUiState(prefs, user, count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    fun onAppLockSwitched(on: Boolean) {
        if (on) eventChannel.trySend(SettingsEvent.SetUpAppLock)
        // Turning it off asks for confirmation first; see turnOffAppLock.
    }

    fun turnOffAppLock() = viewModelScope.launch {
        settings.setAppLock(null)
        pinStore.get().clear()
    }

    fun onBlockScreenshots(on: Boolean) = update { it.copy(blockScreenshots = on) }

    fun onLockAfter(seconds: Int) = update { it.copy(lockAfterSeconds = seconds) }

    fun onAutoDeleteDays(days: Int) = update { it.copy(autoDeleteDays = days) }

    fun onRecordDraws(on: Boolean) = update { it.copy(recordDrawsByDefault = on) }

    fun onLanguage(tag: String?) {
        update { it.copy(language = tag) }
        language.set(tag)
    }

    fun disconnect() = viewModelScope.launch {
        accounts.signOut()
        eventChannel.send(SettingsEvent.Disconnected)
    }

    /** After the second confirmation (spec: S5 "Delete everything", double confirm). */
    fun deleteEverything() = viewModelScope.launch {
        wiper.get().deleteEverything()
        eventChannel.send(SettingsEvent.EverythingDeleted)
    }

    /** Deletes every giveaway; the account, settings, keys, blocklist and past winners stay (plan A33). */
    fun deleteAllGiveaways() = viewModelScope.launch { giveaways.deleteAll() }

    private fun update(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { settings.update(transform) }
    }

    companion object {
        val LOCK_AFTER_OPTIONS = listOf(30, 60, 300, 900)
        val AUTO_DELETE_OPTIONS = listOf(30, 90, 180, 365)
        val LANGUAGE_OPTIONS = listOf<String?>(null, "en", "ar")
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** True when the app lock is on with any method. */
        fun SettingsEntity.lockOn(): Boolean = appLockMethod != null
    }
}
