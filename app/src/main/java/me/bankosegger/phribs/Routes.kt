package me.bankosegger.phribs

import kotlinx.serialization.Serializable

/**
 * The app's navigation destinations, as type-safe routes.
 *
 * Starting a blank workout and starting one from a routine are separate destinations rather than
 * one with a nullable id: Navigation has no built-in [androidx.navigation.NavType] for a nullable
 * primitive, and two destinations beat either a custom NavType or a magic "no routine" id.
 */
@Serializable
object Home

@Serializable
object Settings

@Serializable
object LogWorkout

@Serializable
data class LogRoutine(val routineId: Long)

@Serializable
data class ExerciseProgress(val exerciseName: String)
