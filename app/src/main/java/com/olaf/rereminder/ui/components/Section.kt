package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
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
    // On a narrow phone with large text a long value ("Every month on the last Friday") used to
    // take the whole row and squeeze the title to one letter per line. Now, when the two do not
    // fit side by side, the title keeps at least its longest word — "Wiederholen" is never split —
    // and the value wraps in what is left.
    val titleStyle = MaterialTheme.typography.bodyLarge
    // A value wraps between words only: "Jeden Mo-nat" reads worse than one more line.
    val valueStyle = titleStyle.copy(fontWeight = FontWeight.Medium, hyphens = Hyphens.None)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = maxWidth - ROW_CHROME
        val split = remember(title, value, titleStyle, available, density) {
            fun widthOf(text: String, style: TextStyle) = with(density) {
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp()
            }
            val titleWidth = widthOf(title, titleStyle)
            val valueWidth = widthOf(value, valueStyle)
            if (titleWidth + valueWidth <= available) {
                null
            } else {
                val longestWord = title.split(' ').maxOf { widthOf(it, titleStyle) }
                (available - minOf(valueWidth, available * 0.6f))
                    .coerceAtLeast(longestWord)
                    .coerceAtMost(minOf(titleWidth, available * 0.72f))
            }
        }
        val valueMaxWidth = if (split == null) available else available - split
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tappable(enabled = enabled, pressedScale = 0.99f, onClick = onClick)
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (split == null) {
                Text(text = title, style = titleStyle, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
            } else {
                Text(text = title, style = titleStyle, modifier = Modifier.width(split))
                Spacer(Modifier.width(12.dp))
                Spacer(Modifier.weight(1f))
            }
            // The value is what changed, so it is what moves.
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(Motion.spatial()) { h -> h / 2 } + fadeIn())
                        .togetherWith(fadeOut())
                },
                label = "sectionRowValue",
                modifier = Modifier.widthIn(max = valueMaxWidth),
                contentAlignment = Alignment.CenterEnd,
            ) { text ->
                Text(
                    text = text,
                    style = valueStyle,
                    color = valueColor,
                    textAlign = TextAlign.End,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What a [SectionValueRow] spends besides title and value: its padding, the gap and the arrow. */
private val ROW_CHROME = 72.dp

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
