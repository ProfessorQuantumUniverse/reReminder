package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.olaf.rereminder.R
import com.olaf.rereminder.data.MonthWeekday
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.format.monthWeekChip
import com.olaf.rereminder.ui.format.repeatLabel
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tick
import java.util.Locale

/** One flick from a preset covers almost every reminder people actually set. */
private val QuickIntervals = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 240)

private const val WHEEL_ITEM_HEIGHT_DP = 46
private const val WHEEL_ROWS = 5
private const val MAX_HOURS = 72
private const val TAB_MIN_HEIGHT_DP = 430

/**
 * Below this much screen height — a phone or a small tablet held sideways — the dialog drops its
 * fixed tab height and the wheels show three rows, so the day grid is not pushed off the screen.
 */
private const val COMPACT_HEIGHT_DP = 640

@Composable
private fun isCompactHeight(): Boolean = screenSize().height < COMPACT_HEIGHT_DP.dp

@Composable
private fun wheelRows(): Int = if (isCompactHeight()) 3 else WHEEL_ROWS


private val MaxCount = mapOf(
    RepeatUnit.DAYS to 365,
    RepeatUnit.WEEKS to 52,
    RepeatUnit.MONTHS to 24,
)

/**
 * How often a reminder repeats (#7).
 *
 * The classic hours-and-minutes interval stays the first tab and looks exactly as before; days,
 * weeks and months sit one tap away instead of crowding it. Weeks can pick weekdays and months
 * can pick days of the month — enough for "every 2 weeks on Mon and Thu" or "every 3 months on
 * the 15th" without turning the dialog into a rules engine.
 *
 * [anchorWeekday] and [anchorMonthDay] come from the start moment; switching to weeks or months
 * pre-selects them, so the first thing the user sees is already a sensible rule.
 */
