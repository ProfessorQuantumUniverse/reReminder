package com.olaf.rereminder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.olaf.rereminder.utils.NotificationHelper

/**
 * Re-arms every enabled reminder whenever the system drops or invalidates pending alarms:
 * a reboot, an app update, or a clock/timezone change that moves every schedule window.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) {
            return
        }

        try {
            NotificationHelper.createNotificationChannel(context)

            val scheduler = ReminderScheduler(context)
            if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                // Every stored trigger time was computed against the old clock, so discard them.
                val allIds = com.olaf.rereminder.data.ReminderRepository.get(context)
                    .reminders.value
                    .map { it.id }
                    .toSet()
                scheduler.sync(recomputeIds = allIds)
            } else {
                // Keeps trigger times that are still in the future (short reboot) and
                // recomputes the ones that elapsed while the device was off.
                scheduler.sync()
            }
            Log.d(TAG, "Rescheduled reminders after $action")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule reminders after $action", e)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
