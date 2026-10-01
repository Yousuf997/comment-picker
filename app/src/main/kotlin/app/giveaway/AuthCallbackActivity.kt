package app.giveaway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.giveaway.core.instagram.auth.AuthCallbacks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the verified App Link Instagram redirects to after sign-in, passes it to the waiting screen and returns
 * to it. Starting MainActivity with CLEAR_TOP also closes the Custom Tab sitting above it in the task.
 */
@AndroidEntryPoint
class AuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var callbacks: AuthCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { callbacks.deliver(it.toString()) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
