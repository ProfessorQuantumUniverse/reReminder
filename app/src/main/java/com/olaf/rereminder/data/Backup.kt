package com.olaf.rereminder.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The app-wide settings an export carries along. Every field is optional: a missing one, or one
 * the app doesn't understand, leaves that setting as it is on import.
 *
 * Only what the user chose on purpose travels. Set-up flags, the reliability notices and the
 * global pause stay on the device they belong to, and the top-bar sound mode is left out so an
 * import can't silently mute every reminder.
 */
@Serializable
data class BackupSettings(
    /** Tone URIs; an empty string means the system default. */
    val ringtone: String? = null,
    val alarmTone: String? = null,
    val speechStream: String? = null,
    val snoozeMinutes: Int? = null,
    val snoozeOnAlarmSwipe: Boolean? = null,
    val vibrationEnabled: Boolean? = null,
    val vibrationPattern: Int? = null,
    val compactList: Boolean? = null,
    val sortMode: String? = null,
)

/** Why a file could not be imported. */
enum class BackupError {
    /** Not JSON, or JSON that isn't a reReminder export or reminder list. */
    NOT_A_BACKUP,

    /** Written by a newer reReminder whose format this version doesn't know. */
    TOO_NEW,

    /** A valid file without a single reminder in it. */
    EMPTY,

    /** Larger than any real list of reminders could be. */
    TOO_LARGE,

    /** The file could not be opened or read at all. */
    UNREADABLE,
}

class BackupException(val error: BackupError, cause: Throwable? = null) :
    Exception(error.name, cause)

/** What a file holds, already checked and cleaned up for this device. */
data class BackupContents(
    val reminders: List<Reminder>,
    val settings: BackupSettings?,
)

/** The outcome of adding imported reminders to the current ones. */
data class MergeResult(
    val reminders: List<Reminder>,
    val added: Int,
    /** Imported reminders left out because an identical one is already there. */
    val skipped: Int,
)

/**
 * Export and import of the reminder list (#21), as one JSON file the user keeps wherever they
 * like.
 *
 * The file is meant to survive a reinstall, a new phone and a hand edit, so:
 * - It is versioned and names its format, and it is pretty-printed to be readable.
 * - Import also takes the app's own `reminders.json` and the bare list of 3.x, via [ReminderCodec].
 * - What only made sense on the old phone — armed alarm times, a pending snooze — is dropped, and
 *   values a hand edit could have broken are brought back into range.
 *
 * Pure Kotlin, so all of it is covered by unit tests.
 */
object Backup {

    const val FORMAT = "rereminder-backup"
    const val VERSION = 1

    /** Thousands of reminders fit in far less; anything bigger is not one of our files. */
    const val MAX_BYTES = 2 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    private data class BackupFile(
        val format: String = FORMAT,
        val version: Int = VERSION,
        /** Informational only: which app version wrote the file, and when. */
        val appVersion: String? = null,
        val exportedAt: Long = 0L,
        val reminders: List<Reminder> = emptyList(),
        val settings: BackupSettings? = null,
    )

