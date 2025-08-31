package com.olaf.rereminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.olaf.rereminder.ui.components.IntervalPickerDialogCompose
import com.olaf.rereminder.ui.main.MainViewModel
import com.olaf.rereminder.ui.settings.SettingsActivity
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.components.PermissionRequestScreen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // Move the permission state to class level so it can be accessed by the launcher
    private var hasNotificationPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Update the state when permission is granted
            hasNotificationPermission = true
        } else {
            Toast.makeText(this, "Notification permission is required for the app to function correctly.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Initialize permission state
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        setContent {
            ReReminderTheme {
                if (hasNotificationPermission) {
                    MainScreen(viewModel = viewModel)
                } else {
                    PermissionRequestScreen {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            hasNotificationPermission = true
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere die nächste Erinnerungszeit wenn die App wieder aktiv wird
        viewModel.refreshNextReminderTime()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isReminderEnabled by viewModel.isReminderEnabled.observeAsState(false)
    val nextReminderTime by viewModel.nextReminderTime.observeAsState("")
    val intervalText by viewModel.intervalText.observeAsState("")
    var showIntervalDialog by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("reReminder", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reminders",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("Status:", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setReminderEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                checkedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                    SettingsItem(
                        title = "Reminder Intervall",
                        subtitle = intervalText,
                        onClick = { showIntervalDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = isReminderEnabled,
                enter = fadeIn(animationSpec = spring()) + scaleIn(),
                exit = fadeOut(animationSpec = spring()) + scaleOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (nextReminderTime.isNotEmpty()) {
                        Text(
                            text = "Next reminder at $nextReminderTime ",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
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
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
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


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ReReminderTheme {
        // Preview can be built with a mock ViewModel
    }
}