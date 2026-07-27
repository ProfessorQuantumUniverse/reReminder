package com.olaf.rereminder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.R
import com.olaf.rereminder.ui.theme.ReReminderTheme

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

    // The interval must never be zero, otherwise the alarm would fire immediately.
    fun normalized(): Pair<Int, Int> =
        if (hours == 0 && minutes == 0) 0 to 1 else hours to minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interval_dialog_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TimeStepper(
                    value = hours,
                    onValueChange = { hours = it },
                    range = 0..maxHours,
                    label = stringResource(R.string.hours_label),
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp),
                )
                TimeStepper(
                    value = minutes,
                    onValueChange = { minutes = it },
                    range = 0..59,
                    label = stringResource(R.string.minutes_label),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (h, m) = normalized()
                    onIntervalSelected(h, m)
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
private fun TimeStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
) {
    val increaseLabel = stringResource(R.string.increase_value, label)
    val decreaseLabel = stringResource(R.string.decrease_value, label)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = increaseLabel },
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(84.dp)
                    .padding(vertical = 12.dp)
                    .semantics { contentDescription = "$label: $value" },
            )
        }

        Spacer(Modifier.height(8.dp))

        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = decreaseLabel },
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
