package app.giveaway.feature.onboarding.lock

import app.giveaway.core.data.db.AppLockMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** C-04 acceptance: with a fake clock, the app locks after the idle timeout and not before. */
class AppLockControllerTest {

    private var now = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }
    private val controller = AppLockController(clock)

    private fun awayFor(seconds: Long) {
        controller.onBackground()
        now = now.plusSeconds(seconds)
        controller.onForeground()
    }

    @Test
    fun nothingIsShownUntilSettingsAreKnown() {
        assertEquals(LockStatus.CHECKING, controller.status.value)
    }

    @Test
    fun aColdStartIsLockedWhenAppLockIsOn() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        assertEquals(LockStatus.LOCKED, controller.status.value)
    }

    @Test
    fun withAppLockOffTheAppNeverLocks() {
        controller.onSettings(null, lockAfterSeconds = 60)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
        awayFor(3_600)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }

    @Test
    fun locksAfterTheIdleTimeoutAndNotBefore() {
        controller.onSettings(AppLockMethod.BIOMETRIC, lockAfterSeconds = 60)
        controller.onUnlocked()
        awayFor(59)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
        awayFor(60)
        assertEquals(LockStatus.LOCKED, controller.status.value)
    }

    @Test
    fun theChosenTimeoutIsUsed() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 300)
        controller.onUnlocked()
        awayFor(299)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
        awayFor(300)
        assertEquals(LockStatus.LOCKED, controller.status.value)
    }

    @Test
    fun turningLockOnDoesNotLockImmediately() {
        controller.onSettings(null, lockAfterSeconds = 60)
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }

    @Test
    fun turningLockOffUnlocks() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        controller.onSettings(null, lockAfterSeconds = 60)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }

    @Test
    fun aForegroundWithoutABackgroundChangesNothing() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        controller.onUnlocked()
        now = now.plusSeconds(600)
        controller.onForeground()
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }
}
