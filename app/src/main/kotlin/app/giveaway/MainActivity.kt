package app.giveaway

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.feature.onboarding.lock.AppLockController
import app.giveaway.feature.onboarding.lock.AppLockGate
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settings: Lazy<SettingsRepository>

    @Inject
    lateinit var lockController: AppLockController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Opening the encrypted database unwraps its key; keep that off the main thread.
            val repository = withContext(Dispatchers.IO) { settings.get() }
            repository.observe().collect { lockController.onSettings(it.appLockMethod, it.lockAfterSeconds) }
        }
        setContent {
            GiveawayTheme {
                AppLockGate {
                    GiveawayNavHost()
                }
            }
        }
    }
}
