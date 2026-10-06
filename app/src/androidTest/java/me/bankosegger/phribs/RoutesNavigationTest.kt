package me.bankosegger.phribs

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a graph from the app's real route types. Navigation maps routes to arguments at graph
 * build time, so a route it can't handle fails here — which is the one thing compiling the app
 * can't tell us. The screens are stubbed out to keep the database out of it.
 */
@RunWith(AndroidJUnit4::class)
class RoutesNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun startGraph() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = Home) {
                composable<Home> { Text("home") }
                composable<Settings> { Text("settings") }
                composable<LogWorkout> { Text("log") }
                composable<LogRoutine> { entry ->
                    Text("routine ${entry.toRoute<LogRoutine>().routineId}")
                }
                composable<ExerciseProgress> { entry ->
                    Text("exercise ${entry.toRoute<ExerciseProgress>().exerciseName}")
                }
            }
        }
    }

    @Test
    fun graphStartsAtHome() {
        startGraph()
        compose.onNodeWithText("home").assertIsDisplayed()
    }

    @Test
    fun argumentlessDestinationsNavigateAndPopBack() {
        startGraph()

        compose.runOnIdle { nav.navigate(Settings) }
        compose.onNodeWithText("settings").assertIsDisplayed()

        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("home").assertIsDisplayed()

        compose.runOnIdle { nav.navigate(LogWorkout) }
        compose.onNodeWithText("log").assertIsDisplayed()
    }

    @Test
    fun routineIdSurvivesTheRoundTrip() {
        startGraph()

        compose.runOnIdle { nav.navigate(LogRoutine(routineId = 42L)) }
        compose.onNodeWithText("routine 42").assertIsDisplayed()
    }

    // The reason these routes are type-safe rather than hand-built strings: exercise names contain
    // spaces and slashes, which a string route would have to URL-encode by hand.
    @Test
    fun awkwardExerciseNameSurvivesTheRoundTrip() {
        startGraph()

        val name = "Pull-up / Chin-up (wide, 25% bw)"
        compose.runOnIdle { nav.navigate(ExerciseProgress(name)) }
        compose.onNodeWithText("exercise $name").assertIsDisplayed()
    }
}
