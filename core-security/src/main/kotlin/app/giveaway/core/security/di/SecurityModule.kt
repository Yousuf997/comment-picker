package app.giveaway.core.security.di

import app.giveaway.core.security.lock.BiometricAvailability
import app.giveaway.core.security.lock.DefaultBiometricAvailability
import app.giveaway.core.security.lock.EncryptedPinStore
import app.giveaway.core.security.lock.PinStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {

    @Binds
    abstract fun biometricAvailability(impl: DefaultBiometricAvailability): BiometricAvailability

    @Binds
    abstract fun pinStore(impl: EncryptedPinStore): PinStore

    companion object {
        /** The one clock for the app, so time-based rules (token refresh, PIN lockout) are testable. */
        @Provides
        fun clock(): Clock = Clock.systemUTC()
    }
}
