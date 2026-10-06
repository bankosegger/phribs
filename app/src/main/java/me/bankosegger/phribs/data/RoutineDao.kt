package me.bankosegger.phribs.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    abstract fun getAllRoutines(): Flow<List<RoutineWithExercises>>

    @Insert
    abstract suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    abstract suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    abstract suspend fun deleteExercisesForRoutine(routineId: Long)

    @Query("UPDATE routines SET name = :name WHERE id = :routineId")
    abstract suspend fun renameRoutine(routineId: Long, name: String)

    @Query("DELETE FROM routines WHERE id = :routineId")
    abstract suspend fun deleteRoutineById(routineId: Long)

    @Query("DELETE FROM routines")
    abstract suspend fun deleteAllRoutines()

    private suspend fun insertExerciseNames(routineId: Long, exerciseNames: List<String>) {
        insertRoutineExercises(
            exerciseNames.mapIndexed { i, name ->
                RoutineExerciseEntity(routineId = routineId, position = i, name = name)
            }
        )
    }

    @Transaction
    open suspend fun createRoutine(name: String, exerciseNames: List<String>) {
        insertExerciseNames(insertRoutine(RoutineEntity(name = name)), exerciseNames)
    }

    /** Replaces a routine's exercise list wholesale; the id and name are kept. */
    @Transaction
    open suspend fun replaceRoutineExercises(routineId: Long, exerciseNames: List<String>) {
        deleteExercisesForRoutine(routineId)
        insertExerciseNames(routineId, exerciseNames)
    }
}
