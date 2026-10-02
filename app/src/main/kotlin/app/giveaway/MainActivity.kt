package app.giveaway

import android.os.Build
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
import androidx.navigation.compose.rememberNavController
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

    private var blockScreenshots by mutableStateOf(false)

    private lateinit var secureWindow: SecureWindow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        secureWindow = SecureWindow(window)
        // The recent-apps preview never shows giveaway data (spec: Storage and keys; plan A24).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        lifecycleScope.launch {
            // Opening the encrypted database unwraps its key; keep that off the main thread.
            val repository = withContext(Dispatchers.IO) { settings.get() }
            repository.observe().collect {
                lockController.onSettings(it.appLockMethod, it.lockAfterSeconds)
                blockScreenshots = it.blockScreenshots
            }
        }
        lifecycleScope.launch {
            val connected = withContext(Dispatchers.IO) { accounts.get().observeUsername().first() != null }
            start = if (connected) HomeRoute else WelcomeRoute
        }
        setContent {
            GiveawayTheme {
                // Above the lock gate, so the screen the user was on is still there after unlocking.
                val navController = rememberNavController()
                AppLockGate {
                    val destination = start
                    if (destination == null) {
                        Box(Modifier.fillMaxSize().background(GiveawayTheme.colors.background))
                    } else {
                        GiveawayNavHost(
                            navController = navController,
                            startDestination = destination,
                            blockScreenshots = blockScreenshots,
                            onSecureScreen = { secureWindow.screen = it },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        secureWindow.backgrounded = true
    }

    override fun onResume() {
        super.onResume()
        secureWindow.backgrounded = false
    }
}
