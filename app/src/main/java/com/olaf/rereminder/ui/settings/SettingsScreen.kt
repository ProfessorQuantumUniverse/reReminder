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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
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
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            viewModel.setSelectedRingtone(uri)
        }
    }

    // The user may have just changed exact-alarm or battery settings in the system UI.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSystemStatus()
        onPauseOrDispose { }
    }

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onShowRingtonePicker = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ringtonePickerTitle)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    viewModel.getSelectedRingtone(),
                )
            }
            ringtonePicker.launch(intent)
        },
        onSoundEnabledChange = viewModel::setSoundEnabled,
        onSoundTypeChange = viewModel::setNotificationSoundType,
        onVibrationEnabledChange = viewModel::setVibrationEnabled,
        onVibrationPatternChange = viewModel::setVibrationPattern,
        // Both land on this app's own switch rather than a list of every installed app.
        onFixExactAlarms = { context.openFirstAvailable(Reliability.exactAlarmIntents(context)) },
        onFixBattery = { context.openFirstAvailable(Reliability.batteryIntents(context)) },
        onOpenDkma = {
            context.startActivitySafely(
                Intent(Intent.ACTION_VIEW, uiState.guideUrl.toUri())
            )
        },
    )
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
    onSoundEnabledChange: (Boolean) -> Unit,
    onSoundTypeChange: (String) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onVibrationPatternChange: (Int) -> Unit,
    onFixExactAlarms: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenDkma: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val collapseProgress by rememberCollapseProgress(scrollState)

    var showVibrationDialog by rememberSaveable { mutableStateOf(false) }
    var showSoundTypeDialog by rememberSaveable { mutableStateOf(false) }


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
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_group_alerts)) {
                SectionSwitchRow(
                    title = stringResource(R.string.sound_enabled_label),
                    checked = uiState.soundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                )
                SectionDivider()
                SectionItem(
                    title = stringResource(R.string.sound_mode_title),
                    subtitle = stringResource(
                        if (uiState.soundType == PreferenceHelper.SOUND_TYPE_TTS) {
                            R.string.sound_mode_tts
                        } else {
                            R.string.sound_mode_ringtone
                        }
                    ),
                    onClick = { showSoundTypeDialog = true },
                    enabled = uiState.soundEnabled,
                )
                // The ringtone row only makes sense while a ringtone is what actually plays, so
                // it slides away rather than sitting there greyed out.
                AnimatedVisibility(
                    visible = uiState.soundType == PreferenceHelper.SOUND_TYPE_RINGTONE,
                    enter = fadeIn() + expandVertically(Motion.spatial()),
                    exit = fadeOut() + shrinkVertically(Motion.snappy()),
                ) {
                    SectionItem(
                        title = stringResource(R.string.ringtone_title),
                        subtitle = uiState.ringtone
                            ?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                            ?: stringResource(R.string.default_label),
                        onClick = onShowRingtonePicker,
                        enabled = uiState.soundEnabled,
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

    if (showSoundTypeDialog) {
        val types = listOf(PreferenceHelper.SOUND_TYPE_RINGTONE, PreferenceHelper.SOUND_TYPE_TTS)
        SingleChoiceDialog(
            title = stringResource(R.string.sound_mode_dialog_title),
            options = listOf(
                stringResource(R.string.sound_mode_ringtone),
                stringResource(R.string.sound_mode_tts),
            ),
            selectedIndex = types.indexOf(uiState.soundType).coerceAtLeast(0),
            onSelected = {
                onSoundTypeChange(types[it])
                showSoundTypeDialog = false
            },
            onDismiss = { showSoundTypeDialog = false },
        )
    }
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
            onSoundEnabledChange = {},
            onSoundTypeChange = {},
            onVibrationEnabledChange = {},
            onVibrationPatternChange = {},
            onFixExactAlarms = {},
            onFixBattery = {},
            onOpenDkma = {},
        )
    }
}
