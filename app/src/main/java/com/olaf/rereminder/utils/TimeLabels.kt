package com.olaf.rereminder.utils

import android.content.Context
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Times as the notification and `{next}` show them; outside Compose, so usable in receivers. */
object TimeLabels {

    /** Wall-clock time in the user's locale, e.g. "15:30" or "3:30 PM". */
    fun clock(epochMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

    /**
     * A moment relative to [nowMillis]: "10:30" today, "Tomorrow 10:30", "Sat 10:30" within the
     * week, and "Sat, 17 Oct · 10:30" beyond that. A bare time for a moment next weekend is what
     * made `{next}` look wrong in issue #11.
     */
    fun moment(
        context: Context,
        epochMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val locale = Locale.getDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = clock(epochMillis)
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> time
            days == 1L -> context.getString(R.string.moment_day_time, context.getString(R.string.start_tomorrow), time)
            days in 2..6 -> context.getString(
                R.string.moment_day_time,
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                time,
            )

            else -> context.getString(R.string.schedule_summary, calendarDate(date, today, locale), time)
        }
    }

    private fun calendarDate(date: LocalDate, today: LocalDate, locale: Locale): String {
        val pattern = if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** The length of one cycle: "1 h 30 min", "2 days", "3 weeks", "1 month". */
    fun repeatLength(context: Context, repeat: Repeat): String {
        val res = context.resources
        return when (repeat.unit) {
            RepeatUnit.TIME -> {
                val hours = repeat.every / 60
                val minutes = repeat.every % 60
                when {
                    hours > 0 && minutes > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
                    hours > 0 -> context.getString(R.string.duration_hours, hours)
                    else -> context.getString(R.string.duration_minutes, minutes.coerceAtLeast(1))
                }
            }

            RepeatUnit.DAYS -> res.getQuantityString(R.plurals.duration_days, repeat.every, repeat.every)
            RepeatUnit.WEEKS -> res.getQuantityString(R.plurals.duration_weeks, repeat.every, repeat.every)
            RepeatUnit.MONTHS -> res.getQuantityString(R.plurals.duration_months, repeat.every, repeat.every)
        }
    }

    /** "5 min", "1 h", "1 h 30 min", "1 d" — for early-alert offsets. */
    fun offset(context: Context, minutes: Int): String {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        return when {
            days > 0 && hours == 0 && mins == 0 -> context.resources.getQuantityString(R.plurals.duration_days, days, days)
            days > 0 -> context.getString(R.string.duration_days_hours, days, hours)
            hours > 0 && mins > 0 -> context.getString(R.string.duration_hours_minutes, hours, mins)
            hours > 0 -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_minutes, mins.coerceAtLeast(1))
        }
    }
}
