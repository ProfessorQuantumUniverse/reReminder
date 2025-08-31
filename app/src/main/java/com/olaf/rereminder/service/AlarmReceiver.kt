package com.olaf.rereminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.olaf.rereminder.AlarmScheduler
import com.olaf.rereminder.R
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.SoundHelper
import com.olaf.rereminder.utils.TextToSpeechHelper
import com.olaf.rereminder.utils.VibrationHelper

class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "AlarmReceiver triggered")

        try {
            val preferenceHelper = PreferenceHelper(context)

            if (preferenceHelper.isReminderEnabled()) {
                Log.d(TAG, "Reminder is enabled, showing notification and alerts")

                // Stelle sicher, dass der Notification Channel existiert
                NotificationHelper.createNotificationChannel(context)

                // Benachrichtigung anzeigen
                NotificationHelper.showReminderNotification(context)

                // Vibration ausführen wenn aktiviert
                if (preferenceHelper.isVibrationEnabled()) {
                    Log.d(TAG, "Vibration enabled, triggering vibration")
                    VibrationHelper.vibrate(context, preferenceHelper.getVibrationPattern())
                } else {
                    Log.d(TAG, "Vibration disabled")
                }

                // Sound abspielen oder Text vorlesen, wenn aktiviert
                if (preferenceHelper.isSoundEnabled()) {
                    when (preferenceHelper.getNotificationSoundType()) {
                        PreferenceHelper.SOUND_TYPE_RINGTONE -> {
                            Log.d(TAG, "Sound type is Ringtone, playing ringtone")
                            SoundHelper.playRingtone(context, preferenceHelper.getSelectedRingtone())
                        }
                        PreferenceHelper.SOUND_TYPE_TTS -> {
                            Log.d(TAG, "Sound type is TTS, speaking notification text")
                            val title = preferenceHelper.getNotificationTitle().ifEmpty { context.getString(R.string.reminder_notification_title) }
                            val text = preferenceHelper.getNotificationText().ifEmpty { context.getString(R.string.reminder_notification_text) }
                            TextToSpeechHelper.initialize(context)
                            TextToSpeechHelper.speak("$title. $text")
                        }
                    }
                } else {
                    Log.d(TAG, "Sound disabled")
                }

                // Nächsten Alarm planen
                val alarmScheduler = AlarmScheduler(context)
                alarmScheduler.scheduleRepeatingAlarm()

                Log.d(TAG, "Next alarm scheduled")
            } else {
                Log.d(TAG, "Reminder is disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in AlarmReceiver", e)
        }
    }
}