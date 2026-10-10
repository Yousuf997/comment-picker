package app.giveaway

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.giveaway.feature.create.ImportedCommentsRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.draw.CertificateRoute
import app.giveaway.feature.draw.DrawingRoute
import app.giveaway.feature.draw.WinnersRoute

/**
 * Screen security (spec: App access, Storage and keys; plan M-17). With "Block screenshots" on, FLAG_SECURE covers the
 * screens that show comments or entries (the imported comments, S10) or winners and the revealed seed (S12, S14, S15).
 * The draw recorder renders its own frames, so it keeps working. The recent-apps preview never shows the app: Android
 * 13+ has a switch for that; older versions get FLAG_SECURE while the app is in the background (plan A24).
 */
class SecureWindow(private val window: Window, private val sdk: Int = Build.VERSION.SDK_INT) {
    /** The current screen should be protected. */
    var screen: Boolean = false
        set(value) {
            field = value
            apply()
        }

    /** The app has left the foreground. */
    var backgrounded: Boolean = false
        set(value) {
            field = value
            apply()
        }

    private fun apply() {
        val secure = screen || (backgrounded && sdk < Build.VERSION_CODES.TIRAMISU)
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    companion object {
        /** Whether [destination] shows comments, entries, winners or the seed. */
        fun protects(destination: NavDestination?): Boolean = destination != null && (
            destination.hasRoute<ImportedCommentsRoute>() ||
                destination.hasRoute<ReviewEntriesRoute>() ||
                destination.hasRoute<DrawingRoute>() ||
                destination.hasRoute<WinnersRoute>() ||
                destination.hasRoute<CertificateRoute>()
            )
    }
}
