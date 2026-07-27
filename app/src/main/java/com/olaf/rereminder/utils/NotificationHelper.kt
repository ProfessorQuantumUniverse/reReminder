package com.olaf.rereminder.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.olaf.rereminder.MainActivity
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.displayName

object NotificationHelper {

    private const val CHANNEL_ID = "reminder_channel"

    /** Keeps per-reminder notification ids clear of any other id space. */
    private const val NOTIFICATION_ID_BASE = 1000

    fun createNotificationChannel(context: Context) {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableLights(true)

                if (hasVibrationPermission(context)) {
                    enableVibration(true)
                }

                try {
                    val soundUri = PreferenceHelper(context).getSelectedRingtone()
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()

                    setSound(soundUri, audioAttributes)
                } catch (e: SecurityException) {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                }
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Security exception creating channel", e)
        }
    }

    /**
     * Posts the alert for [reminder]. Each timer gets its own notification id so several timers
     * can be visible at once instead of overwriting each other.
     */
    fun showReminderNotification(context: Context, reminder: Reminder, message: String) {
        try {
            if (!hasNotificationPermission(context)) {
                android.util.Log.w("NotificationHelper", "No notification permission")
                return
            }

            val preferenceHelper = PreferenceHelper(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                reminder.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(reminder.displayName(context))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (reminder.soundEnabled && preferenceHelper.isSoundEnabled()) {
                try {
                    val soundUri = preferenceHelper.getSelectedRingtone()
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    builder.setSound(soundUri)
                } catch (e: SecurityException) {
                    android.util.Log.w("NotificationHelper", "No permission for custom sound", e)
                }
            }

            if (reminder.vibrationEnabled &&
                preferenceHelper.isVibrationEnabled() &&
                hasVibrationPermission(context)
            ) {
                try {
                    builder.setVibrate(
                        vibrationPatternForNotification(preferenceHelper.getVibrationPattern())
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("NotificationHelper", "No permission for vibration", e)
                }
            }

            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + reminder.id, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Security exception showing notification", e)
        }
    }

    fun cancelReminderNotification(context: Context, reminderId: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + reminderId)
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    private fun hasVibrationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.VIBRATE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun vibrationPatternForNotification(pattern: Int): LongArray = when (pattern) {
        0 -> longArrayOf(0, 200)
        2 -> longArrayOf(0, 1000)
        3 -> longArrayOf(0, 300, 100, 300, 100, 300, 100, 300)
        else -> longArrayOf(0, 500, 100, 500)
    }
}
