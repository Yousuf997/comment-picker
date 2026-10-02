package app.giveaway

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** M-17: FLAG_SECURE for protected screens, and for the recents preview on Android 12 and below (plan A24). */
@RunWith(RobolectricTestRunner::class)
class SecureWindowTest {

    private val window = Robolectric.buildActivity(Activity::class.java).setup().get().window

    private fun flagged() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun aProtectedScreenIsSecure() {
        val secure = SecureWindow(window, Build.VERSION_CODES.TIRAMISU)
        secure.screen = true
        assertTrue(flagged())
        secure.screen = false
        assertFalse(flagged())
    }

    @Test
    fun olderAndroidHidesTheRecentsPreviewWhileBackgrounded() {
        val secure = SecureWindow(window, Build.VERSION_CODES.S)
        secure.backgrounded = true
        assertTrue(flagged())
        secure.backgrounded = false
        assertFalse("screenshots work again in the foreground", flagged())
    }

    @Test
    fun android13UsesItsRecentsSwitchInstead() {
        val secure = SecureWindow(window, Build.VERSION_CODES.TIRAMISU)
        secure.backgrounded = true
        assertFalse(flagged())
        secure.screen = true
        assertTrue("a protected screen stays secure in the background", flagged())
    }
}
