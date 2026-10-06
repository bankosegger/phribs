package me.bankosegger.phribs.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.workoutDao()
    }

    @After
    fun closeDb() = db.close()

    private fun exercise(name: String, sets: Int = 3) = Exercise(name, List(sets) { ExerciseSet(10, 50.0) })

    private suspend fun all() = dao.getAllWorkouts().first()

    @Test
    fun insertedWorkoutIsReturnedWithExercises() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise("Squat"), exercise("Bench")))

        val result = all().single().toDomainModel()
        assertEquals(1000L, result.date)
        assertEquals(setOf("Squat", "Bench"), result.exercises.map { it.name }.toSet())
    }

    @Test
    fun workoutsAreNewestFirst() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), emptyList())
        dao.insertFullWorkout(WorkoutEntity(2, 3000), emptyList())
        dao.insertFullWorkout(WorkoutEntity(3, 2000), emptyList())

        assertEquals(listOf(3000L, 2000L, 1000L), all().map { it.workout.date })
    }

    @Test
    fun updateReplacesExercises() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise("Squat"), exercise("Bench")))

        dao.updateFullWorkout(1, listOf(exercise("Deadlift", sets = 5)))

        val exercises = all().single().toDomainModel().exercises
        assertEquals(1, exercises.size)
        assertEquals("Deadlift", exercises[0].name)
        assertEquals(5, exercises[0].sets.size)
    }

    @Test
    fun setsKeepTheirOrderAndValues() = runBlocking {
        val sets = listOf(ExerciseSet(10, 60.0), ExerciseSet(8, 62.5), ExerciseSet(6, 65.0))
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(Exercise("Bench", sets), exercise("Squat")))

        val result = all().single().toDomainModel()
        assertEquals(listOf("Bench", "Squat"), result.exercises.map { it.name })
        assertEquals(sets, result.exercises[0].sets)
    }

    @Test
    fun exerciseNotesRoundTrip() = runBlocking {
        val noted = Exercise("Bench", listOf(ExerciseSet(10, 60.0)), "Left shoulder tight")
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(noted, exercise("Squat")))

        val exercises = all().single().toDomainModel().exercises
        assertEquals("Left shoulder tight", exercises[0].note)
        assertEquals("", exercises[1].note)
    }

    @Test
    fun deletingWorkoutAlsoDeletesItsExercises() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise("Squat")))
        dao.insertFullWorkout(WorkoutEntity(2, 2000), listOf(exercise("Bench")))

        dao.deleteWorkoutById(1)
        // Re-inserting the same id would pick up orphaned exercises if the cascade didn't run.
        dao.insertFullWorkout(WorkoutEntity(1, 1000), emptyList())

        val byId = all().associateBy { it.workout.id }
        assertTrue(byId.getValue(1).exercises.isEmpty())
        assertEquals("Bench", byId.getValue(2).exercises.single().exercise.name)
    }

    @Test
    fun deleteAllRemovesEverything() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise("Squat")))
        dao.insertFullWorkout(WorkoutEntity(2, 2000), listOf(exercise("Bench")))

        dao.deleteAllWorkouts()

        assertEquals(0, dao.getWorkoutCount())
    }

    @Test
    fun seededDataIsInThePastAndHasExercises() = runBlocking {
        val before = System.currentTimeMillis()
        dao.seedTestData(weeks = 12, random = Random(42))

        val workouts = all()
        assertTrue(workouts.size >= 12)
        assertTrue(workouts.all { it.workout.date <= before })
        assertTrue(workouts.all { it.exercises.isNotEmpty() })
    }
}
