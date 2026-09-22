package com.olaf.rereminder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.olaf.rereminder.ui.editor.ReminderEditorRoute
import com.olaf.rereminder.ui.main.MainRoute
import com.olaf.rereminder.ui.settings.SettingsRoute
import com.olaf.rereminder.ui.theme.Motion

object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{id}"
    const val ARG_ID = "id"

    fun editor(id: Int) = "editor/$id"
}

/**
 * The whole app in one navigation graph.
 *
 * The editor rises from below and the list settles back a little behind it, which reads as one
 * surface moving in front of another — the effect an Activity transition can only approximate.
 * Settings, being a sibling rather than a detail, slides in from the side instead.
 */
@Composable
fun ReReminderApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.LIST,
        ) {
            composable(
                route = Routes.LIST,
                enterTransition = { recedeIn() },
                exitTransition = { recedeOut() },
                popEnterTransition = { recedeIn() },
                popExitTransition = { recedeOut() },
            ) {
                MainRoute(
                    onEdit = { id -> navController.navigate(Routes.editor(id)) },
                    onCreate = { navController.navigate(Routes.editor(NEW_REMINDER_ID)) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.IntType
                        defaultValue = NEW_REMINDER_ID
                    }
                ),
                enterTransition = { riseIn() },
                exitTransition = { fadeOut(tween(Motion.EXIT_MILLIS)) },
                popExitTransition = { sinkOut() },
            ) {
                // The id reaches the editor's ViewModel through its SavedStateHandle.
                ReminderEditorRoute(onClose = { navController.popBackStack() })
            }

            composable(
                route = Routes.SETTINGS,
                enterTransition = { slideIn() },
                popExitTransition = { slideOut() },
            ) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

private const val NEW_REMINDER_ID = 0

// --- Transitions ----------------------------------------------------------

/** A detail screen arriving: lifts up from below while growing to full size. */
private fun riseIn(): EnterTransition =
    slideInVertically(Motion.spatial()) { height -> height / 5 } +
        fadeIn(tween(Motion.ENTER_MILLIS)) +
        scaleIn(Motion.spatial(), initialScale = 0.92f)

/** The same screen leaving under the user's back gesture. */
private fun sinkOut(): ExitTransition =
    slideOutVertically(Motion.spatial()) { height -> height / 5 } +
        fadeOut(tween(Motion.EXIT_MILLIS)) +
        scaleOut(Motion.spatial(), targetScale = 0.92f)

/** The list stepping back behind whatever opened on top of it. */
private fun recedeOut(): ExitTransition =
    fadeOut(tween(Motion.EXIT_MILLIS)) + scaleOut(Motion.spatial(), targetScale = 0.96f)

private fun recedeIn(): EnterTransition =
    fadeIn(tween(Motion.ENTER_MILLIS)) + scaleIn(Motion.spatial(), initialScale = 0.96f)

private fun AnimatedContentTransitionScope<*>.slideIn(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = Motion.spatial(),
    ) + fadeIn(tween(Motion.ENTER_MILLIS))

private fun AnimatedContentTransitionScope<*>.slideOut(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = Motion.spatial(),
    ) + fadeOut(tween(Motion.EXIT_MILLIS))
