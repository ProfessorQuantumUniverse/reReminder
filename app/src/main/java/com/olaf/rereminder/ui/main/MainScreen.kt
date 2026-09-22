package com.olaf.rereminder.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.ui.components.CollapsingHeader
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.components.rememberCollapseProgress
import com.olaf.rereminder.ui.format.formatClockTime
import com.olaf.rereminder.ui.format.formatCoarseCountdown
import com.olaf.rereminder.ui.format.formatCountdown
import com.olaf.rereminder.ui.format.formatStartMoment
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.ui.theme.pressScale
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.utils.ReliabilityIssue
import com.olaf.rereminder.utils.ReliabilityStatus

@Composable
fun MainRoute(
    onEdit: (Int) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reliability by viewModel.reliabilityPrompt.collectAsStateWithLifecycle()

    // Re-arms anything that drifted while another screen or another app was in front, and
    // re-checks the reliability story in case the user just changed it in system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    MainScreen(
        uiState = uiState,
        onToggle = viewModel::setEnabled,
        onToggleMaster = viewModel::setMasterEnabled,
        onEdit = onEdit,
        onCreate = onCreate,
        onSettings = onSettings,
    )

    reliability?.let { status ->
        ReliabilityDialog(
            status = status,
            onOpenGuide = {
                viewModel.dismissReliabilityPrompt()
                // A device with no browser would otherwise take the app down with it.
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, status.guideUrl.toUri()))
                }.onFailure {
                    Toast.makeText(context, R.string.reliability_screen_missing, Toast.LENGTH_SHORT)
                        .show()
                }
            },
            onOpenSettings = {
                viewModel.dismissReliabilityPrompt()
                onSettings()
            },
            onSilence = viewModel::silenceReliabilityPrompt,
            onDismiss = viewModel::dismissReliabilityPrompt,
        )
    }
}

@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggle: (Int, Boolean) -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onEdit: (Int) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val collapseProgress by rememberCollapseProgress(listState)
    val view = LocalView.current

    // The button keeps its label until the user starts reading the list, then gets out of the way.
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80 }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CollapsingHeader(
                title = stringResource(R.string.app_name),
                progress = collapseProgress,
                actions = {
                    IconButton(onClick = { view.tap(); onSettings() }) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_button_label),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isEmpty,
                enter = scaleIn(Motion.expressive()) + fadeIn(),
                exit = scaleOut(Motion.snappy()) + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { view.tap(); onCreate() },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_timer)) },
                )
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.isEmpty,
            transitionSpec = {
                (fadeIn(tween(Motion.ENTER_MILLIS)) + scaleIn(Motion.spatial(), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(Motion.EXIT_MILLIS)))
            },
            label = "listOrEmpty",
        ) { empty ->
            if (empty) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onCreate = onCreate,
                )
            } else {
                LazyColumn(
                    state = listState,
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
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .animateItem(),
                        )
                    }
                    items(uiState.rows, key = { it.id }) { row ->
                        ReminderCard(
                            row = row,
                            masterEnabled = uiState.masterEnabled,
                            onToggle = { enabled -> onToggle(row.id, enabled) },
                            onClick = { onEdit(row.id) },
                            // Adding, deleting or reordering a timer slides the rest into place.
                            modifier = Modifier.animateItem(
                                fadeInSpec = Motion.fade(),
                                placementSpec = Motion.spatial(),
                                fadeOutSpec = Motion.fade(),
                            ),
                        )
                    }
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
    val view = LocalView.current

    val containerColor by animateColorAsState(
        targetValue = if (on) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = Motion.fade(),
        label = "masterContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (on) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.fade(),
        label = "masterContent",
    )

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

                val summary = masterSummary(uiState)
                // The summary changes every second while a countdown is running, so it cross-fades
                // rather than blinking from one string to the next.
                AnimatedContent(
                    targetState = summary,
                    transitionSpec = {
                        fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                    },
                    label = "masterSummary",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = on,
                onCheckedChange = { view.tap(); onToggle(it) },
            )
        }
    }
}

