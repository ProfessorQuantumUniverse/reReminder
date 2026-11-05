package com.olaf.rereminder.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
import com.olaf.rereminder.data.BackupError
import com.olaf.rereminder.service.ReminderBackup
import com.olaf.rereminder.service.suggestedBackupName
import com.olaf.rereminder.ui.components.CollapsingHeader
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.components.SectionCard
import com.olaf.rereminder.ui.components.SectionDivider
import com.olaf.rereminder.ui.components.SectionItem
import com.olaf.rereminder.ui.components.SectionSwitchRow
import com.olaf.rereminder.ui.components.rememberCollapseProgress
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tappable
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.Reliability

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Read in composition so a locale change re-reads it, rather than inside the click lambda.
    val ringtonePickerTitle = stringResource(R.string.ringtone_picker_title)

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.setSelectedRingtone(result.data.pickedUri())
    }
    val alarmTonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.setAlarmTone(result.data.pickedUri())
    }

    fun pickerIntent(type: Int, existing: Uri?) =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtonePickerTitle)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }

    // The user may have just changed exact-alarm or battery settings in the system UI.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemStatus()
        onPauseOrDispose { }
    }

    // Export and import go through the system file picker: no storage permission needed (#21).
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::readImport) }

    LaunchedEffect(uiState.backupMessage) {
        val message = uiState.backupMessage ?: return@LaunchedEffect
        val (text, long) = backupMessageText(context, message)
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        viewModel.consumeBackupMessage()
    }

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onShowRingtonePicker = {
            runCatching {
                ringtonePicker.launch(pickerIntent(RingtoneManager.TYPE_NOTIFICATION, uiState.ringtone))
            }
        },
        onShowAlarmTonePicker = {
            runCatching {
                alarmTonePicker.launch(pickerIntent(RingtoneManager.TYPE_ALARM, uiState.alarmTone))
            }
        },
        onSpeechStreamChange = viewModel::setSpeechStream,
        onSnoozeChange = viewModel::setSnoozeMinutes,
        onSnoozeOnAlarmSwipeChange = viewModel::setSnoozeOnAlarmSwipe,
        onVibrationEnabledChange = viewModel::setVibrationEnabled,
        onVibrationPatternChange = viewModel::setVibrationPattern,
        onCompactListChange = viewModel::setCompactList,
        // Both land on this app's own switch rather than a list of every installed app.
        onFixExactAlarms = { context.openFirstAvailable(Reliability.exactAlarmIntents(context)) },
        onFixBattery = { context.openFirstAvailable(Reliability.batteryIntents(context)) },
        onOpenDkma = {
            context.startActivitySafely(
                Intent(Intent.ACTION_VIEW, uiState.guideUrl.toUri())
            )
        },
        onExport = {
            runCatching { exportPicker.launch(suggestedBackupName()) }
                .onFailure { Toast.makeText(context, R.string.backup_no_picker, Toast.LENGTH_SHORT).show() }
        },
        onImport = {
            // Many file managers don't know .json and call it text or plain bytes.
            runCatching { importPicker.launch(arrayOf(BACKUP_MIME_TYPE, "text/*", "application/octet-stream")) }
                .onFailure { Toast.makeText(context, R.string.backup_no_picker, Toast.LENGTH_SHORT).show() }
        },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
    )
}

private const val BACKUP_MIME_TYPE = "application/json"

/** What to tell the user after an export or import, and whether it needs reading time. */
private fun backupMessageText(context: Context, message: BackupMessage): Pair<String, Boolean> {
    val res = context.resources
    return when (message) {
        is BackupMessage.Exported ->
            res.getQuantityString(R.plurals.backup_exported, message.count, message.count) to false

        BackupMessage.ExportFailed -> context.getString(R.string.backup_export_failed) to true

        is BackupMessage.Imported -> {
            val imported = res.getQuantityString(R.plurals.backup_imported, message.added, message.added)
            if (message.skipped > 0) {
                val skipped = res.getQuantityString(R.plurals.backup_skipped, message.skipped, message.skipped)
                "$imported $skipped" to true
            } else {
                imported to false
            }
        }

        is BackupMessage.ImportFailed -> context.getString(
            when (message.error) {
                BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
                BackupError.TOO_NEW -> R.string.backup_error_too_new
                BackupError.EMPTY -> R.string.backup_error_empty
                BackupError.TOO_LARGE -> R.string.backup_error_too_large
                BackupError.UNREADABLE -> R.string.backup_error_unreadable
            }
        ) to true

        BackupMessage.ImportNotSaved -> context.getString(R.string.backup_import_not_saved) to true
    }
}