@Composable
fun RepeatDialog(
    current: Repeat,
    accent: Color,
    anchorWeekday: Int,
    anchorMonthDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Repeat) -> Unit,
) {
    val view = LocalView.current
    val screen = screenSize()
    val sideBySide = isCompactHeight() && screen.width > screen.height

    var unit by rememberSaveable { mutableStateOf(current.unit) }
    var hours by rememberSaveable {
        mutableIntStateOf(if (current.unit == RepeatUnit.TIME) (current.every / 60).coerceIn(0, MAX_HOURS) else 0)
    }
    var minutes by rememberSaveable {
        mutableIntStateOf(if (current.unit == RepeatUnit.TIME) current.every % 60 else 30)
    }
    var count by rememberSaveable {
        mutableIntStateOf(if (current.unit != RepeatUnit.TIME) current.every.coerceAtLeast(1) else 1)
    }
    var weekdays by rememberSaveable { mutableStateOf(current.weekdays.ifEmpty { setOf(anchorWeekday) }) }
    var monthDays by rememberSaveable { mutableStateOf(current.monthDays.ifEmpty { setOf(anchorMonthDay) }) }
    // "The first Friday" (#22): pre-set to the start date's own place in its month.
    var byWeekday by rememberSaveable { mutableStateOf(current.monthWeekday != null) }
    var monthWeek by rememberSaveable {
        mutableIntStateOf(current.monthWeekday?.week ?: weekOfMonth(anchorMonthDay))
    }
    var monthWeekDay by rememberSaveable { mutableIntStateOf(current.monthWeekday?.day ?: anchorWeekday) }

    val result = when (unit) {
        // The interval must never be zero, otherwise the alarm would fire immediately.
        RepeatUnit.TIME -> Repeat(RepeatUnit.TIME, (hours * 60 + minutes).coerceAtLeast(1))
        RepeatUnit.DAYS -> Repeat(RepeatUnit.DAYS, count)
        RepeatUnit.WEEKS -> Repeat(RepeatUnit.WEEKS, count, weekdays = weekdays)
        RepeatUnit.MONTHS -> if (byWeekday) {
            Repeat(RepeatUnit.MONTHS, count, monthWeekday = MonthWeekday(monthWeek, monthWeekDay))
        } else {
            Repeat(RepeatUnit.MONTHS, count, monthDays = monthDays)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // On a very short screen the headline below already says what is being set; the dialog
        // also takes Material's full width, so the wheel and the days fit side by side.
        title = if (isVeryShort()) null else ({ Text(stringResource(R.string.interval_dialog_title)) }),
        properties = DialogProperties(usePlatformDefaultWidth = !sideBySide),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val units = RepeatUnit.entries
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val labels = units.map { stringResource(unitLabel(it)) }
                    val labelStyle = rememberSegmentLabelStyle(labels, maxWidth, MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        units.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = unit == option,
                                onClick = {
                                    view.tick()
                                    if (unit != option && option != RepeatUnit.TIME) {
                                        count = count.coerceIn(1, MaxCount.getValue(option))
                                    }
                                    unit = option
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, units.size),
                                icon = {},
                                label = { Text(labels[index], style = labelStyle, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // The headline reacts to the wheel while it is still moving, so the value being
                // chosen is readable without waiting for the wheel to settle.
                AnimatedContent(
                    targetState = repeatLabel(result),
                    transitionSpec = {
                        val enter = slideInVertically(Motion.spatial()) { h -> h / 2 } +
                            fadeIn(spring(stiffness = Spring.StiffnessMedium))
                        val exit = slideOutVertically(Motion.spatial()) { h -> -h / 2 } +
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        enter togetherWith exit
                    },
                    label = "repeatHeadline",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = unit,
                    transitionSpec = {
                        (fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade()))
                            .using(SizeTransform(clip = false))
                    },
                    label = "repeatUnit",
                    // One height for every tab, so the dialog does not jump when switching — unless
                    // the screen is too short to spare it.
                    modifier = Modifier.heightIn(min = if (isCompactHeight()) 0.dp else TAB_MIN_HEIGHT_DP.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { shown ->
                    when (shown) {
                        RepeatUnit.TIME -> TimeTab(
                            sideBySide = sideBySide,
                            hours = hours,
                            minutes = minutes,
                            onHours = { hours = it },
                            onMinutes = { minutes = it },
                            onPreset = { preset ->
                                view.tap()
                                hours = preset / 60
                                minutes = preset % 60
                            },
                        )

                        // Held sideways there is no height for the wheel above the days: they sit
                        // side by side instead, so nothing has to be scrolled to.
                        else -> SideBySideOrStacked(
                            sideBySide = sideBySide,
                            first = {
                                CountWheel(
                                    unit = shown,
                                    count = count.coerceIn(1, MaxCount.getValue(shown)),
                                    onCount = { count = it },
                                )
                            },
                        ) {
                            when (shown) {
                                RepeatUnit.WEEKS -> {
                                    if (!sideBySide) Spacer(Modifier.height(16.dp))
                                    DayPills(
                                        selected = weekdays,
                                        accent = accent,
                                        onToggle = { day ->
                                            val next = if (day in weekdays) weekdays - day else weekdays + day
                                            // At least one day stays picked; an empty set would
                                            // silently mean "the start's weekday".
                                            if (next.isNotEmpty()) weekdays = next
                                        },
                                    )
                                }

                                RepeatUnit.MONTHS -> {
                                    if (!sideBySide) Spacer(Modifier.height(16.dp))
                                    // Days of the month stay the default; the weekday is one tap away.
                                    val modes = listOf(
                                        stringResource(R.string.repeat_month_by_day),
                                        stringResource(R.string.repeat_month_by_weekday),
                                    )
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val modeStyle = rememberSegmentLabelStyle(modes, maxWidth, MaterialTheme.typography.labelLarge)
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            modes.forEachIndexed { index, label ->
                                                SegmentedButton(
                                                    selected = byWeekday == (index == 1),
                                                    onClick = { view.tick(); byWeekday = index == 1 },
                                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                                    icon = {},
                                                    label = { Text(label, style = modeStyle, maxLines = 1, softWrap = false) },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (byWeekday) {
                                        MonthWeekdayPicker(
                                            week = monthWeek,
                                            day = monthWeekDay,
                                            accent = accent,
                                            onWeek = { view.tap(); monthWeek = it },
                                            onDay = { monthWeekDay = it },
                                        )
                                    } else {
                                        MonthDayGrid(
                                            cellAspect = if (sideBySide) 1.35f else 1f,
                                            selected = monthDays,
                                            accent = accent,
                                            onToggle = { day ->
                                                val next = if (day in monthDays) monthDays - day else monthDays + day
                                                if (next.isNotEmpty()) monthDays = next
                                            },
                                        )
                                        if (monthDays.any { it in 29..30 }) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.repeat_month_end_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { view.tap(); onConfirm(result) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun unitLabel(unit: RepeatUnit): Int = when (unit) {
    RepeatUnit.TIME -> R.string.repeat_unit_time
    RepeatUnit.DAYS -> R.string.repeat_unit_days
    RepeatUnit.WEEKS -> R.string.repeat_unit_weeks
    RepeatUnit.MONTHS -> R.string.repeat_unit_months
}

/**
 * [first] above [rest], or — when [sideBySide] — next to it, so a dialog held sideways on a phone
 * or small tablet needs no scrolling.
 */
@Composable
private fun SideBySideOrStacked(
    sideBySide: Boolean,
    first: @Composable () -> Unit,
    firstWeight: Float = 1f,
    restWeight: Float = 1.5f,
    rest: @Composable ColumnScope.() -> Unit,
) {
    if (sideBySide) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(firstWeight), contentAlignment = Alignment.Center) { first() }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(restWeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = rest,
            )
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            first()
            rest()
        }
    }
}

@Composable
private fun TimeTab(
    sideBySide: Boolean,
    hours: Int,
    minutes: Int,
    onHours: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onPreset: (Int) -> Unit,
) {
    val total = hours * 60 + minutes
    SideBySideOrStacked(sideBySide = sideBySide, firstWeight = 1.3f, restWeight = 1f, first = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WheelBand {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val hoursLabel = stringResource(R.string.hours_label)
                    val minutesLabel = stringResource(R.string.minutes_label)
                    WheelPicker(
                        values = (0..MAX_HOURS).toList(),
                        selected = hours,
                        onValueChange = onHours,
                        label = { it.twoDigits() },
                        contentDescriptionOf = { "$hoursLabel: $it" },
                        itemHeight = WHEEL_ITEM_HEIGHT_DP.dp,
                        visibleItems = wheelRows(),
                        modifier = Modifier.width(92.dp),
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    WheelPicker(
                        values = (0..59).toList(),
                        selected = minutes,
                        onValueChange = onMinutes,
                        label = { it.twoDigits() },
                        contentDescriptionOf = { "$minutesLabel: $it" },
                        itemHeight = WHEEL_ITEM_HEIGHT_DP.dp,
                        visibleItems = wheelRows(),
                        modifier = Modifier.width(92.dp),
                    )
                }
            }
    
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                WheelCaption(stringResource(R.string.hours_label))
                Spacer(Modifier.width(24.dp))
                WheelCaption(stringResource(R.string.minutes_label))
            }
        }
    }) {
        if (!sideBySide) Spacer(Modifier.height(16.dp))

        // Still one tap for the intervals people pick most of the time.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QuickIntervals.forEach { preset ->
                FilterChip(
                    selected = preset == total,
                    onClick = { onPreset(preset) },
                    label = { Text(intervalLabel(preset)) },
                )
            }
        }
    }
}

@Composable
private fun CountWheel(unit: RepeatUnit, count: Int, onCount: (Int) -> Unit) {
    val max = MaxCount.getValue(unit)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WheelBand {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelPicker(
                    values = (1..max).toList(),
                    selected = count,
                    onValueChange = onCount,
                    label = { it.toString() },
                    contentDescriptionOf = { it.toString() },
                    itemHeight = WHEEL_ITEM_HEIGHT_DP.dp,
                    visibleItems = wheelRows(),
                    modifier = Modifier.width(92.dp),
                )
            }
        }
        val caption = when (unit) {
            RepeatUnit.DAYS -> pluralStringResource(R.plurals.unit_days, count)
            RepeatUnit.WEEKS -> pluralStringResource(R.plurals.unit_weeks, count)
            else -> pluralStringResource(R.plurals.unit_months, count)
        }
        WheelCaption(caption)
    }
}

/** The band sits behind the wheels, so the numbers scroll through it. */
@Composable
private fun WheelBand(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT_DP.dp),
            content = {},
        )
        content()
    }
}

/**
 * Days 1–30 in a compact seven-column grid, then "Last day" across the rest of the last row.
 *
 * "Last day" is day 31, which every month clamps to its own last day — so the rule people ask for
 * (#22) is the one the schedule already had, now with a name instead of a number to guess at.
 */
@Composable
private fun MonthDayGrid(
    selected: Set<Int>,
    accent: Color,
    onToggle: (Int) -> Unit,
    /** Width over height of a day cell; held sideways the cells go flatter to save height. */
    cellAspect: Float = 1f,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val weeks = (1..30).chunked(7)
        weeks.forEachIndexed { index, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    TogglePill(
                        label = day.toString(),
                        selected = day in selected,
                        accent = accent,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(cellAspect),
                        onClick = { onToggle(day) },
                    )
                }
                val free = 7 - week.size
                if (index == weeks.lastIndex && free > 0) {
                    TogglePill(
                        label = stringResource(R.string.repeat_last_day_pill),
                        selected = LAST_DAY_OF_MONTH in selected,
                        accent = accent,
                        // As tall as a day cell: the cells' width is a seventh of the row.
                        modifier = Modifier
                            .weight(free.toFloat())
                            .aspectRatio(free * cellAspect),
                        onClick = { onToggle(LAST_DAY_OF_MONTH) },
                    )
                }
            }
        }
    }
}

