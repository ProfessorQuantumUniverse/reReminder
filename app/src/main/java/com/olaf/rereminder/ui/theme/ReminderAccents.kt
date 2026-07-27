package com.olaf.rereminder.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent colours users can assign to a timer so they're distinguishable at a glance.
 * Mid-tone hues, picked to stay readable on both the light and the dark surface.
 */
val ReminderAccents: List<Color> = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF0EA5E9), // sky
    Color(0xFF14B8A6), // teal
    Color(0xFF22C55E), // green
    Color(0xFFF59E0B), // amber
    Color(0xFFEC4899), // pink
)

fun accentColor(index: Int): Color = ReminderAccents[index.mod(ReminderAccents.size)]
