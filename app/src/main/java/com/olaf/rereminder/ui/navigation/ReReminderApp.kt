package com.olaf.rereminder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
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
import androidx.navigation.NavController
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
                    onEdit = { id -> navController.navigateFrom(Routes.LIST, Routes.editor(id)) },
                    onCreate = {
                        navController.navigateFrom(Routes.LIST, Routes.editor(NEW_REMINDER_ID))
                    },
                    onSettings = { navController.navigateFrom(Routes.LIST, Routes.SETTINGS) },
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
                popExitTransition = { sinkOut() },
            ) {
                // The id reaches the editor's ViewModel through its SavedStateHandle.
                ReminderEditorRoute(onClose = { navController.popFrom(Routes.EDITOR) })
            }

            composable(
                route = Routes.SETTINGS,
                enterTransition = { slideIn() },
                popExitTransition = { slideOut() },
            ) {
                SettingsRoute(onBack = { navController.popFrom(Routes.SETTINGS) })
            }
        }
    }
}

private const val NEW_REMINDER_ID = 0

/**
 * Navigation guarded against double taps.
 *
 * A screen keeps receiving input while it animates out, so a second tap on the back arrow (or
 * back, then Save) used to pop again — taking the list with it and leaving an empty window — and
 * two quick taps on a card stacked two editors. Each action now only acts if the screen it came
 * from is still the one on top.
 */
private fun NavController.popFrom(route: String) {
    if (currentDestination?.route == route) popBackStack()
}

private fun NavController.navigateFrom(route: String, destination: String) {
    if (currentDestination?.route == route) navigate(destination)
}

// --- Transitions ----------------------------------------------------------
//
// All of these run on Motion.screenEnter/screenExit, never on springs, so predictive back can
// scrub them and every part of a transition ends on the same frame (see Motion).

/** A detail screen arriving: lifts up from below while growing to full size. */
private fun riseIn(): EnterTransition =
    slideInVertically(Motion.screenEnter()) { height -> height / 5 } +
        fadeIn(tween(Motion.SCREEN_ENTER_MILLIS, easing = LinearEasing)) +
        scaleIn(Motion.screenEnter(), initialScale = 0.92f)

/** The same screen leaving, by the back arrow, a save or the back gesture. */
private fun sinkOut(): ExitTransition =
    slideOutVertically(Motion.screenExit()) { height -> height / 5 } +
        fadeOut(tween(Motion.SCREEN_EXIT_MILLIS, easing = LinearEasing)) +
        scaleOut(Motion.screenExit(), targetScale = 0.92f)

/**
 * The list stepping back behind whatever opened on top of it.
 *
 * It only dims rather than fading out completely: it sits underneath the screen that is fading
 * in, and two half-transparent screens let the bare window background flash through.
 */
private fun recedeOut(): ExitTransition =
    fadeOut(tween(Motion.SCREEN_EXIT_MILLIS, easing = LinearEasing), targetAlpha = 0.4f) +
        scaleOut(Motion.screenExit(), targetScale = 0.96f)

private fun recedeIn(): EnterTransition =
    fadeIn(tween(Motion.SCREEN_ENTER_MILLIS, easing = LinearEasing), initialAlpha = 0.4f) +
        scaleIn(Motion.screenEnter(), initialScale = 0.96f)

private fun AnimatedContentTransitionScope<*>.slideIn(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = Motion.screenEnter(),
    ) + fadeIn(tween(Motion.SCREEN_ENTER_MILLIS, easing = LinearEasing))

private fun AnimatedContentTransitionScope<*>.slideOut(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = Motion.screenExit(),
    ) + fadeOut(tween(Motion.SCREEN_EXIT_MILLIS, easing = LinearEasing))
