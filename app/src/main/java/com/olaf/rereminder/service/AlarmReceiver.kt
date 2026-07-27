package com.olaf.rereminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.olaf.rereminder.data.MessageTemplate
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.displayName
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.SoundHelper
import com.olaf.rereminder.utils.TextToSpeechHelper
import com.olaf.rereminder.utils.VibrationHelper

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) {
            Log.w(TAG, "Alarm without a reminder id, ignoring")
            return
        }

        val repository = ReminderRepository.get(context)
        val reminder = repository.get(reminderId)
        if (reminder == null) {
            Log.d(TAG, "Reminder $reminderId no longer exists")
            return
        }

        // Re-arm FIRST. An alarm only exists once it has fired, so if anything below throws --
        // a broken ringtone URI, a dead TTS engine, the process being killed mid-notification --
        // the chain must already be secured or this reminder would silently stop forever.
        val rescheduled = runCatching { ReminderScheduler(context).reschedule(reminderId) }
            .onFailure { Log.e(TAG, "Could not re-arm reminder $reminderId", it) }
            .isSuccess

        if (!reminder.enabled) {
            Log.d(TAG, "Reminder $reminderId is disabled")
            return
        }
        if (!PreferenceHelper(context).isMasterEnabled()) {
            Log.d(TAG, "All reminders are paused")
            return
        }

        val now = System.currentTimeMillis()
        // A window may have closed between scheduling and firing (e.g. the device slept through
        // the end of the work day), so re-check before alerting.
        if (!reminder.isActiveAt(now)) {
            Log.d(TAG, "Reminder $reminderId fired outside its schedule, skipping the alert")
            return
        }

        try {
            val preferences = PreferenceHelper(context)
            val message = MessageTemplate.render(context, reminder.message, reminder, now)

            NotificationHelper.createNotificationChannel(context)
            NotificationHelper.showReminderNotification(context, reminder, message)

            if (reminder.vibrationEnabled && preferences.isVibrationEnabled()) {
                VibrationHelper.vibrate(context, preferences.getVibrationPattern())
            }

            if (reminder.soundEnabled && preferences.isSoundEnabled()) {
                when (preferences.getNotificationSoundType()) {
                    PreferenceHelper.SOUND_TYPE_TTS -> {
                        val spokenName = reminder.displayName(context)
                        TextToSpeechHelper.initialize(context)
                        TextToSpeechHelper.speak("$spokenName. $message")
                    }

                    else -> SoundHelper.playRingtone(context, preferences.getSelectedRingtone())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error alerting for reminder $reminderId", e)
        }

        if (!rescheduled) {
            // Last-ditch retry so a transient failure above doesn't end the loop.
            runCatching { ReminderScheduler(context).reschedule(reminderId) }
                .onFailure { Log.e(TAG, "Re-arm retry failed for reminder $reminderId", it) }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
