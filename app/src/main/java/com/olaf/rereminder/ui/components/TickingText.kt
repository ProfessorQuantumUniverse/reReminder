package com.olaf.rereminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.olaf.rereminder.ui.theme.Motion

/**
 * A countdown whose digits roll individually.
 *
 * Animating the whole string made every line with a countdown flash once a second, although only
 * the last digit had changed. Here each character is its own slot, and a slot only moves when its
 * character does — "12:39" to "12:40" rolls the last two digits and leaves "12:" standing still.
 *
 * Slots are keyed from the end of the string, so when the countdown gets shorter ("10:00" to
 * "9:59") the seconds stay in the slots they were in instead of every character shifting over.
 * Tabular figures keep each digit the same width, so the text does not jiggle while it ticks.
 */
@Composable
fun TickingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val tabular = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        (slideInVertically(Motion.snappy()) { h -> h } + fadeIn(tween(140)))
                            .togetherWith(
                                slideOutVertically(Motion.snappy()) { h -> -h } + fadeOut(tween(140))
                            )
                    },
                    label = "tickingChar",
                ) { value ->
                    Text(
                        text = value.toString(),
                        style = tabular,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * A line of text with a live countdown inside it, e.g. "Next: Walk in 12:34".
 *
 * Only [ticker] moves from one second to the next. The words around it change only when the state
 * itself changes (a timer paused, a window closing), and that swap is the one moment the whole line
 * is allowed to cross-fade. The words shrink with an ellipsis before the countdown ever does.
 */
@Composable
fun LiveText(
    text: String,
    modifier: Modifier = Modifier,
    ticker: String? = null,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val at = ticker?.takeIf { it.isNotEmpty() }?.let { text.lastIndexOf(it) } ?: -1
    val line = if (at >= 0) {
        LiveLine(prefix = text.substring(0, at), suffix = text.substring(at + ticker!!.length), ticks = true)
    } else {
        LiveLine(prefix = text, suffix = "", ticks = false)
    }

    AnimatedContent(
        targetState = line,
        transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
        modifier = modifier,
        label = "liveText",
    ) { current ->
        Row {
            Text(
                text = current.prefix,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (current.ticks && ticker != null) {
                TickingText(text = ticker, style = style, color = color)
            }
            if (current.suffix.isNotEmpty()) {
                Text(text = current.suffix, style = style, color = color, maxLines = 1)
            }
        }
    }
}

/** The static part of a [LiveText] line — what decides whether the line as a whole changed. */
private data class LiveLine(val prefix: String, val suffix: String, val ticks: Boolean)
