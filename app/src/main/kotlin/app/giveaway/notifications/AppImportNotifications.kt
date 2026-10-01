package app.giveaway.notifications

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import app.giveaway.R
import app.giveaway.core.data.importing.ImportNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import app.giveaway.core.designsystem.R as DesignR

/**
 * The import's ongoing notification (spec: Battery). Generic text with a progress bar, never a title or handle
 * (plan A23). Without the notification permission Android still runs the import; the notification is just hidden.
 */
internal class AppImportNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportNotifications {

    override fun foregroundInfo(giveawayId: Long, imported: Int, expected: Int): ForegroundInfo {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_imports))
                .build(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(DesignR.drawable.ic_task_alt)
            .setContentTitle(context.getString(R.string.notification_importing_title))
            .setProgress(expected, imported.coerceAtMost(expected), expected <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val id = NOTIFICATION_ID_BASE + giveawayId.toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "imports"
        const val NOTIFICATION_ID_BASE = 10_000
    }
}
