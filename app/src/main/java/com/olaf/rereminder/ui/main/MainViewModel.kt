package com.olaf.rereminder.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.isWithinWindowsAt
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.utils.AlertMode
import com.olaf.rereminder.utils.NotificationHelper
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
            val total = reminder.cycleMillis
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
    val compact: Boolean = false,
    val sortMode: String = PreferenceHelper.SORT_CUSTOM,
    val alertMode: AlertMode = AlertMode.SOUND,
) {
    val isEmpty: Boolean get() = loaded && rows.isEmpty()

    val activeCount: Int get() = rows.count { it.reminder.enabled }

    val canReorder: Boolean get() = sortMode == PreferenceHelper.SORT_CUSTOM && rows.size > 1

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
    private val listPrefs = MutableStateFlow(
        ListPrefs(preferences.isCompactList(), preferences.getSortMode(), preferences.getAlertMode())
    )

    /** The last reminder swiped away, kept until the Undo window has passed. */
    private var lastDeleted: Reminder? = null

    /**
     * Bumped on every Undo. The list can bring a swiped-away card back with its swipe state still
     * "dismissed", which would delete it all over again; a new generation gives every card a
     * fresh state.
     */
    private val _undoGeneration = MutableStateFlow(0)
    val undoGeneration: StateFlow<Int> = _undoGeneration.asStateFlow()

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
        combine(repository.reminders, masterEnabled, listPrefs, ticker) { reminders, master, list, _ ->
            val now = System.currentTimeMillis()
            val rows = reminders.map { reminder ->
                ReminderRow(
                    reminder = reminder,
                    // Whole seconds, rounded up: the countdown shows 00:01 until the moment it
                    // fires, and ticks between states that only differ by milliseconds compare
                    // equal, so the StateFlow drops them instead of recomposing the list.
                    remainingMillis = ceilToSecond(reminder.nextTriggerAt - now),
                    isWithinSchedule = reminder.isWithinWindowsAt(now),
                    isPending = reminder.isPending(now),
                )
            }
            MainUiState(
                rows = sort(rows, list.sortMode),
                masterEnabled = master,
                loaded = true,
                compact = list.compact,
                sortMode = list.sortMode,
                alertMode = list.alertMode,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(
                masterEnabled = preferences.isMasterEnabled(),
                compact = preferences.isCompactList(),
                sortMode = preferences.getSortMode(),
                alertMode = preferences.getAlertMode(),
            ),
        )

    fun setMasterEnabled(enabled: Boolean) {
        preferences.setMasterEnabled(enabled)
        masterEnabled.value = enabled
        // Resuming restarts every timer's countdown from now; pausing clears the alarms.
        scheduler.sync(restartIds = repository.reminders.value.map { it.id }.toSet())
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        repository.update(id) { it.copy(enabled = enabled) }
        // Switching on starts a fresh countdown; only this timer, so the others keep running.
        if (enabled) scheduler.restart(id) else scheduler.sync()
    }

    /** Sound → vibrate only → mute → sound. Returns the mode now in effect. */
    fun cycleAlertMode(): AlertMode {
        val next = preferences.getAlertMode().next()
        preferences.setAlertMode(next)
        listPrefs.value = listPrefs.value.copy(alertMode = next)
        return next
    }

    fun setSortMode(mode: String) {
        preferences.setSortMode(mode)
        listPrefs.value = listPrefs.value.copy(sortMode = mode)
    }

    /** Persists a hand-sorted order, given as the ids from top to bottom. */
    fun reorder(ids: List<Int>) {
        val position = ids.withIndex().associate { (index, id) -> id to index }
        repository.mutate { reminders ->
            reminders.map { it.copy(sortIndex = position[it.id] ?: it.sortIndex) }
        }
    }

    /** Removes a reminder from the list; [undoDelete] can bring it back. */
    fun delete(id: Int): Reminder? {
        scheduler.cancel(id)
        val removed = repository.delete(id) ?: return null
        NotificationHelper.cancelReminderNotification(getApplication(), id)
        lastDeleted = removed
        return removed
    }

    fun undoDelete() {
        val reminder = lastDeleted ?: return
        lastDeleted = null
        _undoGeneration.value += 1
        val restored = repository.restore(reminder.copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = 0L))
        // Same anchor as before, so the countdown carries on as if nothing happened.
        scheduler.sync()
        if (restored.anchorMillis <= 0L) scheduler.restart(restored.id)
    }

    /** Re-arms anything that drifted while the app was in the background. */
    fun refresh() {
        masterEnabled.value = preferences.isMasterEnabled()
        listPrefs.value = ListPrefs(preferences.isCompactList(), preferences.getSortMode(), preferences.getAlertMode())
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

    private data class ListPrefs(val compact: Boolean, val sortMode: String, val alertMode: AlertMode)

    private companion object {
        /**
         * Well under a second on purpose. Each timer's seconds roll over at its own offset, and a
         * one-second delay drifts against all of them, so the display would now and then hold a
         * second twice and then skip the next one.
         */
        const val TICK_MILLIS = 250L
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun ceilToSecond(millis: Long): Long =
            if (millis <= 0L) 0L else (millis + 999L) / 1_000L * 1_000L

        /**
         * Hand-sorted by position, or by what fires next — running timers first, soonest on top,
         * paused ones after them in their hand-sorted order.
         */
        fun sort(rows: List<ReminderRow>, mode: String): List<ReminderRow> {
            val byPosition = rows.sortedWith(compareBy({ it.reminder.sortIndex }, { it.id }))
            if (mode != PreferenceHelper.SORT_NEXT) return byPosition
            val (armed, idle) = byPosition.partition { it.reminder.enabled && it.reminder.nextTriggerAt > 0L }
            return armed.sortedBy { it.reminder.nextTriggerAt } + idle
        }
    }
}
