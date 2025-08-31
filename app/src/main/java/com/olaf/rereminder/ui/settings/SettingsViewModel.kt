package com.olaf.rereminder.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.olaf.rereminder.utils.PreferenceHelper

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceHelper = PreferenceHelper(application)

    private val _selectedRingtone = MutableLiveData<Uri?>()
    val selectedRingtone: LiveData<Uri?> = _selectedRingtone

    private val _isSoundEnabled = MutableLiveData<Boolean>()
    val isSoundEnabled: LiveData<Boolean> = _isSoundEnabled

    private val _isVibrationEnabled = MutableLiveData<Boolean>()
    val isVibrationEnabled: LiveData<Boolean> = _isVibrationEnabled

    private val _selectedVibrationPattern = MutableLiveData<Int>()
    val selectedVibrationPattern: LiveData<Int> = _selectedVibrationPattern

    private val _notificationTitle = MutableLiveData<String>()
    val notificationTitle: LiveData<String> = _notificationTitle

    private val _notificationText = MutableLiveData<String>()
    val notificationText: LiveData<String> = _notificationText

    private val _notificationSoundType = MutableLiveData<String>()
    val notificationSoundType: LiveData<String> = _notificationSoundType

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _selectedRingtone.value = preferenceHelper.getSelectedRingtone()
        _isSoundEnabled.value = preferenceHelper.isSoundEnabled()
        _isVibrationEnabled.value = preferenceHelper.isVibrationEnabled()
        _selectedVibrationPattern.value = preferenceHelper.getVibrationPattern()
        _notificationTitle.value = preferenceHelper.getNotificationTitle()
        _notificationText.value = preferenceHelper.getNotificationText()
        _notificationSoundType.value = preferenceHelper.getNotificationSoundType()
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

    fun setNotificationContent(title: String, text: String) {
        _notificationTitle.value = title
        _notificationText.value = text
        preferenceHelper.setNotificationTitle(title)
        preferenceHelper.setNotificationText(text)
    }

    fun setNotificationSoundType(type: String) {
        _notificationSoundType.value = type
        preferenceHelper.setNotificationSoundType(type)
    }
}