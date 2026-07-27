package com.olaf.rereminder.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.service.ReminderScheduler
import com.olaf.rereminder.ui.theme.ReminderAccents
import com.olaf.rereminder.utils.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReminderEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReminderRepository.get(application)
    private val scheduler = ReminderScheduler(application)

    private val _draft = MutableStateFlow(Reminder(id = NEW_ID))
    val draft: StateFlow<Reminder> = _draft.asStateFlow()

    private var initialized = false

    var isNew: Boolean = true
        private set

    /** Loads [id], or seeds a new timer. Safe to call again after a configuration change. */
    fun initialize(id: Int) {
        if (initialized) return
        initialized = true

        val existing = if (id > NEW_ID) repository.get(id) else null
        isNew = existing == null
        _draft.value = existing ?: Reminder(
            id = NEW_ID,
            intervalMinutes = DEFAULT_INTERVAL_MINUTES,
            // Give each new timer a different accent so the list stays easy to scan.
            colorIndex = repository.reminders.value.size % ReminderAccents.size,
        )
    }

    fun update(transform: (Reminder) -> Reminder) {
        _draft.value = transform(_draft.value)
    }

    /** A timer with no weekday selected could never fire, so saving is blocked. */
    val canSave: Boolean
        get() = _draft.value.days.isNotEmpty() && _draft.value.intervalMinutes > 0

    fun save() {
        val draft = _draft.value
        if (draft.days.isEmpty() || draft.intervalMinutes <= 0) return

        val id = if (isNew) {
            repository.add(draft.copy(nextTriggerAt = 0L)).id
        } else {
            repository.update(draft)
            draft.id
        }
        // Timing may have changed, so always recompute this one.
        scheduler.reschedule(id)
    }

    fun delete() {
        val id = _draft.value.id
        if (id <= NEW_ID) return

        scheduler.cancel(id)
        repository.delete(id)
        NotificationHelper.cancelReminderNotification(getApplication(), id)
    }

    companion object {
        const val NEW_ID = 0
        private const val DEFAULT_INTERVAL_MINUTES = 30
    }
}
