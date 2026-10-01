package app.giveaway

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.feature.home.HomeRoute
import app.giveaway.feature.onboarding.WelcomeRoute
import app.giveaway.feature.onboarding.lock.AppLockController
import app.giveaway.feature.onboarding.lock.AppLockGate
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settings: Lazy<SettingsRepository>

    @Inject
    lateinit var accounts: Lazy<AccountRepository>

    @Inject
    lateinit var lockController: AppLockController

    /** S1 until an account is connected, then Home (spec: S1 states, User flows). */
    private var start by mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Opening the encrypted database unwraps its key; keep that off the main thread.
            val repository = withContext(Dispatchers.IO) { settings.get() }
            repository.observe().collect { lockController.onSettings(it.appLockMethod, it.lockAfterSeconds) }
        }
        lifecycleScope.launch {
            val connected = withContext(Dispatchers.IO) { accounts.get().observeUsername().first() != null }
            start = if (connected) HomeRoute else WelcomeRoute
        }
        setContent {
            GiveawayTheme {
                AppLockGate {
                    val destination = start
                    if (destination == null) {
                        Box(Modifier.fillMaxSize().background(GiveawayTheme.colors.background))
                    } else {
                        GiveawayNavHost(startDestination = destination)
                    }
                }
            }
        }
    }
}
