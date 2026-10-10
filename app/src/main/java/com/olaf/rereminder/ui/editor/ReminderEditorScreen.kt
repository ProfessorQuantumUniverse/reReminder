package com.olaf.rereminder.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.MessageTemplate
import com.olaf.rereminder.data.MessageVariable
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.data.SoundChoice
import com.olaf.rereminder.data.SoundMode
import com.olaf.rereminder.data.TimeWindow
import com.olaf.rereminder.ui.components.CollapsingHeader
import com.olaf.rereminder.ui.components.RepeatDialog
import com.olaf.rereminder.ui.components.SectionCard
import com.olaf.rereminder.ui.components.SectionDivider
import com.olaf.rereminder.ui.components.SectionSwitchRow
import com.olaf.rereminder.ui.components.SectionValueRow
import com.olaf.rereminder.ui.components.StartMomentDialog
import com.olaf.rereminder.ui.components.rememberCollapseProgress
import com.olaf.rereminder.ui.format.earlyAlertsLabel
import com.olaf.rereminder.ui.format.formatStartMoment
import com.olaf.rereminder.ui.format.repeatLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.format.windowLabel
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.ReminderAccents
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tappable
import com.olaf.rereminder.ui.theme.tick
import com.olaf.rereminder.utils.NotificationHelper
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun ReminderEditorRoute(
    onClose: () -> Unit,
    viewModel: ReminderEditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val pickerTitle = stringResource(R.string.ringtone_picker_title)

    val tonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // Picking "None" in the system picker is the same as choosing Silent.
        viewModel.update {
            it.copy(
                sound = if (uri == null) {
                    SoundChoice(SoundMode.SILENT)
                } else {
                    SoundChoice(SoundMode.TONE, uri.toString())
                }
            )
        }
    }

    // The full-screen permission can be granted in system settings and come back.
    var fullScreenAllowed by remember { mutableStateOf(NotificationHelper.canUseFullScreenIntent(context)) }
    LifecycleResumeEffect(Unit) {
        fullScreenAllowed = NotificationHelper.canUseFullScreenIntent(context)
        onPauseOrDispose { }
    }

    ReminderEditorScreen(
        draft = draft,
        isNew = viewModel.isNew,
        canSave = draft.isSchedulable,
        fullScreenAllowed = fullScreenAllowed,
        onChange = viewModel::update,
        onPickTone = { current ->
            val type = if (draft.alertStyle == AlertStyle.ALARM) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            }
            runCatching { tonePicker.launch(intent) }
        },
        onAllowFullScreen = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
                    )
                }
            }
        },
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
 * Three blocks: what the reminder says (name, colour, message), when it runs (repeat, start,
 * schedule, early alerts) and how it announces itself (style, sound, vibration). Each block shows
 * one short row per setting; the detail lives in a dialog behind the row, so the power added in
 * 4.0 doesn't turn the page into a form.
 */
