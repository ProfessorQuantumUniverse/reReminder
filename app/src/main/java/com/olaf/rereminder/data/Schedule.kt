package com.olaf.rereminder.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * One alert the scheduler can arm.
 *
 * [eventAt] is the moment the reminder is about; [alertAt] is when the user hears about it —
 * the same moment, or [offsetMinutes] earlier for an early alert.
 */
data class Alert(
    val alertAt: Long,
    val eventAt: Long,
    val offsetMinutes: Int = 0,
) {
    val isEarly: Boolean get() = offsetMinutes > 0
}

/*
 * The schedule math. Everything here is pure — no Android, no clock — so it is covered by plain
 * unit tests, including daylight saving changes.
 *
 * Interval reminders fire on a grid. With no windows the grid starts at the anchor. With windows
 * each stretch of active time (consecutive windows merged) restarts the grid at its opening, so
 * "every 30 min, 09:00–17:00" fires at 09:00, 09:30 … — except in the stretch the anchor falls
 * into, where the grid starts at the anchor instead. A pinned start fires at the start itself;
 * a timer that was simply switched on fires one interval after that.
 *
 * Calendar reminders are walked date by date in local time at the start moment's time of day.
 */

/** The first event strictly after [fromMillis], or null when the schedule can never match. */
fun Reminder.nextEventAfter(fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (!isSchedulable) return null
    return if (repeat.isCalendar) {
        nextCalendarEventAfter(fromMillis, zone)
    } else {
        nextIntervalEventAfter(fromMillis, zone)
    }
}

/**
 * The first alert strictly after [fromMillis]: an event itself, or an early alert ahead of one.
 */
