package com.olaf.rereminder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.util.AtomicFile
import com.olaf.rereminder.utils.PreferenceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Stores the reminder list as one JSON file in device-protected storage (see [Storage]).
 *
 * Three rules keep it trustworthy:
 * - Every change is a read-modify-write under one lock, so the alarm receiver arming the next
 *   alert and the editor saving a reminder can never overwrite each other's change.
 * - Writes go through [AtomicFile]: a crash mid-write leaves the previous version, not half a file.
 * - A file that cannot be read is copied aside and never written over — losing every reminder to
 *   one bad byte is the worst thing this class could do.
 *
 * Holds only the application context, so the singleton can't leak an Activity.
 */
class ReminderRepository private constructor(private val context: Context) {

    private val lock = Any()

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    /** False only while the phone is locked after a reboot and 3.x data has not moved yet. */
    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        synchronized(lock) { load() }
    }

    /** Loads the list if that was not possible before (the phone has been unlocked since). */
    fun ensureLoaded(): Boolean = synchronized(lock) {
        if (!isLoaded) load()
        isLoaded
    }

    fun get(id: Int): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** Adds [reminder] (ignoring its id) at the end of the list and returns it as stored. */
    fun add(reminder: Reminder): Reminder {
        var created = reminder
        mutate { list ->
            created = reminder.copy(
                id = (list.maxOfOrNull { it.id } ?: 0) + 1,
                sortIndex = (list.maxOfOrNull { it.sortIndex } ?: -1) + 1,
            )
            list + created
        }
        return created
    }

    fun update(reminder: Reminder) {
        mutate { list -> list.map { if (it.id == reminder.id) reminder else it } }
    }

    /** Changes one reminder based on its current stored state — never on a stale copy. */
    fun update(id: Int, transform: (Reminder) -> Reminder) {
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    /** Removes the reminder and hands it back, so it can be restored by [restore]. */
    fun delete(id: Int): Reminder? {
        var removed: Reminder? = null
        mutate { list ->
            removed = list.firstOrNull { it.id == id }
            list.filterNot { it.id == id }
        }
        return removed
    }

    /** Puts a deleted reminder back — under its old id unless another reminder took it since. */
    fun restore(reminder: Reminder): Reminder {
        var restored = reminder
        mutate { list ->
            restored = if (list.any { it.id == reminder.id }) {
                reminder.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1)
            } else {
                reminder
            }
            list + restored
        }
        return restored
    }

    /** Applies [transform] to the whole list as one atomic change. */
    fun mutate(transform: (List<Reminder>) -> List<Reminder>) {
        synchronized(lock) {
            if (!isLoaded) load()
            if (!isLoaded) {
                Log.w(TAG, "Storage not available yet, change dropped")
                return
            }
            val current = _reminders.value
            val updated = transform(current)
            if (updated == current) return
            if (write(updated)) {
                _reminders.value = updated
            } else {
                // Keep memory and disk in step; a change that could not be saved did not happen.
                Log.e(TAG, "Could not save reminders, change dropped")
            }
        }
    }

    // --- Disk ------------------------------------------------------------------------------

    private fun file(): AtomicFile = AtomicFile(File(Storage.filesDir(context), FILE_NAME))

    private fun load() {
        if (!Storage.ensureMigrated(context)) {
            Log.i(TAG, "Phone still locked; reminders load once it is unlocked")
            return
        }
        val now = System.currentTimeMillis()
        val file = file()
        val text = try {
            file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            Log.e(TAG, "Could not read reminders", e)
            // Unreadable right now is not the same as empty; try again next time.
            return
        }

        val loaded = if (text != null) {
            runCatching { ReminderCodec.decode(text, now) }
                .onFailure {
                    Log.e(TAG, "Stored reminders are damaged, keeping a copy aside", it)
                    preserveDamaged(text)
                }
                .getOrDefault(emptyList())
        } else {
            migrateFromPreferences(now) ?: return
        }

        _reminders.value = loaded
        isLoaded = true
        moveLegacySoundDefaults()
    }

    /**
     * 4.0 dropped the global "Sound or text-to-speech" and "Sound enabled" settings in favour of
     * each reminder's own sound. Carry them over once, so nobody's reminders change how they sound.
     */
    private fun moveLegacySoundDefaults() {
        val preferences = PreferenceHelper(context)
        val legacy = preferences.legacySoundDefaults() ?: return
        val current = _reminders.value
        val updated = ReminderCodec.applyLegacySoundDefaults(current, legacy.speak, legacy.muted)
        if (updated != current) {
            // Only forget the old settings once the reminders carrying them are safely saved.
            if (!write(updated)) return
            _reminders.value = updated
        }
        preferences.markLegacySoundDefaultsMoved()
    }

    /**
     * First start after updating from 3.x: the list was a JSON string in the preferences (or,
     * before 3.0, a single reminder spread over loose keys). Returns null when that data exists
     * but could not be converted — the preferences are then left untouched for the next attempt.
     */
    private fun migrateFromPreferences(now: Long): List<Reminder>? {
        val preferences = Storage.preferences(context)
        val stored = preferences.getString(KEY_LEGACY_REMINDERS, null)
        val migrated = when {
            stored != null -> runCatching { ReminderCodec.decode(stored, now) }
                .onFailure {
                    Log.e(TAG, "Could not read 3.x reminders, keeping a copy aside", it)
                    preserveDamaged(stored)
                }
                .getOrDefault(emptyList())

            else -> migrateSingleReminder(now)
        }
        if (migrated.isEmpty() && stored == null) return emptyList()

        if (!write(migrated)) return null
        preferences.edit { remove(KEY_LEGACY_REMINDERS) }
        Log.i(TAG, "Migrated ${migrated.size} reminders to the v${ReminderCodec.VERSION} file")
        return migrated
    }

    /** Versions before 3.0 had exactly one reminder stored as loose preference keys. */
    private fun migrateSingleReminder(now: Long): List<Reminder> {
        val preferences = Storage.preferences(context)
        val hasLegacy = preferences.contains(LEGACY_KEY_INTERVAL) ||
            preferences.contains(LEGACY_KEY_ENABLED)
        if (!hasLegacy) return emptyList()

        val legacy = ReminderCodec.LegacyReminder(
            id = 1,
            name = preferences.getString(LEGACY_KEY_TITLE, null)?.takeIf { it.isNotBlank() }.orEmpty(),
            message = preferences.getString(LEGACY_KEY_TEXT, null)?.takeIf { it.isNotBlank() }.orEmpty(),
            intervalMinutes = preferences.getInt(LEGACY_KEY_INTERVAL, 60).coerceAtLeast(1),
            enabled = preferences.getBoolean(LEGACY_KEY_ENABLED, false),
            soundEnabled = preferences.getBoolean(LEGACY_KEY_SOUND, true),
            vibrationEnabled = preferences.getBoolean(LEGACY_KEY_VIBRATION, true),
            nextTriggerAt = preferences.getLong(LEGACY_KEY_NEXT_TRIGGER, 0L),
        )
        return listOf(legacy.toReminder(0, now))
    }

    private fun write(reminders: List<Reminder>): Boolean {
        val file = file()
        val stream = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Could not open reminders for writing", e)
            return false
        }
        return try {
            stream.write(ReminderCodec.encode(reminders).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Could not write reminders", e)
            file.failWrite(stream)
            false
        }
    }

    private fun preserveDamaged(text: String) {
        runCatching {
            File(Storage.filesDir(context), "reminders-damaged-${System.currentTimeMillis()}.json")
                .writeText(text)
        }.onFailure { Log.e(TAG, "Could not keep a copy of the damaged reminders", it) }
    }

    companion object {
        private const val TAG = "ReminderRepository"
        private const val FILE_NAME = "reminders.json"
        private const val KEY_LEGACY_REMINDERS = "reminders_json"

        private const val LEGACY_KEY_ENABLED = "reminder_enabled"
        private const val LEGACY_KEY_INTERVAL = "reminder_interval"
        private const val LEGACY_KEY_TITLE = "notification_title"
        private const val LEGACY_KEY_TEXT = "notification_text"
        private const val LEGACY_KEY_SOUND = "sound_enabled"
        private const val LEGACY_KEY_VIBRATION = "vibration_enabled"
        private const val LEGACY_KEY_NEXT_TRIGGER = "next_reminder_time"

        // Holds only the application context, which lives as long as the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ReminderRepository? = null

        fun get(context: Context): ReminderRepository =
            instance ?: synchronized(this) {
                instance ?: ReminderRepository(context.applicationContext).also { instance = it }
            }
    }
}
