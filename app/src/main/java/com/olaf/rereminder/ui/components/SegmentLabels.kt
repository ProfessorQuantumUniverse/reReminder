package com.olaf.rereminder.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What a segment spends besides its label: the button's padding and its border. */
val SegmentPadding: Dp = 26.dp

/** ...and, for a segment with an icon, the icon and the gap after it. */
val SegmentIconWidth: Dp = 26.dp

/**
 * One label style for a whole segmented button row, shrunk just enough that the longest label
 * fits its segment — on a small phone with large text "Stunden" used to lose its last letter.
 *
 * Material lays a segment's label out without a width limit and clips whatever sticks out, so the
 * text cannot notice on its own that it is too wide; the size is worked out here from the row's
 * width instead. Every segment gets the same size, so the row stays even.
 */
@Composable
fun rememberSegmentLabelStyle(
    labels: List<String>,
    rowWidth: Dp,
    style: TextStyle,
    reserved: Dp = SegmentPadding,
): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, rowWidth, style, reserved, density) {
        val fitted = style.copy(hyphens = Hyphens.None)
        if (labels.isEmpty()) return@remember fitted
        val available = with(density) { (rowWidth / labels.size - reserved).toPx() }
        val widest = labels.maxOf { measurer.measure(it, fitted, maxLines = 1, softWrap = false).size.width }
        if (available <= 0f || widest <= available) {
            fitted
        } else {
            val scale = available / widest
            val size = (fitted.fontSize.value * scale).coerceAtLeast(MIN_LABEL_SP)
            fitted.copy(fontSize = size.sp, letterSpacing = 0.sp)
        }
    }
}

private const val MIN_LABEL_SP = 9f
