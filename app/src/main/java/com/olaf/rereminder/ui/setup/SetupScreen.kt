package com.olaf.rereminder.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaf.rereminder.R
import com.olaf.rereminder.ui.components.MadeInEurope
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.utils.Reliability

@Composable
fun SetupRoute(
    onFinished: () -> Unit,
    viewModel: SetupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Every step but the first hands off to a system screen; this is how their outcome comes back.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refresh()
        if (!granted) {
            Toast.makeText(context, R.string.permission_required_toast, Toast.LENGTH_LONG).show()
        }
    }

    fun openOrExplain(intents: List<Intent>) {
        if (!Reliability.startFirstAvailable(context, intents)) {
            Toast.makeText(context, R.string.reliability_screen_missing, Toast.LENGTH_LONG).show()
        }
    }

    SetupScreen(
        uiState = uiState,
        onStepAction = { step ->
            when (step) {
                SetupStep.NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                SetupStep.EXACT_ALARMS -> openOrExplain(viewModel.exactAlarmIntents())
                SetupStep.BATTERY -> openOrExplain(viewModel.batteryIntents())
                SetupStep.MANUFACTURER -> {
                    viewModel.markVendorGuideSeen()
                    openOrExplain(
                        listOf(Intent(Intent.ACTION_VIEW, viewModel.guideUrl().toUri()))
                    )
                }
            }
        },
        onFinish = {
            viewModel.complete()
            onFinished()
        },
    )
}

/**
 * The guided first run.
 *
 * Each permission is its own step with its own button, and each button lands on the screen that
 * actually toggles the thing — the app never drops the user into a list of every installed app
 * and leaves them to find it. Steps the device has already satisfied collapse to a tick, so the
 * list shrinks as the user works down it.
 */
@Composable
fun SetupScreen(
    uiState: SetupUiState,
    onStepAction: (SetupStep) -> Unit,
    onFinish: () -> Unit,
) {
    val view = LocalView.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val progress by animateFloatAsState(
        targetValue = if (uiState.steps.isEmpty()) {
            1f
        } else {
            uiState.doneCount.toFloat() / uiState.steps.size
        },
        animationSpec = Motion.spatial(),
        label = "setupProgress",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(Motion.fade()) + scaleIn(Motion.expressive(), initialScale = 0.8f),
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )

            Spacer(Modifier.height(24.dp))

            uiState.steps.forEachIndexed { index, state ->
                SetupStepCard(
                    index = index + 1,
                    state = state,
                    expanded = uiState.currentStep == state.step,
                    vendorName = uiState.vendorName,
                    onAction = { onStepAction(state.step) },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { view.tap(); onFinish() },
                enabled = uiState.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (uiState.allDone) R.string.setup_finish else R.string.setup_continue
                    )
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Everything optional can be revisited in Settings, so nobody is trapped here.
            AnimatedVisibility(
                visible = uiState.canContinue && !uiState.allDone,
                enter = fadeIn() + expandVertically(Motion.spatial()),
                exit = fadeOut() + shrinkVertically(Motion.snappy()),
            ) {
                Text(
                    text = stringResource(R.string.setup_skip_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            MadeInEurope()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    index: Int,
    state: SetupStepState,
    expanded: Boolean,
    vendorName: String,
    onAction: () -> Unit,
) {
    val view = LocalView.current
    val done = state.done

    val containerColor by animateColorAsState(
        targetValue = when {
            done -> MaterialTheme.colorScheme.surfaceContainerLow
            expanded -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = Motion.fade(),
        label = "stepContainer",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(index = index, done = done, icon = state.step.icon())

                Spacer(Modifier.width(14.dp))

                Text(
                    text = stringResource(state.step.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )

                AnimatedVisibility(
                    visible = done,
                    enter = scaleIn(Motion.expressive()) + fadeIn(),
                    exit = scaleOut(Motion.snappy()) + fadeOut(),
                ) {
                    Text(
                        text = stringResource(R.string.setup_step_done),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // A finished step shrinks to its title; only the one being worked on explains itself.
            AnimatedVisibility(
                visible = !done,
                enter = fadeIn() + expandVertically(Motion.spatial()),
                exit = fadeOut() + shrinkVertically(Motion.snappy()),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (state.step == SetupStep.MANUFACTURER && vendorName.isNotBlank()) {
                            stringResource(R.string.setup_vendor_body_named, vendorName)
                        } else {
                            stringResource(state.step.bodyRes)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { view.tap(); onAction() }) {
                        Text(stringResource(state.step.actionRes))
                        if (state.step == SetupStep.MANUFACTURER) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (!state.step.required) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.setup_step_optional),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(index: Int, done: Boolean, icon: ImageVector) {
    val background by animateColorAsState(
        targetValue = if (done) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = Motion.fade(),
        label = "badgeBackground",
    )
    // The tick lands with a bounce, which is the only celebration a permission screen gets.
    val scale by animateFloatAsState(
        targetValue = if (done) 1.08f else 1f,
        animationSpec = Motion.expressive(),
        label = "badgeScale",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = done,
            enter = scaleIn(Motion.expressive(), initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(Motion.snappy(), targetScale = 0.3f) + fadeOut(),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(
            visible = !done,
            enter = scaleIn(Motion.expressive(), initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(Motion.snappy(), targetScale = 0.3f) + fadeOut(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun SetupStep.icon(): ImageVector = when (this) {
    SetupStep.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    SetupStep.EXACT_ALARMS -> Icons.Rounded.Schedule
    SetupStep.BATTERY -> Icons.Rounded.BatteryFull
    SetupStep.MANUFACTURER -> Icons.Rounded.PhoneAndroid
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    ReReminderTheme(dynamicColor = false) {
        SetupScreen(
            uiState = SetupUiState(
                steps = listOf(
                    SetupStepState(SetupStep.NOTIFICATIONS, done = true),
                    SetupStepState(SetupStep.EXACT_ALARMS, done = false),
                    SetupStepState(SetupStep.BATTERY, done = false),
                    SetupStepState(SetupStep.MANUFACTURER, done = false),
                ),
                vendorName = "Xiaomi",
            ),
            onStepAction = {},
            onFinish = {},
        )
    }
}