/** Day 31: every shorter month falls back to its last day, so it is always the last one. */
private const val LAST_DAY_OF_MONTH = 31

/** The week of the month [dayOfMonth] falls in: 1–4, or last for the 29th onwards. */
private fun weekOfMonth(dayOfMonth: Int): Int =
    ((dayOfMonth - 1) / 7 + 1).let { if (it > 4) MonthWeekday.LAST else it }

/**
 * "The first … last" over the weekdays: two rows of the same pills, one week and one day. "Last"
 * gets a double cell, so the whole choice stays on one line in every language.
 */
@Composable
private fun MonthWeekdayPicker(
    week: Int,
    day: Int,
    accent: Color,
    onWeek: (Int) -> Unit,
    onDay: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(1, 2, 3, 4, MonthWeekday.LAST).forEach { option ->
                TogglePill(
                    label = stringResource(monthWeekChip(option)),
                    selected = option == week,
                    accent = accent,
                    modifier = Modifier
                        .weight(if (option == MonthWeekday.LAST) 2f else 1f)
                        .height(42.dp),
                    onClick = { onWeek(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DayPills(selected = setOf(day), accent = accent, onToggle = onDay)
    }
}

@Composable
private fun WheelCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(92.dp),
        textAlign = TextAlign.Center,
    )
}

private fun Int.twoDigits(): String = String.format(Locale.getDefault(), "%02d", this)

@Preview
@Composable
private fun RepeatDialogPreview() {
    ReReminderTheme(dynamicColor = false) {
        RepeatDialog(
            current = Repeat(RepeatUnit.WEEKS, 2, weekdays = setOf(1, 4)),
            accent = Color(0xFF3F7DE8),
            anchorWeekday = 1,
            anchorMonthDay = 15,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
