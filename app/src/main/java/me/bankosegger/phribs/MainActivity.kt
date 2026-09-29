package me.bankosegger.phribs

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bankosegger.phribs.data.AppDatabase
import me.bankosegger.phribs.data.ExerciseEntity
import me.bankosegger.phribs.data.WorkoutEntity
import me.bankosegger.phribs.ui.theme.PhribsTheme
import me.bankosegger.phribs.ui.theme.ThemeMode
import me.bankosegger.phribs.ui.theme.ThemePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

// ---------- Data ----------

data class Exercise(val name: String, val sets: Int, val reps: Int, val weightKg: Double)
data class Workout(val id: Long, val date: Long, val exercises: List<Exercise>)

// Legacy Storage helper for fallback and auto-migration to Room
object Storage {
    private const val PREFS = "gym_tracker"
    private const val KEY = "workouts"

    fun load(context: Context): List<Workout> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val w = arr.getJSONObject(i)
                val ex = w.getJSONArray("exercises")
                Workout(
                    id = w.getLong("id"),
                    date = w.getLong("date"),
                    exercises = List(ex.length()) { j ->
                        val e = ex.getJSONObject(j)
                        Exercise(e.getString("name"), e.getInt("sets"), e.getInt("reps"), e.getDouble("weight"))
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, workouts: List<Workout>) {
        val arr = JSONArray()
        workouts.forEach { w ->
            val ex = JSONArray()
            w.exercises.forEach { e ->
                ex.put(
                    JSONObject().put("name", e.name).put("sets", e.sets)
                        .put("reps", e.reps).put("weight", e.weightKg)
                )
            }
            arr.put(JSONObject().put("id", w.id).put("date", w.date).put("exercises", ex))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }
}

// ---------- Helpers ----------

private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun getDisplayFormat() = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

private fun visitDays(workouts: List<Workout>, since: Long = 0L): Int =
    workouts.filter { it.date >= since }
        .map { dayKeyFormat.format(Date(it.date)) }
        .distinct().size

private fun startOfWeek(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun weekStart(time: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun weekKey(cal: Calendar): String = dayKeyFormat.format(cal.time)

private data class Streaks(val current: Int, val best: Int)

// Weekly rather than daily: rest days are part of training and shouldn't break a streak.
private fun weeklyStreaks(workouts: List<Workout>): Streaks {
    val weeks = workouts.map { weekKey(weekStart(it.date)) }.toSet()
    if (weeks.isEmpty()) return Streaks(0, 0)

    val cursor = weekStart(System.currentTimeMillis())
    // An empty current week doesn't break the streak until it's over.
    if (weekKey(cursor) !in weeks) cursor.add(Calendar.WEEK_OF_YEAR, -1)
    var current = 0
    while (weekKey(cursor) in weeks) {
        current++
        cursor.add(Calendar.WEEK_OF_YEAR, -1)
    }

    var best = 0
    var run = 0
    val walk = weekStart(workouts.minOf { it.date })
    val end = weekStart(System.currentTimeMillis())
    while (!walk.after(end)) {
        run = if (weekKey(walk) in weeks) run + 1 else 0
        best = maxOf(best, run)
        walk.add(Calendar.WEEK_OF_YEAR, 1)
    }
    return Streaks(current, best)
}

private fun fmtWeight(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

private fun describe(e: Exercise): String {
    val weight = if (e.weightKg > 0) " @ ${fmtWeight(e.weightKg)} kg" else ""
    return "${e.sets} × ${e.reps}$weight"
}

// ---------- App ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(this)) }
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

    var logging by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
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

// ---------- Settings screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == themeMode,
                                    onClick = { onThemeModeChange(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == themeMode, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Home screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workouts: List<Workout>,
    onLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: (Workout) -> Unit,
    onUpdate: (Workout, List<Exercise>) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "GYM TRACKER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLog,
                icon = { Icon(Icons.Default.Add, contentDescription = "Log workout") },
                text = { Text("Log Workout", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard("This Week", visitDays(workouts, startOfWeek()), Icons.Default.DateRange, Modifier.weight(1f))
                    StatCard("This Month", visitDays(workouts, startOfMonth()), Icons.Default.CalendarMonth, Modifier.weight(1f))
                    StatCard("All Time", visitDays(workouts), Icons.Default.EmojiEvents, Modifier.weight(1f))
                }
            }

            item { ActivityCard(workouts) }

            // Top Exercises Card
            item { TopExercisesCard(workouts) }

            // History Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Workout History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${workouts.size} logged",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty state or workouts list
            if (workouts.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No workouts recorded yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tap 'Log Workout' below to start tracking your progress.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            } else {
                items(workouts, key = { it.id }) { w ->
                    WorkoutCard(w = w, onDelete = onDelete, onUpdate = onUpdate)
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Phribs Gym Tracker v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatCard(label: String, value: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "$value",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val HEATMAP_WEEKS = 18

@Composable
fun ActivityCard(workouts: List<Workout>) {
    val streaks = remember(workouts) { weeklyStreaks(workouts) }
    val setsPerDay = remember(workouts) {
        workouts.groupBy { dayKeyFormat.format(Date(it.date)) }
            .mapValues { (_, ws) -> ws.sumOf { w -> w.exercises.sumOf { it.sets } } }
    }
    // Columns are weeks, rows are days; null marks days in the future.
    val grid = remember(workouts) {
        val today = System.currentTimeMillis()
        val cal = weekStart(today).apply { add(Calendar.WEEK_OF_YEAR, -(HEATMAP_WEEKS - 1)) }
        List(HEATMAP_WEEKS) {
            List(7) {
                val key = if (cal.timeInMillis > today) null else dayKeyFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                key
            }
        }
    }
    val maxSets = setsPerDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    val empty = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val levels = listOf(empty, primary.copy(alpha = 0.3f), primary.copy(alpha = 0.5f), primary.copy(alpha = 0.75f), primary)
    fun levelFor(sets: Int): Int = if (sets <= 0) 0 else ((sets * 4 + maxSets - 1) / maxSets).coerceIn(1, 4)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Best: ${streaks.best} wk",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${streaks.current}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "week streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (day in 0 until 7) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        grid.forEach { week ->
                            val key = week[day]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        color = if (key == null) Color.Transparent
                                        else levels[levelFor(setsPerDay[key] ?: 0)],
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Last $HEATMAP_WEEKS weeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                levels.forEach { c ->
                    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                }
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TopExercisesCard(workouts: List<Workout>) {
    val top = remember(workouts) {
        workouts.flatMap { it.exercises }
            .groupingBy { it.name }.eachCount()
            .entries.sortedByDescending { it.value }.take(4)
    }
    if (top.isEmpty()) return

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Most Frequently Trained",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            top.forEach { (name, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "${count}×",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutCard(
    w: Workout,
    onDelete: (Workout) -> Unit,
    onUpdate: (Workout, List<Exercise>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = getDisplayFormat().format(Date(w.date)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${w.exercises.size} exercise${if (w.exercises.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    w.exercises.forEach { e ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = e.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = describe(e),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Log")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = { editingWorkout = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Exercises")
                        }
                    }
                }
            }
        }
    }

    if (editingWorkout) {
        EditWorkoutDialog(
            workout = w,
            onSave = { updatedExercises ->
                editingWorkout = false
                onUpdate(w, updatedExercises)
            },
            onDismiss = { editingWorkout = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete this workout?") },
            text = { Text("This entry will be permanently removed from your workout history.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete(w) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- Edit Workout Dialog ----------

@Composable
fun EditWorkoutDialog(
    workout: Workout,
    onSave: (List<Exercise>) -> Unit,
    onDismiss: () -> Unit
) {
    val editedExercises = remember { mutableStateListOf<Exercise>().apply { addAll(workout.exercises) } }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }

    fun clearInput() {
        editingIndex = null
        name = ""
        sets = "3"
        reps = "10"
        weight = ""
    }

    fun startEditing(index: Int, e: Exercise) {
        editingIndex = index
        name = e.name
        sets = e.sets.toString()
        reps = e.reps.toString()
        weight = if (e.weightKg > 0) fmtWeight(e.weightKg) else ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Edit Workout Exercises", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Exercise List
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(editedExercises) { index, e ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (editingIndex == index) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(e.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(describe(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                IconButton(onClick = { startEditing(index, e) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    editedExercises.removeAt(index)
                                    if (editingIndex == index) clearInput()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = if (editingIndex != null) "Editing Selected Exercise" else "Add New Exercise",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                    NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
                    NumberField("Weight", weight, { weight = it }, Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingIndex != null) {
                        OutlinedButton(
                            onClick = { clearInput() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel Edit")
                        }
                    }
                    Button(
                        onClick = {
                            val newExercise = Exercise(
                                name = name.trim(),
                                sets = sets.toIntOrNull() ?: 0,
                                reps = reps.toIntOrNull() ?: 0,
                                weightKg = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                            )
                            if (editingIndex != null) {
                                editedExercises[editingIndex!!] = newExercise
                            } else {
                                editedExercises.add(newExercise)
                            }
                            clearInput()
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (editingIndex != null) "Update" else "Add Exercise")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedExercises.toList()) },
                enabled = editedExercises.isNotEmpty()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------- Log workout screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    knownExercises: List<String>,
    onSave: (List<Exercise>) -> Unit,
    onCancel: () -> Unit
) {
    val added = remember { mutableStateListOf<Exercise>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("") }

    fun clearInput() {
        editingIndex = null
        name = ""
        sets = "3"
        reps = "10"
        weight = ""
    }

    fun startEditing(index: Int, e: Exercise) {
        editingIndex = index
        name = e.name
        sets = e.sets.toString()
        reps = e.reps.toString()
        weight = if (e.weightKg > 0) fmtWeight(e.weightKg) else ""
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Log Workout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Exercise Input Card
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (editingIndex != null) "Edit Selected Exercise" else "Add Exercise",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Exercise name (e.g. Bench Press)") },
                            leadingIcon = {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (knownExercises.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Quick select recent:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(knownExercises) { ex ->
                                        AssistChip(
                                            onClick = { name = ex },
                                            label = { Text(ex) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                            NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
                            NumberField("Weight (kg)", weight, { weight = it }, Modifier.weight(1f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editingIndex != null) {
                                OutlinedButton(
                                    onClick = { clearInput() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel Edit")
                                }
                            }
                            Button(
                                onClick = {
                                    val newExercise = Exercise(
                                        name = name.trim(),
                                        sets = sets.toIntOrNull() ?: 0,
                                        reps = reps.toIntOrNull() ?: 0,
                                        weightKg = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    )
                                    if (editingIndex != null) {
                                        added[editingIndex!!] = newExercise
                                    } else {
                                        added += newExercise
                                    }
                                    clearInput()
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (editingIndex != null) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (editingIndex != null) "Update Exercise" else "Add to Workout",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Added exercises list section
            if (added.isNotEmpty()) {
                item {
                    Text(
                        text = "Current Workout Exercises (${added.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                itemsIndexed(added) { index, e ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { startEditing(index, e) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (editingIndex == index) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = e.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = describe(e),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { startEditing(index, e) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit exercise",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                added.removeAt(index)
                                if (editingIndex == index) clearInput()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove exercise",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Save / Cancel actions
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(added.toList()) },
                        enabled = added.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save Workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}