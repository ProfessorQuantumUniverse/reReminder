package com.olaf.rereminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.components.PermissionRequestScreen
import com.olaf.rereminder.ui.editor.ReminderEditorActivity
import com.olaf.rereminder.ui.format.formatClockTime
import com.olaf.rereminder.ui.format.formatCountdown
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.main.MainUiState
import com.olaf.rereminder.ui.main.MainViewModel
import com.olaf.rereminder.ui.main.ReminderRow
import com.olaf.rereminder.ui.settings.SettingsActivity
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.utils.DeviceUtils
import com.olaf.rereminder.utils.PreferenceHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var hasNotificationPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hasNotificationPermission = true
        } else {
            Toast.makeText(this, R.string.permission_required_toast, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        hasNotificationPermission = notificationPermissionGranted()

        setContent {
            ReReminderTheme {
                if (hasNotificationPermission) {
                    MainRoute(viewModel = viewModel)
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
        hasNotificationPermission = notificationPermissionGranted()
        viewModel.refresh()
    }

    private fun notificationPermissionGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}

@Composable
private fun MainRoute(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val preferences = remember(context) { PreferenceHelper(context) }
    var dkmaUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val slug = DeviceUtils.getDontKillMyAppSlug()
        if (slug != null && !preferences.isDontKillMyAppWarningShown()) {
            dkmaUrl = "https://dontkillmyapp.com/$slug"
        }
    }

    MainScreen(
        uiState = uiState,
        onToggle = viewModel::setEnabled,
        onToggleMaster = viewModel::setMasterEnabled,
        onEdit = { id -> context.startActivity(ReminderEditorActivity.editIntent(context, id)) },
        onCreate = { context.startActivity(ReminderEditorActivity.createIntent(context)) },
        onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
    )

    dkmaUrl?.let { url ->
        DontKillMyAppDialog(
            onOpenGuide = {
                preferences.setDontKillMyAppWarningShown(true)
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                dkmaUrl = null
            },
            onDismiss = {
                preferences.setDontKillMyAppWarningShown(true)
                dkmaUrl = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggle: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onEdit: (Int) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Single row: title and action share a baseline instead of the title sitting
            // low under a floating icon the way a LargeTopAppBar renders it.
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_button_label),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!uiState.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_timer)) },
                )
            }
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onCreate = onCreate,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    // Leave room for the FAB so the last card stays reachable.
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    start = 20.dp,
                    end = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "master") {
                    MasterCard(
                        uiState = uiState,
                        onToggle = onToggleMaster,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(uiState.rows, key = { it.id }) { row ->
                    ReminderCard(
                        row = row,
                        masterEnabled = uiState.masterEnabled,
                        onToggle = { enabled -> onToggle(row.id, enabled) },
                        onClick = { onEdit(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MasterCard(
    uiState: MainUiState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = uiState.masterEnabled
    val next = uiState.nextUp

    val containerColor by animateColorAsState(
        targetValue = if (on) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "masterContainer",
    )
    val contentColor = if (on) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.master_all_reminders),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !on -> stringResource(R.string.master_paused)
                        next == null -> stringResource(R.string.master_nothing_running)
                        !next.isWithinSchedule -> stringResource(
                            R.string.master_next_at,
                            next.reminder.name.ifBlank {
                                stringResource(R.string.reminder_default_name)
                            },
                            formatClockTime(next.reminder.nextTriggerAt),
                        )

                        else -> stringResource(
                            R.string.master_next_in,
                            next.reminder.name.ifBlank {
                                stringResource(R.string.reminder_default_name)
                            },
                            formatCountdown(next.remainingMillis),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ReminderCard(
    row: ReminderRow,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val reminder = row.reminder
    val accent = accentColor(reminder.colorIndex)
    // A timer only reads as running when the master switch lets it.
    val active = reminder.enabled && masterEnabled

    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "cardContainer",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBadge(row = row, accent = accent, active = active)

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.name.ifBlank {
                            stringResource(R.string.reminder_default_name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.every_interval,
                            intervalLabel(reminder.intervalMinutes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    // Reflects the timer's own setting, so a global pause doesn't look like
                    // the user switched every timer off individually.
                    checked = reminder.enabled,
                    onCheckedChange = onToggle,
                    enabled = masterEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedBorderColor = accent,
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaChip(
                    icon = { modifier ->
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = modifier)
                    },
                    text = scheduleSummary(reminder),
                    // Long schedule summaries give way first; the countdown must stay readable.
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                MetaChip(
                    icon = { modifier ->
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = modifier)
                    },
                    text = statusText(row, masterEnabled),
                    emphasised = active && row.isWithinSchedule,
                )
            }
        }
    }
}

@Composable
private fun statusText(row: ReminderRow, masterEnabled: Boolean): String {
    val reminder = row.reminder
    return when {
        !masterEnabled || !reminder.enabled -> stringResource(R.string.paused)
        reminder.nextTriggerAt <= 0L -> stringResource(R.string.schedule_never)
        !row.isWithinSchedule -> stringResource(
            R.string.waiting_for_window,
            formatClockTime(reminder.nextTriggerAt),
        )

        else -> stringResource(R.string.next_in, formatCountdown(row.remainingMillis))
    }
}

@Composable
private fun ProgressBadge(row: ReminderRow, accent: Color, active: Boolean) {
    val progress by animateFloatAsState(
        targetValue = row.progress,
        animationSpec = tween(600),
        label = "cardProgress",
    )

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                color = accent,
                trackColor = accent.copy(alpha = 0.18f),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (active) accent.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) {
                    Icons.Rounded.NotificationsActive
                } else {
                    Icons.Rounded.NotificationsOff
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaChip(
    icon: @Composable (Modifier) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (emphasised) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (emphasised) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .size(56.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.empty_action))
        }
        Spacer(Modifier.height(40.dp))
        MadeInEurope()
    }
}

@Composable
private fun DontKillMyAppDialog(onOpenGuide: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dkma_title)) },
        text = { Text(stringResource(R.string.dkma_text)) },
        confirmButton = {
            Button(onClick = onOpenGuide) {
                Text(stringResource(R.string.dkma_open_guide))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dkma_do_not_show_again))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    val now = System.currentTimeMillis()
    ReReminderTheme(dynamicColor = false) {
        MainScreen(
            uiState = MainUiState(
                loaded = true,
                rows = listOf(
                    ReminderRow(
                        reminder = Reminder(
                            id = 1,
                            name = "Take a walk",
                            intervalMinutes = 50,
                            colorIndex = 0,
                            nextTriggerAt = now + 12 * 60_000L,
                        ),
                        remainingMillis = 12 * 60_000L,
                        isWithinSchedule = true,
                    ),
                    ReminderRow(
                        reminder = Reminder(
                            id = 2,
                            name = "Sit or stand",
                            intervalMinutes = 20,
                            days = Reminder.WEEKDAYS,
                            startMinute = 8 * 60,
                            endMinute = 17 * 60,
                            colorIndex = 2,
                            nextTriggerAt = now + 3 * 60_000L,
                        ),
                        remainingMillis = 3 * 60_000L,
                        isWithinSchedule = true,
                    ),
                    ReminderRow(
                        reminder = Reminder(
                            id = 3,
                            name = "Drink water",
                            intervalMinutes = 90,
                            enabled = false,
                            colorIndex = 4,
                        ),
                        remainingMillis = 0L,
                        isWithinSchedule = false,
                    ),
                ),
            ),
            onToggle = { _, _ -> },
            onToggleMaster = {},
            onEdit = {},
            onCreate = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    ReReminderTheme(dynamicColor = false) {
        MainScreen(
            uiState = MainUiState(loaded = true),
            onToggle = { _, _ -> },
            onToggleMaster = {},
            onEdit = {},
            onCreate = {},
            onSettings = {},
        )
    }
}
