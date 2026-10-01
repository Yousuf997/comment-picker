package app.giveaway.feature.create

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** C-11 acceptance: S6 grid with a fake Instagram; Continue is disabled until a tile is selected. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class PickPostScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val account = FakeInstagram(
        mapOf(
            null to page(
                media("p1", comments = 2400, day = 28),
                media("r1", reel = true, comments = 312, day = 25),
                media("p2", comments = 1, day = 21),
                media("r2", reel = true, comments = 87, day = 18),
                media("p3", comments = 0, day = 12),
                media("p4", comments = 45, day = 9),
            ),
        ),
    )

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun tile(id: String) = compose.onNodeWithTag("pick_post:tile:$id")

    private fun show(instagram: FakeInstagram = account, onContinue: (IgMedia) -> Unit = {}) {
        val viewModel = PickPostViewModel(instagram)
        compose.setContent {
            GiveawayTheme { PickPostScreen(onBack = {}, onContinue = onContinue, viewModel = viewModel) }
        }
    }

    @Test
    fun continueIsDisabledUntilATileIsSelected() {
        val picked = mutableListOf<IgMedia>()
        show(onContinue = { picked += it })
        text(R.string.wizard_continue).assertIsNotEnabled()
        tile("r1").performClick()
        tile("r1").assertIsSelected()
        text(R.string.wizard_continue).assertIsEnabled().performClick()
        assertEquals(listOf("r1"), picked.map { it.id })
    }

    @Test
    fun theSummaryNamesTheSelection() {
        show()
        text(R.string.pick_post_none_selected).assertExists()
        tile("p2").performClick()
        compose.onNodeWithText("1 comment", substring = true).assertExists()
    }

    @Test
    fun filtersSwitchBetweenPostsAndReels() {
        show()
        text(R.string.pick_post_filter_reels).performClick()
        compose.waitForIdle()
        tile("r1").assertExists()
        tile("p1").assertDoesNotExist()
        text(R.string.pick_post_filter_posts).performClick()
        compose.waitForIdle()
        tile("p1").assertExists()
        tile("r1").assertDoesNotExist()
    }

    @Test
    fun anAccountWithNoPostsSaysSo() {
        show(FakeInstagram(mapOf(null to page())))
        text(R.string.pick_post_empty_all).assertExists()
        text(R.string.wizard_continue).assertIsNotEnabled()
    }

    @Test
    fun aFailedLoadOffersRetry() {
        val flaky = FakeInstagram(mapOf(null to IgResult.Err(IgError.Offline)))
        show(flaky)
        text(R.string.pick_post_error_offline).assertExists()
        flaky.overrides[null] = page(media("p1"))
        text(R.string.pick_post_retry).performClick()
        compose.waitForIdle()
        tile("p1").assertExists()
    }

    @Test
    fun loadingShowsSkeletonTiles() {
        compose.setContent { GiveawayTheme { Loading() } }
        compose.onNodeWithTag("pick_post:loading").assertExists()
    }

    @Test
    fun screenshotLight() {
        show()
        tile("r1").performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/s6_pick_post_light.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h844dp-xhdpi")
    fun screenshotArabic() {
        show()
        tile("p1").performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/s6_pick_post_arabic.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-night-xhdpi")
    fun screenshotLoadingDark() = captureRoboImage("src/test/screenshots/s6_pick_post_loading_dark.png") {
        GiveawayTheme { Loading() }
    }

    @Composable
    private fun Loading() {
        val loading = LoadStates(LoadState.Loading, LoadState.NotLoading(false), LoadState.NotLoading(false))
        PickPostScreen(
            filter = MediaFilter.ALL,
            selected = null,
            media = flowOf(PagingData.empty<IgMedia>(loading)).collectAsLazyPagingItems(),
            onBack = {},
            onFilter = {},
            onSelect = {},
            onContinue = {},
        )
    }
}
