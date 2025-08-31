package com.olaf.rereminder.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.olaf.rereminder.MainActivity
import com.olaf.rereminder.R

object NotificationHelper {
    
    private const val CHANNEL_ID = "reminder_channel"
    private const val NOTIFICATION_ID = 1001
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val preferenceHelper = PreferenceHelper(context)

                val name = "Erinnerungen"
                val descriptionText = "Kanal für Erinnerungsbenachrichtigungen"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableLights(true)

                    // Vibration nur aktivieren wenn Berechtigung vorhanden
                    if (hasVibrationPermission(context)) {
                        enableVibration(true)
                    }

                    // Sound-Einstellungen nur wenn verfügbar
                    try {
                        val soundUri = preferenceHelper.getSelectedRingtone()
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()

                        setSound(soundUri, audioAttributes)
                    } catch (e: SecurityException) {
                        // Fallback auf Standard-Sound wenn Berechtigung fehlt
                        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                    }
                }

                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: SecurityException) {
                // Log error but continue without advanced features
                android.util.Log.e("NotificationHelper", "Security exception creating channel", e)
            }
        }
    }

    fun showReminderNotification(context: Context) {
        try {
            // Prüfe Notification-Berechtigung
            if (!hasNotificationPermission(context)) {
                android.util.Log.w("NotificationHelper", "No notification permission")
                return
            }

            val preferenceHelper = PreferenceHelper(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Erinnerung")
                .setContentText("Zeit für eine Pause!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            // Sound explizit setzen wenn aktiviert und berechtigt
            if (preferenceHelper.isSoundEnabled()) {
                try {
                    val soundUri = preferenceHelper.getSelectedRingtone()
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    builder.setSound(soundUri)
                } catch (e: SecurityException) {
                    android.util.Log.w("NotificationHelper", "No permission for custom sound", e)
                }
            }

            // Vibration explizit setzen wenn aktiviert und berechtigt
            if (preferenceHelper.isVibrationEnabled() && hasVibrationPermission(context)) {
                try {
                    val vibrationPattern = getVibrationPatternForNotification(preferenceHelper.getVibrationPattern())
                    builder.setVibrate(vibrationPattern)
                } catch (e: SecurityException) {
                    android.util.Log.w("NotificationHelper", "No permission for vibration", e)
                }
            }

            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Security exception showing notification", e)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun hasVibrationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getVibrationPatternForChannel(pattern: Int): LongArray {
        return when (pattern) {
            0 -> longArrayOf(0, 200) // Kurz
            1 -> longArrayOf(0, 500) // Standard
            2 -> longArrayOf(0, 1000) // Lang
            3 -> longArrayOf(0, 300, 100, 300, 100, 300) // Pulsierend
            else -> longArrayOf(0, 500) // Standard
        }
    }

    private fun getVibrationPatternForNotification(pattern: Int): LongArray {
        return when (pattern) {
            0 -> longArrayOf(0, 200) // Kurz
            1 -> longArrayOf(0, 500, 100, 500) // Standard mit Wiederholung
            2 -> longArrayOf(0, 1000) // Lang
            3 -> longArrayOf(0, 300, 100, 300, 100, 300, 100, 300) // Pulsierend länger
            else -> longArrayOf(0, 500, 100, 500) // Standard mit Wiederholung
        }
    }
}