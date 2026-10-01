package app.giveaway.di

import app.giveaway.core.data.di.ImportModule
import app.giveaway.core.data.importing.ImportWork
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.flowOf

/** UI tests: no WorkManager under Robolectric, so imports never start. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ImportModule::class])
object FakeImportModule {
    @Provides
    fun importWork(): ImportWork = object : ImportWork {
        override fun start(giveawayId: Long) = Unit

        override fun observeActive(giveawayId: Long) = flowOf(false)
    }
}
