package com.olaf.rereminder.ui.editor

import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * The edits the timing card makes to a draft. Pure — no Compose, no clock unless one is passed in —
 * so the rules that keep a draft consistent are covered by plain unit tests.
 *
 * A calendar repeat (days, weeks, months) is shown as two plain rows: the date it starts on and
 * the time or times of day it goes off at. As soon as the user touches either one, the draft keeps
 * its times in [Repeat.timesOfDay] and its start at the midnight of the start date, so the start
 * only says which day comes first and never hides one of that day's times.
 */

/** What a freshly added time of day starts out at, before the user moves the clock. */
internal const val DEFAULT_NEW_TIME_MINUTE = 12 * 60

/**
 * Applies a new repeat rule and keeps the rest of the reminder consistent with it: calendar
 * repeats need a start to count from, and early alerts must stay shorter than the gap between two
 * events.
 *
 * The repeat dialog only sets the unit and the dates, so the times of day are carried over — also
 * through an interval, where they do nothing, so trying "every 2 hours" and going back to "every
 * day" still finds 7:00 and 19:00. Saving drops them from an interval for good.
 */
internal fun Reminder.withRepeat(
    repeat: Repeat,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Reminder {
    val next = repeat.copy(timesOfDay = this.repeat.timesOfDay)
    val start = when {
        next.isCalendar && !hasStartMoment -> nextFullHour(nowMillis, zone)
        // An interval counts from a moment, not a day: start it at the first of the times.
        !next.isCalendar && this.repeat.isCalendar && this.repeat.hasOwnTimes && hasStartMoment ->
            Reminder.startMomentOf(dateOf(startAtMillis, zone), this.repeat.activeTimes.first(), zone)

        else -> startAtMillis
    }
    return copy(
        repeat = next,
        startAtMillis = start,
        earlyAlerts = earlyAlerts.filter { it < next.shortestGapMinutes },
    )
}

/** Moves a calendar reminder to start on [date], keeping the times of day it goes off at. */
internal fun Reminder.withStartDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Reminder {
    val times = calendarTimes(zone).ifEmpty { listOf(DEFAULT_NEW_TIME_MINUTE) }
    return copy(startAtMillis = startOfDay(date, zone)).withTimes(times, zone)
}

/** Sets the time of day at [index] to [minuteOfDay], or adds it when [index] is not in the list. */
internal fun Reminder.withTime(
    index: Int,
    minuteOfDay: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Reminder {
    val times = calendarTimes(zone).toMutableList()
    if (index in times.indices) times[index] = minuteOfDay else times += minuteOfDay
    return withTimes(times, zone)
}

/** Removes the time of day at [index]; the last one stays, a calendar repeat needs a time. */
internal fun Reminder.withoutTime(index: Int, zone: ZoneId = ZoneId.systemDefault()): Reminder =
    withTimes(calendarTimes(zone).filterIndexed { i, _ -> i != index }, zone)

internal fun Reminder.withTimes(times: List<Int>, zone: ZoneId = ZoneId.systemDefault()): Reminder {
    val picked = times
        .filter { it in 0 until Reminder.MINUTES_PER_DAY }
        .distinct()
        .sorted()
        .take(Reminder.MAX_TIMES_PER_DAY)
        .toSet()
    if (picked.isEmpty()) return this
    val next = repeat.copy(timesOfDay = picked)
    return copy(
        repeat = next,
        // With times of its own the start only names the first day: from its midnight, so a
        // morning time on the start date is not cut off by an afternoon start left over from before.
        startAtMillis = if (hasStartMoment) startOfDay(dateOf(startAtMillis, zone), zone) else startAtMillis,
        // Two times an hour apart leave no room for an alert two hours ahead of each of them.
        earlyAlerts = earlyAlerts.filter { it < next.shortestGapMinutes },
    )
}

/** Times of day only mean something for a calendar repeat; an interval keeps none once saved. */
internal fun Reminder.withoutStrayTimes(): Reminder =
    if (repeat.isCalendar || repeat.timesOfDay.isEmpty()) this else copy(repeat = repeat.copy(timesOfDay = emptySet()))

private fun nextFullHour(nowMillis: Long, zone: ZoneId): Long =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        .plusHours(1).withMinute(0).withSecond(0).withNano(0)
        .atZone(zone).toInstant().toEpochMilli()

private fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

private fun startOfDay(date: LocalDate, zone: ZoneId): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()
