package com.olaf.rereminder.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.ui.theme.tick
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A drag-to-set value wheel.
 *
 * Beta testers were tapping a stepper fifteen times to reach thirty minutes, so the primary
 * gesture here is a flick: one throw crosses the whole range, and the list snaps to whichever
 * item sits under the selection band. Values away from the centre shrink and fade, which is what
 * makes it read as a physical wheel rather than a scrolling list.
 *
 * [onValueChange] reports the value under the band continuously while dragging, not only once
 * the wheel comes to rest, so the live summary above it keeps up with the thumb.
 */
@Composable
fun <T> WheelPicker(
    values: List<T>,
    selected: T,
    onValueChange: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 46.dp,
    visibleItems: Int = 5,
    contentDescriptionOf: (T) -> String = { label(it) },
) {
    if (values.isEmpty()) return

    // An odd count keeps exactly one item centred in the selection band.
    val rowCount = if (visibleItems % 2 == 0) visibleItems + 1 else visibleItems
    val edgePadding = itemHeight * (rowCount / 2)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val view = LocalView.current

    // The item under the band is the first visible one: the leading content padding has already
    // pushed it into the centre, so no extra offset maths is needed.
    val centeredIndex by remember(values) {
        derivedStateOf {
            val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val carry = if (listState.firstVisibleItemScrollOffset > itemSize / 2) 1 else 0
            (listState.firstVisibleItemIndex + carry).coerceIn(values.indices)
        }
    }

    // The collector below outlives any single composition, so it must read the latest selection
    // and callback rather than the ones captured when it started.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // True while the wheel is gliding to a value set from outside. The values it passes on the way
    // are not choices: reporting them would overwrite the preset with whatever the wheel happened
    // to cross first — tapping "5 min" from "4 h" used to land on 3 h 05 min.
    var gliding by remember { mutableStateOf(false) }

    LaunchedEffect(values) {
        snapshotFlow { centeredIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (gliding) return@collect
                val value = values[index]
                if (value != currentSelected) {
                    view.tick()
                    currentOnValueChange(value)
                }
            }
    }

    // Follow programmatic changes (a preset chip, say) without fighting an in-progress drag.
    LaunchedEffect(selected) {
        val target = values.indexOf(selected)
        if (target >= 0 && target != centeredIndex && !listState.isScrollInProgress) {
            gliding = true
            try {
                listState.animateScrollToItem(target)
            } finally {
                // A finger landing on the wheel mid-glide cancels it; from then on it is the
                // user choosing again.
                gliding = false
            }
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = edgePadding),
        modifier = modifier
            .height(itemHeight * rowCount)
            // Without an offscreen layer the DstIn mask below would punch through everything
            // already painted underneath the wheel, dialog surface included.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.25f to Color.Black,
                        0.75f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        items(values.size) { index ->
            val distance = kotlin.math.abs(index - centeredIndex)
            val focused = distance == 0
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(values[index]),
                    style = if (focused) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    textAlign = TextAlign.Center,
                    color = if (focused) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            val shrink = (distance * 0.09f).coerceAtMost(0.3f)
                            scaleX = 1f - shrink
                            scaleY = 1f - shrink
                            alpha = 1f - (distance * 0.32f).coerceAtMost(0.78f)
                        }
                        .semantics { contentDescription = contentDescriptionOf(values[index]) },
                )
            }
        }
    }
}
