package com.olaf.rereminder.ui.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The screen's size, for dialogs that change their layout when there is little room — a phone or
 * a small tablet held sideways.
 *
 * Read from the configuration on purpose: inside a dialog, `LocalWindowInfo` describes the
 * dialog's own window, which is the very thing being sized.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun screenSize(): DpSize {
    val configuration = LocalConfiguration.current
    return DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
}

/**
 * A phone held sideways: so little height that dialogs drop their titles and icons and use
 * Material's full dialog width, to keep their content in view.
 */
@Composable
internal fun isVeryShort(): Boolean = screenSize().height < VERY_SHORT_HEIGHT

private val VERY_SHORT_HEIGHT = 480.dp
