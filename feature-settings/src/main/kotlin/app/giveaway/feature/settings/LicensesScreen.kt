package app.giveaway.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R as DesignR

/** A third-party component shipped in the app and its license. Names are proper nouns and stay untranslated. */
internal data class OpenSourceComponent(val name: String, val license: String)

private const val APACHE_2 = "Apache License 2.0"
private const val OFL = "SIL Open Font License 1.1"

internal val OPEN_SOURCE_COMPONENTS = listOf(
    OpenSourceComponent("Android Jetpack (AndroidX, Compose, Room, WorkManager, Biometric, Browser)", APACHE_2),
    OpenSourceComponent("Kotlin, kotlinx.coroutines, kotlinx.serialization", APACHE_2),
    OpenSourceComponent("Dagger and Hilt", APACHE_2),
    OpenSourceComponent("OkHttp and Retrofit", APACHE_2),
    OpenSourceComponent("Coil", APACHE_2),
    OpenSourceComponent("Tink", APACHE_2),
    OpenSourceComponent("SQLCipher for Android", "BSD-style (Zetetic)"),
    OpenSourceComponent("Argon2Kt", "MIT License"),
    OpenSourceComponent("Material Symbols", APACHE_2),
    OpenSourceComponent("Bricolage Grotesque", OFL),
    OpenSourceComponent("Manrope", OFL),
    OpenSourceComponent("JetBrains Mono", OFL),
    OpenSourceComponent("IBM Plex Sans Arabic", OFL),
)

/** Open-source licenses (spec: S5). */
@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:licenses"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(DesignR.string.navigate_back))
            }
            Text(
                stringResource(R.string.settings_licenses),
                style = GiveawayTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(OPEN_SOURCE_COMPONENTS) { component ->
                Column {
                    Text(
                        component.name,
                        style = GiveawayTheme.typography.bodyStrong,
                        color = GiveawayTheme.colors.onBackground,
                    )
                    Text(
                        component.license,
                        style = GiveawayTheme.typography.caption,
                        color = GiveawayTheme.colors.onMuted,
                    )
                }
            }
        }
    }
}