private fun Intent?.pickedUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        this?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

/** Not every OEM ships the system screens these intents point at. */
private fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure {
            Toast.makeText(this, R.string.reliability_screen_missing, Toast.LENGTH_SHORT).show()
        }
}

/** Walks the intent cascade, and says so when the device offers none of them. */
private fun Context.openFirstAvailable(intents: List<Intent>) {
    if (!Reliability.startFirstAvailable(this, intents)) {
        Toast.makeText(this, R.string.reliability_screen_missing, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onShowRingtonePicker: () -> Unit,
    onShowAlarmTonePicker: () -> Unit,
    onSpeechStreamChange: (String) -> Unit,
    onSnoozeChange: (Int) -> Unit,
    onSnoozeOnAlarmSwipeChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onVibrationPatternChange: (Int) -> Unit,
    onCompactListChange: (Boolean) -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenDkma: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: (ReminderBackup.Mode, Boolean) -> Unit,
    onCancelImport: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val collapseProgress by rememberCollapseProgress(scrollState)

    var showVibrationDialog by rememberSaveable { mutableStateOf(false) }
    var showSpeechDialog by rememberSaveable { mutableStateOf(false) }
    var showSnoozeDialog by rememberSaveable { mutableStateOf(false) }
    var showAlarmSwipeDialog by rememberSaveable { mutableStateOf(false) }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CollapsingHeader(
                title = stringResource(R.string.settings_title),
                progress = collapseProgress,
                navigationIcon = {
                    IconButton(onClick = { view.tap(); onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_group_alerts)) {
                // What "Default" sounds like in a reminder's own sound choice.
                SectionItem(
                    title = stringResource(R.string.ringtone_title),
                    subtitle = rememberToneTitle(context, uiState.ringtone)
                        ?: stringResource(R.string.default_label),
                    onClick = onShowRingtonePicker,
                )
                SectionItem(
                    title = stringResource(R.string.alarm_tone_title),
                    subtitle = rememberToneTitle(context, uiState.alarmTone)
                        ?: stringResource(R.string.default_label),
                    onClick = onShowAlarmTonePicker,
                )
                SectionDivider()
                // Spoken reminders follow the volume chosen here, so a muted video can't swallow
                // them (#9).
                SectionItem(
                    title = stringResource(R.string.speech_stream_title),
                    subtitle = stringResource(speechStreamLabel(uiState.speechStream)),
                    onClick = { showSpeechDialog = true },
                )
                SectionItem(
                    title = stringResource(R.string.snooze_title),
                    subtitle = stringResource(R.string.snooze_minutes, uiState.snoozeMinutes),
                    onClick = { showSnoozeDialog = true },
                )
                // Only Android 14+ lets the user swipe away the alarm notification at all (#20).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SectionItem(
                        title = stringResource(R.string.alarm_swipe_title),
                        subtitle = if (uiState.snoozeOnAlarmSwipe) {
                            stringResource(R.string.alarm_swipe_snooze, uiState.snoozeMinutes)
                        } else {
                            stringResource(R.string.alarm_swipe_dismiss)
                        },
                        onClick = { showAlarmSwipeDialog = true },
                    )
                }
                SectionDivider()
                SectionSwitchRow(
                    title = stringResource(R.string.vibration_enabled_label),
                    checked = uiState.vibrationEnabled,
                    onCheckedChange = onVibrationEnabledChange,
                )
                SectionItem(
                    title = stringResource(R.string.vibration_pattern_title),
                    subtitle = stringResource(vibrationPatternLabel(uiState.vibrationPattern)),
                    onClick = { showVibrationDialog = true },
                    enabled = uiState.vibrationEnabled,
                )
            }

            Text(
                text = stringResource(R.string.settings_alerts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            SectionCard(title = stringResource(R.string.settings_group_display)) {
                SectionSwitchRow(
                    title = stringResource(R.string.compact_list_title),
                    subtitle = stringResource(R.string.compact_list_summary),
                    checked = uiState.compactList,
                    onCheckedChange = onCompactListChange,
                )
            }

            SectionCard(title = stringResource(R.string.settings_group_backup)) {
                SectionItem(
                    title = stringResource(R.string.backup_export_title),
                    subtitle = stringResource(R.string.backup_export_summary),
                    onClick = onExport,
                    enabled = !uiState.backupBusy,
                )
                SectionItem(
                    title = stringResource(R.string.backup_import_title),
                    subtitle = stringResource(R.string.backup_import_summary),
                    onClick = onImport,
                    enabled = !uiState.backupBusy,
                )
            }

            Text(
                text = stringResource(R.string.backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            SectionCard(title = stringResource(R.string.settings_group_reliability)) {
                StatusItem(
                    title = stringResource(R.string.reliability_exact_alarms),
                    ok = uiState.exactAlarmsAllowed,
                    subtitle = stringResource(
                        if (uiState.exactAlarmsAllowed) {
                            R.string.reliability_exact_alarms_on
                        } else {
                            R.string.reliability_exact_alarms_off
                        }
                    ),
                    onClick = onFixExactAlarms,
                )
                SectionDivider()
                StatusItem(
                    title = stringResource(R.string.reliability_battery),
                    ok = uiState.batteryUnrestricted,
                    subtitle = stringResource(
                        if (uiState.batteryUnrestricted) {
                            R.string.reliability_battery_on
                        } else {
                            R.string.reliability_battery_off
                        }
                    ),
                    onClick = onFixBattery,
                )
                SectionDivider()
                SectionItem(
                    title = stringResource(R.string.reliability_manufacturer),
                    // Naming the vendor tells the user the link is about their phone, not a
                    // generic disclaimer — and it is where the link actually goes.
                    subtitle = if (uiState.vendorName.isBlank()) {
                        stringResource(R.string.reliability_manufacturer_summary)
                    } else {
                        stringResource(
                            R.string.reliability_manufacturer_summary_vendor,
                            uiState.vendorName,
                        )
                    },
                    onClick = onOpenDkma,
                )
            }

            Text(
                text = stringResource(R.string.reliability_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(8.dp))
            MadeInEurope()
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showVibrationDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.vibration_pattern_title),
            options = VibrationPatterns.map { stringResource(it) },
            selectedIndex = uiState.vibrationPattern,
            onSelected = {
                onVibrationPatternChange(it)
                showVibrationDialog = false
            },
            onDismiss = { showVibrationDialog = false },
        )
    }

    if (showSpeechDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.speech_stream_title),
            options = SpeechStreams.map { stringResource(speechStreamLabel(it)) },
            selectedIndex = SpeechStreams.indexOf(uiState.speechStream).coerceAtLeast(0),
            onSelected = {
                onSpeechStreamChange(SpeechStreams[it])
                showSpeechDialog = false
            },
            onDismiss = { showSpeechDialog = false },
        )
    }

    if (showAlarmSwipeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.alarm_swipe_title),
            options = listOf(
                stringResource(R.string.alarm_swipe_dismiss),
                stringResource(R.string.alarm_swipe_snooze, uiState.snoozeMinutes),
            ),
            selectedIndex = if (uiState.snoozeOnAlarmSwipe) 1 else 0,
            onSelected = {
                onSnoozeOnAlarmSwipeChange(it == 1)
                showAlarmSwipeDialog = false
            },
            onDismiss = { showAlarmSwipeDialog = false },
        )
    }

    if (showSnoozeDialog) {
        val choices = PreferenceHelper.SNOOZE_CHOICES
        SingleChoiceDialog(
            title = stringResource(R.string.snooze_title),
            options = choices.map { stringResource(R.string.snooze_minutes, it) },
            selectedIndex = choices.indexOf(uiState.snoozeMinutes).coerceAtLeast(0),
            onSelected = {
                onSnoozeChange(choices[it])
                showSnoozeDialog = false
            },
            onDismiss = { showSnoozeDialog = false },
        )
    }

    uiState.importPreview?.let { preview ->
        ImportDialog(preview = preview, onConfirm = onConfirmImport, onDismiss = onCancelImport)
    }
}

