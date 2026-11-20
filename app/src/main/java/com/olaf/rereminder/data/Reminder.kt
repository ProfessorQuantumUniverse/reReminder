package com.olaf.rereminder.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What a [Repeat] counts in. */
@Serializable
enum class RepeatUnit {
    /** Minutes and hours: the classic interval timer, optionally inside time windows. */
    TIME,
    DAYS,
    WEEKS,
    MONTHS,
}

/**
 * How often a reminder comes round.
 *
 * [RepeatUnit.TIME] counts minutes ("every 1 h 30 min"). The calendar units count days, weeks or
 * months: they pick the dates, and [timesOfDay] picks the times of day on each of them, in local
 * time — so "every day at 09:00" stays at 09:00 across daylight saving changes.
 *
 * Dates and times are kept apart on purpose. One rule then covers every calendar unit: "twice a
 * day at 07:00 and 19:00" and "every week on Thu and Sun at 15:00 and 17:00" are the same model
 * with a different unit, and adding a time never disturbs the dates it fires on.
 */
@Serializable
data class Repeat(
    val unit: RepeatUnit = RepeatUnit.TIME,
    /** Minutes for [RepeatUnit.TIME], otherwise the number of days, weeks or months. */
    val every: Int = 60,
    /** [RepeatUnit.WEEKS] only: ISO weekdays to fire on. Empty means the start moment's weekday. */
    val weekdays: Set<Int> = emptySet(),
    /** [RepeatUnit.MONTHS] only: days of the month. 29–31 fall back to the last day of shorter months. */
    val monthDays: Set<Int> = emptySet(),
    /**
     * Calendar units only: the times of day to fire at on every date the rule picks, as minutes
     * from local midnight — so 07:00 and 19:00 is `{420, 1140}`. Empty means the start moment's
     * own time of day, which is what every calendar repeat did before times could be picked.
     */
    val timesOfDay: Set<Int> = emptySet(),
) {
    val isCalendar: Boolean
        get() = unit != RepeatUnit.TIME

    /** The times of day that can actually match, in order; an interval repeat has none. */
    val activeTimes: List<Int>
        get() = if (isCalendar) {
            timesOfDay.filter { it in 0 until Reminder.MINUTES_PER_DAY }.sorted()
        } else {
            emptyList()
        }

    /** True when the repeat names its own times, so the start moment only picks the first day. */
    val hasOwnTimes: Boolean
        get() = activeTimes.isNotEmpty()

    /**
     * The shortest gap there can be between two events, in minutes. An early alert must be
     * shorter than this, or it would land before the previous event.
     */
    val shortestGapMinutes: Long
        get() {
            val betweenDates = shortestDateGapMinutes
            val times = activeTimes
            if (times.size < 2) return betweenDates
            // Several times a day: the closest two events are either two times on the same date,
            // or the last time of one date and the first time of the next one.
            val gaps = times.zipWithNext { a, b -> (b - a).toLong() } +
                (betweenDates - (times.last() - times.first()))
            return gaps.min().coerceAtLeast(1)
        }

    /** The shortest gap between two dates this repeat fires on, in minutes. */
    private val shortestDateGapMinutes: Long
        get() {
            val n = every.coerceAtLeast(1).toLong()
            return when (unit) {
                RepeatUnit.TIME -> n
                RepeatUnit.DAYS -> n * DAY_MINUTES
                RepeatUnit.WEEKS -> {
                    val days = weekdays.sorted()
                    if (days.size <= 1) {
                        n * 7 * DAY_MINUTES
                    } else {
                        val gaps = days.zipWithNext { a, b -> (b - a).toLong() } +
                            (n * 7 - (days.last() - days.first()))
                        gaps.min() * DAY_MINUTES
                    }
                }

                RepeatUnit.MONTHS -> {
                    val days = monthDays.sorted()
                    if (days.size <= 1) {
                        n * SHORTEST_MONTH_DAYS * DAY_MINUTES
                    } else {
                        // Day 31 can collapse onto day 30 in a short month, so count conservatively.
                        val gaps = days.zipWithNext { a, b -> (b - a).toLong() } +
                            (n * SHORTEST_MONTH_DAYS - (days.last() - days.first()))
                        (gaps.min() - 1).coerceAtLeast(1) * DAY_MINUTES
                    }
                }
            }
        }

    private companion object {
        const val DAY_MINUTES = 24L * 60
        const val SHORTEST_MONTH_DAYS = 28L
    }
}

/**
 * One slot in which an interval reminder may fire: on [days], between [startMinute] and
 * [endMinute] (minutes from local midnight). A window where [startMinute] >= [endMinute] wraps
 * past midnight, so 22:00–06:00 is a valid night window.
 */
@Serializable
data class TimeWindow(
    /** ISO weekday numbers, 1 = Monday … 7 = Sunday. */
    val days: Set<Int> = Reminder.WEEKDAYS,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
) {
    val isAllDay: Boolean
        get() = startMinute == 0 && endMinute >= Reminder.MINUTES_PER_DAY

    val isOvernight: Boolean
        get() = !isAllDay && startMinute >= endMinute
}

@Serializable
enum class AlertStyle { NOTIFICATION, ALARM }

@Serializable
enum class SoundMode {
    /** Whatever Settings say for this alert style. */
    DEFAULT,
    SILENT,
    /** [SoundChoice.toneUri] */
    TONE,
    /** Text-to-speech reads the name and message. */
    SPEAK,
}

