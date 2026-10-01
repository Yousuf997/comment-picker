package app.giveaway.core.data.di

import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.DefaultAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun accountRepository(impl: DefaultAccountRepository): AccountRepository
}
