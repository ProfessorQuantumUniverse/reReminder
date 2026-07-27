package com.olaf.rereminder.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.utils.PreferenceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** App-wide alert settings; per-timer options live in the timer editor. */
data class SettingsUiState(
    val ringtone: Uri? = null,
    val soundEnabled: Boolean = true,
    val soundType: String = PreferenceHelper.SOUND_TYPE_RINGTONE,
    val vibrationEnabled: Boolean = true,
    val vibrationPattern: Int = 1,
    /** System-level conditions that decide whether reminders actually arrive on time. */
    val exactAlarmsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceHelper(application)

    private val _uiState = MutableStateFlow(readSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun readSettings() = SettingsUiState(
        ringtone = preferences.getSelectedRingtone(),
        soundEnabled = preferences.isSoundEnabled(),
        soundType = preferences.getNotificationSoundType(),
        vibrationEnabled = preferences.isVibrationEnabled(),
        vibrationPattern = preferences.getVibrationPattern(),
        exactAlarmsAllowed = exactAlarmsAllowed(),
        batteryUnrestricted = batteryUnrestricted(),
    )

    /** Both can be changed outside the app, so re-read them whenever the screen resumes. */
    fun refreshSystemStatus() {
        _uiState.update {
            it.copy(
                exactAlarmsAllowed = exactAlarmsAllowed(),
                batteryUnrestricted = batteryUnrestricted(),
            )
        }
    }

    private fun exactAlarmsAllowed(): Boolean =
        ReminderScheduler(getApplication()).canScheduleExactAlarms()

    private fun batteryUnrestricted(): Boolean {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun getSelectedRingtone(): Uri? = _uiState.value.ringtone

    fun setSelectedRingtone(uri: Uri?) {
        preferences.setSelectedRingtone(uri)
        _uiState.update { it.copy(ringtone = uri) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        preferences.setSoundEnabled(enabled)
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun setNotificationSoundType(type: String) {
        preferences.setNotificationSoundType(type)
        _uiState.update { it.copy(soundType = type) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        preferences.setVibrationEnabled(enabled)
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setVibrationPattern(pattern: Int) {
        preferences.setVibrationPattern(pattern)
        _uiState.update { it.copy(vibrationPattern = pattern) }
    }
}
