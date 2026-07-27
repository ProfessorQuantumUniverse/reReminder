package com.olaf.rereminder.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaf.rereminder.R
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.utils.DeviceUtils
import com.olaf.rereminder.utils.PreferenceHelper

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ReReminderTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onShowRingtonePicker = ::showRingtonePicker,
                    onSoundEnabledChange = viewModel::setSoundEnabled,
                    onSoundTypeChange = viewModel::setNotificationSoundType,
                    onVibrationEnabledChange = viewModel::setVibrationEnabled,
                    onVibrationPatternChange = viewModel::setVibrationPattern,
                    onFixExactAlarms = ::openExactAlarmSettings,
                    onFixBattery = ::openBatterySettings,
                    onOpenDkma = ::openDontKillMyApp,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just changed these in system settings.
        viewModel.refreshSystemStatus()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData("package:$packageName".toUri())
        startActivitySafely(intent)
    }

    private fun openBatterySettings() {
        startActivitySafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openDontKillMyApp() {
        val slug = DeviceUtils.getDontKillMyAppSlug() ?: ""
        startActivitySafely(Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/$slug".toUri()))
    }

    private fun startActivitySafely(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                // Not every OEM ships these screens.
                Toast.makeText(this, R.string.reliability_screen_missing, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.ringtone_picker_title),
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                viewModel.getSelectedRingtone(),
            )
        }
        ringtonePickerLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    var showVibrationDialog by rememberSaveable { mutableStateOf(false) }
    var showSoundTypeDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_alerts)) {
                SwitchSettingItem(
                    title = stringResource(R.string.sound_enabled_label),
                    checked = uiState.soundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                )
                SettingsDivider()
                SettingsItem(
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
                if (uiState.soundType == PreferenceHelper.SOUND_TYPE_RINGTONE) {
                    SettingsItem(
                        title = stringResource(R.string.ringtone_title),
                        subtitle = uiState.ringtone
                            ?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                            ?: stringResource(R.string.default_label),
                        onClick = onShowRingtonePicker,
                        enabled = uiState.soundEnabled,
                    )
                }
                SettingsDivider()
                SwitchSettingItem(
                    title = stringResource(R.string.vibration_enabled_label),
                    checked = uiState.vibrationEnabled,
                    onCheckedChange = onVibrationEnabledChange,
                )
                SettingsItem(
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

            SettingsGroup(title = stringResource(R.string.settings_group_reliability)) {
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
                SettingsDivider()
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
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.reliability_manufacturer),
                    subtitle = stringResource(R.string.reliability_manufacturer_summary),
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

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
        )
    }
}

/** A settings row that also reports whether the system condition behind it is healthy. */
@Composable
private fun StatusItem(
    title: String,
    subtitle: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                            .clickable { onSelected(index) }
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
