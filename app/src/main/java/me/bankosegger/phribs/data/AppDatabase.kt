package me.bankosegger.phribs.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bankosegger.phribs.Storage

@Database(entities = [WorkoutEntity::class, ExerciseEntity::class, ExerciseSetEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        // Splits each exercise's sets × reps @ weight into one row per set. The old exercises table is
        // dropped before exercise_sets exists, so no cascade can hit the copied sets.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `exercises_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("INSERT INTO `exercises_new` (`id`, `workoutId`, `name`) SELECT `id`, `workoutId`, `name` FROM `exercises`")
                db.execSQL(
                    "CREATE TEMP TABLE `sets_tmp` AS " +
                        "WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 99) " +
                        "SELECT e.`id` AS exerciseId, n.i AS position, e.`reps` AS reps, e.`weightKg` AS weightKg " +
                        "FROM `exercises` e JOIN n ON n.i < max(e.`sets`, 1)"
                )
                db.execSQL("DROP TABLE `exercises`")
                db.execSQL("ALTER TABLE `exercises_new` RENAME TO `exercises`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_workoutId` ON `exercises` (`workoutId`)")
                db.execSQL(
                    "CREATE TABLE `exercise_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `reps` INTEGER NOT NULL, " +
                        "`weightKg` REAL NOT NULL, " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_exerciseId` ON `exercise_sets` (`exerciseId`)")
                db.execSQL(
                    "INSERT INTO `exercise_sets` (`exerciseId`, `position`, `reps`, `weightKg`) " +
                        "SELECT exerciseId, position, reps, weightKg FROM `sets_tmp` ORDER BY exerciseId, position"
                )
                db.execSQL("DROP TABLE `sets_tmp`")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
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
                dao.importWorkouts(legacy)
            }
        }
    }
}