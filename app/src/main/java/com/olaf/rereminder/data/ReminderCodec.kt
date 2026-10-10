package com.olaf.rereminder.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Turns the reminder list into the stored JSON and back, including the upgrade from 3.x.
 *
 * Pure Kotlin so the migration is covered by unit tests. Decoding throws on anything it cannot
 * make sense of — the caller decides what to do with a damaged file, and must never treat it as
 * "no reminders" and write over it.
 */
object ReminderCodec {

    const val VERSION = 2

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class StoreFile(
        val version: Int = VERSION,
        val reminders: List<Reminder> = emptyList(),
    )

    fun encode(reminders: List<Reminder>): String =
        json.encodeToString(StoreFile.serializer(), StoreFile(VERSION, reminders))

    /**
     * Reads either the current format (`{"version":2,…}`) or the bare list 3.x wrote.
     * [nowMillis] seeds the anchor of migrated timers that were not running.
     */
    fun decode(text: String, nowMillis: Long): List<Reminder> {
        val element = json.parseToJsonElement(text)
        return if (element is JsonArray) {
            json.decodeFromJsonElement(
                kotlinx.serialization.builtins.ListSerializer(LegacyReminder.serializer()),
                element,
            ).mapIndexed { index, legacy -> legacy.toReminder(index, nowMillis) }
        } else {
            json.decodeFromJsonElement(StoreFile.serializer(), element).reminders
        }
    }

    /**
     * Carries the global sound settings of 3.x onto the reminders, now that each reminder picks
     * its own sound: "text-to-speech" makes default-sound reminders speak, and a muted app makes
     * them silent. Alarms were never affected by either setting, so they are left alone.
     */
    fun applyLegacySoundDefaults(reminders: List<Reminder>, speak: Boolean, muted: Boolean): List<Reminder> {
        val mode = when {
            muted -> SoundMode.SILENT
            speak -> SoundMode.SPEAK
            else -> return reminders
        }
        return reminders.map { reminder ->
            if (reminder.alertStyle == AlertStyle.NOTIFICATION && reminder.sound.mode == SoundMode.DEFAULT) {
                reminder.copy(sound = reminder.sound.copy(mode = mode))
            } else {
                reminder
            }
        }
    }

    /** The shape reminders had in 3.0 – 3.1.1. */
    @Serializable
    internal data class LegacyReminder(
        val id: Int,
        val name: String = "",
        val message: String = "",
        val intervalMinutes: Int = 60,
        val enabled: Boolean = true,
        val days: Set<Int> = Reminder.ALL_DAYS,
        val startMinute: Int = 0,
        val endMinute: Int = Reminder.MINUTES_PER_DAY,
        val colorIndex: Int = 0,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val startAtMillis: Long = 0L,
        val nextTriggerAt: Long = 0L,
    ) {
        fun toReminder(index: Int, nowMillis: Long): Reminder {
            val interval = intervalMinutes.coerceAtLeast(1)
            val window = TimeWindow(days = days, startMinute = startMinute, endMinute = endMinute)
            val unrestricted = days.size == 7 && window.isAllDay
            // Keep a running countdown where it was: its last tick is one interval before the
            // armed alarm. Anything else simply starts counting from the upgrade.
            val anchor = if (nextTriggerAt > nowMillis) nextTriggerAt - interval * 60_000L else nowMillis
            return Reminder(
                id = id,
                name = name,
                message = message,
                enabled = enabled,
                colorIndex = colorIndex,
                repeat = Repeat(unit = RepeatUnit.TIME, every = interval),
                windows = if (unrestricted) emptyList() else listOf(window),
                startAtMillis = startAtMillis,
                anchorMillis = anchor,
                sound = SoundChoice(mode = if (soundEnabled) SoundMode.DEFAULT else SoundMode.SILENT),
                vibrationEnabled = vibrationEnabled,
                sortIndex = index,
                nextTriggerAt = 0L,
            )
        }
    }
}
