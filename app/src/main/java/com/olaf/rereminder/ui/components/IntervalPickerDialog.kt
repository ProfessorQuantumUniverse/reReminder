package com.olaf.rereminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun IntervalPickerDialogCompose(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onIntervalSelected: (hours: Int, minutes: Int) -> Unit,
    maxHours: Int = 72
) {
    var selectedHours by remember { mutableStateOf((currentInterval / 60).coerceIn(0, maxHours)) }
    var selectedMinutes by remember { mutableStateOf((currentInterval % 60).coerceIn(1, 59)) }

    fun normalize() { if (selectedHours == 0 && selectedMinutes == 0) selectedMinutes = 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Reminder Interval",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimeStepperValue(
                        value = selectedHours,
                        onValueChange = { selectedHours = it.coerceIn(0, maxHours); normalize() },
                        range = 0..maxHours,
                        label = "Hours" // nur für Semantics hier
                    )

                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )

                    TimeStepperValue(
                        value = selectedMinutes,
                        onValueChange = { selectedMinutes = it.coerceIn(0, 59); normalize() },
                        range = 0..59,
                        label = "Minutes"
                    )
                }
                // Labels separat, damit vertikale Zentrierung nur auf Zahlen/Buttons wirkt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hours", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier) // Platzhalter unter dem Doppelpunkt
                    Text("Minutes", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
        confirmButton = {
            Button(onClick = { normalize(); onIntervalSelected(selectedHours, selectedMinutes) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TimeStepperValue(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(IntrinsicSize.Min)
    ) {
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            modifier = Modifier.semantics { contentDescription = "$label increase" }
        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(vertical = 2.dp)
                .semantics { contentDescription = "$label Value $value" },
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) },
            modifier = Modifier.semantics { contentDescription = "$label reduce" }
        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
    }
}