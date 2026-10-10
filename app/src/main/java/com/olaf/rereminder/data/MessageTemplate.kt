package com.olaf.rereminder.data

import android.content.Context
import com.olaf.rereminder.R
import com.olaf.rereminder.utils.TimeLabels
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * A placeholder users can drop into a reminder's message, e.g. "Time for a walk — it's {time}".
 *
 * [token] is what gets typed; [labelRes] names it in the editor's chip row.
 */
enum class MessageVariable(val token: String, val labelRes: Int) {
    TIME("{time}", R.string.variable_time),
    DATE("{date}", R.string.variable_date),
    DAY("{day}", R.string.variable_day),
    NAME("{name}", R.string.variable_name),
    INTERVAL("{interval}", R.string.variable_interval),
    NEXT("{next}", R.string.variable_next),
}

/** The timer's name, or a neutral fallback when the user left it empty. */
fun Reminder.displayName(context: Context): String =
    name.ifBlank { context.getString(R.string.reminder_default_name) }

object MessageTemplate {

    /**
     * Substitutes every [MessageVariable] in [template]. Unknown `{...}` sequences are left alone
     * so a message like "{not a variable}" survives untouched.
     *
     * `{next}` is the next *event* still ahead: for an early alert, the event it announces; for
     * the event's own alert, the one after it. It carries the day whenever that isn't today.
     */
    fun render(
        context: Context,
        template: String,
        reminder: Reminder,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (template.isBlank()) return context.getString(R.string.reminder_notification_text)
        if (!template.contains('{')) return template

        val locale = Locale.getDefault()
        var result = template

        for (variable in MessageVariable.entries) {
            if (!result.contains(variable.token)) continue
            val value = when (variable) {
                MessageVariable.TIME -> TimeLabels.clock(nowMillis)
                MessageVariable.DATE -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(nowMillis))
                MessageVariable.DAY -> Instant.ofEpochMilli(nowMillis)
                    .atZone(zone)
                    .dayOfWeek
                    .getDisplayName(TextStyle.FULL, locale)

                MessageVariable.NAME -> reminder.displayName(context)
                MessageVariable.INTERVAL -> TimeLabels.repeatLength(context, reminder.repeat)
                MessageVariable.NEXT -> reminder.nextEventAfter(nowMillis, zone)
                    ?.let { TimeLabels.moment(context, it, nowMillis, zone) }
                    .orEmpty()
            }
            result = result.replace(variable.token, value)
        }
        return result
    }
}
