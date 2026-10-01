package app.giveaway.feature.onboarding.lock

import app.giveaway.core.data.db.AppLockMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LockStatus {
    /** Settings not read yet: show nothing, so content never flashes before the lock appears. */
    CHECKING,
    LOCKED,
    UNLOCKED,
}

/**
 * Decides when the app is locked (spec: App access; plan C-04). Locked on a cold start when app lock is on, and again
 * when the app returns after spending at least the chosen idle time in the background (default 1 minute).
 * Turning app lock on (S3, S5) doesn't lock immediately.
 */
@Singleton
class AppLockController @Inject constructor(private val clock: Clock) {

    private val mutableStatus = MutableStateFlow(LockStatus.CHECKING)
    val status: StateFlow<LockStatus> = mutableStatus.asStateFlow()

    var method: AppLockMethod? = null
        private set

    private var lockAfter: Duration = Duration.ofSeconds(DEFAULT_LOCK_AFTER_SECONDS)
    private var backgroundedAt: Instant? = null

    /** Called with the current settings on start and whenever they change. */
    fun onSettings(lockMethod: AppLockMethod?, lockAfterSeconds: Int) {
        method = lockMethod
        lockAfter = Duration.ofSeconds(lockAfterSeconds.toLong())
        when {
            lockMethod == null -> mutableStatus.value = LockStatus.UNLOCKED
            mutableStatus.value == LockStatus.CHECKING -> mutableStatus.value = LockStatus.LOCKED
        }
    }

    /** The whole app went to the background (process lifecycle ON_STOP). */
    fun onBackground() {
        backgroundedAt = clock.instant()
    }

    /** The app came back to the foreground (process lifecycle ON_START). */
    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (method != null && Duration.between(since, clock.instant()) >= lockAfter) {
            mutableStatus.value = LockStatus.LOCKED
        }
    }

    fun onUnlocked() {
        mutableStatus.value = LockStatus.UNLOCKED
    }

    companion object {
        const val DEFAULT_LOCK_AFTER_SECONDS = 60L
    }
}
