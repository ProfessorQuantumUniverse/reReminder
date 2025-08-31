package com.olaf.rereminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.olaf.rereminder.service.AlarmReceiver
import com.olaf.rereminder.utils.PreferenceHelper

class AlarmScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferenceHelper = PreferenceHelper(context)
    
    fun scheduleRepeatingAlarm() {
        val intervalMinutes = preferenceHelper.getReminderInterval()
        val intervalMillis = intervalMinutes * 60 * 1000L
        
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = System.currentTimeMillis() + intervalMillis
        
        // Speichere die geplante Zeit
        preferenceHelper.setNextReminderTime(triggerTime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        // Lösche die gespeicherte Zeit
        preferenceHelper.setNextReminderTime(0)
    }

    fun getNextReminderTime(): Long {
        return preferenceHelper.getNextReminderTime()
    }
    
    companion object {
        private const val ALARM_REQUEST_CODE = 1000
    }
}