@Composable
private fun masterSummary(uiState: MainUiState): String {
    val next = uiState.nextUp
    val fallbackName = stringResource(R.string.reminder_default_name)
    return when {
        !uiState.masterEnabled -> stringResource(R.string.master_paused)
        next == null -> stringResource(R.string.master_nothing_running)
        next.isPending -> stringResource(
            R.string.master_next_on,
            next.reminder.name.ifBlank { fallbackName },
            formatStartMoment(next.reminder.startAtMillis),
        )

        !next.isWithinSchedule -> stringResource(
            R.string.master_next_at,
            next.reminder.name.ifBlank { fallbackName },
            formatClockTime(next.reminder.nextTriggerAt),
        )

        else -> stringResource(
            R.string.master_next_in,
            next.reminder.name.ifBlank { fallbackName },
            formatCountdown(next.remainingMillis),
        )
    }
}

@Composable
private fun ReminderCard(
    row: ReminderRow,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reminder = row.reminder
    val accent = accentColor(reminder.colorIndex)
    // A timer only reads as running when the master switch lets it.
    val active = reminder.enabled && masterEnabled
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = Motion.fade(),
        label = "cardContainer",
    )

    Card(
        onClick = { view.tap(); onClick() },
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBadge(row = row, accent = accent, active = active)

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleColor by animateColorAsState(
                        targetValue = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        animationSpec = Motion.fade(),
                        label = "cardTitle",
                    )
                    Text(
                        text = reminder.name.ifBlank {
                            stringResource(R.string.reminder_default_name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = titleColor,
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
                    onCheckedChange = { view.tap(); onToggle(it) },
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
                    icon = { iconModifier ->
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    text = scheduleSummary(reminder),
                    // Long schedule summaries give way first; the countdown must stay readable.
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                MetaChip(
                    icon = { iconModifier ->
                        Icon(
                            imageVector = if (row.isPending) {
                                Icons.Rounded.EventAvailable
                            } else {
                                Icons.Rounded.Timer
                            },
                            contentDescription = null,
                            modifier = iconModifier,
                        )
                    },
                    text = statusText(row, masterEnabled),
                    emphasised = active && row.isWithinSchedule && !row.isPending,
                    animateText = true,
                )
            }

            // Only a timer that has not begun yet — and is actually armed — needs to say when it
            // will. A paused one already says "Paused"; a countdown next to that reads as a
            // contradiction.
            AnimatedVisibility(
                visible = row.isPending && active,
                enter = fadeIn() + expandVertically(Motion.spatial()),
                exit = fadeOut() + shrinkVertically(Motion.snappy()),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    StartBanner(
                        startAtMillis = reminder.startAtMillis,
                        remainingMillis = row.remainingMillis,
                        accent = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartBanner(startAtMillis: Long, remainingMillis: Long, accent: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.EventAvailable,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.starts_on,
                    formatStartMoment(startAtMillis),
                    formatCoarseCountdown(remainingMillis),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusText(row: ReminderRow, masterEnabled: Boolean): String {
    val reminder = row.reminder
    return when {
        !masterEnabled || !reminder.enabled -> stringResource(R.string.paused)
        row.isPending -> stringResource(R.string.not_started_yet)
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

    // The last minute before a reminder fires, the badge breathes — visible from across a desk
    // without needing to read the countdown.
    val imminent = active && !row.isPending &&
        row.remainingMillis in 1..IMMINENT_MILLIS &&
        row.isWithinSchedule
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (imminent) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = active,
            enter = fadeIn() + scaleIn(Motion.expressive(), initialScale = 0.6f),
            exit = fadeOut() + scaleOut(Motion.snappy(), targetScale = 0.6f),
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                color = accent,
                trackColor = accent.copy(alpha = 0.18f),
            )
        }

        val badgeColor by animateColorAsState(
            targetValue = if (active) {
                accent.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            animationSpec = Motion.fade(),
            label = "badgeFill",
        )

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    row.isPending && active -> BadgeIcon.PENDING
                    active -> BadgeIcon.RUNNING
                    else -> BadgeIcon.OFF
                },
                transitionSpec = {
                    (scaleIn(Motion.expressive(), initialScale = 0.5f) + fadeIn())
                        .togetherWith(scaleOut(Motion.snappy(), targetScale = 0.5f) + fadeOut())
                },
                label = "badgeIcon",
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        BadgeIcon.RUNNING -> Icons.Rounded.NotificationsActive
                        BadgeIcon.PENDING -> Icons.Rounded.EventAvailable
                        BadgeIcon.OFF -> Icons.Rounded.NotificationsOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state == BadgeIcon.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        accent
                    },
                )
            }
        }
    }
}

private enum class BadgeIcon { RUNNING, PENDING, OFF }

private const val IMMINENT_MILLIS = 60_000L

@Composable
private fun MetaChip(
    icon: @Composable (Modifier) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
    animateText: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = if (emphasised) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = Motion.fade(),
        label = "chipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (emphasised) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.fade(),
        label = "chipContent",
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            if (animateText) {
                // A ticking countdown rolls upward one value at a time.
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        (slideInVertically(Motion.snappy()) { h -> h } + fadeIn(tween(140)))
                            .togetherWith(
                                slideOutVertically(Motion.snappy()) { h -> -h } + fadeOut(tween(140))
                            )
                    },
                    label = "chipText",
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    val view = LocalView.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val transition = rememberInfiniteTransition(label = "emptyFloat")
    val float by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emptyFloatY",
    )

    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(400)) + scaleIn(Motion.expressive(), initialScale = 0.6f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // A slow drift keeps an otherwise dead screen alive.
                modifier = Modifier.graphicsLayer { translationY = float },
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(24.dp)
                        .size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(400, delayMillis = 120)) +
                slideInVertically(Motion.spatial()) { it / 3 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Button(onClick = { view.tap(); onCreate() }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.empty_action))
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        MadeInEurope()
    }
}

