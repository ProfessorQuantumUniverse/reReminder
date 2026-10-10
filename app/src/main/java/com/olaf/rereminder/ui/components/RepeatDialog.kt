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
import androidx.compose.foundation.layout.Column
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
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.ui.format.intervalLabel
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

    val result = when (unit) {
        // The interval must never be zero, otherwise the alarm would fire immediately.
        RepeatUnit.TIME -> Repeat(RepeatUnit.TIME, (hours * 60 + minutes).coerceAtLeast(1))
        RepeatUnit.DAYS -> Repeat(RepeatUnit.DAYS, count)
        RepeatUnit.WEEKS -> Repeat(RepeatUnit.WEEKS, count, weekdays = weekdays)
        RepeatUnit.MONTHS -> Repeat(RepeatUnit.MONTHS, count, monthDays = monthDays)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interval_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val units = RepeatUnit.entries
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
                            label = {
                                Text(
                                    stringResource(unitLabel(option)),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                        )
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
                    // One height for every tab, so the dialog does not jump when switching.
                    modifier = Modifier.heightIn(min = TAB_MIN_HEIGHT_DP.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { shown ->
                    when (shown) {
                        RepeatUnit.TIME -> TimeTab(
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

                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CountWheel(
                                unit = shown,
                                count = count.coerceIn(1, MaxCount.getValue(shown)),
                                onCount = { count = it },
                            )
                            when (shown) {
                                RepeatUnit.WEEKS -> {
                                    Spacer(Modifier.height(16.dp))
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
                                    Spacer(Modifier.height(16.dp))
                                    MonthDayGrid(
                                        selected = monthDays,
                                        accent = accent,
                                        onToggle = { day ->
                                            val next = if (day in monthDays) monthDays - day else monthDays + day
                                            if (next.isNotEmpty()) monthDays = next
                                        },
                                    )
                                    if (monthDays.any { it > 28 }) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.repeat_month_end_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
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

@Composable
private fun TimeTab(
    hours: Int,
    minutes: Int,
    onHours: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onPreset: (Int) -> Unit,
) {
    val total = hours * 60 + minutes
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
                    visibleItems = WHEEL_ROWS,
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
                    visibleItems = WHEEL_ROWS,
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

        Spacer(Modifier.height(16.dp))

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
                    visibleItems = WHEEL_ROWS,
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

/** Days 1–31 in a compact seven-column grid. */
@Composable
private fun MonthDayGrid(selected: Set<Int>, accent: Color, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..31).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    TogglePill(
                        label = day.toString(),
                        selected = day in selected,
                        accent = accent,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        onClick = { onToggle(day) },
                    )
                }
                // Keep the last row's cells the same size as the others.
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
