package app.giveaway.feature.draw

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.media.CertificateContent
import app.giveaway.core.media.CertificateContent.Kind
import java.io.File
import java.util.Locale
import app.giveaway.core.designsystem.R as DesignR

/** The certificate as S15 shows it: the same words as the PDF (spec: S15 content). */
@Composable
internal fun CertificateCard(content: CertificateContent) {
    val colors = GiveawayTheme.colors
    GiveawayCard(modifier = Modifier.fillMaxWidth().testTag("certificate:card")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        content.heading,
                        style = GiveawayTheme.typography.title,
                        color = colors.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(content.title, style = GiveawayTheme.typography.bodyStrong, color = colors.onBackground)
                }
                Box(
                    modifier = Modifier.size(44.dp).background(colors.accentSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(DesignR.drawable.ic_task_alt), contentDescription = null, tint = colors.accent)
                }
            }
            content.sections.forEach { section ->
                HorizontalDivider(color = colors.outline)
                Text(
                    section.heading.uppercase(Locale.ROOT),
                    style = GiveawayTheme.typography.overline,
                    color = colors.accentOnSoft,
                )
                section.rows.forEach { CertificateRow(it) }
            }
            Text(content.footer, style = GiveawayTheme.typography.caption, color = colors.onMuted)
        }
    }
}

@Composable
private fun CertificateRow(row: CertificateContent.Row) {
    val colors = GiveawayTheme.colors
    if (row.kind == Kind.WARNING) {
        Text(
            row.value,
            style = GiveawayTheme.typography.caption,
            color = colors.accentOnSoft,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, RoundedCornerShape(12.dp))
                .padding(10.dp),
        )
        return
    }
    Column {
        row.label?.let { Text(it, style = GiveawayTheme.typography.caption, color = colors.onMuted) }
        val style = when (row.kind) {
            Kind.CODE -> GiveawayTheme.typography.code
            Kind.STRONG -> GiveawayTheme.typography.bodyStrong
            else -> GiveawayTheme.typography.body
        }
        Text(row.value, style = style, color = colors.onBackground)
    }
}

/** Asked on S14 before the certificate fixes the result (plan A28). */
@Composable
internal fun MakeCertificateDialog(pending: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.certificate_make_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.certificate_make_body))
                if (pending > 0) {
                    Text(pluralStringResource(R.plurals.certificate_make_pending, pending, formatCount(pending)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("certificate:make_confirm")) {
                Text(stringResource(R.string.certificate_make_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.certificate_make_cancel)) } },
    )
}

/**
 * Opens Instagram's Story composer with the certificate image (spec: S15), or the share sheet when Instagram isn't
 * installed. Only the certificates folder is shared, through FileProvider.
 */
internal fun shareToStory(context: Context, image: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.certificates", image)
    val story = Intent(STORY_ACTION)
        .setDataAndType(uri, "image/png")
        .setPackage(INSTAGRAM)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra("source_application", BuildConfig.META_APP_ID)
    if (story.resolveActivity(context.packageManager) != null) {
        context.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(story)
    } else {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.certificate_share_title)))
    }
}

private const val STORY_ACTION = "com.instagram.share.ADD_TO_STORY"
private const val INSTAGRAM = "com.instagram.android"
