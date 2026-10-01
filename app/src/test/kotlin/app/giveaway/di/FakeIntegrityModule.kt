package app.giveaway.di

import app.giveaway.core.security.integrity.IntegrityModule
import app.giveaway.core.security.integrity.PlayIntegrity
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** UI tests: there are no Play services under Robolectric, so integrity is never available. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [IntegrityModule::class])
object FakeIntegrityModule {
    @Provides
    fun playIntegrity(): PlayIntegrity = object : PlayIntegrity {
        override suspend fun classicToken(nonce: String): String? = null

        override suspend fun standardToken(requestHash: String): String? = null
    }
}
