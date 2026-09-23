package com.olaf.rereminder.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
import com.olaf.rereminder.data.MessageTemplate
import com.olaf.rereminder.data.MessageVariable
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.ui.components.CollapsingHeader
import com.olaf.rereminder.ui.components.IntervalPickerDialogCompose
import com.olaf.rereminder.ui.components.SectionCard
import com.olaf.rereminder.ui.components.SectionDivider
import com.olaf.rereminder.ui.components.SectionSwitchRow
import com.olaf.rereminder.ui.components.SectionValueRow
import com.olaf.rereminder.ui.components.StartMomentDialog
import com.olaf.rereminder.ui.components.rememberCollapseProgress
import com.olaf.rereminder.ui.format.dayInitial
import com.olaf.rereminder.ui.format.formatMinuteOfDay
import com.olaf.rereminder.ui.format.formatStartMoment
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.ReminderAccents
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tappable
import java.time.DayOfWeek

@Composable
fun ReminderEditorRoute(
    onClose: () -> Unit,
    viewModel: ReminderEditorViewModel = viewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    ReminderEditorScreen(
        draft = draft,
        isNew = viewModel.isNew,
        onChange = viewModel::update,
        onSave = {
            viewModel.save()
            onClose()
        },
        onDelete = {
            viewModel.delete()
            onClose()
        },
        onBack = onClose,
    )
}

/**
 * The reminder editor.
 *
 * Three blocks rather than seven: what the reminder says (name, colour, message), when it runs
 * (interval, start, schedule) and how it announces itself (sound, vibration). Every option the
 * old layout had is still here — the grouping just stopped competing with itself. It used to mix
 * all-caps headers, full-bleed dividers and hand-drawn outlines in one scroll, which is three
 * ways of saying "these belong together" fighting for the same job.
 */
