package me.bankosegger.phribs

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bankosegger.phribs.data.AppDatabase
import me.bankosegger.phribs.data.WorkoutEntity
import me.bankosegger.phribs.data.loadExerciseNames
import me.bankosegger.phribs.data.seedTestData
import me.bankosegger.phribs.ui.ExerciseProgressScreen
import me.bankosegger.phribs.ui.HomeScreen
import me.bankosegger.phribs.ui.LogWorkoutScreen
import me.bankosegger.phribs.ui.SettingsScreen
import me.bankosegger.phribs.ui.theme.PhribsTheme
import me.bankosegger.phribs.ui.theme.ThemeMode
import me.bankosegger.phribs.ui.theme.ThemePreferences
import me.bankosegger.phribs.ui.theme.isDark
import me.bankosegger.phribs.widget.PhribsWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            var themeMode by remember { mutableStateOf(ThemePreferences
                .getThemeMode(this)) }

            var dynamicColor by remember { mutableStateOf(ThemePreferences
                .getDynamicColor(this)) }
            val darkTheme = themeMode.isDark()
            // The default edge-to-edge style follows the system theme, not the one picked in-app.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) SystemBarStyle
                    .dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            PhribsTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GymApp(
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemePreferences.setThemeMode(this, mode)
                        },
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = { enabled ->
                            dynamicColor = enabled
                            ThemePreferences.setDynamicColor(this, enabled)
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Test", showSystemUi = true)
@Composable
fun GymAppPreview() {
    GymApp(
        themeMode = ThemeMode.DARK,
        onThemeModeChange = {},
        dynamicColor = true,
        onDynamicColorChange = {}
    )
}

@Composable
fun GymApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val database = remember { AppDatabase.getDatabase(context) }
    val workoutDao = remember { database.workoutDao() }
    val routineDao = remember { database.routineDao() }
    val scope = rememberCoroutineScope()

    // Live Flows from the Room database. Null until the first emission, which lets destinations
    // that need real data tell "still loading" apart from "genuinely empty".
    val loadedWorkouts by workoutDao.getAllWorkouts().collectAsState(initial = null)
    val loadedRoutines by routineDao.getAllRoutines().collectAsState(initial = null)
    val workouts =
        remember(loadedWorkouts) { loadedWorkouts.orEmpty().map { it.toDomainModel() } }
    val routines =
        remember(loadedRoutines) { loadedRoutines.orEmpty().map { it.toDomainModel() } }
    val databaseLoaded = loadedWorkouts != null && loadedRoutines != null
    val catalogExerciseNames = remember { loadExerciseNames(context) }

    var weeklyGoal by remember { mutableStateOf(ThemePreferences.getWeeklyGoal(context)) }
    var weightUnit by remember { mutableStateOf(ThemePreferences.getWeightUnit(context)) }
    var restTimerEnabled by remember { mutableStateOf(ThemePreferences
        .getRestTimerEnabled(context)) }
    var restSeconds by remember { mutableStateOf(ThemePreferences.getRestSeconds(context)) }
    var restVibrate by remember { mutableStateOf(ThemePreferences.getRestVibrate(context)) }

    // Every database change flows through here, so this keeps the widget in sync.
    LaunchedEffect(loadedWorkouts, themeMode, dynamicColor, weeklyGoal) {
        PhribsWidget().updateAll(context)
    }

    // A second tap can arrive before the first navigation commits, which would push a duplicate
    // destination or pop past the start destination. An entry leaves RESUMED as soon as it begins
    // navigating away, so that is the signal to ignore the repeat.
    fun settled() =
        navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

    fun goTo(route: Any) {
        if (settled()) navController.navigate(route)
    }

    fun goBack() {
        if (settled()) navController.popBackStack()
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // Saving over an existing name replaces that routine, which is how routines get edited.
    fun saveRoutine(name: String, exerciseNames: List<String>) {
        val existing = findRoutineByName(routines, name)
        scope.launch(Dispatchers.IO) {
            if (existing != null) routineDao
                .replaceRoutineExercises(existing.id, exerciseNames)
            else routineDao.createRoutine(name, exerciseNames)
        }
        toast(if (existing != null)
            "Updated routine \"$name\"" else "Saved routine \"$name\""
        )
    }

    fun export(uri: Uri?, format: BackupFormat) {
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!
                        .bufferedWriter().use {
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
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!
                        .bufferedReader().use { it.readText() }
                    Backup.decode(text).also { workoutDao.importWorkouts(it) }.size
                }.getOrNull()
            }
            toast(if (count != null)
                "Imported $count workouts" else "Import failed: unrecognized file"
            )
        }
    }

    // A blank workout and one started from a routine are the same screen; only the pre-fill differs.
    // Kept as a composable lambda so both destinations share it without replumbing every argument.
    val logWorkout: @Composable (Routine?) -> Unit = { routine ->
        LogWorkoutScreen(
            knownExercises = workouts.flatMap { w -> w.exercises.map { it.name } }.distinct(),
            catalogExerciseNames = catalogExerciseNames,
            previousBests = remember(workouts) { bestSets(workouts) },
            weightUnit = weightUnit,
            restSeconds = restSeconds.takeIf { restTimerEnabled },
            initialExercises = remember(routine, workouts) {
                routine?.let { prefill(it, lastSessions(workouts)) }.orEmpty()
            },
            routineName = routine?.name,
            routines = routines,
            onSaveRoutine = ::saveRoutine,
            onSave = { exercises ->
                val now = System.currentTimeMillis()
                scope.launch(Dispatchers.IO) {
                    workoutDao.insertFullWorkout(WorkoutEntity(id = now, date = now), exercises)
                }
                goBack()
            },
            onCancel = ::goBack
        )
    }

    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.fillMaxSize()
    ) {
        composable<Home> {
            HomeScreen(
                workouts = workouts,
                weeklyGoal = weeklyGoal,
                weightUnit = weightUnit,
                catalogExerciseNames = catalogExerciseNames,
                routines = routines,
                onLog = { goTo(LogWorkout) },
                onStartRoutine = { routine -> goTo(LogRoutine(routine.id)) },
                onSaveRoutine = ::saveRoutine,
                onRenameRoutine = { routine, name ->
                    scope.launch(Dispatchers.IO) { routineDao
                        .renameRoutine(routine.id, name) }
                },
                onDeleteRoutine = { routine ->
                    scope.launch(Dispatchers.IO) { routineDao
                        .deleteRoutineById(routine.id) }
                },
                onOpenSettings = { goTo(Settings) },
                onOpenExercise = { name -> goTo(ExerciseProgress(name)) },
                onDelete = { w ->
                    scope.launch(Dispatchers.IO) {
                        workoutDao.deleteWorkoutById(w.id)
                    }
                },
                onUpdate = { w, newExercises ->
                    scope.launch(Dispatchers.IO) {
                        workoutDao.updateFullWorkout(w.id, newExercises)
                    }
                }
            )
        }

        composable<LogWorkout> { logWorkout(null) }

        composable<LogRoutine> { entry ->
            val routineId = entry.toRoute<LogRoutine>().routineId
            // The screen snapshots its pre-fill on first composition, so opening it before the
            // database has emitted would strand it on an empty workout. A routine deleted in the
            // meantime falls through to a blank one.
            if (databaseLoaded) logWorkout(routines.firstOrNull { it.id == routineId })
        }

        composable<ExerciseProgress> { entry ->
            ExerciseProgressScreen(
                name = entry.toRoute<ExerciseProgress>().exerciseName,
                workouts = workouts,
                weightUnit = weightUnit,
                onBack = ::goBack
            )
        }

        composable<Settings> {
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                dynamicColor = dynamicColor,
                onDynamicColorChange = onDynamicColorChange,
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
                restTimerEnabled = restTimerEnabled,
                onRestTimerEnabledChange = { enabled ->
                    restTimerEnabled = enabled
                    ThemePreferences.setRestTimerEnabled(context, enabled)
                    // A countdown left running would have no way to be
                    // stopped once the UI for it is gone.
                    if (!enabled) RestTimer.skip(context)
                },
                restSeconds = restSeconds,
                onRestSecondsChange = { seconds ->
                    restSeconds = seconds
                    ThemePreferences.setRestSeconds(context, seconds)
                },
                restVibrate = restVibrate,
                onRestVibrateChange = { enabled ->
                    restVibrate = enabled
                    ThemePreferences.setRestVibrate(context, enabled)
                },
                onExport = { format ->
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val name = "phribs-$stamp.${format.extension}"
                    when (format) {
                        BackupFormat.JSON -> exportJson.launch(name)
                        BackupFormat.CSV -> exportCsv.launch(name)
                    }
                },
                // Some file pickers label CSV as text/plain or octet-stream, so don't
                // filter too strictly.
                onImport = { importLauncher
                    .launch(arrayOf("application/json", "text/*",
                        "application/octet-stream")) },
                onSeedTestData = { scope
                    .launch(Dispatchers.IO) { workoutDao.seedTestData() } },
                onClearAllData = {
                    scope.launch(Dispatchers.IO) {
                        workoutDao.deleteAllWorkouts()
                        routineDao.deleteAllRoutines()
                    }
                },
                onBack = ::goBack
            )
        }
    }
}
