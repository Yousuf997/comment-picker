package app.giveaway.core.data.di

import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.DefaultAccountRepository
import app.giveaway.core.data.giveaway.DefaultGiveawayOpener
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.GiveawayOpener
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.instagram.api.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun accountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    abstract fun tokenProvider(impl: DefaultAccountRepository): TokenProvider

    @Binds
    abstract fun settingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    abstract fun giveawayRepository(impl: DefaultGiveawayRepository): GiveawayRepository

    @Binds
    abstract fun giveawayOpener(impl: DefaultGiveawayOpener): GiveawayOpener
}
