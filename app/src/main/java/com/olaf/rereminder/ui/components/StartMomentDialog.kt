package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.ui.format.formatStartMoment
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.tap
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

private enum class Step { DATE, TIME }

/**
 * Picks the exact moment a reminder's loop begins — the thing beta testers noticed was missing:
 * a timer could say "every 30 minutes" but never "starting Wednesday at 09:00".
 *
 * Date first, then time, because that is the order people say it in. Both steps live in one
 * dialog and slide across, so choosing a start never costs two separate popups.
 *
 * Built on a raw [Dialog] rather than an `AlertDialog`: Material's date grid asks for a fixed
 * 360dp, and an alert dialog's insets clip the last weekday column off it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartMomentDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val zone = remember { ZoneId.systemDefault() }

    val seed = remember(initialMillis) {
        if (initialMillis > 0L) {
            Reminder.localDateTimeOf(initialMillis, zone)
        } else {
            // Default to the next full hour: a sensible start nobody has to correct.
            LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        }
    }

    var step by remember { mutableStateOf(Step.DATE) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = seed.toLocalDate().toUtcMillis(),
    )
    val timePickerState = rememberTimePickerState(
        initialHour = seed.hour,
        initialMinute = seed.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )

    // The date picker reports UTC midnight, so read it back in UTC before taking the date.
    val pickedDate: LocalDate = datePickerState.selectedDateMillis
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        ?: seed.toLocalDate()

    val resultMillis = Reminder.startMomentOf(
        date = pickedDate,
        minuteOfDay = timePickerState.hour * 60 + timePickerState.minute,
        zone = zone,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 24.dp)
                .widthIn(max = 400.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (step == Step.DATE) {
                        Icons.Rounded.CalendarMonth
                    } else {
                        Icons.Rounded.Schedule
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.start_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                // Always shows the whole moment, so switching steps never hides half the answer.
                Text(
                    text = formatStartMoment(resultMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val forward = targetState == Step.TIME
                            val enter = slideInHorizontally(Motion.spatial()) { w ->
                                if (forward) w else -w
                            } + fadeIn()
                            val exit = slideOutHorizontally(Motion.spatial()) { w ->
                                if (forward) -w else w
                            } + fadeOut()
                            enter togetherWith exit
                        },
                        label = "startStep",
                    ) { current ->
                        when (current) {
                            Step.DATE -> DateStep(
                                zone = zone,
                                onStartNow = { onConfirm(0L) },
                                onPickShortcut = { date ->
                                    datePickerState.selectedDateMillis = date.toUtcMillis()
                                },
                                datePicker = {
                                    DatePicker(
                                        state = datePickerState,
                                        title = null,
                                        headline = null,
                                        // The dialog already prints the chosen moment above, and
                                        // the toggle would otherwise sit alone in an empty band.
                                        showModeToggle = false,
                                    )
                                },
                            )

                            Step.TIME -> Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                TimePicker(state = timePickerState)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (step) {
                        Step.DATE -> {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { view.tap(); step = Step.TIME }) {
                                Text(stringResource(R.string.start_pick_time))
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp).width(18.dp),
                                )
                            }
                        }

                        Step.TIME -> {
                            TextButton(onClick = { view.tap(); step = Step.DATE }) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp).width(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.start_pick_date))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { view.tap(); onConfirm(resultMillis) }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStep(
    zone: ZoneId,
    onStartNow: () -> Unit,
    onPickShortcut: (LocalDate) -> Unit,
    datePicker: @Composable () -> Unit,
) {
    val view = LocalView.current
    val today = LocalDate.now(zone)
    Column {
        // Most start dates are a day or two out; the grid is for everything else.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Un-pinning belongs here, next to the pinning: the editor row would otherwise need
            // a second control that only ever does one thing.
            SuggestionChip(
                onClick = { view.tap(); onStartNow() },
                label = { Text(stringResource(R.string.start_right_away)) },
            )
            DateShortcut(R.string.start_today, today, onPickShortcut)
            DateShortcut(R.string.start_tomorrow, today.plusDays(1), onPickShortcut)
            DateShortcut(
                R.string.start_next_monday,
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                onPickShortcut,
            )
        }
        datePicker()
    }
}

@Composable
private fun DateShortcut(labelRes: Int, date: LocalDate, onPick: (LocalDate) -> Unit) {
    val view = LocalView.current
    SuggestionChip(
        onClick = { view.tap(); onPick(date) },
        label = { Text(stringResource(labelRes)) },
    )
}

private fun LocalDate.toUtcMillis(): Long =
    atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC).toEpochMilli()
