package com.olaf.rereminder.ui.editor

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.olaf.rereminder.R
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.SoundChoice
import com.olaf.rereminder.data.SoundMode
import com.olaf.rereminder.data.TimeWindow
import com.olaf.rereminder.ui.components.DayPills
import com.olaf.rereminder.ui.components.SectionSwitchRow
import com.olaf.rereminder.ui.components.SectionValueRow
import com.olaf.rereminder.ui.format.formatMinuteOfDay
import com.olaf.rereminder.ui.format.offsetLabel
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tappable
import com.olaf.rereminder.utils.AlertPlayer
import com.olaf.rereminder.utils.AlertSound

// --- Time window ---------------------------------------------------------------------------

/** Edits one active-hours window: its days and its time span. */
@Composable
internal fun WindowDialog(
    initial: TimeWindow,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (TimeWindow) -> Unit,
) {
    val view = LocalView.current
    var window by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<TimeTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.window_dialog_title)) },
        text = {
            Column {
                DayPills(
                    selected = window.days,
                    accent = accent,
                    onToggle = { day ->
                        window = window.copy(days = if (day in window.days) window.days - day else window.days + day)
                    },
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { view.tap(); window = window.copy(days = Reminder.ALL_DAYS) },
                        label = { Text(stringResource(R.string.preset_every_day)) },
                    )
                    AssistChip(
                        onClick = { view.tap(); window = window.copy(days = Reminder.WEEKDAYS) },
                        label = { Text(stringResource(R.string.preset_weekdays)) },
                    )
                    AssistChip(
                        onClick = { view.tap(); window = window.copy(days = Reminder.WEEKEND) },
                        label = { Text(stringResource(R.string.preset_weekend)) },
                    )
                }
                SectionSwitchRow(
                    title = stringResource(R.string.editor_all_day),
                    checked = window.isAllDay,
                    onCheckedChange = { allDay ->
                        window = if (allDay) {
                            window.copy(startMinute = 0, endMinute = Reminder.MINUTES_PER_DAY)
                        } else {
                            window.copy(startMinute = 9 * 60, endMinute = 17 * 60)
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                AnimatedVisibility(
                    visible = !window.isAllDay,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        SectionValueRow(
                            title = stringResource(R.string.editor_from),
                            value = formatMinuteOfDay(window.startMinute),
                            onClick = { picking = TimeTarget.START },
                        )
                        SectionValueRow(
                            title = stringResource(R.string.editor_until),
                            value = formatMinuteOfDay(window.endMinute),
                            onClick = { picking = TimeTarget.END },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = window.days.isNotEmpty(),
                onClick = { view.tap(); onConfirm(window) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    picking?.let { target ->
        TimePickerDialog(
            initialMinuteOfDay = if (target == TimeTarget.START) window.startMinute else window.endMinute,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                window = if (target == TimeTarget.START) {
                    window.copy(startMinute = minute)
                } else {
                    window.copy(endMinute = minute)
                }
                picking = null
            },
        )
    }
}

private enum class TimeTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val time = Reminder.minuteToLocalTime(initialMinuteOfDay)
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --- Early alerts --------------------------------------------------------------------------

/** The offsets offered, in minutes. Only those shorter than the gap between events appear. */
private val EarlyAlertChoices = listOf(5, 10, 15, 30, 60, 24 * 60)

/** Pick up to [Reminder.MAX_EARLY_ALERTS] heads-ups ahead of each event (#11). */
@Composable
internal fun EarlyAlertsDialog(
    selected: List<Int>,
    shortestGapMinutes: Long,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    val view = LocalView.current
    val choices = EarlyAlertChoices.filter { it < shortestGapMinutes }
    var picked by rememberSaveable { mutableStateOf(selected.filter { it in choices }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.early_alerts_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.early_alerts_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (choices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.early_alerts_interval_too_short),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    choices.forEach { minutes ->
                        val isPicked = minutes in picked
                        FilterChip(
                            selected = isPicked,
                            enabled = isPicked || picked.size < Reminder.MAX_EARLY_ALERTS,
                            onClick = {
                                view.tap()
                                picked = if (isPicked) picked - minutes else picked + minutes
                            },
                            label = { Text(stringResource(R.string.early_alert_chip, offsetLabel(minutes))) },
                            leadingIcon = {
                                // The tick pops in rather than the chip just changing colour.
                                AnimatedVisibility(
                                    visible = isPicked,
                                    enter = scaleIn() + fadeIn(),
                                    exit = scaleOut() + fadeOut(),
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { view.tap(); onConfirm(picked.sortedDescending()) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// --- Sound ---------------------------------------------------------------------------------

/** The display name of a ringtone, resolved once per URI. */
@Composable
internal fun rememberToneTitle(uri: String?): String? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            runCatching { RingtoneManager.getRingtone(context, it.toUri())?.getTitle(context) }.getOrNull()
        }
    }
}

/** What the Sound row shows. */
@Composable
internal fun soundLabel(sound: SoundChoice): String = when (sound.mode) {
    SoundMode.DEFAULT -> stringResource(R.string.default_label)
    SoundMode.SILENT -> stringResource(R.string.sound_silent)
    SoundMode.SPEAK -> stringResource(R.string.sound_speak)
    SoundMode.TONE -> rememberToneTitle(sound.toneUri) ?: stringResource(R.string.sound_tone)
}

/**
 * This reminder's own sound (#13): the default from Settings, silence, a tone of its own, or the
 * message read aloud. A preview plays the current choice, so picking a sound doesn't mean waiting
 * for the reminder to fire.
 */
@Composable
internal fun SoundDialog(
    current: SoundChoice,
    alertStyle: AlertStyle,
    onPickTone: (currentUri: Uri?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (SoundChoice) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var choice by remember(current) { mutableStateOf(current) }
    val toneTitle = rememberToneTitle(choice.toneUri)

    // One preview at a time; it stops when the dialog goes away.
    var player by remember { mutableStateOf<AlertPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.stop() } }

    fun stopPreview() {
        player?.stop()
        player = null
    }

    val usage = if (alertStyle == AlertStyle.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
    val previewSound: AlertSound? = when (choice.mode) {
        SoundMode.SILENT -> null
        SoundMode.SPEAK -> AlertSound.Speech(stringResource(R.string.sound_preview_phrase))
        SoundMode.TONE -> AlertSound.Tone(choice.toneUri?.toUri())
        SoundMode.DEFAULT -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound_dialog_title)) },
        text = {
            Column {
                SoundOption(
                    label = stringResource(R.string.sound_default_option),
                    supporting = stringResource(
                        if (alertStyle == AlertStyle.ALARM) R.string.sound_default_supporting_alarm else R.string.sound_default_supporting
                    ),
                    selected = choice.mode == SoundMode.DEFAULT,
                    onClick = { stopPreview(); choice = choice.copy(mode = SoundMode.DEFAULT) },
                )
                SoundOption(
                    label = stringResource(R.string.sound_tone),
                    supporting = toneTitle ?: stringResource(R.string.sound_tone_choose),
                    selected = choice.mode == SoundMode.TONE,
                    onClick = {
                        stopPreview()
                        if (choice.toneUri == null) {
                            onPickTone(null)
                        } else {
                            choice = choice.copy(mode = SoundMode.TONE)
                        }
                    },
                    trailing = {
                        TextButton(onClick = { stopPreview(); onPickTone(choice.toneUri?.toUri()) }) {
                            Text(stringResource(R.string.sound_tone_change))
                        }
                    },
                )
                SoundOption(
                    label = stringResource(R.string.sound_speak),
                    supporting = stringResource(R.string.sound_speak_supporting),
                    selected = choice.mode == SoundMode.SPEAK,
                    onClick = { stopPreview(); choice = choice.copy(mode = SoundMode.SPEAK) },
                )
                SoundOption(
                    label = stringResource(R.string.sound_silent),
                    supporting = null,
                    selected = choice.mode == SoundMode.SILENT,
                    onClick = { stopPreview(); choice = choice.copy(mode = SoundMode.SILENT) },
                )

                AnimatedVisibility(
                    visible = previewSound != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PreviewButton(
                        playing = player != null,
                        onClick = {
                            view.tap()
                            if (player != null) {
                                stopPreview()
                            } else if (previewSound != null) {
                                val preview = AlertPlayer(context, usage, loop = false, maxMillis = PREVIEW_MILLIS)
                                player = preview
                                preview.play(previewSound, vibration = null) {
                                    if (player === preview) player = null
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { view.tap(); stopPreview(); onConfirm(choice) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { stopPreview(); onDismiss() }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val PREVIEW_MILLIS = 6_000L

@Composable
private fun SoundOption(
    label: String,
    supporting: String?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null && selected) trailing()
    }
}

/** Play/stop with a soft ring that pulses while the preview is sounding. */
@Composable
private fun PreviewButton(playing: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "previewPulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringScale",
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringAlpha",
    )
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .tappable(pressedScale = 0.95f, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            if (playing) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .scale(ringScale)
                        .graphicsLayer { alpha = ringAlpha }
                        .background(primary, CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (playing) R.string.sound_preview_stop else R.string.sound_preview),
            style = MaterialTheme.typography.labelLarge,
            color = primary,
        )
    }
}
