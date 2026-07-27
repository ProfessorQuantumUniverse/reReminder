package com.olaf.rereminder.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaf.rereminder.R
import com.olaf.rereminder.data.MessageTemplate
import com.olaf.rereminder.data.MessageVariable
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.ui.components.IntervalPickerDialogCompose
import com.olaf.rereminder.ui.format.dayInitial
import com.olaf.rereminder.ui.format.formatMinuteOfDay
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.ReminderAccents
import com.olaf.rereminder.ui.theme.accentColor
import java.time.DayOfWeek

class ReminderEditorActivity : ComponentActivity() {

    private val viewModel: ReminderEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        viewModel.initialize(intent.getIntExtra(EXTRA_REMINDER_ID, ReminderEditorViewModel.NEW_ID))

        setContent {
            ReReminderTheme {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                ReminderEditorScreen(
                    draft = draft,
                    isNew = viewModel.isNew,
                    onChange = viewModel::update,
                    onSave = {
                        viewModel.save()
                        finish()
                    },
                    onDelete = {
                        viewModel.delete()
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_REMINDER_ID = "reminder_id"

        fun createIntent(context: Context): Intent =
            Intent(context, ReminderEditorActivity::class.java)

        fun editIntent(context: Context, id: Int): Intent =
            Intent(context, ReminderEditorActivity::class.java)
                .putExtra(EXTRA_REMINDER_ID, id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    // A timer without any restriction runs every day, all day — that is the "no schedule" state.
    val hasSchedule = !(draft.isEveryDay && draft.isAllDay)
    var scheduleOpen by rememberSaveable(draft.id) { mutableStateOf(hasSchedule) }

    val accent = accentColor(draft.colorIndex)
    val canSave = draft.days.isNotEmpty() && draft.intervalMinutes > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isNew) R.string.editor_new_title else R.string.editor_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = canSave) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Both of these are plain form fields on purpose: the outline says "editable"
                // without the user having to work it out.
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { name -> onChange { it.copy(name = name) } },
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                IntervalField(
                    intervalMinutes = draft.intervalMinutes,
                    accent = accent,
                    onClick = { showIntervalDialog = true },
                )

                ColourPicker(
                    selectedIndex = draft.colorIndex,
                    onSelect = { index -> onChange { it.copy(colorIndex = index) } },
                )
            }

            GroupDivider()

            Group(title = stringResource(R.string.editor_message_label)) {
                MessageGroup(draft = draft, onChange = onChange)
            }

            GroupDivider()

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

            GroupDivider()

            Group(title = stringResource(R.string.editor_section_alerts)) {
                SwitchRow(
                    title = stringResource(R.string.editor_sound),
                    checked = draft.soundEnabled,
                    onCheckedChange = { enabled -> onChange { it.copy(soundEnabled = enabled) } },
                )
                SwitchRow(
                    title = stringResource(R.string.editor_vibration),
                    checked = draft.vibrationEnabled,
                    onCheckedChange = { enabled -> onChange { it.copy(vibrationEnabled = enabled) } },
                )
                Text(
                    text = stringResource(R.string.editor_alert_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            if (!isNew) {
                GroupDivider()
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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

            Spacer(Modifier.height(24.dp))
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
                Button(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
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

// --- Interval -------------------------------------------------------------

/**
 * Deliberately mirrors an [OutlinedTextField]: same outline, same floating label, plus an edit
 * icon. It reads as "a field you change", not as decoration.
 */
@Composable
private fun IntervalField(intervalMinutes: Int, accent: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraSmall,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = intervalLabel(intervalMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.editor_change_interval),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Floating label, sitting on the border like a real text field's.
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_repeat_every),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ColourPicker(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ReminderAccents.forEachIndexed { index, color ->
            val selected = index == selectedIndex.mod(ReminderAccents.size)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(
                            R.string.editor_colour_selected,
                            index + 1,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// --- Message --------------------------------------------------------------

@Composable
private fun MessageGroup(draft: Reminder, onChange: ((Reminder) -> Reminder) -> Unit) {
    val context = LocalContext.current
    // Tracked as TextFieldValue so variable chips can insert at the caret.
    var field by remember(draft.id) {
        mutableStateOf(TextFieldValue(draft.message, TextRange(draft.message.length)))
    }
    var variablesOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = field,
            onValueChange = { value ->
                field = value
                onChange { it.copy(message = value.text) }
            },
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

        AnimatedVisibility(visible = variablesOpen) {
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
                                field = field.insertAtCaret(variable.token)
                                onChange { it.copy(message = field.text) }
                            },
                            label = { Text(stringResource(variable.labelRes)) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        AnimatedVisibility(visible = draft.message.contains('{')) {
            Column {
                Spacer(Modifier.height(4.dp))
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
}

// --- Schedule -------------------------------------------------------------

@Composable
private fun ScheduleSection(
    draft: Reminder,
    accent: Color,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onPickTime: (TimeTarget) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        // Collapsed by default: a timer with no schedule is the simple, common case.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenChange(!open) }
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.editor_section_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (open) {
                        scheduleSummary(draft)
                    } else {
                        stringResource(R.string.editor_schedule_always)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = open, onCheckedChange = onOpenChange)
        }

        AnimatedVisibility(visible = open) {
            Column {
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

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onChange { it.copy(days = Reminder.ALL_DAYS) } },
                        label = { Text(stringResource(R.string.preset_every_day)) },
                    )
                    AssistChip(
                        onClick = { onChange { it.copy(days = Reminder.WEEKDAYS) } },
                        label = { Text(stringResource(R.string.preset_weekdays)) },
                    )
                    AssistChip(
                        onClick = { onChange { it.copy(days = setOf(6, 7)) } },
                        label = { Text(stringResource(R.string.preset_weekend)) },
                    )
                }

                Spacer(Modifier.height(4.dp))

                SwitchRow(
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

                AnimatedVisibility(visible = !draft.isAllDay) {
                    Column {
                        ValueRow(
                            title = stringResource(R.string.editor_from),
                            value = formatMinuteOfDay(draft.startMinute),
                            onClick = { onPickTime(TimeTarget.START) },
                        )
                        ValueRow(
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
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ExpandableHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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
                .rotate(rotation),
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(
                if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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
