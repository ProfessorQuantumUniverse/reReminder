package com.olaf.rereminder.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Stores the timer list as JSON inside the app's SharedPreferences.
 *
 * Deliberately holds only the [SharedPreferences] and no Context, so the singleton can't leak
 * an Activity. There is at most a handful of reminders, so a full rewrite per mutation is fine.
 */
class ReminderRepository private constructor(private val preferences: SharedPreferences) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _reminders = MutableStateFlow(loadOrMigrate())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    fun get(id: Int): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** Adds [reminder] (ignoring its id) and returns it with the id it was actually given. */
    fun add(reminder: Reminder): Reminder {
        val nextId = (_reminders.value.maxOfOrNull { it.id } ?: 0) + 1
        val created = reminder.copy(id = nextId)
        persist(_reminders.value + created)
        return created
    }

    fun update(reminder: Reminder) {
        persist(_reminders.value.map { if (it.id == reminder.id) reminder else it })
    }

    fun delete(id: Int) {
        persist(_reminders.value.filterNot { it.id == id })
    }

    fun replaceAll(reminders: List<Reminder>) {
        persist(reminders)
    }

    private fun persist(reminders: List<Reminder>) {
        _reminders.value = reminders
        writeToDisk(reminders)
    }

    private fun writeToDisk(reminders: List<Reminder>) {
        preferences.edit { putString(KEY_REMINDERS, json.encodeToString(reminders)) }
    }

    private fun loadOrMigrate(): List<Reminder> {
        val stored = preferences.getString(KEY_REMINDERS, null)
        if (stored != null) {
            return runCatching { json.decodeFromString<List<Reminder>>(stored) }
                .onFailure { Log.e(TAG, "Could not read stored reminders", it) }
                .getOrDefault(emptyList())
        }
        return migrateLegacyReminder()
    }

    /**
     * Versions before 3.0 had exactly one reminder stored as loose preference keys.
     * Carry it over so upgrading users keep their timer instead of finding an empty list.
     */
    private fun migrateLegacyReminder(): List<Reminder> {
        val hasLegacy = preferences.contains(LEGACY_KEY_INTERVAL) ||
            preferences.contains(LEGACY_KEY_ENABLED)
        if (!hasLegacy) return emptyList()

        val migrated = Reminder(
            id = 1,
            name = preferences.getString(LEGACY_KEY_TITLE, null)?.takeIf { it.isNotBlank() }.orEmpty(),
            message = preferences.getString(LEGACY_KEY_TEXT, null)?.takeIf { it.isNotBlank() }.orEmpty(),
            intervalMinutes = preferences.getInt(LEGACY_KEY_INTERVAL, 60).coerceAtLeast(1),
            enabled = preferences.getBoolean(LEGACY_KEY_ENABLED, false),
            soundEnabled = preferences.getBoolean(LEGACY_KEY_SOUND, true),
            vibrationEnabled = preferences.getBoolean(LEGACY_KEY_VIBRATION, true),
            nextTriggerAt = preferences.getLong(LEGACY_KEY_NEXT_TRIGGER, 0L),
        )

        Log.i(TAG, "Migrated legacy single reminder into the timer list")
        val reminders = listOf(migrated)
        // Runs from the _reminders initialiser, so write straight to disk — the flow does not
        // exist yet and will be seeded with this return value.
        writeToDisk(reminders)
        return reminders
    }

    companion object {
        private const val TAG = "ReminderRepository"
        private const val PREF_NAME = "reminder_preferences"
        private const val KEY_REMINDERS = "reminders_json"

        private const val LEGACY_KEY_ENABLED = "reminder_enabled"
        private const val LEGACY_KEY_INTERVAL = "reminder_interval"
        private const val LEGACY_KEY_TITLE = "notification_title"
        private const val LEGACY_KEY_TEXT = "notification_text"
        private const val LEGACY_KEY_SOUND = "sound_enabled"
        private const val LEGACY_KEY_VIBRATION = "vibration_enabled"
        private const val LEGACY_KEY_NEXT_TRIGGER = "next_reminder_time"

        @Volatile
        private var instance: ReminderRepository? = null

        fun get(context: Context): ReminderRepository =
            instance ?: synchronized(this) {
                instance ?: ReminderRepository(
                    context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
