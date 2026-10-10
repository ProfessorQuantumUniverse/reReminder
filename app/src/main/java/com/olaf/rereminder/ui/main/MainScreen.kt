package com.olaf.rereminder.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.olaf.rereminder.data.AlertStyle
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.Repeat
import com.olaf.rereminder.data.RepeatUnit
import com.olaf.rereminder.data.displayName
import com.olaf.rereminder.ui.components.CollapsingHeader
import com.olaf.rereminder.ui.components.LiveText
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.components.rememberCollapseProgress
import com.olaf.rereminder.ui.format.formatCoarseCountdown
import com.olaf.rereminder.ui.format.formatCountdown
import com.olaf.rereminder.ui.format.formatStartMoment
import com.olaf.rereminder.ui.format.repeatLabel
import com.olaf.rereminder.ui.format.scheduleSummary
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.ui.theme.pressScale
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tick
import com.olaf.rereminder.utils.AlertMode
import com.olaf.rereminder.utils.PreferenceHelper
import com.olaf.rereminder.utils.ReliabilityIssue
import com.olaf.rereminder.utils.ReliabilityStatus
import com.olaf.rereminder.utils.TimeLabels
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    val undoGeneration by viewModel.undoGeneration.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.list_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val alertModeLabels = AlertMode.entries.associateWith { stringResource(alertModeMessage(it)) }

    // Re-arms anything that drifted while another screen or another app was in front, and
    // re-checks the reliability story in case the user just changed it in system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    MainScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        undoGeneration = undoGeneration,
        onToggle = viewModel::setEnabled,
        onToggleMaster = viewModel::setMasterEnabled,
        onEdit = onEdit,
        onCreate = onCreate,
        onSettings = onSettings,
        onSortMode = viewModel::setSortMode,
        onCycleAlertMode = {
            val mode = viewModel.cycleAlertMode()
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(alertModeLabels.getValue(mode), duration = SnackbarDuration.Short)
            }
        },
        onReorder = viewModel::reorder,
        onDelete = { id ->
            val removed = viewModel.delete(id) ?: return@MainScreen
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = deletedMessage.format(removed.displayName(context)),
                    actionLabel = undoLabel,
                    // Long enough to notice a slip of the thumb and take it back.
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            }
        },
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
    onSortMode: (String) -> Unit,
    onCycleAlertMode: () -> Unit = {},
    onReorder: (List<Int>) -> Unit,
    onDelete: (Int) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    undoGeneration: Int = 0,
) {
    val listState = rememberLazyListState()
    val collapseProgress by rememberCollapseProgress(listState)
    val view = LocalView.current

    // The button keeps its label until the user starts reading the list, then gets out of the way.
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80 }
    }

    // While a card is being dragged the list shows this order instead of the stored one, so the
    // card follows the finger at once; it is saved when the finger lifts.
    var dragOrder by remember { mutableStateOf<List<Int>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val storedIds = uiState.rows.map { it.id }
    LaunchedEffect(storedIds, dragging) {
        // Hand back to the stored order once it has caught up with the drop.
        if (!dragging && dragOrder != null && (dragOrder == storedIds || dragOrder!!.toSet() != storedIds.toSet())) {
            dragOrder = null
        }
    }
    val rows = dragOrder
        ?.mapNotNull { id -> uiState.rows.firstOrNull { it.id == id } }
        ?.takeIf { it.size == uiState.rows.size }
        ?: uiState.rows

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Int ?: return@rememberReorderableLazyListState
        val toId = to.key as? Int ?: return@rememberReorderableLazyListState
        val order = (dragOrder ?: rows.map { it.id }).toMutableList()
        val fromIndex = order.indexOf(fromId)
        val toIndex = order.indexOf(toId)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        order.add(toIndex, order.removeAt(fromIndex))
        dragOrder = order
        view.tick()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CollapsingHeader(
                title = stringResource(R.string.app_name),
                progress = collapseProgress,
                actions = {
                    AlertModeButton(mode = uiState.alertMode, onClick = onCycleAlertMode)
                    AnimatedVisibility(
                        visible = uiState.rows.size > 1,
                        enter = scaleIn(Motion.expressive()) + fadeIn(),
                        exit = scaleOut(Motion.snappy()) + fadeOut(),
                    ) {
                        SortMenu(mode = uiState.sortMode, onSortMode = onSortMode)
                    }
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
                    verticalArrangement = Arrangement.spacedBy(if (uiState.compact) 8.dp else 12.dp),
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
                    items(rows, key = { it.id }) { row ->
                        ReorderableItem(reorderState, key = row.id) { isDragging ->
                            // A lifted card rises a little above the rest.
                            val lift by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                animationSpec = Motion.spatial(),
                                label = "dragLift",
                            )
                            val liftScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
                                animationSpec = Motion.spatial(),
                                label = "dragScale",
                            )
                            SwipeToDelete(
                                enabled = !isDragging,
                                resetKey = undoGeneration,
                                onDelete = { onDelete(row.id) },
                            ) {
                                ReminderCard(
                                    row = row,
                                    masterEnabled = uiState.masterEnabled,
                                    compact = uiState.compact,
                                    elevation = lift,
                                    onToggle = { enabled -> onToggle(row.id, enabled) },
                                    onClick = { onEdit(row.id) },
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = liftScale
                                            scaleY = liftScale
                                        }
                                        .longPressDraggableHandle(
                                            enabled = uiState.canReorder,
                                            onDragStarted = {
                                                dragging = true
                                                view.tap()
                                            },
                                            onDragStopped = {
                                                dragging = false
                                                dragOrder?.let(onReorder)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sound → vibrate only → mute, one tap each. Anything but "sound" is tinted, so a muted app is
 * visible at a glance and nobody wonders later why their reminders went quiet.
 */
@Composable
private fun AlertModeButton(mode: AlertMode, onClick: () -> Unit) {
    val view = LocalView.current
    val tint by animateColorAsState(
        targetValue = if (mode == AlertMode.SOUND) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        animationSpec = Motion.fade(),
        label = "alertModeTint",
    )
    IconButton(onClick = { view.tap(); onClick() }) {
        // The new icon turns in while the old one turns away — a small dial being clicked over.
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (scaleIn(Motion.expressive(), initialScale = 0.5f) + fadeIn())
                    .togetherWith(scaleOut(Motion.snappy(), targetScale = 0.5f) + fadeOut())
            },
            label = "alertModeIcon",
        ) { shown ->
            val rotation = remember { Animatable(-45f) }
            LaunchedEffect(Unit) { rotation.animateTo(0f, Motion.expressive()) }
            Icon(
                imageVector = when (shown) {
                    AlertMode.SOUND -> Icons.AutoMirrored.Rounded.VolumeUp
                    AlertMode.VIBRATE -> Icons.Rounded.Vibration
                    AlertMode.MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
                },
                contentDescription = stringResource(alertModeMessage(shown)),
                tint = tint,
                modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
            )
        }
    }
}

private fun alertModeMessage(mode: AlertMode): Int = when (mode) {
    AlertMode.SOUND -> R.string.alert_mode_sound
    AlertMode.VIBRATE -> R.string.alert_mode_vibrate
    AlertMode.MUTE -> R.string.alert_mode_mute
}

/** Custom order (hold and drag) or soonest first — two choices, one small menu (#8). */
@Composable
private fun SortMenu(mode: String, onSortMode: (String) -> Unit) {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { view.tap(); open = true }) {
            Icon(Icons.Rounded.SwapVert, contentDescription = stringResource(R.string.sort_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                PreferenceHelper.SORT_CUSTOM to R.string.sort_custom,
                PreferenceHelper.SORT_NEXT to R.string.sort_next,
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = mode == value, onClick = null) },
                    onClick = {
                        view.tick()
                        onSortMode(value)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Swipe a card to the left to delete it; Undo in the snackbar brings it back. The bin grows and
 * deepens as the swipe nears the point of no return, and ticks once when it gets there.
 */
@Composable
private fun SwipeToDelete(
    enabled: Boolean,
    /** A new value starts from a settled card — see [MainViewModel.undoGeneration]. */
    resetKey: Int,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val state = key(resetKey) { rememberSwipeToDismissBoxState() }
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart

    LaunchedEffect(armed) { if (armed) view.tick() }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (armed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                animationSpec = Motion.fade(),
                label = "deleteBackground",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (armed) 1.25f else 0.85f,
                animationSpec = Motion.expressive(),
                label = "deleteIcon",
            )
            // Only there while the card is actually being swiped. Drawn all the time, it peeked
            // out round the edges whenever the card shrank under a press or lifted for a drag.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
        },
    ) {
        content()
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
                // Only the countdown's digits tick; the sentence around it stays put.
                LiveText(
                    text = summary.text,
                    ticker = summary.ticker,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { view.tap(); onToggle(it) },
            )
        }
    }
}

/** A line of text and, when it contains one, the live countdown inside it. */
private data class StatusLine(val text: String, val ticker: String? = null)

/** A countdown reads well for the next few hours; beyond a day the moment itself is clearer. */
private const val COUNTDOWN_LIMIT_MILLIS = 24 * 60 * 60_000L

@Composable
private fun masterSummary(uiState: MainUiState): StatusLine {
    val context = LocalContext.current
    val next = uiState.nextUp
    val fallbackName = stringResource(R.string.reminder_default_name)
    return when {
        !uiState.masterEnabled -> StatusLine(stringResource(R.string.master_paused))
        next == null -> StatusLine(stringResource(R.string.master_nothing_running))
        next.isPending -> StatusLine(
            stringResource(
                R.string.master_next_on,
                next.reminder.name.ifBlank { fallbackName },
                formatStartMoment(next.reminder.firstEventAt),
            )
        )

        !next.isWithinSchedule || next.remainingMillis > COUNTDOWN_LIMIT_MILLIS -> StatusLine(
            stringResource(
                R.string.master_next_at,
                next.reminder.name.ifBlank { fallbackName },
                TimeLabels.moment(context, next.reminder.nextTriggerAt),
            )
        )

        else -> {
            val countdown = formatCountdown(next.remainingMillis)
            StatusLine(
                stringResource(
                    R.string.master_next_in,
                    next.reminder.name.ifBlank { fallbackName },
                    countdown,
                ),
                ticker = countdown,
            )
        }
    }
}

@Composable
private fun ReminderCard(
    row: ReminderRow,
    masterEnabled: Boolean,
    compact: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
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
        elevation = CardDefaults.cardElevation(defaultElevation = elevation, pressedElevation = elevation),
    ) {
        // Switching to the compact list morphs every card in place rather than swapping it.
        Column(
            modifier = Modifier
                .animateContentSize(Motion.spatial())
                .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBadge(row = row, accent = accent, active = active, compact = compact)

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reminder.name.ifBlank {
                                stringResource(R.string.reminder_default_name)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = titleColor,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (reminder.alertStyle == AlertStyle.ALARM) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Alarm,
                                contentDescription = stringResource(R.string.alert_style_alarm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    if (compact) {
                        // Title and when it is due — all the compact list asks for (#8).
                        val status = statusText(row, masterEnabled)
                        LiveText(
                            text = status.text,
                            ticker = status.ticker,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active && row.isWithinSchedule && !row.isPending) {
                                accent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    } else {
                        Text(
                            text = repeatLabel(reminder.repeat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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

            if (!compact) {
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
                        status = statusText(row, masterEnabled),
                        emphasised = active && row.isWithinSchedule && !row.isPending,
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
                            startAtMillis = reminder.firstEventAt,
                            // Counted to the event itself, not to an early alert ahead of it.
                            remainingMillis = row.remainingMillis +
                                (reminder.nextEventAt - reminder.nextTriggerAt).coerceAtLeast(0L),
                            accent = accent,
                        )
                    }
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
private fun statusText(row: ReminderRow, masterEnabled: Boolean): StatusLine {
    val context = LocalContext.current
    val reminder = row.reminder
    return when {
        !masterEnabled || !reminder.enabled -> StatusLine(stringResource(R.string.paused))
        row.isPending -> StatusLine(stringResource(R.string.not_started_yet))
        reminder.nextTriggerAt <= 0L -> StatusLine(stringResource(R.string.schedule_never))
        !row.isWithinSchedule || row.remainingMillis > COUNTDOWN_LIMIT_MILLIS -> StatusLine(
            stringResource(R.string.next_at, TimeLabels.moment(context, reminder.nextTriggerAt))
        )

        else -> {
            val countdown = formatCountdown(row.remainingMillis)
            StatusLine(stringResource(R.string.next_in, countdown), ticker = countdown)
        }
    }
}

@Composable
private fun ProgressBadge(row: ReminderRow, accent: Color, active: Boolean, compact: Boolean) {
    val progress by animateFloatAsState(
        targetValue = row.progress,
        animationSpec = tween(600),
        label = "cardProgress",
    )
    val size by animateDpAsState(
        targetValue = if (compact) 40.dp else 48.dp,
        animationSpec = Motion.spatial(),
        label = "badgeSize",
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
            .size(size)
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
                modifier = Modifier.size(size),
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
                .size(size - 14.dp)
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
    modifier: Modifier = Modifier,
    text: String = "",
    status: StatusLine? = null,
    emphasised: Boolean = false,
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
            if (status != null) {
                // A ticking countdown rolls only the digits that changed.
                LiveText(
                    text = status.text,
                    ticker = status.ticker,
                    style = MaterialTheme.typography.labelMedium,
                )
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

private fun previewRows(now: Long) = listOf(
    ReminderRow(
        reminder = Reminder(
            id = 1,
            name = "Take a walk",
            repeat = Repeat(RepeatUnit.TIME, 50),
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
            repeat = Repeat(RepeatUnit.WEEKS, 1),
            alertStyle = AlertStyle.ALARM,
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
            repeat = Repeat(RepeatUnit.TIME, 90),
            enabled = false,
            colorIndex = 4,
        ),
        remainingMillis = 0L,
        isWithinSchedule = false,
    ),
)

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    ReReminderTheme(dynamicColor = false) {
        MainScreen(
            uiState = MainUiState(loaded = true, rows = previewRows(System.currentTimeMillis())),
            onToggle = { _, _ -> },
            onToggleMaster = {},
            onEdit = {},
            onCreate = {},
            onSettings = {},
            onSortMode = {},
            onReorder = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompactListPreview() {
    ReReminderTheme(dynamicColor = false) {
        MainScreen(
            uiState = MainUiState(loaded = true, compact = true, rows = previewRows(System.currentTimeMillis())),
            onToggle = { _, _ -> },
            onToggleMaster = {},
            onEdit = {},
            onCreate = {},
            onSettings = {},
            onSortMode = {},
            onReorder = {},
            onDelete = {},
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
            onSortMode = {},
            onReorder = {},
            onDelete = {},
        )
    }
}
