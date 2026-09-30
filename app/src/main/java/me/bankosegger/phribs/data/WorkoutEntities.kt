package me.bankosegger.phribs.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSet
import me.bankosegger.phribs.Workout

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val id: Long,
    val date: Long
)

@Entity(
    tableName = "exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String
)

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"])]
)
data class ExerciseSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double
)

data class ExerciseWithSets(
    @Embedded val exercise: ExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseId"
    )
    val sets: List<ExerciseSetEntity>
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = ExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exercises: List<ExerciseWithSets>
) {
    // Relations come back in no guaranteed order, so sort by insertion order explicitly.
    fun toDomainModel(): Workout {
        return Workout(
            id = workout.id,
            date = workout.date,
            exercises = exercises.sortedBy { it.exercise.id }.map { e ->
                Exercise(
                    name = e.exercise.name,
                    sets = e.sets.sortedBy { it.position }.map { ExerciseSet(it.reps, it.weightKg) }
                )
            }
        )
    }
}
