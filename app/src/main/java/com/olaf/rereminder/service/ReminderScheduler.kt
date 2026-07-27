package com.olaf.rereminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.utils.PreferenceHelper

/**
 * Owns one exact alarm per enabled reminder. The reminder id doubles as the PendingIntent request
 * code, which keeps each timer's alarm independent of the others.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val repository = ReminderRepository.get(context)

    /**
     * Brings the alarms in line with the stored reminders and writes the resulting trigger times
     * back. Timers keep their pending alarm unless their id is in [recomputeIds] — so toggling one
     * timer never restarts the countdown of another.
     */
    fun sync(recomputeIds: Set<Int> = emptySet()) {
        val now = System.currentTimeMillis()
        val masterEnabled = PreferenceHelper(context).isMasterEnabled()

        val updated = repository.reminders.value.map { reminder ->
            when {
                // The master switch silences everything without clearing per-timer state.
                !masterEnabled || !reminder.enabled -> {
                    cancelAlarm(reminder.id)
                    reminder.copy(nextTriggerAt = 0L)
                }

                else -> arm(reminder, now, recompute = reminder.id in recomputeIds)
            }
        }
        repository.replaceAll(updated)
    }

    /** Recomputes just this reminder — used after its interval or schedule changed. */
    fun reschedule(id: Int) = sync(recomputeIds = setOf(id))

    fun cancel(id: Int) {
        cancelAlarm(id)
        repository.get(id)?.let { repository.update(it.copy(nextTriggerAt = 0L)) }
    }

    private fun arm(reminder: Reminder, now: Long, recompute: Boolean): Reminder {
        val keepExisting = !recompute &&
            reminder.nextTriggerAt > now &&
            reminder.nextTriggerAt <= now + reminder.intervalMillis

        val triggerAt = if (keepExisting) {
            reminder.nextTriggerAt
        } else {
            reminder.nextTriggerAfter(now) ?: run {
                // No weekday selected — nothing can ever fire.
                cancelAlarm(reminder.id)
                return reminder.copy(nextTriggerAt = 0L)
            }
        }

        setExactAlarm(reminder.id, triggerAt)
        return reminder.copy(nextTriggerAt = triggerAt)
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

    private fun setExactAlarm(id: Int, triggerAtMillis: Long) {
        val pendingIntent = pendingIntent(id)
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
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied for reminder $id, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun cancelAlarm(id: Int) = alarmManager.cancel(pendingIntent(id))

    private fun pendingIntent(id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            // The data URI keeps the intents distinct; extras alone are ignored by filterEquals.
            data = "rereminder://timer/$id".toUri()
            putExtra(EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val ACTION_REMIND = "com.olaf.rereminder.action.REMIND"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
