package com.olaf.rereminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.olaf.rereminder.data.Alert
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.nextAlertAfter
import com.olaf.rereminder.utils.PreferenceHelper

/**
 * Owns one exact alarm per enabled reminder (plus one for a snoozed alarm). The reminder id
 * doubles as the PendingIntent request code, which keeps each timer's alarm independent.
 *
 * Every reminder fires on a fixed grid (see `Schedule.kt`), so working out the next alert is
 * deterministic: syncing twice gives the same answer, and syncing never restarts a countdown.
 * Only [restart] moves a timer's grid — when it is switched on, or its timing was edited.
 */
class ReminderScheduler(context: Context) {

    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val repository = ReminderRepository.get(this.context)

    /**
     * Brings the alarms in line with the stored reminders and writes the resulting trigger times
     * back. Reminders in [restartIds] start a fresh countdown from now.
     */
    fun sync(restartIds: Set<Int> = emptySet()) {
        if (!repository.ensureLoaded()) return
        val now = System.currentTimeMillis()
        val masterEnabled = PreferenceHelper(context).isMasterEnabled()

        repository.mutate { reminders ->
            reminders.map { reminder ->
                when {
                    // The master switch silences everything without clearing per-timer state.
                    !masterEnabled || !reminder.enabled -> {
                        cancelAlarms(reminder.id)
                        reminder.copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = 0L)
                    }

                    else -> {
                        val restarted = reminder.id in restartIds || reminder.anchorMillis <= 0L
                        arm(if (restarted) reminder.copy(anchorMillis = now) else reminder, now)
                    }
                }
            }
        }
    }

    /** Restarts one timer's countdown — after it was switched on or its timing changed. */
    fun restart(id: Int) = sync(restartIds = setOf(id))

    /** Rings an alarm-style reminder again in [minutes]. */
    fun snooze(id: Int, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        repository.update(id) { it.copy(snoozeUntil = until) }
        sync()
    }

    fun clearSnooze(id: Int) {
        cancelSnoozeAlarm(id)
        repository.update(id) { it.copy(snoozeUntil = 0L) }
    }

    fun cancel(id: Int) {
        cancelAlarms(id)
        repository.update(id) { it.copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = 0L) }
    }

    private fun arm(reminder: Reminder, now: Long): Reminder {
        val snoozeUntil = if (reminder.snoozeUntil > now) {
            setAlarm(snoozeIntent(reminder.id), reminder.snoozeUntil, reminder.id)
            reminder.snoozeUntil
        } else {
            // A snooze that came due while the phone was off is not worth ringing late.
            cancelSnoozeAlarm(reminder.id)
            0L
        }

        val alert = reminder.nextAlertAfter(now) ?: run {
            // No weekday selected, or no start moment for a calendar reminder: nothing can fire.
            alarmManager.cancel(remindIntent(reminder.id, null))
            return reminder.copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = snoozeUntil)
        }

        setAlarm(remindIntent(reminder.id, alert), alert.alertAt, reminder.id)
        return reminder.copy(
            nextTriggerAt = alert.alertAt,
            nextEventAt = alert.eventAt,
            snoozeUntil = snoozeUntil,
        )
    }

    /**
     * True when the system will let us post exact alarms. Android 12+ can revoke this at any
     * time, so it is checked before every scheduling call rather than cached.
     */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun setAlarm(pendingIntent: PendingIntent, triggerAtMillis: Long, id: Int) {
        try {
            if (canScheduleExactAlarms()) {
                // AllowWhileIdle so Doze cannot swallow the reminder.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                // Permission revoked by the user: still fire, just without exact timing,
                // rather than dropping the reminder entirely.
                Log.w(TAG, "Exact alarms not permitted, scheduling reminder $id inexactly")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied for reminder $id, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarms(id: Int) {
        alarmManager.cancel(remindIntent(id, null))
        cancelSnoozeAlarm(id)
    }

    private fun cancelSnoozeAlarm(id: Int) = alarmManager.cancel(snoozeIntent(id))

    private fun remindIntent(id: Int, alert: Alert?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            // The data URI keeps the intents distinct; extras alone are ignored by filterEquals.
            data = "rereminder://timer/$id".toUri()
            putExtra(EXTRA_REMINDER_ID, id)
            if (alert != null) {
                putExtra(EXTRA_ALERT_AT, alert.alertAt)
                putExtra(EXTRA_EVENT_AT, alert.eventAt)
                putExtra(EXTRA_OFFSET_MINUTES, alert.offsetMinutes)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeIntent(id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_DUE
            data = "rereminder://snooze/$id".toUri()
            putExtra(EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_BASE + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val SNOOZE_REQUEST_BASE = 1_000_000

        const val ACTION_REMIND = "com.olaf.rereminder.action.REMIND"
        const val ACTION_SNOOZE_DUE = "com.olaf.rereminder.action.SNOOZE_DUE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_ALERT_AT = "alert_at"
        const val EXTRA_EVENT_AT = "event_at"
        const val EXTRA_OFFSET_MINUTES = "offset_minutes"
    }
}
