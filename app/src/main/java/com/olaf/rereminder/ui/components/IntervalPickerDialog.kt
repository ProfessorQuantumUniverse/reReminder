package com.olaf.rereminder.ui.components

import androidx.compose.foundation.layout.*
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
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Erinnerungsintervall",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Section
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Stunden", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column {
                            Button(
                                onClick = { if (selectedHours < 23) selectedHours++ },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("+")
                            }

                            Text(
                                text = selectedHours.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .width(48.dp)
                            )

                            Button(
                                onClick = { if (selectedHours > 0) selectedHours-- },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("-")
                            }
                        }
                    }

                    // Minutes Section
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minuten", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column {
                            Button(
                                onClick = { if (selectedMinutes < 59) selectedMinutes++ },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("+")
                            }

                            Text(
                                text = selectedMinutes.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .width(48.dp)
                            )

                            Button(
                                onClick = { if (selectedMinutes > 1) selectedMinutes-- },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("-")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abbrechen")
                    }

                    Button(
                        onClick = {
                            onIntervalSelected(selectedHours, selectedMinutes)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}