package com.olaf.rereminder.ui.settings

import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.olaf.rereminder.ui.components.IntervalPickerDialogCompose
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
            viewModel.setSelectedRingtone(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        setContent {
            ReReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackPressed = { finish() },
                        onShowRingtonePicker = { showRingtonePicker() }
                    )
                }
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

    val intervalText by viewModel.intervalText.observeAsState("")
    val selectedRingtone by viewModel.selectedRingtone.observeAsState()
    val isSoundEnabled by viewModel.isSoundEnabled.observeAsState(true)
    val isVibrationEnabled by viewModel.isVibrationEnabled.observeAsState(true)
    val selectedVibrationPattern by viewModel.selectedVibrationPattern.observeAsState(1)

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showVibrationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intervall Einstellungen
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Erinnerungsintervall",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { showIntervalDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(text = intervalText)
                        }
                    }
                }
            }

            // Audio Einstellungen
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Audio Einstellungen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ton aktiviert")
                        Switch(
                            checked = isSoundEnabled,
                            onCheckedChange = { viewModel.setSoundEnabled(it) }
                        )
                    }

                    TextButton(
                        onClick = onShowRingtonePicker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = selectedRingtone?.let { uri ->
                                    RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Standard"
                                } ?: "Standard"
                            )
                        }
                    }
                }
            }

            // Vibration Einstellungen
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Vibration Einstellungen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibration aktiviert")
                        Switch(
                            checked = isVibrationEnabled,
                            onCheckedChange = { viewModel.setVibrationEnabled(it) }
                        )
                    }

                    TextButton(
                        onClick = { showVibrationDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = when (selectedVibrationPattern) {
                                    0 -> "Kurz"
                                    1 -> "Standard"
                                    2 -> "Lang"
                                    3 -> "Pulsierend"
                                    else -> "Standard"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Interval Picker Dialog
    if (showIntervalDialog) {
        IntervalPickerDialogCompose(
            currentInterval = viewModel.getReminderInterval(),
            onDismiss = { showIntervalDialog = false },
            onIntervalSelected = { hours, minutes ->
                viewModel.setReminderInterval(hours, minutes)
                showIntervalDialog = false
            }
        )
    }

    // Vibration Pattern Dialog
    if (showVibrationDialog) {
        AlertDialog(
            onDismissRequest = { showVibrationDialog = false },
            title = { Text("Vibrationsmuster auswählen") },
            text = {
                Column {
                    val patterns = listOf("Kurz", "Standard", "Lang", "Pulsierend")
                    patterns.forEachIndexed { index, pattern ->
                        TextButton(
                            onClick = {
                                viewModel.setVibrationPattern(index)
                                showVibrationDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(pattern)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVibrationDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}