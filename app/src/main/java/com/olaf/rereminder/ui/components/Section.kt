package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.tap
import com.olaf.rereminder.ui.theme.tappable

/**
 * The app's one grouping device: a titled card of rows.
 *
 * Settings and the editor both use it, so a row means the same thing wherever it appears. The
 * editor used to mix all-caps headers, full-bleed dividers and hand-drawn outlines in the same
 * scroll, which is what made it read as cluttered — three ways of saying "these belong together".
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            content = content,
        )
    }
}

/** Separates rows inside a [SectionCard]; inset so it never touches the card's rounded edge. */
@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A row whose value sits on the right — for settings you change in a dialog, where the value is
 * short enough to read at a glance ("30 min", "Tomorrow · 09:00").
 */
@Composable
fun SectionValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = Motion.fade(),
        label = "sectionRowAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tappable(enabled = enabled, pressedScale = 0.99f, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // The value is what changed, so it is what moves.
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(Motion.spatial()) { h -> h / 2 } + fadeIn())
                    .togetherWith(fadeOut())
            },
            label = "sectionRowValue",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row whose value is long enough to need its own line underneath the title. */
@Composable
fun SectionItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = Motion.fade(),
        label = "sectionItemAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tappable(enabled = enabled, pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                label = "sectionItemSubtitle",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row that toggles something, optionally explaining the current state underneath. */
@Composable
fun SectionSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tappable(pressedScale = 0.99f) { onCheckedChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = subtitle,
                    transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                    label = "sectionSwitchSubtitle",
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { view.tap(); onCheckedChange(it) })
    }
}
