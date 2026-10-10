package com.olaf.rereminder.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaf.rereminder.R
import com.olaf.rereminder.service.AlarmService
import com.olaf.rereminder.service.RingingAlarm
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.accentColor
import com.olaf.rereminder.ui.theme.pressScale
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.utils.TimeLabels

/**
 * The full-screen alarm. Shows over the lock screen and switches the display on; it closes itself
 * as soon as the alarm stops, whichever way that happened (here, from the notification, or by
 * timing out).
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            ReReminderTheme {
                val alarm by AlarmService.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(alarm) {
                    if (alarm == null) finish()
                }
                alarm?.let { ringing ->
                    AlarmScreen(
                        alarm = ringing,
                        onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE) },
                        onDismiss = { AlarmService.send(this, AlarmService.ACTION_DISMISS) },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(
    alarm: RingingAlarm,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = accentColor(alarm.colorIndex)
    val view = LocalView.current

    // One slow breath — present enough to read as "ringing", calm enough at 6 a.m.
    val breath = rememberInfiniteTransition(label = "alarmBreath")
    val haloScale by breath.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloScale",
    )
    val haloAlpha by breath.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloAlpha",
    )

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .scale(haloScale)
                            .graphicsLayer { alpha = haloAlpha }
                            .background(accent, CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Alarm,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    text = TimeLabels.clock(alarm.eventAt),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = alarm.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alarm.message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = alarm.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.weight(1.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val snoozeSource = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = { view.tap(); onSnooze() },
                        interactionSource = snoozeSource,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .pressScale(snoozeSource, pressedScale = 0.94f),
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.alarm_snooze),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(R.string.snooze_minutes, alarm.snoozeMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    val dismissSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { view.tap(); onDismiss() },
                        interactionSource = dismissSource,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .pressScale(dismissSource, pressedScale = 0.94f),
                    ) {
                        Text(
                            stringResource(R.string.alarm_dismiss),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlarmScreenPreview() {
    ReReminderTheme(dynamicColor = false) {
        AlarmScreen(
            alarm = RingingAlarm(
                reminderId = 1,
                title = "Take medication",
                message = "With a glass of water",
                eventAt = System.currentTimeMillis(),
                colorIndex = 2,
                snoozeMinutes = 10,
            ),
            onSnooze = {},
            onDismiss = {},
        )
    }
}
