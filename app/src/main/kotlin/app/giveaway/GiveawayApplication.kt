package app.giveaway

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import app.giveaway.core.data.work.TokenRefreshWorker
import app.giveaway.feature.onboarding.lock.AppLockController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GiveawayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var lockController: AppLockController

    /** WorkManager starts on demand with Hilt's worker factory; its default initializer is off in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        TokenRefreshWorker.schedule(WorkManager.getInstance(this))
        // Idle lock (spec: App access): time in the background counts, not time on one screen.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = lockController.onForeground()

                override fun onStop(owner: LifecycleOwner) = lockController.onBackground()
            },
        )
    }
}
