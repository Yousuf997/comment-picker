package app.giveaway.core.data.di

import app.giveaway.core.data.draw.KeystoreRecordSigner
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.giveaway.KeystoreSeedVault
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.core.data.work.WorkManagerDeadlineScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The Keystore seed vault and record signer and the WorkManager deadline schedule, in their own module so UI tests
 * can replace them: neither the Android Keystore nor WorkManager runs under Robolectric.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommitModule {
    @Binds
    internal abstract fun seedVault(impl: KeystoreSeedVault): SeedVault

    @Binds
    internal abstract fun deadlineScheduler(impl: WorkManagerDeadlineScheduler): DeadlineScheduler

    @Binds
    internal abstract fun recordSigner(impl: KeystoreRecordSigner): RecordSigner
}