fun Reminder.nextAlertAfter(fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Alert? {
    val maxGap = repeat.shortestGapMinutes
    val offsets = (listOf(0) + earlyAlerts.filter { it > 0 && it < maxGap })
        .distinct()
        .take(Reminder.MAX_EARLY_ALERTS + 1)
    val maxOffsetMillis = offsets.max() * MINUTE

    var best: Alert? = null
    var event = nextEventAfter(fromMillis, zone)
    var guard = 0
    while (event != null && guard++ < MAX_EVENTS_TO_SCAN) {
        // No later event can produce an alert earlier than the best one found so far.
        if (best != null && event - maxOffsetMillis >= best.alertAt) break
        for (offset in offsets) {
            val alertAt = event - offset * MINUTE
            if (alertAt > fromMillis && (best == null || alertAt < best.alertAt)) {
                best = Alert(alertAt = alertAt, eventAt = event, offsetMinutes = offset)
            }
        }
        if (offsets.size == 1) break
        event = nextEventAfter(event, zone)
    }
    return best
}

/** True when [alertAt] is an alert this schedule really produces — not a stale leftover. */
fun Reminder.producesAlertAt(alertAt: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    nextAlertAfter(alertAt - 1, zone)?.alertAt == alertAt

/**
 * Whether [epochMillis] falls inside one of the reminder's windows. Reminders without windows
 * (and calendar reminders) are always "inside" once their start moment has passed.
 */
fun Reminder.isWithinWindowsAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (epochMillis < startAtMillis) return false
    if (repeat.isCalendar || windows.isEmpty()) return true

    // Checked window by window rather than through the merged spans: the list asks this several
    // times a second for every card.
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val minuteOfDay = dateTime.hour * 60 + dateTime.minute
    val today = dateTime.dayOfWeek.value
    val yesterday = dateTime.minusDays(1).dayOfWeek.value
    return activeWindows.any { window ->
        when {
            window.isAllDay -> today in window.days
            // Either the part of a window that opened today, or the tail of yesterday's.
            window.isOvernight -> (today in window.days && minuteOfDay >= window.startMinute) ||
                (yesterday in window.days && minuteOfDay < window.endMinute)

            else -> today in window.days && minuteOfDay in window.startMinute until window.endMinute
        }
    }
}

// --- Interval reminders ----------------------------------------------------------------------

private fun Reminder.nextIntervalEventAfter(fromMillis: Long, zone: ZoneId): Long? {
    val interval = repeat.every * MINUTE
    // Should never be 0 once the scheduler has run, but a fresh draft has no anchor yet.
    val anchor = effectiveAnchor.takeIf { it > 0L } ?: fromMillis
    val pinned = hasStartMoment

    if (activeWindows.isEmpty()) return gridAfter(anchor, pinned, interval, fromMillis)

    // Look around whichever is later — now or the anchor — so a far-off start still finds windows.
    val pivot = maxOf(fromMillis, anchor - 1)
    for (span in activeSpans(pivot, zone)) {
        if (span.close <= fromMillis || span.close <= anchor) continue
        val (base, includeBase) = if (anchor >= span.open) anchor to pinned else span.open to true
        val event = gridAfter(base, includeBase, interval, fromMillis)
        if (event < span.close) return event
    }
    return null
}

/** The first tick of `base + k·interval` after [after]; `k` starts at 0 only if [includeBase]. */
private fun gridAfter(base: Long, includeBase: Boolean, interval: Long, after: Long): Long {
    val first = if (includeBase) 0L else 1L
    val k = if (after < base) first else maxOf(first, (after - base) / interval + 1)
    return base + k * interval
}

/** A stretch of active time; [open] is [Long.MIN_VALUE] when it reaches back past the scan. */
private data class Span(val open: Long, val close: Long)

/**
 * The merged active stretches from a week before [aroundMillis] to a week and a bit after it.
 * Looking back a week is what lets "Mon–Fri, all day" be seen as one stretch from Monday 00:00.
 */
private fun Reminder.activeSpans(aroundMillis: Long, zone: ZoneId): List<Span> {
    val center = Instant.ofEpochMilli(aroundMillis).atZone(zone).toLocalDate()
    val firstDate = center.minusDays(SPAN_LOOKBACK_DAYS)
    val lastDate = center.plusDays(SPAN_LOOKAHEAD_DAYS)
    val scanStart = firstDate.atStartOfDay(zone).toInstant().toEpochMilli()

    val raw = mutableListOf<Span>()
    var date = firstDate
    while (!date.isAfter(lastDate)) {
        val weekday = date.dayOfWeek.value
        for (window in activeWindows) {
            if (weekday !in window.days) continue
            val open = if (window.isAllDay) {
                date.atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                localMillis(date, window.startMinute, zone)
            }
            val close = when {
                window.isAllDay -> date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                window.isOvernight -> localMillis(date.plusDays(1), window.endMinute, zone)
                else -> localMillis(date, window.endMinute, zone)
            }
            if (close > open) raw += Span(open, close)
        }
        date = date.plusDays(1)
    }

    val merged = mutableListOf<Span>()
    for (span in raw.sortedBy { it.open }) {
        val last = merged.lastOrNull()
        if (last != null && span.open <= last.close) {
            merged[merged.lastIndex] = last.copy(close = maxOf(last.close, span.close))
        } else {
            merged += span
        }
    }
    // A stretch that already runs at the start of the scan may have begun earlier still.
    return merged.map { if (it.open <= scanStart) it.copy(open = Long.MIN_VALUE) else it }
}

private fun localMillis(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
    if (minuteOfDay >= Reminder.MINUTES_PER_DAY) {
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    } else {
        date.atTime(Reminder.minuteToLocalTime(minuteOfDay)).atZone(zone).toInstant().toEpochMilli()
    }

// --- Calendar reminders ----------------------------------------------------------------------

private fun Reminder.nextCalendarEventAfter(fromMillis: Long, zone: ZoneId): Long? {
    val anchor = effectiveAnchor.takeIf { it > 0L } ?: return null
    val anchorTime = Instant.ofEpochMilli(anchor).atZone(zone)
    val time: LocalTime = anchorTime.toLocalTime()
    val anchorDate = anchorTime.toLocalDate()
    val every = repeat.every.coerceAtLeast(1)

    fun eventOn(date: LocalDate): Long = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    fun qualifies(date: LocalDate): Boolean {
        val at = eventOn(date)
        return at > fromMillis && at >= anchor
    }

    val fromDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
    val startDate = maxOf(anchorDate, fromDate)

    return when (repeat.unit) {
        RepeatUnit.DAYS -> {
            val elapsed = ChronoUnit.DAYS.between(anchorDate, startDate)
            var date = anchorDate.plusDays(ceilDiv(elapsed, every.toLong()) * every)
            while (!qualifies(date)) date = date.plusDays(every.toLong())
            eventOn(date)
        }

        RepeatUnit.WEEKS -> {
            val weekdays = repeat.weekdays.ifEmpty { setOf(anchorDate.dayOfWeek.value) }
            val anchorWeek = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var date = startDate
            repeat((every * 7 + 7) * 2) {
                val week = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeksApart = ChronoUnit.WEEKS.between(anchorWeek, week)
                if (weeksApart % every == 0L && date.dayOfWeek.value in weekdays && qualifies(date)) {
                    return eventOn(date)
                }
                date = date.plusDays(1)
            }
            null
        }

        RepeatUnit.MONTHS -> {
            val monthDays = repeat.monthDays.ifEmpty { setOf(anchorDate.dayOfMonth) }
            val anchorMonth = YearMonth.from(anchorDate)
            var month = YearMonth.from(startDate)
            repeat(every * 2 + 2) {
                if (ChronoUnit.MONTHS.between(anchorMonth, month) % every == 0L) {
                    val dates = monthDays
                        .map { month.atDay(it.coerceIn(1, month.lengthOfMonth())) }
                        .distinct()
                        .sorted()
                    dates.firstOrNull { it >= anchorDate && qualifies(it) }?.let { return eventOn(it) }
                }
                month = month.plusMonths(1)
            }
            null
        }

        RepeatUnit.TIME -> null
    }
}

private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0L) 0L else (a + b - 1) / b

private const val MINUTE = 60_000L
private const val MAX_EVENTS_TO_SCAN = 16
private const val SPAN_LOOKBACK_DAYS = 8L
private const val SPAN_LOOKAHEAD_DAYS = 9L
