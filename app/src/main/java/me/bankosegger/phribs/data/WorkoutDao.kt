package me.bankosegger.phribs.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.bankosegger.phribs.Workout

@Dao
abstract class WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    abstract fun getAllWorkouts(): Flow<List<WorkoutWithExercises>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkout(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Transaction
    open suspend fun insertFullWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>) {
        insertWorkout(workout)
        insertExercises(exercises)
    }

    @Query("DELETE FROM exercises WHERE workoutId = :workoutId")
    abstract suspend fun deleteExercisesForWorkout(workoutId: Long)

    @Transaction
    open suspend fun updateFullWorkout(workoutId: Long, exercises: List<ExerciseEntity>) {
        deleteExercisesForWorkout(workoutId)
        insertExercises(exercises)
    }

    // Replaces workouts with matching ids, so importing the same backup twice doesn't duplicate anything.
    @Transaction
    open suspend fun importWorkouts(workouts: List<Workout>) {
        workouts.forEach { w ->
            insertWorkout(WorkoutEntity(id = w.id, date = w.date))
            deleteExercisesForWorkout(w.id)
            insertExercises(w.exercises.map {
                ExerciseEntity(workoutId = w.id, name = it.name, sets = it.sets, reps = it.reps, weightKg = it.weightKg)
            })
        }
    }

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    abstract suspend fun deleteWorkoutById(workoutId: Long)

    @Query("DELETE FROM workouts")
    abstract suspend fun deleteAllWorkouts()

    @Query("SELECT COUNT(*) FROM workouts")
    abstract suspend fun getWorkoutCount(): Int
}