@Composable
fun ReminderEditorScreen(
    draft: Reminder,
    isNew: Boolean,
    canSave: Boolean,
    fullScreenAllowed: Boolean,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onPickTone: (Uri?) -> Unit,
    onAllowFullScreen: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var showRepeatDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }
    var showEarlyDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    /** Index into the windows being edited; -1 while adding a new one. */
    var editingWindow by remember { mutableStateOf<Int?>(null) }

    val accent by animateColorAsState(
        targetValue = accentColor(draft.colorIndex),
        animationSpec = Motion.fade(),
        label = "editorAccent",
    )

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
                    title = stringResource(R.string.editor_repeat),
                    value = repeatLabel(draft.repeat),
                    // The repeat is the point of the whole screen, so it keeps the accent.
                    valueColor = accent,
                    onClick = { showRepeatDialog = true },
                )
                SectionDivider()
                SectionValueRow(
                    title = stringResource(R.string.editor_section_start),
                    value = if (draft.repeat.isCalendar && !draft.hasStartMoment) {
                        stringResource(R.string.start_required)
                    } else {
                        formatStartMoment(draft.startAtMillis)
                    },
                    valueColor = if (draft.repeat.isCalendar && !draft.hasStartMoment) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    onClick = { showStartDialog = true },
                )
                // Days, weeks and months fire at the start's time of day; hours of activity only
                // mean something for an interval timer, so the section steps aside.
                AnimatedVisibility(
                    visible = !draft.repeat.isCalendar,
                    enter = fadeIn() + expandVertically(Motion.spatial()),
                    exit = fadeOut() + shrinkVertically(Motion.snappy()),
                ) {
                    Column {
                        SectionDivider()
                        ScheduleSection(
                            draft = draft,
                            onChange = onChange,
                            onEditWindow = { editingWindow = it },
                        )
                    }
                }
                SectionDivider()
                SectionValueRow(
                    title = stringResource(R.string.early_alerts_title),
                    value = earlyAlertsLabel(draft.earlyAlerts),
                    onClick = { showEarlyDialog = true },
                )
            }

            Column {
                SectionCard(title = stringResource(R.string.editor_section_alerts)) {
                    AlertStyleRow(
                        style = draft.alertStyle,
                        accent = accent,
                        onStyle = { style -> onChange { it.copy(alertStyle = style) } },
                    )
                    AnimatedVisibility(
                        visible = draft.alertStyle == AlertStyle.ALARM && !fullScreenAllowed,
                        enter = fadeIn() + expandVertically(Motion.spatial()),
                        exit = fadeOut() + shrinkVertically(Motion.snappy()),
                    ) {
                        FullScreenHint(onAllow = onAllowFullScreen)
                    }
                    SectionDivider()
                    SectionValueRow(
                        title = stringResource(R.string.editor_sound),
                        value = soundLabel(draft.sound),
                        onClick = { showSoundDialog = true },
                    )
                    SectionDivider()
                    SectionSwitchRow(
                        title = stringResource(R.string.editor_vibration),
                        checked = draft.vibrationEnabled,
                        onCheckedChange = { on -> onChange { it.copy(vibrationEnabled = on) } },
                    )
                }
                Text(
                    text = stringResource(
                        if (draft.alertStyle == AlertStyle.ALARM) R.string.editor_alarm_hint else R.string.editor_alert_hint
                    ),
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

    if (showRepeatDialog) {
        val zone = remember { ZoneId.systemDefault() }
        val anchor = (if (draft.hasStartMoment) Reminder.localDateTimeOf(draft.startAtMillis, zone) else LocalDateTime.now(zone))
        RepeatDialog(
            current = draft.repeat,
            accent = accent,
            anchorWeekday = anchor.dayOfWeek.value,
            anchorMonthDay = anchor.dayOfMonth,
            onDismiss = { showRepeatDialog = false },
            onConfirm = { repeat ->
                onChange { it.withRepeat(repeat) }
                showRepeatDialog = false
            },
        )
    }

    if (showStartDialog) {
        StartMomentDialog(
            initialMillis = draft.startAtMillis,
            allowImmediate = !draft.repeat.isCalendar,
            onDismiss = { showStartDialog = false },
            onConfirm = { millis ->
                onChange { it.copy(startAtMillis = millis) }
                showStartDialog = false
            },
        )
    }

    if (showEarlyDialog) {
        EarlyAlertsDialog(
            selected = draft.earlyAlerts,
            shortestGapMinutes = draft.repeat.shortestGapMinutes,
            onDismiss = { showEarlyDialog = false },
            onConfirm = { offsets ->
                onChange { it.copy(earlyAlerts = offsets) }
                showEarlyDialog = false
            },
        )
    }

    if (showSoundDialog) {
        SoundDialog(
            current = draft.sound,
            alertStyle = draft.alertStyle,
            onPickTone = onPickTone,
            onDismiss = { showSoundDialog = false },
            onConfirm = { sound ->
                onChange { it.copy(sound = sound) }
                showSoundDialog = false
            },
        )
    }

    editingWindow?.let { index ->
        WindowDialog(
            initial = draft.windows.getOrNull(index) ?: TimeWindow(),
            accent = accent,
            onDismiss = { editingWindow = null },
            onConfirm = { window ->
                onChange {
                    val windows = it.windows.toMutableList()
                    if (index in windows.indices) windows[index] = window else windows += window
                    it.copy(windows = windows)
                }
                editingWindow = null
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

/**
 * Applies a new repeat rule and keeps the rest of the reminder consistent with it: calendar
 * repeats need a start moment to count from, and early alerts must stay shorter than the gap
 * between two events.
 */
private fun Reminder.withRepeat(repeat: Repeat): Reminder {
    val start = if (repeat.isCalendar && !hasStartMoment) {
        // Next full hour: a sensible first event nobody has to correct.
        val zone = ZoneId.systemDefault()
        LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
            .atZone(zone).toInstant().toEpochMilli()
    } else {
        startAtMillis
    }
    return copy(
        repeat = repeat,
        startAtMillis = start,
        earlyAlerts = earlyAlerts.filter { it < repeat.shortestGapMinutes },
    )
}

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
 * The active-hours restriction: one switch row that grows its windows underneath. Off is the
 * common case, so the detail never occupies the screen unless it is in use. Several windows
 * (#7) are one line each — "Mon–Fri · 9:00–17:00", "Sat · 10:00–12:00".
 */
@Composable
private fun ScheduleSection(
    draft: Reminder,
    onChange: ((Reminder) -> Reminder) -> Unit,
    onEditWindow: (Int) -> Unit,
) {
    val view = LocalView.current
    val open = draft.windows.isNotEmpty()

    Column {
        SectionSwitchRow(
            title = stringResource(R.string.editor_section_schedule),
            subtitle = scheduleSummary(draft),
            checked = open,
            onCheckedChange = { on ->
                onChange {
                    // Switching on means "restrict it" — offer the common case straight away.
                    it.copy(windows = if (on) listOf(TimeWindow()) else emptyList())
                }
            },
        )

        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(Motion.spatial()),
            exit = fadeOut() + shrinkVertically(Motion.snappy()),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                draft.windows.forEachIndexed { index, window ->
                    WindowRow(
                        label = windowLabel(window),
                        removable = draft.windows.size > 1,
                        onClick = { onEditWindow(index) },
                        onRemove = {
                            view.tap()
                            onChange { it.copy(windows = it.windows.filterIndexed { i, _ -> i != index }) }
                        },
                    )
                }
                TextButton(
                    onClick = { view.tap(); onEditWindow(-1) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_add_window))
                }
            }
        }
    }
}

@Composable
private fun WindowRow(label: String, removable: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .tappable(pressedScale = 0.98f, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
        )
        AnimatedVisibility(
            visible = removable,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.editor_remove_window),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Alerts ---------------------------------------------------------------

/** Notification or alarm (#10). The chosen icon gives a small wiggle, like a bell being rung. */
@Composable
private fun AlertStyleRow(style: AlertStyle, accent: Color, onStyle: (AlertStyle) -> Unit) {
    val view = LocalView.current
    Column(modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.editor_alert_style),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(10.dp))
        val options = AlertStyle.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val selected = style == option
                SegmentedButton(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            view.tick()
                            onStyle(option)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = accent.copy(alpha = 0.18f),
                    ),
                    icon = {
                        WigglingIcon(
                            selected = selected,
                            icon = {
                                Icon(
                                    if (option == AlertStyle.ALARM) Icons.Rounded.Alarm else Icons.Rounded.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    },
                    label = {
                        Text(
                            stringResource(
                                if (option == AlertStyle.ALARM) R.string.alert_style_alarm else R.string.alert_style_notification
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun WigglingIcon(selected: Boolean, icon: @Composable () -> Unit) {
    val rotation = remember { Animatable(0f) }
    // Only a change of style rings the bell — not the editor opening with one already chosen.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        if (selected) {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 420
                    -14f at 70
                    12f at 160
                    -7f at 250
                    4f at 330
                },
            )
        }
    }
    Box(modifier = Modifier.graphicsLayer { rotationZ = rotation.value }) { icon() }
}

@Composable
private fun FullScreenHint(onAllow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.alarm_full_screen_needed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAllow) { Text(stringResource(R.string.alarm_full_screen_allow)) }
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
                repeat = Repeat(RepeatUnit.TIME, 50),
                windows = listOf(TimeWindow()),
                colorIndex = 1,
            ),
            isNew = false,
            canSave = true,
            fullScreenAllowed = true,
            onChange = {},
            onPickTone = {},
            onAllowFullScreen = {},
            onSave = {},
            onDelete = {},
            onBack = {},
        )
    }
}
