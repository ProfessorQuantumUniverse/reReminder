package com.olaf.rereminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.PowerManager
import android.util.Log
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.MessageTemplate
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.displayName
import com.olaf.rereminder.data.producesAlertAt
import com.olaf.rereminder.utils.AlertPlayer
import com.olaf.rereminder.utils.AlertSound
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.SoundChannels
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles every alarm the scheduler arms.
 *
 * The receiver keeps the broadcast open with [goAsync] and holds a short wake lock until the
 * sound has finished, instead of returning straight away and leaving MediaPlayer or the speech
 * engine to run in a process Android is free to kill (or a CPU free to sleep) mid-sentence.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) {
            Log.w(TAG, "Alarm without a reminder id, ignoring")
            return
        }

        val pending = goAsync()
        val wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
        val released = AtomicBoolean(false)
        val done = {
            if (released.compareAndSet(false, true)) {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                pending.finish()
            }
        }

        try {
            when (intent.action) {
                ReminderScheduler.ACTION_REMIND -> onRemind(context, reminderId, intent, done)
                ReminderScheduler.ACTION_SNOOZE_DUE -> onSnoozeDue(context, reminderId, done)
                else -> done()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling alarm for reminder $reminderId", e)
            done()
        }
    }

    private fun onRemind(context: Context, id: Int, intent: Intent, done: () -> Unit) {
        val repository = ReminderRepository.get(context)
        val reminder = repository.takeIf { it.ensureLoaded() }?.get(id)
        if (reminder == null) {
            Log.d(TAG, "Reminder $id no longer exists")
            done()
            return
        }
        val alertAt = intent.getLongExtra(ReminderScheduler.EXTRA_ALERT_AT, reminder.nextTriggerAt)
        val eventAt = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_AT, reminder.nextEventAt)
        val offset = intent.getIntExtra(ReminderScheduler.EXTRA_OFFSET_MINUTES, 0)

        // Re-arm FIRST. An alarm only exists once it has fired, so if anything below throws --
        // a broken ringtone URI, a dead TTS engine, the process being killed mid-notification --
        // the chain must already be secured or this reminder would silently stop forever.
        val rescheduled = runCatching { ReminderScheduler(context).sync() }
            .onFailure { Log.e(TAG, "Could not re-arm reminder $id", it) }
            .isSuccess

        try {
            val preferences = PreferenceHelper(context)
            val now = System.currentTimeMillis()
            when {
                !reminder.enabled || !preferences.isMasterEnabled() -> done()

                // The schedule changed after this alarm was armed; it is no longer wanted.
                alertAt > 0L && !reminder.producesAlertAt(alertAt) -> {
                    Log.d(TAG, "Stale alarm for reminder $id, skipping")
                    done()
                }

                // Delivered far too late (the phone was asleep beyond Doze's reach, or off).
                // A "time to stretch" an hour late is noise, but a dated event is worth a note.
                alertAt > 0L && now - alertAt > LATE_TOLERANCE_MILLIS -> {
                    if (offset == 0 && (reminder.repeat.isCalendar || reminder.alertStyle == AlertStyle.ALARM)) {
                        NotificationHelper.createNotificationChannels(context)
                        NotificationHelper.showMissedNotification(context, reminder, eventAt.takeIf { it > 0L } ?: alertAt)
                    }
                    done()
                }

                else -> alert(context, reminder, eventAt, offset, now, done)
            }
        } finally {
            if (!rescheduled) {
                // Last-ditch retry so a transient failure above doesn't end the loop.
                runCatching { ReminderScheduler(context).sync() }
                    .onFailure { Log.e(TAG, "Re-arm retry failed for reminder $id", it) }
            }
        }
    }

    private fun onSnoozeDue(context: Context, id: Int, done: () -> Unit) {
        val repository = ReminderRepository.get(context)
        val reminder = repository.takeIf { it.ensureLoaded() }?.get(id)
        val scheduler = ReminderScheduler(context)
        scheduler.clearSnooze(id)
        if (reminder == null || !reminder.enabled || !PreferenceHelper(context).isMasterEnabled()) {
            done()
            return
        }
        val now = System.currentTimeMillis()
        alert(context, reminder, eventAt = now, offset = 0, now = now, done = done, forceAlarm = true)
    }

    private fun alert(
        context: Context,
        reminder: Reminder,
        eventAt: Long,
        offset: Int,
        now: Long,
        done: () -> Unit,
        forceAlarm: Boolean = false,
    ) {
        NotificationHelper.createNotificationChannels(context)

        // Early alerts are always plain notifications; only the event itself rings as an alarm.
        val ringsAsAlarm = offset == 0 && (forceAlarm || reminder.alertStyle == AlertStyle.ALARM)
        if (ringsAsAlarm && AlarmService.start(context, reminder.id, eventAt)) {
            done()
            return
        }

        val preferences = PreferenceHelper(context)
        val message = MessageTemplate.render(context, reminder.message, reminder, now)

        // Tones and vibration are played by the system through the channel for that sound;
        // Android 17 would mute the app playing them itself from the background.
        val plan = SoundChannels.plan(reminder, preferences, early = offset > 0)
        val channelId = SoundChannels.channelFor(context, plan.tone, plan.vibration)
        SoundChannels.prune(
            context,
            SoundChannels.channelsInUse(ReminderRepository.get(context).reminders.value, preferences) + channelId,
        )
        val posted = NotificationHelper.showReminderNotification(context, reminder, message, eventAt, offset, channelId)
        if (!posted || !plan.speak) {
            done()
            return
        }

        // Speech is the one sound the app makes itself, so it honours Do Not Disturb and the
        // ringer by hand. It runs in a short foreground service: the speech engine is then
        // treated as in use and allowed to play.
        val spoken = AlertSound.Speech("${reminder.displayName(context)}. $message")
        val (gatedSound, _) = AlertPlayer.gate(context, spoken, vibrate = false)
        if (gatedSound == AlertSound.Silent) {
            done()
            return
        }
        val speechUsage = AlertPlayer.speechUsage(preferences.getSpeechStream())
        val handedOver = AlertSoundService.start(
            context = context,
            reminderId = reminder.id,
            message = message,
            eventAt = eventAt,
            offsetMinutes = offset,
            channelId = channelId,
            sound = gatedSound,
            speechUsage = speechUsage,
        )
        if (handedOver) {
            done()
            return
        }
        AlertPlayer(
            context = context,
            usage = AudioAttributes.USAGE_NOTIFICATION,
            loop = false,
            speechUsage = speechUsage,
        ).play(sound = gatedSound, vibration = null, onFinished = done)
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        const val WAKE_LOCK_TAG = "reReminder:alert"
        const val WAKE_LOCK_TIMEOUT_MILLIS = 40_000L
        const val LATE_TOLERANCE_MILLIS = 30 * 60_000L
    }
}
