package com.olaf.rereminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.olaf.rereminder.ui.navigation.ReReminderApp
import com.olaf.rereminder.ui.setup.SetupRoute
import com.olaf.rereminder.ui.theme.Motion
import com.olaf.rereminder.ui.theme.ReReminderTheme
import com.olaf.rereminder.utils.PreferenceHelper

/**
 * The app's only Activity. Every screen is a destination inside one Compose navigation graph, so
 * moving between the list, the editor and the settings is a continuous animation rather than an
 * Activity swap.
 *
 * The one thing that lives outside the graph is the guided first run: it has no way back, and
 * nothing behind it to navigate to yet.
 */
class MainActivity : ComponentActivity() {

    private var setupComplete by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setupComplete = readSetupComplete()

        setContent {
            ReReminderTheme {
                // Finishing setup reveals the app rather than replacing it in one frame.
                AnimatedContent(
                    targetState = setupComplete,
                    transitionSpec = {
                        (fadeIn(Motion.fade()) + scaleIn(Motion.spatial(), initialScale = 0.94f))
                            .togetherWith(
                                fadeOut(Motion.fade()) +
                                    scaleOut(Motion.spatial(), targetScale = 1.06f)
                            )
                    },
                    label = "setupGate",
                ) { ready ->
                    if (ready) {
                        ReReminderApp()
                    } else {
                        SetupRoute(onFinished = { setupComplete = true })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A user who revoked notifications from system settings is sent back through setup,
        // because without them the app cannot do the one thing it is for.
        if (setupComplete && !notificationPermissionGranted()) {
            setupComplete = false
        }
    }

    private fun readSetupComplete(): Boolean =
        PreferenceHelper(this).isSetupComplete(notificationPermissionGranted()) &&
            notificationPermissionGranted()

    private fun notificationPermissionGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
