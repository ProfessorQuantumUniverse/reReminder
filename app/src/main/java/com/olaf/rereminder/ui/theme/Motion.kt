package com.olaf.rereminder.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState

/**
 * The app's motion vocabulary.
 *
 * Everything that moves uses a spring rather than a fixed duration, so an interrupted animation
 * carries its velocity into the next one instead of snapping. Durations only appear where there
 * is nothing to interrupt — colour cross-fades and the countdown ring.
 */
object Motion {

    /** Default for anything that changes size or position: settles fast, barely overshoots. */
    fun <T> spatial() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** For press feedback and other small, snappy reactions. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessHigh,
    )

    /** A visibly bouncy spring, reserved for moments worth noticing (a timer starting). */
    fun <T> expressive() = spring<T>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Colour and alpha have no mass, so they cross-fade on a duration. */
    fun <T> fade() = tween<T>(durationMillis = 280)

    const val ENTER_MILLIS = 320
    const val EXIT_MILLIS = 220

    /**
     * Screen transitions are the one place that deliberately does not use springs.
     *
     * With predictive back the system scrubs the pop transition along with the user's thumb and
     * then plays out the rest, and that only lines up when every part of the transition has a real
     * duration. Mixing a spring scale with a short tween fade meant a screen shrank under the
     * finger, then vanished in a single frame once the fade had long finished. Here every part of
     * a screen transition runs the same length, so scale, slide and fade arrive together.
     */
    const val SCREEN_ENTER_MILLIS = 380
    const val SCREEN_EXIT_MILLIS = 300

    fun <T> screenEnter() = tween<T>(SCREEN_ENTER_MILLIS, easing = EmphasizedDecelerate)
    fun <T> screenExit() = tween<T>(SCREEN_EXIT_MILLIS, easing = EmphasizedAccelerate)

    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Scales the element down while it is held.
 *
 * Applied to every tappable surface in the app: it is the cheapest possible confirmation that a
 * touch landed, and it makes a list of cards feel physical rather than painted on.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappy(),
        label = "pressScale",
    )
    scale(scale)
}

/**
 * A tap target that scales on press and ticks haptically on release — for elements that are not
 * backed by a Material component with its own interaction source (colour dots, day toggles).
 */
fun Modifier.tappable(
    enabled: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    this
        .pressScale(interactionSource, pressedScale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
        ) {
            view.tick()
            onClick()
        }
}

/**
 * Haptics go through the View rather than Compose's `LocalHapticFeedback` so the app can use the
 * precise system constants — a wheel notch should feel like a clock tick, not like a long press.
 */

/** A single light notch — wheel steps, toggles, chips. */
fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Slightly firmer than [tick] — committing a value. */
fun View.tap() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
