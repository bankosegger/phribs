package me.bankosegger.phribs

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    var weeklyGoal by remember { mutableStateOf(ThemePreferences.getWeeklyGoal(context)) }
    var weightUnit by remember { mutableStateOf(ThemePreferences.getWeightUnit(context)) }

    // Every database change flows through here, so this keeps the widget in sync.
    LaunchedEffect(workoutsWithExercises, themeMode, weeklyGoal) {
        PhribsWidget().updateAll(context)
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun export(uri: Uri?, format: BackupFormat) {
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                        it.write(Backup.encode(workouts, format))
                    }
                }.isSuccess
            }
            toast(if (ok) "Exported ${workouts.size} workouts" else "Export failed")
        }
    }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.JSON.mimeType)
    ) { export(it, BackupFormat.JSON) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.CSV.mimeType)
    ) { export(it, BackupFormat.CSV) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    Backup.decode(text).also { workoutDao.importWorkouts(it) }.size
                }.getOrNull()
            }
            toast(if (count != null) "Imported $count workouts" else "Import failed: unrecognized file")
        }
    }

    var logging by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            weeklyGoal = weeklyGoal,
            onWeeklyGoalChange = { goal ->
                weeklyGoal = goal
                ThemePreferences.setWeeklyGoal(context, goal)
            },
            weightUnit = weightUnit,
            onWeightUnitChange = { unit ->
                weightUnit = unit
                ThemePreferences.setWeightUnit(context, unit)
            },
            onExport = { format ->
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val name = "phribs-$stamp.${format.extension}"
                when (format) {
                    BackupFormat.JSON -> exportJson.launch(name)
                    BackupFormat.CSV -> exportCsv.launch(name)
                }
            },
            // Some file pickers label CSV as text/plain or octet-stream, so don't filter too strictly.
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            onSeedTestData = { scope.launch(Dispatchers.IO) { workoutDao.seedTestData() } },
            onClearAllData = { scope.launch(Dispatchers.IO) { workoutDao.deleteAllWorkouts() } },
            onBack = { showSettings = false }
        )
    } else if (logging) {
        BackHandler { logging = false }
        LogWorkoutScreen(
            knownExercises = workouts.flatMap { w -> w.exercises.map { it.name } }.distinct(),
            weightUnit = weightUnit,
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
            weeklyGoal = weeklyGoal,
            weightUnit = weightUnit,
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
