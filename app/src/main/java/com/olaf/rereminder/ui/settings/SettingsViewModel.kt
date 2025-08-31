package com.olaf.rereminder.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.olaf.rereminder.utils.PreferenceHelper

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceHelper = PreferenceHelper(application)

    private val _reminderInterval = MutableLiveData<Int>()
    val intervalText: LiveData<String> = _reminderInterval.map { minutes ->
        when {
            minutes < 60 -> "$minutes Minuten"
            minutes % 60 == 0 -> "${minutes / 60} Stunde${if (minutes / 60 > 1) "n" else ""}"
            else -> {
                val hours = minutes / 60
                val mins = minutes % 60
                "$hours Stunde${if (hours > 1) "n" else ""} $mins Minuten"
            }
        }
    }

    private val _selectedRingtone = MutableLiveData<Uri?>()
    val selectedRingtone: LiveData<Uri?> = _selectedRingtone

    private val _isSoundEnabled = MutableLiveData<Boolean>()
    val isSoundEnabled: LiveData<Boolean> = _isSoundEnabled

    private val _isVibrationEnabled = MutableLiveData<Boolean>()
    val isVibrationEnabled: LiveData<Boolean> = _isVibrationEnabled

    private val _selectedVibrationPattern = MutableLiveData<Int>()
    val selectedVibrationPattern: LiveData<Int> = _selectedVibrationPattern

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _reminderInterval.value = preferenceHelper.getReminderInterval()
        _selectedRingtone.value = preferenceHelper.getSelectedRingtone()
        _isSoundEnabled.value = preferenceHelper.isSoundEnabled()
        _isVibrationEnabled.value = preferenceHelper.isVibrationEnabled()
        _selectedVibrationPattern.value = preferenceHelper.getVibrationPattern()
    }

    fun getReminderInterval(): Int {
        return _reminderInterval.value ?: preferenceHelper.getReminderInterval()
    }

    fun setReminderInterval(hours: Int, minutes: Int) {
        val totalMinutes = hours * 60 + minutes
        _reminderInterval.value = totalMinutes
        preferenceHelper.setReminderInterval(totalMinutes)
    }

    fun getSelectedRingtone(): Uri? {
        return _selectedRingtone.value ?: preferenceHelper.getSelectedRingtone()
    }

    fun setSelectedRingtone(uri: Uri?) {
        _selectedRingtone.value = uri
        preferenceHelper.setSelectedRingtone(uri)
    }

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        preferenceHelper.setSoundEnabled(enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        preferenceHelper.setVibrationEnabled(enabled)
    }

    fun setVibrationPattern(pattern: Int) {
        _selectedVibrationPattern.value = pattern
        preferenceHelper.setVibrationPattern(pattern)
    }
}