    fun encode(
        reminders: List<Reminder>,
        settings: BackupSettings?,
        exportedAt: Long,
        appVersion: String?,
    ): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            appVersion = appVersion,
            exportedAt = exportedAt,
            reminders = reminders.sortedBy { it.sortIndex }.map { it.withoutDeviceState() },
            settings = settings,
        ),
    )

    /**
     * Reads an export, or the app's own store file, or a 3.x list. Throws [BackupException] for
     * anything else; never returns a half-understood file.
     */
    fun decode(text: String, nowMillis: Long): BackupContents {
        if (text.length > MAX_BYTES) throw BackupException(BackupError.TOO_LARGE)
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }

        val contents = try {
            if (element is JsonObject && "format" in element) {
                val format = (element["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (format != FORMAT) throw BackupException(BackupError.NOT_A_BACKUP)
                val version = (element["version"] as? JsonPrimitive)?.intOrNull
                    ?: throw BackupException(BackupError.NOT_A_BACKUP)
                if (version > VERSION) throw BackupException(BackupError.TOO_NEW)
                val file = json.decodeFromJsonElement(BackupFile.serializer(), element)
                BackupContents(file.reminders, file.settings)
            } else {
                if (element is JsonObject && "reminders" !in element) {
                    throw BackupException(BackupError.NOT_A_BACKUP)
                }
                val stored = element as? JsonObject
                val storedVersion = (stored?.get("version") as? JsonPrimitive)?.intOrNull
                if (storedVersion != null && storedVersion > ReminderCodec.VERSION) {
                    throw BackupException(BackupError.TOO_NEW)
                }
                BackupContents(ReminderCodec.decode(text, nowMillis), settings = null)
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: SerializationException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }

        if (contents.reminders.isEmpty()) throw BackupException(BackupError.EMPTY)
        return contents.copy(reminders = contents.reminders.map { sanitize(it) })
    }

    /** Brings a reminder from a file into a shape this device can schedule. */
    fun sanitize(reminder: Reminder): Reminder {
        val repeat = reminder.repeat.copy(
            every = reminder.repeat.every.coerceAtLeast(1),
            weekdays = reminder.repeat.weekdays.filterTo(sortedSetOf()) { it in 1..7 },
            monthDays = reminder.repeat.monthDays.filterTo(sortedSetOf()) { it in 1..31 },
        )
        val windows = reminder.windows.map { window ->
            window.copy(
                days = window.days.filterTo(sortedSetOf()) { it in 1..7 },
                startMinute = window.startMinute.coerceIn(0, Reminder.MINUTES_PER_DAY - 1),
                endMinute = window.endMinute.coerceIn(0, Reminder.MINUTES_PER_DAY),
            )
        }
        val earlyAlerts = reminder.earlyAlerts
            .filter { it > 0 && it < repeat.shortestGapMinutes }
            .distinct()
            .take(Reminder.MAX_EARLY_ALERTS)
        return reminder.withoutDeviceState().copy(
            repeat = repeat,
            windows = windows,
            earlyAlerts = earlyAlerts,
            startAtMillis = reminder.startAtMillis.coerceAtLeast(0L),
            anchorMillis = reminder.anchorMillis.coerceAtLeast(0L),
            sound = if (reminder.sound.mode == SoundMode.TONE && reminder.sound.toneUri.isNullOrBlank()) {
                SoundChoice(SoundMode.DEFAULT)
            } else {
                reminder.sound
            },
        )
    }

    /**
     * Adds [imported] after [current] under fresh ids, leaving out any that match a reminder
     * already there — apart from its place in the list, whether it is switched on and its device
     * state — so importing the same file twice changes nothing.
     */
    fun merge(current: List<Reminder>, imported: List<Reminder>): MergeResult {
        val seen = current.mapTo(mutableSetOf()) { it.contentKey() }
        var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        var nextSort = (current.maxOfOrNull { it.sortIndex } ?: -1) + 1
        val added = mutableListOf<Reminder>()
        var skipped = 0
        for (reminder in imported) {
            if (!seen.add(reminder.contentKey())) {
                skipped++
                continue
            }
            added += reminder.copy(id = nextId++, sortIndex = nextSort++)
        }
        return MergeResult(current + added, added.size, skipped)
    }

    /** The imported list on its own, renumbered: ids in a hand-edited file can't be trusted. */
    fun replace(imported: List<Reminder>): List<Reminder> =
        imported.mapIndexed { index, reminder -> reminder.copy(id = index + 1, sortIndex = index) }

    private fun Reminder.contentKey(): Reminder =
        withoutDeviceState().copy(id = 0, sortIndex = 0, enabled = true, anchorMillis = 0L)

    private fun Reminder.withoutDeviceState(): Reminder =
        copy(nextTriggerAt = 0L, nextEventAt = 0L, snoozeUntil = 0L)
}
