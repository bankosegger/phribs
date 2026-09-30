package me.bankosegger.phribs.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun exercise(workoutId: Long, name: String, sets: Int = 3) =
        ExerciseEntity(workoutId = workoutId, name = name, sets = sets, reps = 10, weightKg = 50.0)

    private suspend fun all() = dao.getAllWorkouts().first()

    @Test
    fun insertedWorkoutIsReturnedWithExercises() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise(1, "Squat"), exercise(1, "Bench")))

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
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise(1, "Squat"), exercise(1, "Bench")))

        dao.updateFullWorkout(1, listOf(exercise(1, "Deadlift", sets = 5)))

        val exercises = all().single().exercises
        assertEquals(1, exercises.size)
        assertEquals("Deadlift", exercises[0].name)
        assertEquals(5, exercises[0].sets)
    }

    @Test
    fun deletingWorkoutAlsoDeletesItsExercises() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise(1, "Squat")))
        dao.insertFullWorkout(WorkoutEntity(2, 2000), listOf(exercise(2, "Bench")))

        dao.deleteWorkoutById(1)
        // Re-inserting the same id would pick up orphaned exercises if the cascade didn't run.
        dao.insertFullWorkout(WorkoutEntity(1, 1000), emptyList())

        val byId = all().associateBy { it.workout.id }
        assertTrue(byId.getValue(1).exercises.isEmpty())
        assertEquals("Bench", byId.getValue(2).exercises.single().name)
    }

    @Test
    fun deleteAllRemovesEverything() = runBlocking {
        dao.insertFullWorkout(WorkoutEntity(1, 1000), listOf(exercise(1, "Squat")))
        dao.insertFullWorkout(WorkoutEntity(2, 2000), listOf(exercise(2, "Bench")))

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
