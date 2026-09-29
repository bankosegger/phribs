package me.bankosegger.phribs.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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

    @Delete
    abstract suspend fun deleteWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    abstract suspend fun deleteWorkoutById(workoutId: Long)

    @Query("SELECT COUNT(*) FROM workouts")
    abstract suspend fun getWorkoutCount(): Int
}