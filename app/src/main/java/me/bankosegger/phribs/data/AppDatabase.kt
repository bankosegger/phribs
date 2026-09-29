package me.bankosegger.phribs.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bankosegger.phribs.Storage

@Database(entities = [WorkoutEntity::class, ExerciseEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_tracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                // Automatically migrate legacy SharedPreferences data if present
                CoroutineScope(Dispatchers.IO).launch {
                    migrateLegacyData(context.applicationContext, instance.workoutDao())
                }

                instance
            }
        }

        private suspend fun migrateLegacyData(context: Context, dao: WorkoutDao) {
            val legacy = Storage.load(context)
            if (legacy.isNotEmpty() && dao.getWorkoutCount() == 0) {
                legacy.forEach { w ->
                    val workoutEntity = WorkoutEntity(id = w.id, date = w.date)
                    val exerciseEntities = w.exercises.map { e ->
                        ExerciseEntity(
                            workoutId = w.id,
                            name = e.name,
                            sets = e.sets,
                            reps = e.reps,
                            weightKg = e.weightKg
                        )
                    }
                    dao.insertFullWorkout(workoutEntity, exerciseEntities)
                }
            }
        }
    }
}