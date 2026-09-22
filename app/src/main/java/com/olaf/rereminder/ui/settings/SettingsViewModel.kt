package com.olaf.rereminder.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.Reliability
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
    /** Empty when the manufacturer could not be read. */
    val vendorName: String = "",
    /** The dontkillmyapp.com page for this device, or its general guide. */
    val guideUrl: String = "https://dontkillmyapp.com/general",
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceHelper(application)

    private val _uiState = MutableStateFlow(readSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun readSettings(): SettingsUiState {
        val status = Reliability.status(getApplication())
        return SettingsUiState(
            ringtone = preferences.getSelectedRingtone(),
            soundEnabled = preferences.isSoundEnabled(),
            soundType = preferences.getNotificationSoundType(),
            vibrationEnabled = preferences.isVibrationEnabled(),
            vibrationPattern = preferences.getVibrationPattern(),
            exactAlarmsAllowed = status.exactAlarmsAllowed,
            batteryUnrestricted = status.batteryUnrestricted,
            vendorName = status.vendorName,
            guideUrl = status.guideUrl,
        )
    }

    /** Both can be changed outside the app, so re-read them whenever the screen resumes. */
    fun refreshSystemStatus() {
        val status = Reliability.status(getApplication())
        _uiState.update {
            it.copy(
                exactAlarmsAllowed = status.exactAlarmsAllowed,
                batteryUnrestricted = status.batteryUnrestricted,
            )
        }
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
