package app.giveaway.feature.onboarding

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.R as DesignR

/** Where S2 is in the sign-in flow. The OAuth flow behind it arrives with F-12. */
sealed interface ConnectState {
    /** Ready to start: explains requirements and permissions. */
    data object Idle : ConnectState

    /** Returned from Instagram's page; the code is being exchanged for a token. */
    data object Loading : ConnectState

    /** Signed in with a personal account, which Instagram's API can't read. */
    data object PersonalAccount : ConnectState

    /** The user closed Instagram's page without signing in. */
    data object Cancelled : ConnectState

    /** Instagram or the login helper couldn't be reached. */
    data object NetworkError : ConnectState
}

/** Instagram Help Center article on switching to a professional account. Re-check at build time (A3). */
const val PROFESSIONAL_ACCOUNT_HELP_URL = "https://help.instagram.com/502981923235522"

/** S2 with sign-in wired up: opens Instagram's page in a Custom Tab and reports success through [onConnected]. */
@Composable
internal fun ConnectInstagramScreen(
    onConnected: () -> Unit,
    viewModel: ConnectInstagramViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectEvent.OpenBrowser -> try {
                    val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
                    tab.launchUrl(context, Uri.parse(event.url))
                } catch (e: ActivityNotFoundException) {
                    viewModel.onBrowserUnavailable()
                }
                ConnectEvent.SignedIn -> onConnected()
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    ConnectInstagramScreen(
        state = state,
        onContinue = viewModel::onContinue,
        onOpenHelp = { uriHandler.openUri(PROFESSIONAL_ACCOUNT_HELP_URL) },
    )
}

/**
 * S2 Connect Instagram: explains what the app needs and can do, then starts sign-in on Instagram's own page.
 * [onContinue] starts (or restarts) sign-in; [onOpenHelp] opens the "switch to a professional account" guide.
 */
@Composable
internal fun ConnectInstagramScreen(
    state: ConnectState,
    onContinue: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiveawayTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPaddingWide)
            .testTag("screen:S2"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.connect_title),
            style = GiveawayTheme.typography.display,
            color = colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.connect_body), style = GiveawayTheme.typography.body, color = colors.onMuted)

        StateNotice(state, onContinue, onOpenHelp)

        if (state != ConnectState.PersonalAccount) {
            NoticeCard(
                title = stringResource(R.string.connect_requirement_title),
                body = stringResource(R.string.connect_requirement_body),
                tone = NoticeTone.Info,
                actionLabel = stringResource(R.string.connect_how_to_switch),
                onAction = onOpenHelp,
            )
        }

        PermissionList(
            heading = stringResource(R.string.connect_we_can),
            icon = DesignR.drawable.ic_check,
            tint = colors.success,
            items = listOf(
                stringResource(R.string.connect_can_read_posts),
                stringResource(R.string.connect_can_read_comments),
            ),
        )
        PermissionList(
            heading = stringResource(R.string.connect_we_never),
            icon = DesignR.drawable.ic_close,
            tint = colors.danger,
            items = listOf(
                stringResource(R.string.connect_never_post),
                stringResource(R.string.connect_never_password),
                stringResource(R.string.connect_never_upload),
            ),
        )

        if (state == ConnectState.Loading) {
            LoadingRow()
        } else {
            val label = when (state) {
                ConnectState.Idle -> R.string.connect_continue
                else -> R.string.connect_retry
            }
            PrimaryButton(stringResource(label), onClick = onContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StateNotice(state: ConnectState, onContinue: () -> Unit, onOpenHelp: () -> Unit) {
    val modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (state) {
        ConnectState.PersonalAccount -> NoticeCard(
            title = stringResource(R.string.connect_personal_title),
            body = stringResource(R.string.connect_personal_body),
            tone = NoticeTone.Warning,
            actionLabel = stringResource(R.string.connect_how_to_switch),
            onAction = onOpenHelp,
            modifier = modifier,
        )
        ConnectState.NetworkError -> NoticeCard(
            title = stringResource(R.string.connect_network_title),
            body = stringResource(R.string.connect_network_body),
            tone = NoticeTone.Warning,
            actionLabel = stringResource(R.string.connect_retry),
            onAction = onContinue,
            modifier = modifier,
        )
        ConnectState.Cancelled -> Text(
            stringResource(R.string.connect_cancelled),
            style = GiveawayTheme.typography.body,
            color = GiveawayTheme.colors.onMuted,
            modifier = modifier,
        )
        ConnectState.Idle, ConnectState.Loading -> Unit
    }
}

@Composable
private fun PermissionList(heading: String, icon: Int, tint: Color, items: List<String>) {
    GiveawayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                heading.uppercase(),
                style = GiveawayTheme.typography.overline,
                color = GiveawayTheme.colors.onMuted,
                modifier = Modifier.semantics { heading() },
            )
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Decorative: the heading says whether the list is allowed or never done.
                    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(item, style = GiveawayTheme.typography.body, color = GiveawayTheme.colors.onBackground)
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = GiveawayTheme.colors.accent,
            strokeWidth = 3.dp,
        )
        Text(stringResource(R.string.connect_loading), style = GiveawayTheme.typography.bodyStrong)
    }
}
