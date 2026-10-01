package app.giveaway

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.giveaway.feature.onboarding.WelcomeScreen

/** Placeholder graph; F-08 replaces it with typed routes S1 to S15. */
@Composable
fun GiveawayNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") { WelcomeScreen() }
    }
}
