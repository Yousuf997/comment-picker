package app.giveaway.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.R as DesignR

/** S1 Welcome: first-run introduction and trust. */
@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiveawayTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPaddingWide)
            .testTag("screen:S1"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AppMark()
        TicketsIllustration(modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.welcome_headline),
            style = GiveawayTheme.typography.displayLarge,
            color = colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.welcome_body), style = GiveawayTheme.typography.body, color = colors.onMuted)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrustPoint(DesignR.drawable.ic_lock, stringResource(R.string.welcome_trust_device))
            TrustPoint(DesignR.drawable.ic_task_alt, stringResource(R.string.welcome_trust_verify))
            TrustPoint(DesignR.drawable.ic_login, stringResource(R.string.welcome_trust_official))
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            stringResource(R.string.welcome_get_started),
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.welcome_footnote),
            style = GiveawayTheme.typography.caption,
            color = colors.onMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppMark() {
    val colors = GiveawayTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null, tint = colors.onAccent)
        }
        Text(
            stringResource(DesignR.string.app_name),
            style = GiveawayTheme.typography.titleLarge,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun TrustPoint(icon: Int, text: String) {
    val colors = GiveawayTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.accentOnSoft)
        }
        Text(text, style = GiveawayTheme.typography.bodyStrong, color = colors.onBackground)
    }
}

/** Three entry tickets, the middle one marked "Winner". Decorative, so hidden from TalkBack. */
@Composable
private fun TicketsIllustration(modifier: Modifier = Modifier) {
    val colors = GiveawayTheme.colors
    Box(
        modifier = modifier
            .height(150.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Ticket(fill = colors.surfaceMuted, angle = -12f, offsetX = -70)
        Ticket(fill = colors.surfaceMuted, angle = 10f, offsetX = 70)
        Ticket(fill = colors.accent, angle = 0f, offsetX = 0, label = stringResource(R.string.welcome_ticket_winner))
    }
}

@Composable
private fun Ticket(fill: Color, angle: Float, offsetX: Int, label: String? = null) {
    val colors = GiveawayTheme.colors
    Box(
        modifier = Modifier
            .offset(x = offsetX.dp)
            .size(width = 120.dp, height = 76.dp)
            .rotate(angle)
            .background(fill, GiveawayTheme.shapes.button),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = GiveawayTheme.typography.titleLarge, color = colors.onAccent)
        }
    }
}
