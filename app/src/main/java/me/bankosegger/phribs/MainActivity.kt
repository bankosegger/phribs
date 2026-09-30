package me.bankosegger.phribs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bankosegger.phribs.data.AppDatabase
import me.bankosegger.phribs.data.ExerciseEntity
import me.bankosegger.phribs.data.WorkoutEntity
import me.bankosegger.phribs.data.seedTestData
import me.bankosegger.phribs.ui.HomeScreen
import me.bankosegger.phribs.ui.LogWorkoutScreen
import me.bankosegger.phribs.ui.SettingsScreen
import me.bankosegger.phribs.ui.theme.PhribsTheme
import me.bankosegger.phribs.ui.theme.ThemeMode
import me.bankosegger.phribs.ui.theme.ThemePreferences
import me.bankosegger.phribs.ui.theme.isDark
import me.bankosegger.phribs.widget.PhribsWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(this)) }
            val darkTheme = themeMode.isDark()
            // The default edge-to-edge style follows the system theme, not the one picked in-app.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PhribsTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GymApp(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemePreferences.setThemeMode(this, mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GymApp(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val workoutDao = remember { database.workoutDao() }
    val scope = rememberCoroutineScope()

    // Live Flow from Room Database
    val workoutsWithExercises by workoutDao.getAllWorkouts().collectAsState(initial = emptyList())
    val workouts = remember(workoutsWithExercises) {
        workoutsWithExercises.map { it.toDomainModel() }
    }

    // Every database change flows through here, so this keeps the widget in sync.
    LaunchedEffect(workoutsWithExercises, themeMode) {
        PhribsWidget().updateAll(context)
    }

    var logging by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onSeedTestData = { scope.launch(Dispatchers.IO) { workoutDao.seedTestData() } },
            onClearAllData = { scope.launch(Dispatchers.IO) { workoutDao.deleteAllWorkouts() } },
            onBack = { showSettings = false }
        )
    } else if (logging) {
        BackHandler { logging = false }
        LogWorkoutScreen(
            knownExercises = workouts.flatMap { w -> w.exercises.map { it.name } }.distinct(),
            onSave = { exercises ->
                val now = System.currentTimeMillis()
                scope.launch(Dispatchers.IO) {
                    val workoutEntity = WorkoutEntity(id = now, date = now)
                    val exerciseEntities = exercises.map { e ->
                        ExerciseEntity(
                            workoutId = now,
                            name = e.name,
                            sets = e.sets,
                            reps = e.reps,
                            weightKg = e.weightKg
                        )
                    }
                    workoutDao.insertFullWorkout(workoutEntity, exerciseEntities)
                }
                logging = false
            },
            onCancel = { logging = false }
        )
    } else {
        HomeScreen(
            workouts = workouts,
            onLog = { logging = true },
            onOpenSettings = { showSettings = true },
            onDelete = { w ->
                scope.launch(Dispatchers.IO) {
                    workoutDao.deleteWorkoutById(w.id)
                }
            },
            onUpdate = { w, newExercises ->
                scope.launch(Dispatchers.IO) {
                    val exerciseEntities = newExercises.map { e ->
                        ExerciseEntity(
                            workoutId = w.id,
                            name = e.name,
                            sets = e.sets,
                            reps = e.reps,
                            weightKg = e.weightKg
                        )
                    }
                    workoutDao.updateFullWorkout(w.id, exerciseEntities)
                }
            }
        )
    }
}
