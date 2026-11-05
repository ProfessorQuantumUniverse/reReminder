package com.olaf.rereminder.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olaf.rereminder.data.BackupContents
import com.olaf.rereminder.data.BackupError
import com.olaf.rereminder.data.BackupException
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.service.ReminderBackup
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.Reliability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** App-wide alert settings; per-timer options live in the timer editor. */
data class SettingsUiState(
    val ringtone: Uri? = null,
    val alarmTone: Uri? = null,
    val speechStream: String = PreferenceHelper.STREAM_NOTIFICATION,
    val snoozeMinutes: Int = PreferenceHelper.DEFAULT_SNOOZE_MINUTES,
    val snoozeOnAlarmSwipe: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val vibrationPattern: Int = 1,
    val compactList: Boolean = false,
    /** System-level conditions that decide whether reminders actually arrive on time. */
    val exactAlarmsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    /** Empty when the manufacturer could not be read. */
    val vendorName: String = "",
    /** The dontkillmyapp.com page for this device, or its general guide. */
    val guideUrl: String = "https://dontkillmyapp.com/general",
    /** True while an export or import is reading or writing its file. */
    val backupBusy: Boolean = false,
    /** A checked file waiting for the user to choose how to import it. */
    val importPreview: ImportPreview? = null,
    /** The outcome of the last export or import, shown once. */
    val backupMessage: BackupMessage? = null,
)

data class ImportPreview(
    val contents: BackupContents,
    /** How many reminders there are now; a replace deletes them. */
    val currentCount: Int,
    /** How many imported reminders "add" would leave out as already there. */
    val duplicates: Int,
) {
    val count: Int get() = contents.reminders.size
    val hasSettings: Boolean get() = contents.settings != null
}

sealed interface BackupMessage {
    data class Exported(val count: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data class Imported(val added: Int, val skipped: Int) : BackupMessage
    data class ImportFailed(val error: BackupError) : BackupMessage

    /** The file was fine, but the new list could not be saved; nothing changed. */
    data object ImportNotSaved : BackupMessage
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceHelper(application)

    private val _uiState = MutableStateFlow(readSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun readSettings(): SettingsUiState {
        val status = Reliability.status(getApplication())
        return SettingsUiState(
            ringtone = preferences.getSelectedRingtone(),
            alarmTone = preferences.getAlarmTone(),
            speechStream = preferences.getSpeechStream(),
            snoozeMinutes = preferences.getSnoozeMinutes(),
            snoozeOnAlarmSwipe = preferences.isSnoozeOnAlarmSwipe(),
            vibrationEnabled = preferences.isVibrationEnabled(),
            vibrationPattern = preferences.getVibrationPattern(),
            compactList = preferences.isCompactList(),
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

    fun setSelectedRingtone(uri: Uri?) {
        preferences.setSelectedRingtone(uri)
        _uiState.update { it.copy(ringtone = uri) }
    }

    fun setAlarmTone(uri: Uri?) {
        preferences.setAlarmTone(uri)
        _uiState.update { it.copy(alarmTone = uri) }
    }



    fun setSpeechStream(stream: String) {
        preferences.setSpeechStream(stream)
        _uiState.update { it.copy(speechStream = stream) }
    }

    fun setSnoozeMinutes(minutes: Int) {
        preferences.setSnoozeMinutes(minutes)
        _uiState.update { it.copy(snoozeMinutes = minutes) }
    }

    fun setSnoozeOnAlarmSwipe(snooze: Boolean) {
        preferences.setSnoozeOnAlarmSwipe(snooze)
        _uiState.update { it.copy(snoozeOnAlarmSwipe = snooze) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        preferences.setVibrationEnabled(enabled)
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setVibrationPattern(pattern: Int) {
        preferences.setVibrationPattern(pattern)
        _uiState.update { it.copy(vibrationPattern = pattern) }
    }

    fun setCompactList(compact: Boolean) {
        preferences.setCompactList(compact)
        _uiState.update { it.copy(compactList = compact) }
    }

    // --- Export and import (#21) ----------------------------------------------------------

    private val backup = ReminderBackup(application)

    fun exportTo(uri: Uri) = runBackup {
        val message = try {
            BackupMessage.Exported(backup.export(uri))
        } catch (e: IOException) {
            Log.e(TAG, "Export failed", e)
            BackupMessage.ExportFailed
        } catch (e: SecurityException) {
            Log.e(TAG, "Export failed", e)
            BackupMessage.ExportFailed
        }
        _uiState.update { it.copy(backupMessage = message) }
    }

    /** Reads and checks the file; the user then picks how to import it from [ImportPreview]. */
    fun readImport(uri: Uri) = runBackup {
        try {
            val contents = backup.read(uri)
            val preview = ImportPreview(
                contents = contents,
                currentCount = ReminderRepository.get(getApplication()).reminders.value.size,
                duplicates = backup.duplicatesOf(contents),
            )
            _uiState.update { it.copy(importPreview = preview) }
        } catch (e: BackupException) {
            Log.w(TAG, "Import file rejected: ${e.error}", e)
            _uiState.update { it.copy(backupMessage = BackupMessage.ImportFailed(e.error)) }
        } catch (e: RuntimeException) {
            // A file picker or provider misbehaving must not take the app down.
            Log.e(TAG, "Import failed", e)
            _uiState.update { it.copy(backupMessage = BackupMessage.ImportFailed(BackupError.UNREADABLE)) }
        }
    }

    fun confirmImport(mode: ReminderBackup.Mode, withSettings: Boolean) {
        val preview = _uiState.value.importPreview ?: return
        _uiState.update { it.copy(importPreview = null) }
        runBackup {
            val result = backup.import(preview.contents, mode, withSettings)
            val message = if (result == null) {
                BackupMessage.ImportNotSaved
            } else {
                BackupMessage.Imported(result.added, result.skipped)
            }
            // Imported settings show up on this screen straight away.
            _uiState.update { readSettings().copy(backupMessage = message) }
        }
    }

    fun cancelImport() {
        _uiState.update { it.copy(importPreview = null) }
    }

    fun consumeBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }

    /** One file operation at a time, off the main thread. */
    private fun runBackup(work: suspend () -> Unit) {
        if (_uiState.value.backupBusy) return
        _uiState.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { work() }
            } finally {
                _uiState.update { it.copy(backupBusy = false) }
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
