package app.giveaway.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.giveaway.MainActivity
import app.giveaway.R
import app.giveaway.core.data.work.DeadlineNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import app.giveaway.core.designsystem.R as DesignR

/**
 * "Entries have closed" reminder (spec: S8). The text is generic, never a giveaway title or handle, because
 * notifications show on the lock screen (spec: Security, plan A23). Tapping opens the app, behind the app lock.
 */
internal class AppDeadlineNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeadlineNotifier {

    override fun entriesClosed(giveawayId: Long) {
        val denied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (denied) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_deadlines))
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(DesignR.drawable.ic_task_alt)
            .setContentTitle(context.getString(R.string.notification_entries_closed_title))
            .setContentText(context.getString(R.string.notification_entries_closed_text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per giveaway, so two deadlines don't overwrite each other.
        manager.notify(NOTIFICATION_TAG, giveawayId.toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "deadlines"
        const val NOTIFICATION_TAG = "deadline"
    }
}
