package me.bankosegger.phribs.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import me.bankosegger.phribs.Routine

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId"])]
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val name: String
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val exercises: List<RoutineExerciseEntity>
) {
    // Relations come back in no guaranteed order, so restore the saved order explicitly.
    fun toDomainModel(): Routine = Routine(
        id = routine.id,
        name = routine.name,
        exerciseNames = exercises.sortedBy { it.position }.map { it.name }
    )
}
