package me.bankosegger.phribs.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.Workout

@Dao
abstract class WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    abstract fun getAllWorkouts(): Flow<List<WorkoutWithExercises>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkout(workout: WorkoutEntity)

    @Insert
    abstract suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert
    abstract suspend fun insertSets(sets: List<ExerciseSetEntity>)

    private suspend fun insertExercises(workoutId: Long, exercises: List<Exercise>) {
        exercises.forEach { e ->
            val exerciseId = insertExercise(ExerciseEntity(workoutId = workoutId, name = e.name))
            insertSets(e.sets.mapIndexed { i, s ->
                ExerciseSetEntity(exerciseId = exerciseId, position = i, reps = s.reps, weightKg = s.weightKg)
            })
        }
    }

    @Transaction
    open suspend fun insertFullWorkout(workout: WorkoutEntity, exercises: List<Exercise>) {
        insertWorkout(workout)
        insertExercises(workout.id, exercises)
    }

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    abstract suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Transaction
    open suspend fun updateFullWorkout(workoutId: Long, exercises: List<Exercise>) {
        deleteExercisesForWorkout(workoutId)
        insertExercises(workoutId, exercises)
    }

    // Replaces workouts with matching ids, so importing the same backup twice doesn't duplicate anything.
    @Transaction
    open suspend fun importWorkouts(workouts: List<Workout>) {
        workouts.forEach { w ->
            insertWorkout(WorkoutEntity(id = w.id, date = w.date))
            deleteExercisesForWorkout(w.id)
            insertExercises(w.id, w.exercises)
        }
    }

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    abstract suspend fun deleteWorkoutById(workoutId: Long)

    @Query("DELETE FROM workouts")
    abstract suspend fun deleteAllWorkouts()

    @Query("SELECT COUNT(*) FROM workouts")
    abstract suspend fun getWorkoutCount(): Int
}
