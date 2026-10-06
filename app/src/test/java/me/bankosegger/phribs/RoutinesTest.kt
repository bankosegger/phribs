package me.bankosegger.phribs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutinesTest {
    private val day = 86_400_000L

    private fun workout(id: Long, daysAgo: Int, vararg exercises: Exercise) =
        Workout(id = id, date = 1_700_000_000_000L - daysAgo * day, exercises = exercises.toList())

    private fun exercise(name: String, vararg sets: Pair<Int, Double>, note: String = "") =
        Exercise(name, sets.map { ExerciseSet(it.first, it.second) }, note)

    @Test
    fun `last sessions keep the newest version of each exercise`() {
        val workouts = listOf(
            workout(1, daysAgo = 10, exercise("Bench Press", 10 to 60.0)),
            workout(2, daysAgo = 2, exercise("Bench Press", 8 to 70.0, 8 to 70.0))
        )
        val last = lastSessions(workouts)
        assertEquals(listOf(ExerciseSet(8, 70.0), ExerciseSet(8, 70.0)), last["bench press"]?.sets)
    }

    @Test
    fun `last sessions match names case-insensitively`() {
        val workouts = listOf(workout(1, daysAgo = 1, exercise("Bench PRESS", 5 to 80.0)))
        assertEquals(1, lastSessions(workouts)["bench press"]?.sets?.size)
    }

    @Test
    fun `prefill carries over the sets last logged`() {
        val workouts = listOf(
            workout(1, daysAgo = 5, exercise("Squat", 5 to 100.0, 5 to 100.0)),
            workout(2, daysAgo = 1, exercise("Squat", 5 to 105.0, 5 to 105.0, 3 to 105.0))
        )
        val routine = Routine(1, "Leg day", listOf("Squat"))
        val filled = prefill(routine, lastSessions(workouts))
        assertEquals(1, filled.size)
        assertEquals(List(2) { ExerciseSet(5, 105.0) } + ExerciseSet(3, 105.0), filled[0].sets)
    }

    @Test
    fun `prefill drops the note from the previous session`() {
        val workouts = listOf(workout(1, daysAgo = 1, exercise("Squat", 5 to 100.0, note = "felt heavy")))
        val filled = prefill(Routine(1, "Leg day", listOf("Squat")), lastSessions(workouts))
        assertEquals("", filled[0].note)
    }

    @Test
    fun `prefill gives an unlogged exercise one default set`() {
        val filled = prefill(Routine(1, "New", listOf("Face Pull")), lastSessions(emptyList()))
        assertEquals(listOf(ExerciseSet(10, 0.0)), filled[0].sets)
        assertEquals("Face Pull", filled[0].name)
    }

    @Test
    fun `prefill prefers the spelling already in the history`() {
        val workouts = listOf(workout(1, daysAgo = 1, exercise("Bench Press", 5 to 80.0)))
        val filled = prefill(Routine(1, "Push", listOf("bench press")), lastSessions(workouts))
        assertEquals("Bench Press", filled[0].name)
    }

    @Test
    fun `prefill keeps the routine's order`() {
        val workouts = listOf(
            workout(1, daysAgo = 1, exercise("Dips", 10 to 0.0), exercise("Bench Press", 5 to 80.0))
        )
        val routine = Routine(1, "Push", listOf("Bench Press", "Dips"))
        assertEquals(listOf("Bench Press", "Dips"), prefill(routine, lastSessions(workouts)).map { it.name })
    }

    @Test
    fun `routine exercise names drop blanks and duplicates`() {
        val exercises = listOf(
            exercise("Bench Press", 5 to 80.0),
            exercise("  ", 5 to 0.0),
            exercise("bench press", 5 to 85.0),
            exercise(" Dips ", 10 to 0.0)
        )
        assertEquals(listOf("Bench Press", "Dips"), routineExerciseNames(exercises))
    }

    @Test
    fun `routines are found by name ignoring case and surrounding space`() {
        val routines = listOf(Routine(1, "Push day", listOf("Bench Press")))
        assertEquals(1L, findRoutineByName(routines, "  push DAY ")?.id)
        assertNull(findRoutineByName(routines, "Pull day"))
    }
}
