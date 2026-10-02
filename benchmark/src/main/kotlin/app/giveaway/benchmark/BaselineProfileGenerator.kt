package app.giveaway.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the Baseline Profile for the first screens a user sees (plan H-06): `./gradlew :app:generateBaselineProfile`
 * on a device or emulator, then commit the generated file.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startAndOpenSignIn() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // S1 to S2 on a fresh install; with an account the app opens on Home and this step is skipped.
        device.findObject(By.text("Get started"))?.click()
        device.wait(Until.hasObject(By.res("screen:S2")), WAIT_MS)
    }
}

private const val WAIT_MS = 5_000L
