package com.olaf.rereminder.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.Reliability
import com.olaf.rereminder.utils.ReliabilityStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** One row in the timer list, with its live countdown already resolved. */
data class ReminderRow(
    val reminder: Reminder,
    val remainingMillis: Long,
    val isWithinSchedule: Boolean,
    /** True while the pinned start date is still ahead, so the loop has not begun. */
    val isPending: Boolean = false,
) {
    val id: Int get() = reminder.id

    /** 0f right after firing, approaching 1f as the next reminder gets closer. */
    val progress: Float
        get() {
            val total = reminder.intervalMillis
            // A timer waiting for its start date has no interval to be partway through yet.
            if (isPending || !reminder.enabled || total <= 0L || reminder.nextTriggerAt <= 0L) {
                return 0f
            }
            return ((total - remainingMillis).toFloat() / total).coerceIn(0f, 1f)
        }
}

data class MainUiState(
    val rows: List<ReminderRow> = emptyList(),
    val masterEnabled: Boolean = true,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && rows.isEmpty()

    val activeCount: Int get() = rows.count { it.reminder.enabled }

    /** Whichever enabled timer fires soonest — drives the summary line. */
    val nextUp: ReminderRow?
        get() = rows
            .filter { it.reminder.enabled && it.reminder.nextTriggerAt > 0L }
            .minByOrNull { it.reminder.nextTriggerAt }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository.get(application)
    private val scheduler = ReminderScheduler(application)
    private val preferences = PreferenceHelper(application)

    private val masterEnabled = MutableStateFlow(preferences.isMasterEnabled())

    /** Non-null while there is a reliability problem the user has not been told about yet. */
    private val _reliabilityPrompt = MutableStateFlow<ReliabilityStatus?>(null)
    val reliabilityPrompt: StateFlow<ReliabilityStatus?> = _reliabilityPrompt.asStateFlow()

    /** Only runs while [uiState] has collectors, i.e. while the list is actually on screen. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<MainUiState> =
        combine(repository.reminders, masterEnabled, ticker) { reminders, master, _ ->
            val now = System.currentTimeMillis()
            MainUiState(
                rows = reminders.map { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        remainingMillis = (reminder.nextTriggerAt - now).coerceAtLeast(0L),
                        isWithinSchedule = reminder.isActiveAt(now),
                        isPending = reminder.isPending(now),
                    )
                },
                masterEnabled = master,
                loaded = true,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(masterEnabled = preferences.isMasterEnabled()),
        )

    fun setMasterEnabled(enabled: Boolean) {
        preferences.setMasterEnabled(enabled)
        masterEnabled.value = enabled
        // Resuming restarts every timer's countdown from now; pausing clears the alarms.
        scheduler.sync(recomputeIds = repository.reminders.value.map { it.id }.toSet())
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        val reminder = repository.get(id) ?: return
        repository.update(reminder.copy(enabled = enabled))
        // Recompute only this timer, so the others keep counting down uninterrupted.
        scheduler.reschedule(id)
    }

    /** Re-arms anything that drifted while the app was in the background. */
    fun refresh() {
        masterEnabled.value = preferences.isMasterEnabled()
        scheduler.sync()
        checkReliability()
    }

    /**
     * Re-checked on every resume rather than once at startup: the user may have just come back
     * from the system screen where they revoked exact alarms.
     */
    private fun checkReliability() {
        val status = Reliability.status(getApplication())
        _reliabilityPrompt.value = status.takeIf {
            it.needsAttention &&
                !preferences.isReliabilityPromptDismissed(it.signature, it.isVendorOnly)
        }
    }

    /** Stops this particular set of problems being raised again. */
    fun dismissReliabilityPrompt() {
        _reliabilityPrompt.value?.let { preferences.setReliabilityPromptDismissed(it.signature) }
        _reliabilityPrompt.value = null
    }

    /** "Don't show again" — no reliability notice from here on, whatever changes. */
    fun silenceReliabilityPrompt() {
        preferences.silenceReliabilityPrompt()
        _reliabilityPrompt.value = null
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
