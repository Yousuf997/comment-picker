package app.giveaway.notifications

import app.giveaway.core.data.importing.ImportNotifications
import app.giveaway.core.data.work.DeadlineNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    abstract fun deadlineNotifier(impl: AppDeadlineNotifier): DeadlineNotifier

    @Binds
    abstract fun importNotifications(impl: AppImportNotifications): ImportNotifications
}
