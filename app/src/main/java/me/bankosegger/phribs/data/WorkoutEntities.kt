package me.bankosegger.phribs.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import me.bankosegger.phribs.Exercise
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
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutId"
    )
    val exercises: List<ExerciseEntity>
) {
    fun toDomainModel(): Workout {
        return Workout(
            id = workout.id,
            date = workout.date,
            exercises = exercises.map {
                Exercise(
                    name = it.name,
                    sets = it.sets,
                    reps = it.reps,
                    weightKg = it.weightKg
                )
            }
        )
    }
}