@Composable
fun ReminderEditorScreen(
    draft: Reminder,
    isNew: Boolean,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    // A timer without any restriction runs every day, all day — that is the "no schedule" state.
    val hasSchedule = !(draft.isEveryDay && draft.isAllDay)
    var scheduleOpen by rememberSaveable(draft.id) { mutableStateOf(hasSchedule) }

    val accent by animateColorAsState(
        targetValue = accentColor(draft.colorIndex),
        animationSpec = Motion.fade(),
        label = "editorAccent",
    )
    val canSave = draft.days.isNotEmpty() && draft.intervalMinutes > 0

    val scrollState = rememberScrollState()
    val collapseProgress by rememberCollapseProgress(scrollState)
    val view = LocalView.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CollapsingHeader(
                title = stringResource(
                    if (isNew) R.string.editor_new_title else R.string.editor_edit_title
                ),
                progress = collapseProgress,
                navigationIcon = {
                    IconButton(onClick = { view.tap(); onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Save grows in only once the draft is actually saveable, which explains the
                    // rule without needing a disabled-looking button the user can poke at.
                    AnimatedVisibility(
                        visible = canSave,
                        enter = scaleIn(Motion.expressive(), initialScale = 0.6f) + fadeIn(),
                        exit = scaleOut(Motion.snappy(), targetScale = 0.6f) + fadeOut(),
                    ) {
                        FilledTonalButton(
                            onClick = { view.tap(); onSave() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Padding inside the scroll, so the content passes behind the collapsed header
                // instead of being clipped at the height the expanded one reserves.
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IdentityBlock(draft = draft, onChange = onChange)

            SectionCard(title = stringResource(R.string.editor_section_timing)) {
                SectionValueRow(
                    title = stringResource(R.string.editor_repeat_every),
                    value = intervalLabel(draft.intervalMinutes),
                    // The interval is the point of the whole screen, so it keeps the accent.
                    valueColor = accent,
                    onClick = { showIntervalDialog = true },
                )
                SectionDivider()
                SectionValueRow(
                    title = stringResource(R.string.editor_section_start),
                    value = formatStartMoment(draft.startAtMillis),
                    onClick = { showStartDialog = true },
                )
                SectionDivider()
                ScheduleSection(
                    draft = draft,
                    accent = accent,
                    open = scheduleOpen,
                    onOpenChange = { open ->
                        scheduleOpen = open
                        onChange {
                            if (open) {
                                // Opening means "restrict it" — offer the common case straight away.
                                it.copy(
                                    days = Reminder.WEEKDAYS,
                                    startMinute = 9 * 60,
                                    endMinute = 17 * 60,
                                )
                            } else {
                                it.copy(
                                    days = Reminder.ALL_DAYS,
                                    startMinute = 0,
                                    endMinute = Reminder.MINUTES_PER_DAY,
                                )
                            }
                        }
                    },
                    onChange = onChange,
                    onPickTime = { timeTarget = it },
                )
            }

            Column {
                SectionCard(title = stringResource(R.string.editor_section_alerts)) {
                    SectionSwitchRow(
                        title = stringResource(R.string.editor_sound),
                        checked = draft.soundEnabled,
                        onCheckedChange = { on -> onChange { it.copy(soundEnabled = on) } },
                    )
                    SectionDivider()
                    SectionSwitchRow(
                        title = stringResource(R.string.editor_vibration),
                        checked = draft.vibrationEnabled,
                        onCheckedChange = { on -> onChange { it.copy(vibrationEnabled = on) } },
                    )
                }
                Text(
                    text = stringResource(R.string.editor_alert_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                )
            }

            if (!isNew) {
                TextButton(
                    onClick = { view.tap(); showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showIntervalDialog) {
        IntervalPickerDialogCompose(
            currentInterval = draft.intervalMinutes,
            onDismiss = { showIntervalDialog = false },
            onIntervalSelected = { hours, minutes ->
                onChange { it.copy(intervalMinutes = hours * 60 + minutes) }
                showIntervalDialog = false
            },
        )
    }

    if (showStartDialog) {
        StartMomentDialog(
            initialMillis = draft.startAtMillis,
            onDismiss = { showStartDialog = false },
            onConfirm = { millis ->
                onChange { it.copy(startAtMillis = millis) }
                showStartDialog = false
            },
        )
    }

    timeTarget?.let { target ->
        val current = if (target == TimeTarget.START) draft.startMinute else draft.endMinute
        TimePickerDialog(
            initialMinuteOfDay = current,
            onDismiss = { timeTarget = null },
            onConfirm = { minuteOfDay ->
                onChange {
                    if (target == TimeTarget.START) {
                        it.copy(startMinute = minuteOfDay)
                    } else {
                        it.copy(endMinute = minuteOfDay)
                    }
                }
                timeTarget = null
            },
        )
    }

    if (showDeleteDialog) {
        val name = draft.name.ifBlank { stringResource(R.string.reminder_default_name) }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.editor_delete_confirm_text, name)) },
            confirmButton = {
                Button(
                    onClick = {
                        // A dialog is its own window: left open, it would sit on top of the whole
                        // exit transition and only vanish once the editor had gone.
                        showDeleteDialog = false
                        view.tap()
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private enum class TimeTarget { START, END }

// --- What the reminder says ----------------------------------------------

/**
 * Name, colour and message, with no card around them.
 *
 * These are the only fields on the screen the user types into, so they keep the outline that says
 * "editable" — and being the one uncarded block makes them read as the top of the page rather
 * than as three more settings.
 */
@Composable
private fun IdentityBlock(draft: Reminder, onChange: ((Reminder) -> Reminder) -> Unit) {
    val context = LocalContext.current
    // Tracked as TextFieldValue so variable chips can insert at the caret.
    var message by remember(draft.id) {
        mutableStateOf(TextFieldValue(draft.message, TextRange(draft.message.length)))
    }
    var variablesOpen by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> onChange { it.copy(name = name) } },
            label = { Text(stringResource(R.string.editor_name_label)) },
            placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Unlabelled on purpose: six coloured dots with one ticked need no explaining, and a
        // label here would pull weight away from the two fields it sits between.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            ReminderAccents.forEachIndexed { index, color ->
                ColourDot(
                    color = color,
                    selected = index == draft.colorIndex.mod(ReminderAccents.size),
                    index = index,
                    onSelect = { onChange { it.copy(colorIndex = index) } },
                )
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { value ->
                message = value
                onChange { it.copy(message = value.text) }
            },
            label = { Text(stringResource(R.string.editor_message_label)) },
            placeholder = { Text(stringResource(R.string.editor_message_placeholder)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // Folded away by default — most people just type a sentence and never need this.
        ExpandableHeader(
            title = stringResource(R.string.editor_insert_variable),
            expanded = variablesOpen,
            onToggle = { variablesOpen = !variablesOpen },
        )

        AnimatedVisibility(
            visible = variablesOpen,
            enter = fadeIn() + expandVertically(Motion.spatial()),
            exit = fadeOut() + shrinkVertically(Motion.snappy()),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.editor_message_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageVariable.entries.forEach { variable ->
                        AssistChip(
                            onClick = {
                                message = message.insertAtCaret(variable.token)
                                onChange { it.copy(message = message.text) }
                            },
                            label = { Text(stringResource(variable.labelRes)) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = draft.message.contains('{'),
            enter = fadeIn() + expandVertically(Motion.spatial()),
            exit = fadeOut() + shrinkVertically(Motion.snappy()),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = stringResource(
                        R.string.editor_preview,
                        MessageTemplate.render(context, draft.message, draft),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ColourDot(color: Color, selected: Boolean, index: Int, onSelect: () -> Unit) {
    // The chosen dot swells slightly and the ring draws itself in.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = Motion.expressive(),
        label = "colourScale",
    )
    val ringWidth by animateFloatAsState(
        targetValue = if (selected) 3f else 0f,
        animationSpec = Motion.spatial(),
        label = "colourRing",
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(color)
            .border(
                width = ringWidth.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .tappable(pressedScale = 0.82f, onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(Motion.expressive()) + fadeIn(),
            exit = scaleOut(Motion.snappy()) + fadeOut(),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.editor_colour_selected, index + 1),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// --- Schedule -------------------------------------------------------------

/**
 * The weekday and time-window restriction: one switch row that grows its details underneath.
 * Off is the common case, so the detail never occupies the screen unless it is in use.
 */
@Composable
private fun ScheduleSection(
    draft: Reminder,
    accent: Color,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onPickTime: (TimeTarget) -> Unit,
) {
    val view = LocalView.current

    Column {
        SectionSwitchRow(
            title = stringResource(R.string.editor_section_schedule),
            subtitle = if (open) {
                scheduleSummary(draft)
            } else {
                stringResource(R.string.editor_schedule_always)
            },
            checked = open,
            onCheckedChange = onOpenChange,
        )

        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(Motion.spatial()),
            exit = fadeOut() + shrinkVertically(Motion.snappy()),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (isoDay in 1..7) {
                        DayToggle(
                            label = dayInitial(DayOfWeek.of(isoDay)),
                            selected = isoDay in draft.days,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onChange { reminder ->
                                    val days = reminder.days.toMutableSet()
                                    if (!days.add(isoDay)) days.remove(isoDay)
                                    reminder.copy(days = days)
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { view.tap(); onChange { it.copy(days = Reminder.ALL_DAYS) } },
                        label = { Text(stringResource(R.string.preset_every_day)) },
                    )
                    AssistChip(
                        onClick = { view.tap(); onChange { it.copy(days = Reminder.WEEKDAYS) } },
                        label = { Text(stringResource(R.string.preset_weekdays)) },
                    )
                    AssistChip(
                        onClick = { view.tap(); onChange { it.copy(days = setOf(6, 7)) } },
                        label = { Text(stringResource(R.string.preset_weekend)) },
                    )
                }

                SectionSwitchRow(
                    title = stringResource(R.string.editor_all_day),
                    checked = draft.isAllDay,
                    onCheckedChange = { allDay ->
                        onChange {
                            if (allDay) {
                                it.copy(startMinute = 0, endMinute = Reminder.MINUTES_PER_DAY)
                            } else {
                                it.copy(startMinute = 9 * 60, endMinute = 17 * 60)
                            }
                        }
                    },
                )

                AnimatedVisibility(
                    visible = !draft.isAllDay,
                    enter = fadeIn() + expandVertically(Motion.spatial()),
                    exit = fadeOut() + shrinkVertically(Motion.snappy()),
                ) {
                    Column {
                        SectionValueRow(
                            title = stringResource(R.string.editor_from),
                            value = formatMinuteOfDay(draft.startMinute),
                            onClick = { onPickTime(TimeTarget.START) },
                        )
                        SectionValueRow(
                            title = stringResource(R.string.editor_until),
                            value = formatMinuteOfDay(draft.endMinute),
                            onClick = { onPickTime(TimeTarget.END) },
                        )
                    }
                }
            }
        }
    }
}

// --- Building blocks ------------------------------------------------------

@Composable
private fun ExpandableHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.spatial(),
        label = "chevron",
    )
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .tappable(pressedScale = 0.94f, onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun DayToggle(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = Motion.fade(),
        label = "dayBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fade(),
        label = "dayText",
    )
    // No resting scale for the selected state: the pills sit only a few dp apart, and a bouncy
    // spring past 1f pushed neighbours into each other. The press scale from `tappable` and the
    // colour fill are feedback enough.
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(background)
            .tappable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = textColor,
        )
    }
}

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
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
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

/** Inserts [text] at the caret (replacing any selection) and leaves the caret after it. */
private fun TextFieldValue.insertAtCaret(text: String): TextFieldValue {
    val start = selection.min.coerceIn(0, this.text.length)
    val end = selection.max.coerceIn(0, this.text.length)
    val updated = this.text.replaceRange(start, end, text)
    val caret = start + text.length
    return TextFieldValue(updated, TextRange(caret))
}

@Preview(showBackground = true)
@Composable
private fun ReminderEditorPreview() {
    ReReminderTheme(dynamicColor = false) {
        ReminderEditorScreen(
            draft = Reminder(
                id = 1,
                name = "Take a walk",
                message = "Time to move",
                intervalMinutes = 50,
                colorIndex = 1,
            ),
            isNew = false,
            onChange = {},
            onSave = {},
            onDelete = {},
            onBack = {},
        )
    }
}
