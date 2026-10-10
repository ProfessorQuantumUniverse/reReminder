package com.olaf.rereminder.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.data.TimeWindow
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Human readable interval, e.g. "1 h 30 min", "45 min", "2 h". */
@Composable
fun intervalLabel(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    }
}

/** Counts down as "1:02:03" or "02:03" once under an hour. */
fun formatCountdown(remainingMillis: Long): String {
    val duration = remainingMillis.coerceAtLeast(0L).milliseconds
    return duration.toComponents { hours, minutes, seconds, _ ->
        if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * A coarse countdown for waits measured in days rather than seconds — a start date three weeks
 * out reads as "21 d 4 h", not as a five-digit hour count.
 */
@Composable
fun formatCoarseCountdown(remainingMillis: Long): String {
    val duration = remainingMillis.coerceAtLeast(0L).milliseconds
    return duration.toComponents { days, hours, minutes, _, _ ->
        when {
            days > 0 -> stringResource(R.string.duration_days_hours, days.toInt(), hours)
            hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
            else -> stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
        }
    }
}

/** Wall-clock time in the user's locale, e.g. "15:30" or "3:30 PM". */
fun formatClockTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

/** Date in the user's locale without the year when it is the current one. */
fun formatCalendarDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Reminder.localDateTimeOf(epochMillis, zone).toLocalDate()
    val pattern = if (date.year == LocalDate.now(zone).year) "EEE, d MMM" else "EEE, d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/**
 * The full start moment on one line, e.g. "Today · 09:00" or "Wed, 24 Sept · 09:00".
 *
 * Near dates get a word instead of a date, because "Tomorrow at 7" is what the user was thinking
 * when they set it.
 */
@Composable
fun formatStartMoment(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epochMillis <= 0L) return stringResource(R.string.start_immediately)

    val date = Reminder.localDateTimeOf(epochMillis, zone).toLocalDate()
    val today = LocalDate.now(zone)
    val dayLabel = when (date) {
        today -> stringResource(R.string.start_today)
        today.plusDays(1) -> stringResource(R.string.start_tomorrow)
        else -> formatCalendarDate(epochMillis, zone)
    }
    return stringResource(R.string.schedule_summary, dayLabel, formatClockTime(epochMillis))
}

/** Formats minutes-from-midnight as a localised time, e.g. 510 -> "8:30". */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    Reminder.minuteToLocalTime(minuteOfDay)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun dayInitial(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.NARROW, Locale.getDefault())

fun dayShortName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Mon–Fri", "Every day", or a comma list for arbitrary selections. */
@Composable
fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.schedule_never)
    days.size == 7 -> stringResource(R.string.schedule_every_day)
    days == Reminder.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
    days == setOf(6, 7) -> stringResource(R.string.schedule_weekends)
    else -> days.sorted().joinToString(", ") { dayShortName(DayOfWeek.of(it)) }
}

/** "Mon–Fri · 8:00–17:00". */
@Composable
fun windowLabel(window: TimeWindow): String {
    val days = daysLabel(window.days)
    if (window.days.isEmpty()) return days

    val hours = if (window.isAllDay) {
        stringResource(R.string.schedule_all_day)
    } else {
        stringResource(
            R.string.schedule_between,
            formatMinuteOfDay(window.startMinute),
            formatMinuteOfDay(window.endMinute),
        )
    }
    return stringResource(R.string.schedule_summary, days, hours)
}

/**
 * When a reminder may fire, in one short line: its window ("Mon–Fri · 9:00–17:00"), the number of
 * windows when there are several, the time of day for calendar repeats, or "Any time".
 */
@Composable
fun scheduleSummary(reminder: Reminder): String = when {
    reminder.repeat.isCalendar -> if (reminder.hasStartMoment) {
        stringResource(R.string.schedule_at_time, formatClockTime(reminder.startAtMillis))
    } else {
        stringResource(R.string.schedule_needs_start)
    }

    reminder.windows.isEmpty() -> stringResource(R.string.editor_schedule_always)
    reminder.windows.size == 1 -> windowLabel(reminder.windows.single())
    else -> pluralStringResource(R.plurals.schedule_window_count, reminder.windows.size, reminder.windows.size)
}

/**
 * The repeat rule as a sentence fragment: "Every 30 min", "Every day", "Every 2 weeks on Mon,
 * Thu", "Every month on the 15th".
 */
@Composable
fun repeatLabel(repeat: Repeat): String {
    val n = repeat.every.coerceAtLeast(1)
    return when (repeat.unit) {
        RepeatUnit.TIME -> stringResource(R.string.every_interval, intervalLabel(n))
        RepeatUnit.DAYS -> pluralStringResource(R.plurals.repeat_every_days, n, n)
        RepeatUnit.WEEKS -> {
            val base = pluralStringResource(R.plurals.repeat_every_weeks, n, n)
            if (repeat.weekdays.isEmpty()) {
                base
            } else {
                stringResource(R.string.repeat_on_days, base, daysLabel(repeat.weekdays))
            }
        }

        RepeatUnit.MONTHS -> {
            val base = pluralStringResource(R.plurals.repeat_every_months, n, n)
            if (repeat.monthDays.isEmpty()) {
                base
            } else {
                stringResource(
                    R.string.repeat_on_month_days,
                    base,
                    repeat.monthDays.sorted().joinToString(", ") { ordinal(it) },
                )
            }
        }
    }
}

/** "1st", "22nd" in English; "1.", "22." elsewhere, which reads correctly in German. */
fun ordinal(day: Int, locale: Locale = Locale.getDefault()): String {
    if (locale.language != "en") return "$day."
    val suffix = when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

/** "10 min", "1 h", "1 day" — an early alert's offset. */
@Composable
fun offsetLabel(minutes: Int): String = when {
    minutes >= 24 * 60 && minutes % (24 * 60) == 0 ->
        pluralStringResource(R.plurals.duration_days, minutes / (24 * 60), minutes / (24 * 60))

    else -> intervalLabel(minutes)
}

/** "None", "10 min before", "10 & 5 min before" style summary for the editor row. */
@Composable
fun earlyAlertsLabel(offsets: List<Int>): String {
    if (offsets.isEmpty()) return stringResource(R.string.early_alerts_none)
    val joined = offsets.sortedDescending().map { offsetLabel(it) }.joinToString(", ")
    return stringResource(R.string.early_alerts_before, joined)
}
