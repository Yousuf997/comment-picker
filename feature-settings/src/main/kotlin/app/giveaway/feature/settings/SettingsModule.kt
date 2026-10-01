package app.giveaway.feature.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    abstract fun appLanguage(impl: AppCompatLanguage): AppLanguage
}
