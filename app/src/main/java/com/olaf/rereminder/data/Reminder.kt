package com.olaf.rereminder.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One recurring timer.
 *
 * A reminder fires every [intervalMinutes], but only inside its schedule: on the weekdays in
 * [days] and between [startMinute] and [endMinute] (minutes from local midnight). A window where
 * [startMinute] >= [endMinute] wraps past midnight, so 22:00–06:00 is a valid night schedule.
 */
@Serializable
data class Reminder(
    val id: Int,
    val name: String = "",
    val message: String = "",
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    /** ISO weekday numbers, 1 = Monday … 7 = Sunday. */
    val days: Set<Int> = ALL_DAYS,
    val startMinute: Int = 0,
    val endMinute: Int = MINUTES_PER_DAY,
    val colorIndex: Int = 0,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Epoch millis of the scheduled alarm, 0 when not scheduled. */
    val nextTriggerAt: Long = 0L,
) {
    /** True when the schedule covers the whole day (no time restriction). */
    val isAllDay: Boolean
        get() = startMinute == 0 && endMinute >= MINUTES_PER_DAY

    val isEveryDay: Boolean
        get() = days.size == 7

    /** A window that wraps past midnight, e.g. 22:00 → 06:00. */
    private val isOvernight: Boolean
        get() = !isAllDay && startMinute >= endMinute

    val intervalMillis: Long
        get() = intervalMinutes * 60_000L

    /** Whether [epochMillis] falls inside an active slot of this reminder's schedule. */
    fun isActiveAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (days.isEmpty()) return false

        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        val today = dateTime.dayOfWeek.value

        return when {
            isAllDay -> today in days
            isOvernight -> {
                // Either the tail of a window that started today, or one that started yesterday.
                val yesterday = dateTime.minusDays(1).dayOfWeek.value
                (today in days && minuteOfDay >= startMinute) ||
                    (yesterday in days && minuteOfDay < endMinute)
            }

            else -> today in days && minuteOfDay in startMinute until endMinute
        }
    }

    /**
     * The next moment this reminder should fire after [fromMillis], or null when the schedule can
     * never match (no weekdays selected). Normally that is simply `from + interval`; if that lands
     * outside the schedule it snaps forward to the start of the next active window.
     */
    fun nextTriggerAfter(fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (days.isEmpty() || intervalMinutes <= 0) return null

        val candidate = fromMillis + intervalMillis
        if (isActiveAt(candidate, zone)) return candidate
        return nextWindowStartAfter(candidate, zone)
    }

    /**
     * The first moment at or after [fromMillis] at which the reminder becomes active again.
     * Used both for snapping a missed trigger forward and for scheduling a freshly enabled timer.
     */
    fun nextWindowStartAfter(fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (days.isEmpty()) return null

        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        var date: LocalDate = from.toLocalDate()

        // A window opens at most once per day, so eight days always covers a full week plus today.
        repeat(DAYS_TO_SCAN) {
            if (date.dayOfWeek.value in days) {
                val startTime = if (isAllDay) LocalTime.MIDNIGHT else minuteToLocalTime(startMinute)
                val start = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
                if (start >= fromMillis) return start
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun dayOfWeekSet(): Set<DayOfWeek> = days.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet()

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        private const val DAYS_TO_SCAN = 8

        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()

        fun minuteToLocalTime(minuteOfDay: Int): LocalTime {
            val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            return LocalTime.of(clamped / 60, clamped % 60)
        }
    }
}
