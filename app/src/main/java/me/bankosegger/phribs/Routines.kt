package me.bankosegger.phribs

// Matches SetInput's defaults, so an exercise with no history opens as "1 × 10" rather than blank.
private val DEFAULT_SET = ExerciseSet(reps = 10, weightKg = 0.0)

/** The most recent logged version of every exercise, keyed by lowercased name. */
internal fun lastSessions(workouts: List<Workout>): Map<String, Exercise> =
    workouts.sortedBy { it.date }
        .flatMap { it.exercises }
        // Later entries win, so this ends up holding the newest version of each name.
        .associateBy { it.name.lowercase() }

/**
 * The exercises to start a workout with, each carrying over the sets from the last time it was
 * logged. Notes are dropped: they describe how that session went, not this one.
 */
internal fun prefill(routine: Routine, lastSessions: Map<String, Exercise>): List<Exercise> =
    routine.exerciseNames.map { name ->
        val last = lastSessions[name.lowercase()]
        Exercise(
            // The logged spelling is the one the user has been using; keep history consistent with it.
            name = last?.name ?: name,
            sets = last?.sets?.takeIf { it.isNotEmpty() } ?: listOf(DEFAULT_SET)
        )
    }

/** Exercise names of a workout, de-duplicated, ready to be saved as a routine. */
internal fun routineExerciseNames(exercises: List<Exercise>): List<String> =
    exercises.map { it.name.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

internal fun findRoutineByName(routines: List<Routine>, name: String): Routine? =
    routines.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
