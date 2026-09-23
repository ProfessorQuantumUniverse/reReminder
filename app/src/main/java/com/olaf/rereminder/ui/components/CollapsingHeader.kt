package com.olaf.rereminder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.olaf.rereminder.ui.theme.Motion

private val ExpandedHeight = 118.dp
private val CollapsedHeight = 62.dp
private val ActionRowHeight = 56.dp

/**
 * The app's header, hand-built rather than a Material `TopAppBar`.
 *
 * The stock bar can only cross-fade between a large and a small title, which is exactly the
 * boxed-in, chrome-heavy look this app is moving away from. Here the title is a single element
 * that scales and slides continuously with the scroll offset, the bar has no elevation or fill
 * until content actually passes underneath it, and the divider is a soft gradient rather than a
 * hard rule.
 */
@Composable
fun CollapsingHeader(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = progress.coerceIn(0f, 1f)
    val height = lerp(ExpandedHeight, CollapsedHeight, p)

    // Once collapsed the title has to clear the navigation icon; expanded it starts at the margin.
    val startPadding = if (navigationIcon != null) lerp(20.dp, 52.dp, p) else 20.dp
    val titleScale = lerp(1f, 0.64f, p)

    val containerColor by animateColorAsState(
        targetValue = if (p > 0.02f) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            Color.Transparent
        },
        animationSpec = Motion.fade(),
        label = "headerContainer",
    )

    // The header always reserves its expanded height, however far it has collapsed, and the
    // collapsing bar is drawn inside that space. If the reserved height followed the collapse, the
    // Scaffold would hand the content a different top padding on every frame, which changes how
    // far the content can scroll, which changes the collapse: at the end of a list that feedback
    // loop never settled and the title flickered between two sizes. The part of the reservation
    // the bar no longer covers is empty and takes no touches, so content scrolls up behind it.
    Box(modifier = modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .statusBarsPadding()
                .height(ExpandedHeight),
        )
        CollapsingBar(
            title = title,
            progress = p,
            height = height,
            startPadding = startPadding,
            titleScale = titleScale,
            containerColor = containerColor,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

@Composable
private fun CollapsingBar(
    title: String,
    progress: Float,
    height: Dp,
    startPadding: Dp,
    titleScale: Float,
    containerColor: Color,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val p = progress
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Fill first, inset second: the container colour runs behind the status bar while the
            // title and actions sit below it.
            .background(containerColor)
            .statusBarsPadding()
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ActionRowHeight)
                .padding(horizontal = 4.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            navigationIcon?.invoke()
            Box(Modifier.weight(1f))
            actions()
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startPadding, end = 20.dp, bottom = lerp(18.dp, 16.dp, p))
                .graphicsLayer {
                    // Scaling from the left edge keeps the first letter anchored, so the title
                    // shrinks in place instead of drifting sideways.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = titleScale
                    scaleY = titleScale
                },
        )

        // A gradient hairline: present enough to separate, too soft to read as a toolbar edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .graphicsLayer { alpha = p }
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to MaterialTheme.colorScheme.outlineVariant,
                        1f to Color.Transparent,
                    )
                ),
        )
    }
}

/**
 * How far the header has collapsed, 0f expanded to 1f collapsed.
 *
 * Read from the raw scroll offset rather than an animation, so the header tracks the finger
 * frame for frame instead of chasing it.
 */
@Composable
fun rememberCollapseProgress(
    listState: LazyListState,
    distance: Dp = ExpandedHeight - CollapsedHeight,
): State<Float> {
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return remember(listState, distancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / distancePx).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
fun rememberCollapseProgress(
    scrollState: ScrollState,
    distance: Dp = ExpandedHeight - CollapsedHeight,
): State<Float> {
    val distancePx = with(LocalDensity.current) { distance.toPx() }
    return remember(scrollState, distancePx) {
        derivedStateOf { (scrollState.value / distancePx).coerceIn(0f, 1f) }
    }
}
