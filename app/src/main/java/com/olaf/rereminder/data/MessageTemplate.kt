package com.olaf.rereminder.data

import android.content.Context
import com.olaf.rereminder.R
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
                MessageVariable.TIME -> formatTime(nowMillis)
                MessageVariable.DATE -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(nowMillis))
                MessageVariable.DAY -> Instant.ofEpochMilli(nowMillis)
                    .atZone(zone)
                    .dayOfWeek
                    .getDisplayName(TextStyle.FULL, locale)

                MessageVariable.NAME -> reminder.name.ifBlank {
                    context.getString(R.string.reminder_default_name)
                }

                MessageVariable.INTERVAL -> formatInterval(context, reminder.intervalMinutes)
                MessageVariable.NEXT -> reminder.nextTriggerAfter(nowMillis, zone)
                    ?.let { formatTime(it) }
                    .orEmpty()
            }
            result = result.replace(variable.token, value)
        }
        return result
    }

    private fun formatTime(epochMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

    private fun formatInterval(context: Context, totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            hours > 0 -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_minutes, minutes.coerceAtLeast(1))
        }
    }
}
