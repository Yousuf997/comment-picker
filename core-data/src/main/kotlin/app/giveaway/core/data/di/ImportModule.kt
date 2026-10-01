package app.giveaway.core.data.di

import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.importing.WorkManagerImportWork
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** WorkManager-backed import, in its own module so UI tests can replace it (no WorkManager under Robolectric). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {
    @Binds
    internal abstract fun importWork(impl: WorkManagerImportWork): ImportWork
}
