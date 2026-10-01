package app.giveaway.di

import app.giveaway.core.data.di.CommitModule
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.work.DeadlineScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** UI tests: no Android Keystore or WorkManager under Robolectric. The fake vault only reverses the bytes. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CommitModule::class])
object FakeCommitModule {
    @Provides
    fun seedVault(): SeedVault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }

    @Provides
    fun deadlineScheduler(): DeadlineScheduler = DeadlineScheduler { _, _ -> }

    @Provides
    fun recordSigner(): RecordSigner = object : RecordSigner {
        override fun sign(record: ByteArray) = RecordSigner.Signature(ByteArray(0), ByteArray(0), "test")
    }
}