/**
 * Explains why reminders might not arrive, and links to the page that actually covers this
 * device rather than the site's front door.
 *
 * It names the problems it found, because "your manufacturer may stop apps" on its own gives the
 * user nothing to act on — and it only appears for a combination of problems they have not
 * already waved away.
 */
@Composable
private fun ReliabilityDialog(
    status: ReliabilityStatus,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onSilence: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Something the app can walk the user to; a manufacturer restriction is not.
    val hasFixableIssue = status.issues.any { it != ReliabilityIssue.VENDOR }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.reliability_prompt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.reliability_prompt_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                status.issues.forEach { issue ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = issueLabel(issue, status.vendorName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (hasFixableIssue) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.reliability_prompt_open_settings))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenGuide) {
                Text(stringResource(R.string.reliability_prompt_open_guide))
            }
        },
        dismissButton = {
            TextButton(onClick = onSilence) {
                Text(stringResource(R.string.dkma_do_not_show_again))
            }
        },
    )
}

@Composable
private fun issueLabel(issue: ReliabilityIssue, vendorName: String): String =
    if (issue == ReliabilityIssue.VENDOR) {
        if (vendorName.isBlank()) {
            stringResource(R.string.reliability_issue_vendor_generic)
        } else {
            stringResource(issue.labelRes, vendorName)
        }
    } else {
        stringResource(issue.labelRes)
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
                            name = "Lunch at the cathedral",
                            intervalMinutes = 60 * 24 * 7,
                            colorIndex = 3,
                            startAtMillis = now + 14L * 24 * 60 * 60_000L,
                            nextTriggerAt = now + 14L * 24 * 60 * 60_000L,
                        ),
                        remainingMillis = 14L * 24 * 60 * 60_000L,
                        isWithinSchedule = false,
                        isPending = true,
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
