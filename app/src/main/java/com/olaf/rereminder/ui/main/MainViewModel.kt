package com.olaf.rereminder.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.olaf.rereminder.AlarmScheduler
import com.olaf.rereminder.utils.PreferenceHelper
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceHelper = PreferenceHelper(application)
    private val alarmScheduler = AlarmScheduler(application)

    private val _isReminderEnabled = MutableLiveData<Boolean>()
    val isReminderEnabled: LiveData<Boolean> = _isReminderEnabled

    private val _nextReminderTime = MutableLiveData<String>()
    val nextReminderTime: LiveData<String> = _nextReminderTime

    private val _reminderInterval = MutableLiveData<Int>()
    val intervalText: LiveData<String> = _reminderInterval.map { minutes ->
        when {
            minutes < 1 -> "Ungültiges Intervall"
            minutes < 60 -> "$minutes Minuten"
            minutes % 60 == 0 -> "${minutes / 60} Stunde${if (minutes / 60 > 1) "n" else ""}"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "$hours Stunde${if (hours > 1) "n" else ""} $mins Minuten"
            }
        }
    }

    init {
        _isReminderEnabled.value = preferenceHelper.isReminderEnabled()
        _reminderInterval.value = preferenceHelper.getReminderInterval()
        updateNextReminderTime()
    }

    fun setReminderEnabled(enabled: Boolean) {
        preferenceHelper.setReminderEnabled(enabled)
        _isReminderEnabled.value = enabled
        if (enabled) {
            scheduleReminder()
        } else {
            cancelReminder()
            _nextReminderTime.value = ""
        }
    }

    fun scheduleReminder() {
        if (_isReminderEnabled.value == true) {
            alarmScheduler.scheduleRepeatingAlarm()
            updateNextReminderTime()
        }
    }

    fun cancelReminder() {
        alarmScheduler.cancelAlarm()
        _nextReminderTime.value = ""
    }

    fun getReminderInterval(): Int {
        return _reminderInterval.value ?: preferenceHelper.getReminderInterval()
    }

    fun setReminderInterval(hours: Int, minutes: Int) {
        val totalMinutes = hours * 60 + minutes
        if (totalMinutes > 0) {
            preferenceHelper.setReminderInterval(totalMinutes)
            _reminderInterval.value = totalMinutes
            if (_isReminderEnabled.value == true) {
                scheduleReminder()
            }
        }
    }

    private fun updateNextReminderTime() {
        if (_isReminderEnabled.value == true) {
            val intervalMinutes = preferenceHelper.getReminderInterval()
            if (intervalMinutes > 0) {
                val nextTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                _nextReminderTime.value = dateFormat.format(Date(nextTime))
            } else {
                _nextReminderTime.value = "Ungültiges Intervall"
            }
        } else {
            _nextReminderTime.value = ""
        }
    }
}