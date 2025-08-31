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
import com.olaf.rereminder.ui.theme.ReReminderTheme

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
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Erinnerungston auswählen")
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

    var showVibrationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
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
            SettingsGroup(title = "Benachrichtigungen") {
                SwitchSettingItem(
                    title = "Ton",
                    checked = isSoundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) }
                )
                SettingsItem(
                    title = "Klingelton",
                    subtitle = selectedRingtone?.let { uri ->
                        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Standard"
                    } ?: "Standard",
                    onClick = onShowRingtonePicker,
                    enabled = isSoundEnabled
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchSettingItem(
                    title = "Vibration",
                    checked = isVibrationEnabled,
                    onCheckedChange = { viewModel.setVibrationEnabled(it) }
                )
                SettingsItem(
                    title = "Vibrationsmuster",
                    subtitle = when (selectedVibrationPattern) {
                        0 -> "Kurz"
                        1 -> "Standard"
                        2 -> "Lang"
                        3 -> "Pulsierend"
                        else -> "Standard"
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
        title = { Text("Vibrationsmuster") },
        text = {
            Column {
                val patterns = listOf("Kurz", "Standard", "Lang", "Pulsierend")
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
                Text("Schließen")
            }
        }
    )
}