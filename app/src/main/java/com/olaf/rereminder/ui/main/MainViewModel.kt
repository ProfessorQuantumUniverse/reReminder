package com.olaf.rereminder.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
    
    init {
        _isReminderEnabled.value = preferenceHelper.isReminderEnabled()
        updateNextReminderTime()
    }
    
    fun setReminderEnabled(enabled: Boolean) {
        preferenceHelper.setReminderEnabled(enabled)
        _isReminderEnabled.value = enabled
        if (enabled) {
            updateNextReminderTime()
        } else {
            _nextReminderTime.value = ""
        }
    }
    
    fun scheduleReminder(context: Context) {
        if (_isReminderEnabled.value == true) {
            alarmScheduler.scheduleRepeatingAlarm()
            updateNextReminderTime()
        }
    }
    
    fun cancelReminder(context: Context) {
        alarmScheduler.cancelAlarm()
        _nextReminderTime.value = ""
    }
    
    private fun updateNextReminderTime() {
        if (_isReminderEnabled.value == true) {
            val intervalMinutes = preferenceHelper.getReminderInterval()
            val nextTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            _nextReminderTime.value = dateFormat.format(Date(nextTime))
        } else {
            _nextReminderTime.value = ""
        }
    }
}