package com.olaf.rereminder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.ui.format.dayInitial
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.tappable
import java.time.DayOfWeek

/** Seven weekday toggles in a row, Monday first. */
@Composable
fun DayPills(
    selected: Set<Int>,
    accent: Color,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (isoDay in 1..7) {
            TogglePill(
                label = dayInitial(DayOfWeek.of(isoDay)),
                selected = isoDay in selected,
                accent = accent,
                modifier = Modifier
                    .weight(1f)
                    .height(height),
                onClick = { onToggle(isoDay) },
            )
        }
    }
}

/**
 * One round toggle. No resting scale for the selected state: the pills sit only a few dp apart,
 * and a bouncy spring past 1f pushed neighbours into each other. The press scale from `tappable`
 * and the colour fill are feedback enough.
 */
@Composable
fun TogglePill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = Motion.fade(),
        label = "pillBackground",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fade(),
        label = "pillText",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .tappable(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = textColor,
        )
    }
}