@Serializable
data class SoundChoice(
    val mode: SoundMode = SoundMode.DEFAULT,
    val toneUri: String? = null,
)

/**
 * One recurring reminder.
 *
 * It fires on its [repeat] rule — for interval reminders only inside [windows] — with optional
 * [earlyAlerts] ahead of each event. The math lives in `Schedule.kt`.
 *
 * Every reminder has a fixed grid it fires on, anchored to [startAtMillis] when the user pinned a
 * start moment and to [anchorMillis] (the moment it was switched on) otherwise. Counting from the
 * anchor rather than from whenever the last alarm happened to arrive is what keeps a reminder
 * from drifting later and later.
 */
@Serializable
data class Reminder(
    val id: Int,
    val name: String = "",
    val message: String = "",
    val enabled: Boolean = true,
    val colorIndex: Int = 0,
    val repeat: Repeat = Repeat(),
    /** Interval reminders only. Empty means "any time, any day". */
    val windows: List<TimeWindow> = emptyList(),
    /** Epoch millis of the first event the user pinned, 0 when the timer starts right away. */
    val startAtMillis: Long = 0L,
    /** When the timer was last switched on; the grid's origin when no start moment is pinned. */
    val anchorMillis: Long = 0L,
    /** Minutes before each event to alert as well, e.g. [10, 5]. */
    val earlyAlerts: List<Int> = emptyList(),
    val alertStyle: AlertStyle = AlertStyle.NOTIFICATION,
    val sound: SoundChoice = SoundChoice(),
    val vibrationEnabled: Boolean = true,
    /** Position in the list when sorted by hand. */
    val sortIndex: Int = 0,
    /** Epoch millis of the armed alert, 0 when not scheduled. */
    val nextTriggerAt: Long = 0L,
    /** The event the armed alert belongs to; differs from [nextTriggerAt] for early alerts. */
    val nextEventAt: Long = 0L,
    /** A snoozed alarm rings again at this moment; 0 when nothing is snoozed. */
    val snoozeUntil: Long = 0L,
) {
    /** True when the user pinned an explicit first-event date and time. */
    val hasStartMoment: Boolean
        get() = startAtMillis > 0L

    /**
     * When a reminder that has not begun yet will first fire: the first event the schedule
     * produces, which is later than the start moment when that falls outside the chosen days.
     */
    val firstEventAt: Long
        get() = if (nextEventAt > 0L) nextEventAt else startAtMillis

    /** The origin of this reminder's grid. */
    val effectiveAnchor: Long
        get() = if (hasStartMoment) startAtMillis else anchorMillis

    /** Interval length for [RepeatUnit.TIME]; the editor and list show it as "every …". */
    val intervalMinutes: Int
        get() = if (repeat.unit == RepeatUnit.TIME) repeat.every else 0

    /** Roughly how long one cycle lasts, for the progress ring. */
    val cycleMillis: Long
        get() = repeat.shortestGapMinutes * 60_000L

    /** Windows that can actually match; only interval reminders have any. */
    val activeWindows: List<TimeWindow>
        get() = if (repeat.isCalendar) emptyList() else windows.filter { it.days.isNotEmpty() }

    /**
     * The times of day a calendar reminder goes off at, in order — the start moment's own time
     * when the repeat names none, so the editor and the summary always have one to show.
     */
    fun calendarTimes(zone: ZoneId = ZoneId.systemDefault()): List<Int> = when {
        !repeat.isCalendar -> emptyList()
        repeat.hasOwnTimes -> repeat.activeTimes
        hasStartMoment -> listOf(minuteOfDayOf(startAtMillis, zone))
        else -> emptyList()
    }

    /** True while the start moment is still ahead, so the loop has not begun yet. */
    fun isPending(nowMillis: Long = System.currentTimeMillis()): Boolean =
        hasStartMoment && startAtMillis > nowMillis

    /** Whether the reminder could ever fire with its current settings. */
    val isSchedulable: Boolean
        get() = when {
            repeat.every <= 0 -> false
            repeat.isCalendar -> hasStartMoment
            windows.isEmpty() -> true
            else -> activeWindows.isNotEmpty() && windows.all { it.days.isNotEmpty() }
        }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)

        /** The most early alerts a reminder may carry, so a single event never turns into a barrage. */
        const val MAX_EARLY_ALERTS = 3

        /**
         * The most times of day a calendar repeat may name. Past a dozen, an interval timer with
         * a window is the better tool — and it keeps the editor a list rather than a timetable.
         */
        const val MAX_TIMES_PER_DAY = 12

        fun minuteToLocalTime(minuteOfDay: Int): LocalTime {
            val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
            return LocalTime.of(clamped / 60, clamped % 60)
        }

        /** The minute of the local day [epochMillis] falls on, e.g. 07:00 -> 420. */
        fun minuteOfDayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
            localDateTimeOf(epochMillis, zone).toLocalTime().let { it.hour * 60 + it.minute }

        /** Builds an epoch-millis start moment from a local date and a minute of the day. */
        fun startMomentOf(
            date: LocalDate,
            minuteOfDay: Int,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Long = LocalDateTime.of(date, minuteToLocalTime(minuteOfDay))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        fun localDateTimeOf(
            epochMillis: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): LocalDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
    }
}
