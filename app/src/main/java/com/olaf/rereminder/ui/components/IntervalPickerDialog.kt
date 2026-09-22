package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.R
import com.olaf.rereminder.ui.format.intervalLabel
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.ui.theme.tap
import java.util.Locale

/** One flick from a preset covers almost every reminder people actually set. */
private val QuickIntervals = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 240)

private const val WHEEL_ITEM_HEIGHT_DP = 46
private const val WHEEL_ROWS = 5

@Composable
fun IntervalPickerDialogCompose(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onIntervalSelected: (hours: Int, minutes: Int) -> Unit,
    maxHours: Int = 72,
) {
    var hours by rememberSaveable {
        mutableIntStateOf((currentInterval / 60).coerceIn(0, maxHours))
    }
    var minutes by rememberSaveable {
        mutableIntStateOf((currentInterval % 60).coerceIn(0, 59))
    }
    val view = LocalView.current

    val total = hours * 60 + minutes
    // The interval must never be zero, otherwise the alarm would fire immediately.
    val effectiveTotal = total.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interval_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The headline reacts to the wheel while it is still moving, so the value being
                // chosen is readable without waiting for the wheel to settle.
                AnimatedContent(
                    targetState = effectiveTotal,
                    transitionSpec = {
                        val up = targetState > initialState
                        val enter = slideInVertically(Motion.spatial()) { h -> if (up) h else -h } +
                            fadeIn(spring(stiffness = Spring.StiffnessMedium))
                        val exit = slideOutVertically(Motion.spatial()) { h -> if (up) -h else h } +
                            fadeOut(spring(stiffness = Spring.StiffnessMedium))
                        enter togetherWith exit
                    },
                    label = "intervalHeadline",
                ) { value ->
                    Text(
                        text = intervalLabel(value),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.interval_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Box(contentAlignment = Alignment.Center) {
                    // The band sits behind the wheels, so the numbers scroll through it.
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WHEEL_ITEM_HEIGHT_DP.dp),
                        content = {},
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        val hoursLabel = stringResource(R.string.hours_label)
                        val minutesLabel = stringResource(R.string.minutes_label)

                        WheelPicker(
                            values = (0..maxHours).toList(),
                            selected = hours,
                            onValueChange = { hours = it },
                            label = { it.twoDigits() },
                            contentDescriptionOf = { "$hoursLabel: $it" },
                            itemHeight = WHEEL_ITEM_HEIGHT_DP.dp,
                            visibleItems = WHEEL_ROWS,
                            modifier = Modifier.width(92.dp),
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        WheelPicker(
                            values = (0..59).toList(),
                            selected = minutes,
                            onValueChange = { minutes = it },
                            label = { it.twoDigits() },
                            contentDescriptionOf = { "$minutesLabel: $it" },
                            itemHeight = WHEEL_ITEM_HEIGHT_DP.dp,
                            visibleItems = WHEEL_ROWS,
                            modifier = Modifier.width(92.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WheelCaption(stringResource(R.string.hours_label))
                    Spacer(Modifier.width(24.dp))
                    WheelCaption(stringResource(R.string.minutes_label))
                }

                Spacer(Modifier.height(16.dp))

                // Still one tap for the intervals people pick most of the time.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QuickIntervals.forEach { preset ->
                        FilterChip(
                            selected = preset == total,
                            onClick = {
                                view.tap()
                                hours = preset / 60
                                minutes = preset % 60
                            },
                            label = { Text(intervalLabel(preset)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    view.tap()
                    onIntervalSelected(effectiveTotal / 60, effectiveTotal % 60)
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun WheelCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(92.dp),
        textAlign = TextAlign.Center,
    )
}

private fun Int.twoDigits(): String = String.format(Locale.getDefault(), "%02d", this)

@Preview
@Composable
private fun IntervalPickerPreview() {
    ReReminderTheme(dynamicColor = false) {
        IntervalPickerDialogCompose(
            currentInterval = 90,
            onDismiss = {},
            onIntervalSelected = { _, _ -> },
        )
    }
}
