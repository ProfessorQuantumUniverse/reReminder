package com.olaf.rereminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper

/**
 * Re-arms every enabled reminder whenever the system drops or invalidates pending alarms:
 * a reboot (already before the phone is unlocked), an app update, a clock or timezone change, or
 * exact alarms being allowed again.
 *
 * The schedule is a fixed grid, so re-arming simply picks the next point on it — nothing drifts
 * and a timezone change moves windows and calendar reminders to the new local time.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) return

        try {
            val repository = ReminderRepository.get(context)
            if (!repository.ensureLoaded()) {
                // Locked after a reboot and the data has not moved to device storage yet; the
                // unlocked BOOT_COMPLETED that follows will do it.
                Log.i(TAG, "Storage not ready during $action")
                return
            }
            NotificationHelper.createNotificationChannels(context)

            if (action in BOOT_ACTIONS) noteMissedWhileOff(context, repository)

            ReminderScheduler(context).sync()
            Log.d(TAG, "Rescheduled reminders after $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule reminders after $action", e)
        }
    }

    /**
     * An alarm that came due while the phone was off never fired. For dated events and alarms
     * that is worth a quiet "missed" note; an every-20-minutes nudge is not.
     */
    private fun noteMissedWhileOff(context: Context, repository: ReminderRepository) {
        if (!PreferenceHelper(context).isMasterEnabled()) return
        val now = System.currentTimeMillis()
        repository.reminders.value
            .filter { reminder ->
                reminder.enabled &&
                    reminder.nextTriggerAt in (now - MISSED_WINDOW_MILLIS) until now &&
                    reminder.nextEventAt == reminder.nextTriggerAt &&
                    (reminder.repeat.isCalendar || reminder.alertStyle == AlertStyle.ALARM)
            }
            .forEach { NotificationHelper.showMissedNotification(context, it, it.nextEventAt) }
    }

    private companion object {
        const val TAG = "BootReceiver"
        const val MISSED_WINDOW_MILLIS = 12 * 60 * 60_000L
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"

        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED; only sent on 12+. */
        const val EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val BOOT_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            QUICKBOOT_POWERON,
            HTC_QUICKBOOT_POWERON,
        )

        val HANDLED_ACTIONS = BOOT_ACTIONS + setOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            EXACT_ALARM_PERMISSION_CHANGED,
        )
    }
}
