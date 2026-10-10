package com.olaf.rereminder.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.ui.navigation.Routes
import com.olaf.rereminder.ui.theme.ReminderAccents
import com.olaf.rereminder.utils.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the reminder being edited.
 *
 * The id arrives through [SavedStateHandle] — it is a navigation argument — so the draft is
 * already loaded by the time the screen first composes, and the editor never renders a frame of
 * a blank reminder before filling itself in.
 */
class ReminderEditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = ReminderRepository.get(application)
    private val scheduler = ReminderScheduler(application)

    private val existing: Reminder? =
        savedStateHandle.get<Int>(Routes.ARG_ID)
            ?.takeIf { it > NEW_ID }
            ?.let { repository.get(it) }

    val isNew: Boolean = existing == null

    private val _draft = MutableStateFlow(
        existing ?: Reminder(
            id = NEW_ID,
            repeat = Repeat(RepeatUnit.TIME, DEFAULT_INTERVAL_MINUTES),
            // Give each new timer a different accent so the list stays easy to scan.
            colorIndex = repository.reminders.value.size % ReminderAccents.size,
        )
    )
    val draft: StateFlow<Reminder> = _draft.asStateFlow()

    fun update(transform: (Reminder) -> Reminder) {
        _draft.value = transform(_draft.value)
    }

    /**
     * Set once the draft has been saved or deleted. The editor keeps taking taps while it animates
     * away, and a second tap on Save for a new timer would otherwise add it twice.
     */
    private var finished = false

    fun save() {
        val draft = _draft.value.let { it.copy(earlyAlerts = it.earlyAlerts.filter { offset -> offset < it.repeat.shortestGapMinutes }) }
        if (finished || !draft.isSchedulable) return
        finished = true

        if (isNew) {
            val created = repository.add(draft.copy(anchorMillis = 0L, nextTriggerAt = 0L, nextEventAt = 0L))
            scheduler.restart(created.id)
            return
        }

        val before = existing!!
        // Only what the editor shows comes from the draft; the scheduler's bookkeeping and the
        // list position stay as stored, in case they moved while the editor was open.
        repository.update(draft.id) { stored ->
            draft.copy(
                enabled = stored.enabled,
                sortIndex = stored.sortIndex,
                anchorMillis = stored.anchorMillis,
                nextTriggerAt = stored.nextTriggerAt,
                nextEventAt = stored.nextEventAt,
                snoozeUntil = stored.snoozeUntil,
            )
        }
        val timingChanged = before.repeat != draft.repeat ||
            before.windows != draft.windows ||
            before.startAtMillis != draft.startAtMillis
        // A new rhythm starts counting now; renaming a reminder must not reset its countdown.
        if (timingChanged) scheduler.restart(draft.id) else scheduler.sync()
    }

    fun delete() {
        val id = _draft.value.id
        if (finished || id <= NEW_ID) return
        finished = true

        scheduler.cancel(id)
        repository.delete(id)
        NotificationHelper.cancelReminderNotification(getApplication(), id)
    }

    companion object {
        const val NEW_ID = 0
        private const val DEFAULT_INTERVAL_MINUTES = 30
    }
}