/**
 * Asks how to import a checked file. Adding is the safe default; replacing deletes the current
 * reminders, so it asks once more.
 */
@Composable
private fun ImportDialog(
    preview: ImportPreview,
    onConfirm: (ReminderBackup.Mode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var withSettings by rememberSaveable { mutableStateOf(true) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val restoreSettings = preview.hasSettings && withSettings

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.backup_replace_body,
                        preview.currentCount,
                        preview.currentCount,
                        preview.count,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(ReminderBackup.Mode.REPLACE, restoreSettings) }) {
                    Text(
                        stringResource(R.string.backup_replace_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
        return
    }

    val hasCurrent = preview.currentCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.backup_import_question, preview.count, preview.count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasCurrent) {
                    Text(
                        pluralStringResource(
                            R.plurals.backup_import_choice,
                            preview.currentCount,
                            preview.currentCount,
                        )
                    )
                    if (preview.duplicates > 0) {
                        Text(
                            pluralStringResource(R.plurals.backup_duplicates, preview.duplicates, preview.duplicates),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (preview.hasSettings) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappable(pressedScale = 0.98f) { withSettings = !withSettings },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = withSettings, onCheckedChange = { withSettings = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_with_settings))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (hasCurrent) {
                    TextButton(onClick = { confirmReplace = true }) {
                        Text(stringResource(R.string.backup_replace_action))
                    }
                }
                TextButton(onClick = { onConfirm(ReminderBackup.Mode.ADD, restoreSettings) }) {
                    Text(stringResource(if (hasCurrent) R.string.backup_add_action else R.string.backup_import_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun rememberToneTitle(context: Context, uri: Uri?): String? =
    // Resolving the title queries a content provider, so it happens once per ringtone rather
    // than on every recomposition of the screen.
    remember(uri) {
        uri?.let {
            runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull()
        }
    }

private val SpeechStreams = listOf(
    PreferenceHelper.STREAM_NOTIFICATION,
    PreferenceHelper.STREAM_MEDIA,
    PreferenceHelper.STREAM_ALARM,
)

private fun speechStreamLabel(stream: String): Int = when (stream) {
    PreferenceHelper.STREAM_MEDIA -> R.string.speech_stream_media
    PreferenceHelper.STREAM_ALARM -> R.string.speech_stream_alarm
    else -> R.string.speech_stream_notification
}

private val VibrationPatterns = listOf(
    R.string.vibration_pattern_short,
    R.string.vibration_pattern_default,
    R.string.vibration_pattern_long,
    R.string.vibration_pattern_pulsating,
)

private fun vibrationPatternLabel(pattern: Int): Int =
    VibrationPatterns.getOrElse(pattern) { R.string.vibration_pattern_default }

/** A settings row that also reports whether the system condition behind it is healthy. */
@Composable
private fun StatusItem(
    title: String,
    subtitle: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (ok) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = Motion.fade(),
        label = "statusTint",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixing a restriction in system settings and coming back should visibly land.
        AnimatedContent(
            targetState = ok,
            transitionSpec = {
                (scaleIn(Motion.expressive(), initialScale = 0.4f) + fadeIn())
                    .togetherWith(scaleOut(Motion.snappy(), targetScale = 0.4f) + fadeOut())
            },
            label = "statusIcon",
        ) { healthy ->
            Icon(
                imageVector = if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "statusSubtitle",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else tint,
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappable(pressedScale = 0.98f) { onSelected(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { onSelected(index) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ReReminderTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(),
            onBack = {},
            onShowRingtonePicker = {},
            onShowAlarmTonePicker = {},
            onSpeechStreamChange = {},
            onSnoozeChange = {},
            onSnoozeOnAlarmSwipeChange = {},
            onVibrationEnabledChange = {},
            onVibrationPatternChange = {},
            onCompactListChange = {},
            onFixExactAlarms = {},
            onFixBattery = {},
            onOpenDkma = {},
            onExport = {},
            onImport = {},
            onConfirmImport = { _, _ -> },
            onCancelImport = {},
        )
    }
}
