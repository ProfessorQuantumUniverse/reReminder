package com.olaf.rereminder.ui.settings

import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.olaf.rereminder.R
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.utils.PreferenceHelper

class SettingsActivity : ComponentActivity() {

    private lateinit var viewModel: SettingsViewModel

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { viewModel.setSelectedRingtone(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setContent {
            ReReminderTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() },
                    onShowRingtonePicker = { showRingtonePicker() }
                )
            }
        }
    }

    private fun showRingtonePicker() {
        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Ringtone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, viewModel.getSelectedRingtone())
        }
        ringtonePickerLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackPressed: () -> Unit,
    onShowRingtonePicker: () -> Unit
) {
    val context = LocalContext.current

    val selectedRingtone by viewModel.selectedRingtone.observeAsState()
    val isSoundEnabled by viewModel.isSoundEnabled.observeAsState(true)
    val isVibrationEnabled by viewModel.isVibrationEnabled.observeAsState(true)
    val selectedVibrationPattern by viewModel.selectedVibrationPattern.observeAsState(1)
    val notificationTitle by viewModel.notificationTitle.observeAsState("")
    val notificationText by viewModel.notificationText.observeAsState("")
    val notificationSoundType by viewModel.notificationSoundType.observeAsState(PreferenceHelper.SOUND_TYPE_RINGTONE)

    var showVibrationDialog by remember { mutableStateOf(false) }
    var showNotificationTextDialog by remember { mutableStateOf(false) }
    var showSoundTypeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            SettingsGroup(title = "Notifications") {
                SettingsItem(
                    title = "Notification Text",
                    subtitle = "Adjust the title and message",
                    onClick = { showNotificationTextDialog = true }
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "Sound",
                    checked = isSoundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) }
                )
                SettingsItem(
                    title = "Sound or Text-to-Speech",
                    subtitle = if (notificationSoundType == PreferenceHelper.SOUND_TYPE_TTS) "Text-to-Speech" else "Sound",
                    onClick = { showSoundTypeDialog = true },
                    enabled = isSoundEnabled
                )
                if (notificationSoundType == PreferenceHelper.SOUND_TYPE_RINGTONE) {
                    SettingsItem(
                        title = "Sound",
                        subtitle = selectedRingtone?.let { uri ->
                            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Default"
                        } ?: "Default",
                        onClick = onShowRingtonePicker,
                        enabled = isSoundEnabled
                    )
                }
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "Vibration",
                    checked = isVibrationEnabled,
                    onCheckedChange = { viewModel.setVibrationEnabled(it) }
                )
                SettingsItem(
                    title = "Vibration Pattern",
                    subtitle = when (selectedVibrationPattern) {
                        0 -> "Short"
                        1 -> "Default"
                        2 -> "Long"
                        3 -> "Pulsating"
                        else -> "Default"
                    },
                    onClick = { showVibrationDialog = true },
                    enabled = isVibrationEnabled
                )
            }
        }
    }

    if (showVibrationDialog) {
        VibrationPatternDialog(
            currentPattern = selectedVibrationPattern,
            onDismiss = { showVibrationDialog = false },
            onPatternSelected = {
                viewModel.setVibrationPattern(it)
                showVibrationDialog = false
            }
        )
    }

    if (showNotificationTextDialog) {
        NotificationTextDialog(
            currentTitle = notificationTitle,
            currentText = notificationText,
            defaultTitle = context.getString(R.string.reminder_notification_title),
            defaultText = context.getString(R.string.reminder_notification_text),
            onDismiss = { showNotificationTextDialog = false },
            onConfirm = { title, text ->
                viewModel.setNotificationContent(title, text)
                showNotificationTextDialog = false
            }
        )
    }

    if (showSoundTypeDialog) {
        SoundTypeDialog(
            currentSoundType = notificationSoundType,
            onDismiss = { showSoundTypeDialog = false },
            onSoundTypeSelected = { type ->
                viewModel.setNotificationSoundType(type)
                showSoundTypeDialog = false
            }
        )
    }
}

@Composable
fun SoundTypeDialog(
    currentSoundType: String,
    onDismiss: () -> Unit,
    onSoundTypeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound/Text-to-speech") },
        text = {
            Column {
                val soundTypes = listOf(
                    "Sound" to PreferenceHelper.SOUND_TYPE_RINGTONE,
                    "Text-to-Speech" to PreferenceHelper.SOUND_TYPE_TTS
                )
                soundTypes.forEach { (label, type) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSoundTypeSelected(type) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSoundType == type,
                            onClick = { onSoundTypeSelected(type) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun SwitchSettingItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
fun VibrationPatternDialog(currentPattern: Int, onDismiss: () -> Unit, onPatternSelected: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vibration Pattern") },
        text = {
            Column {
                val patterns = listOf("Short", "Default", "Long", "Pulsating")
                patterns.forEachIndexed { index, pattern ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPatternSelected(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentPattern == index,
                            onClick = { onPatternSelected(index) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(pattern)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun NotificationTextDialog(
    currentTitle: String,
    currentText: String,
    defaultTitle: String,
    defaultText: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, text: String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var text by remember { mutableStateOf(currentText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text(defaultTitle) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    placeholder = { Text(defaultText) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}