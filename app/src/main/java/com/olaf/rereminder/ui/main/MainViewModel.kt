package com.olaf.rereminder.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.olaf.rereminder.AlarmScheduler
import com.olaf.rereminder.utils.PreferenceHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            minutes < 60 -> "$minutes Minutes"
            minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes / 60 > 1) "s" else ""}"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "$hours hour${if (hours > 1) "s" else ""} $mins Minutes"
            }
        }
    }

    init {
        _isReminderEnabled.value = preferenceHelper.isReminderEnabled()
        _reminderInterval.value = preferenceHelper.getReminderInterval()
        updateNextReminderTime()

        // Starte automatische Aktualisierung der nächsten Erinnerungszeit
        startNextReminderTimeUpdater()
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
            val nextTime = alarmScheduler.getNextReminderTime()
            if (nextTime > 0 && nextTime > System.currentTimeMillis()) {
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                _nextReminderTime.value = dateFormat.format(Date(nextTime))
            } else {
                _nextReminderTime.value = ""
            }
        } else {
            _nextReminderTime.value = ""
        }
    }

    private fun startNextReminderTimeUpdater() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // Aktualisiere alle 30 Sekunden
                if (_isReminderEnabled.value == true) {
                    updateNextReminderTime()
                }
            }
        }
    }

    fun refreshNextReminderTime() {
        updateNextReminderTime()
    }
}