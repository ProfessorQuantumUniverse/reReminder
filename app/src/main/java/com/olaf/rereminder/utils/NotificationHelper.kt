package com.olaf.rereminder.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.olaf.rereminder.MainActivity
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.displayName

/**
 * Posts reminder notifications.
 *
 * The channels created here are silent. A reminder that should make a sound posts to the
 * channel for that sound instead (see [SoundChannels]); alarms and spoken reminders play their
 * own audio (see [AlertPlayer]).
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_REMINDERS = "reminders_v2"
    const val CHANNEL_ALARMS = "alarms_v2"
    const val CHANNEL_MISSED = "missed_v2"

    /** 3.x channel whose sound was baked in; removed so it can't play a second sound. */
    private const val LEGACY_CHANNEL = "reminder_channel"

    /** Keeps per-reminder notification ids clear of any other id space. */
    private const val NOTIFICATION_ID_BASE = 1000
    private const val MISSED_ID_BASE = 500_000
    const val ALARM_NOTIFICATION_ID = 999

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.deleteNotificationChannel(LEGACY_CHANNEL)
            manager.createNotificationChannels(
                listOf(
                    silentChannel(
                        CHANNEL_REMINDERS,
                        context.getString(R.string.notification_channel_name),
                        context.getString(R.string.notification_channel_description),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                    silentChannel(
                        CHANNEL_ALARMS,
                        context.getString(R.string.channel_alarms_name),
                        context.getString(R.string.channel_alarms_description),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                    silentChannel(
                        CHANNEL_MISSED,
                        context.getString(R.string.channel_missed_name),
                        context.getString(R.string.channel_missed_description),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            )
        }.onFailure { Log.e(TAG, "Could not create notification channels", it) }
    }

    private fun silentChannel(id: String, name: String, description: String, importance: Int) =
        NotificationChannel(id, name, importance).apply {
            this.description = description
            setSound(null, null)
            enableVibration(false)
            enableLights(true)
        }

    /**
     * Whether a notification on [channelId] will actually be shown. When it won't, the app keeps
     * quiet too — a sound with nothing on screen to explain it is worse than no reminder.
     */
    fun canPost(context: Context, channelId: String = CHANNEL_REMINDERS): Boolean {
        if (!hasNotificationPermission(context)) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Posts the alert for [reminder]. Each timer gets its own notification id so several timers
     * can be visible at once instead of overwriting each other.
     */
    fun showReminderNotification(
        context: Context,
        reminder: Reminder,
        message: String,
        eventAt: Long,
        offsetMinutes: Int,
        channelId: String = CHANNEL_REMINDERS,
    ): Boolean {
        if (!canPost(context, channelId)) {
            Log.w(TAG, "Notifications are off, reminder ${reminder.id} not shown")
            return false
        }
        return notify(
            context,
            reminderNotificationId(reminder.id),
            buildReminderNotification(context, reminder, message, eventAt, offsetMinutes, channelId),
        )
    }

    fun reminderNotificationId(reminderId: Int): Int = NOTIFICATION_ID_BASE + reminderId

    /**
     * The reminder's notification on [channelId], whose sound and vibration the system plays.
     * [onlyAlertOnce] is for re-posting the same notification (as a foreground service's),
     * which must not ring a second time.
     */
    fun buildReminderNotification(
        context: Context,
        reminder: Reminder,
        message: String,
        eventAt: Long,
        offsetMinutes: Int,
        channelId: String = CHANNEL_REMINDERS,
        onlyAlertOnce: Boolean = false,
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.displayName(context))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, reminder.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)

        if (offsetMinutes > 0 && eventAt > 0L) {
            // An early alert is about something still ahead, so it says what and when.
            builder.setSubText(
                context.getString(
                    R.string.notification_early,
                    TimeLabels.offset(context, offsetMinutes),
                    TimeLabels.clock(eventAt),
                )
            )
            builder.setWhen(eventAt).setShowWhen(true)
        }
        return builder.build()
    }

    /** A quiet note that an alert could not ring — the phone was off, or nobody stopped the alarm. */
    fun showMissedNotification(context: Context, reminder: Reminder, eventAt: Long) {
        if (!canPost(context, CHANNEL_MISSED)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_missed_title, reminder.displayName(context)))
            .setContentText(context.getString(R.string.notification_missed_text, TimeLabels.moment(context, eventAt)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, reminder.id))
            .setWhen(eventAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        notify(context, MISSED_ID_BASE + reminder.id, notification)
    }

    fun cancelReminderNotification(context: Context, reminderId: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + reminderId)
        NotificationManagerCompat.from(context).cancel(MISSED_ID_BASE + reminderId)
    }

    fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification): Boolean =
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception showing notification", e)
            false
        }

    /** Android 14+ only lets the alarm take over the lock screen once the user allowed it. */
    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
