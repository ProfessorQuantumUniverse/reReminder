package com.olaf.rereminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun IntervalPickerDialogCompose(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onIntervalSelected: (hours: Int, minutes: Int) -> Unit
) {
    var selectedHours by remember { mutableStateOf(currentInterval / 60) }
    var selectedMinutes by remember { mutableStateOf(maxOf(1, currentInterval % 60)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Intervall einstellen",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimePicker(
                        label = "Stunden",
                        value = selectedHours,
                        onValueChange = { selectedHours = it },
                        range = 0..23
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    TimePicker(
                        label = "Minuten",
                        value = selectedMinutes,
                        onValueChange = { selectedMinutes = it },
                        range = 1..59
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onIntervalSelected(selectedHours, selectedMinutes) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
fun TimePicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - 1).coerceIn(range)) }) {
                Text("-", style = MaterialTheme.typography.headlineSmall)
            }

            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.width(60.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            IconButton(onClick = { onValueChange((value + 1).coerceIn(range)) }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}