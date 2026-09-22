package com.olaf.rereminder.ui.setup

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.Reliability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceHelper(application)

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    /**
     * Re-read on every resume: each step sends the user into a system screen and the only way to
     * learn what they chose there is to look again when they come back.
     */
    fun refresh() {
        _uiState.value = readState()
    }

    private fun readState(): SetupUiState {
        val context = getApplication<Application>()
        val status = Reliability.status(context)
        return SetupUiState(
            steps = buildSetupSteps(
                needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                notificationsGranted = notificationsGranted(),
                supportsExactAlarmSetting = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                exactAlarmsAllowed = status.exactAlarmsAllowed,
                batteryUnrestricted = status.batteryUnrestricted,
                vendor = status.vendor,
                vendorGuideSeen = preferences.isVendorGuideSeen(),
            ),
            vendor = status.vendor,
            vendorName = status.vendorName,
        )
    }

    /** There is no system flag for "read the guide", so opening it is what marks it done. */
    fun markVendorGuideSeen() {
        preferences.setVendorGuideSeen()
        refresh()
    }

    /**
     * Ends setup, whether the user fixed everything or skipped the optional parts.
     *
     * Whatever they chose to live with is recorded as an already-seen reliability problem, so the
     * app does not greet them with a dialog about the very thing they just declined. A problem
     * that appears *later* still has a different signature, and still gets raised once.
     */
    fun complete() {
        preferences.setSetupComplete()
        val status = Reliability.status(getApplication())
        if (status.needsAttention) {
            preferences.setReliabilityPromptDismissed(status.signature)
        }
    }

    private fun notificationsGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun exactAlarmIntents() = Reliability.exactAlarmIntents(getApplication())

    fun batteryIntents() = Reliability.batteryIntents(getApplication())

    fun guideUrl(): String = Reliability.guideUrl(Reliability.vendor())

    /** True when the scheduler can already post exact alarms, checked fresh. */
    fun exactAlarmsAllowed(): Boolean =
        ReminderScheduler(getApplication()).canScheduleExactAlarms()
}
