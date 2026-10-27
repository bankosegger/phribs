package me.bankosegger.phribs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSet
import me.bankosegger.phribs.Routine
import me.bankosegger.phribs.beatsRecord
import me.bankosegger.phribs.exerciseOptions
import me.bankosegger.phribs.fmtWeight
import me.bankosegger.phribs.prefill
import me.bankosegger.phribs.routineExerciseNames
import me.bankosegger.phribs.ui.theme.WeightUnit
import me.bankosegger.phribs.ui.theme.toUnit

/** An exercise as it is being logged: its sets are edited in place on the workout screen. */
private class ExerciseDraft(val name: String, sets: List<SetInput>, note: String = "") {
    val sets = mutableStateListOf<SetInput>().apply { addAll(sets) }
    var note by mutableStateOf(note)
    var showNote by mutableStateOf(note.isNotBlank())

    fun toExercise(unit: WeightUnit) = Exercise(name, sets.toSets(unit), note.trim())
}

/**
 * @param knownExercises previously logged exercise names, most relevant first; offered before the catalog.
 * @param lastSessions the newest logged version of each exercise, keyed by lowercased name. Used to
 *   pre-fill newly added exercises and to show what was done last time next to each set.
 * @param restSeconds rest length to count down after each finished set, or `null` to not offer one.
 * @param initialExercises exercises to open with, as pre-filled by a routine.
 * @param routineName name of the routine this was started from, shown in place of the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    knownExercises: List<String>,
    catalogExerciseNames: List<String> = emptyList(),
    lastSessions: Map<String, Exercise> = emptyMap(),
    previousBests: Map<String, ExerciseSet> = emptyMap(),
    weightUnit: WeightUnit = WeightUnit.KG,
    restSeconds: Int? = null,
    initialExercises: List<Exercise> = emptyList(),
    routineName: String? = null,
    routines: List<Routine> = emptyList(),
    onSaveRoutine: (String, List<String>) -> Unit = { _, _ -> },
    onSave: (List<Exercise>) -> Unit,
    onCancel: () -> Unit
) {
    val startRest = rememberRestTimerStarter()
    val drafts = remember {
        mutableStateListOf<ExerciseDraft>().apply {
            addAll(initialExercises.map { ExerciseDraft(it.name, it.toSetInputs(weightUnit), it.note) })
        }
    }
    var picking by remember { mutableStateOf(false) }
    var savingRoutine by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun exercises() = drafts.map { it.toExercise(weightUnit) }

    // Leaving would throw away everything typed so far, so ask first once there is something to lose.
    fun leave() {
        if (drafts.isEmpty()) onCancel() else confirmDiscard = true
    }
    BackHandler(enabled = drafts.isNotEmpty()) { confirmDiscard = true }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(routineName ?: "Log Workout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (routineName != null) "Update routine" else "Save as routine") },
                                    leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        savingRoutine = true
                                    }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { onSave(exercises()) }, enabled = drafts.isNotEmpty()) {
                        Text("Finish", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { RestTimerBar(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (drafts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Add your first exercise to start",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            itemsIndexed(drafts) { index, draft ->
                ExerciseCard(
                    draft = draft,
                    weightUnit = weightUnit,
                    previous = lastSessions[draft.name.lowercase()]?.sets.orEmpty(),
                    previousBest = previousBests[draft.name.lowercase()],
                    onSetDone = { restSeconds?.let(startRest) },
                    onRemove = { drafts.removeAt(index) }
                )
            }

            item {
                Button(
                    onClick = { picking = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add exercise", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (picking) {
        ExercisePickerSheet(
            recent = knownExercises,
            catalog = catalogExerciseNames,
            onPick = { name ->
                picking = false
                val start = prefill(name, lastSessions)
                drafts += ExerciseDraft(start.name, start.toSetInputs(weightUnit))
            },
            onDismiss = { picking = false }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("The exercises you've logged here won't be saved.") },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onCancel()
                }) { Text("Discard") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDiscard = false }) { Text("Keep logging") }
            }
        )
    }

    if (savingRoutine) {
        RoutineNameDialog(
            title = if (routineName != null) "Update routine" else "Save as routine",
            initialName = routineName ?: "",
            // Reusing the routine's own name is the point of "Update Routine", not a clash.
            existingRoutines = routines.filterNot { it.name.equals(routineName, ignoreCase = true) },
            onConfirm = { name ->
                savingRoutine = false
                onSaveRoutine(name, routineExerciseNames(exercises()))
            },
            onDismiss = { savingRoutine = false }
        )
    }
}

/**
 * @param previous the sets logged for this exercise last time, shown next to the matching set number.
 * @param onSetDone called when a set is ticked off, to start the rest countdown.
 */
@Composable
private fun ExerciseCard(
    draft: ExerciseDraft,
    weightUnit: WeightUnit,
    previous: List<ExerciseSet>,
    previousBest: ExerciseSet?,
    onSetDone: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = draft.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (beatsRecord(draft.sets.toSets(weightUnit), previousBest)) {
                    Spacer(Modifier.width(8.dp))
                    PrBadge("New PR")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options for ${draft.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!draft.showNote) {
                            DropdownMenuItem(
                                text = { Text("Add note") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    draft.showNote = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove exercise") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            }
                        )
                    }
                }
            }

            SetRow(
                number = { HeaderText("SET") },
                previous = { HeaderText("PREVIOUS") },
                weight = { HeaderText(weightUnit.unitSymbol.uppercase()) },
                reps = { HeaderText("REPS") },
                done = { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { HeaderText("✓") } }
            )

            draft.sets.forEachIndexed { i, set ->
                val rowColor = if (set.done) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
                SetRow(
                    modifier = Modifier.background(rowColor, RoundedCornerShape(8.dp)),
                    number = {
                        Text(
                            text = "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    },
                    previous = {
                        Text(
                            text = previous.getOrNull(i)?.let { fmtPrevious(it, weightUnit) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    },
                    weight = {
                        SetField(set.weight, { draft.sets[i] = set.copy(weight = it) }, "Set ${i + 1} weight")
                    },
                    reps = {
                        SetField(set.reps, { draft.sets[i] = set.copy(reps = it) }, "Set ${i + 1} reps")
                    },
                    done = {
                        FilledIconToggleButton(
                            checked = set.done,
                            onCheckedChange = { checked ->
                                draft.sets[i] = set.copy(done = checked)
                                if (checked) onSetDone()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Set ${i + 1} done",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Copying the last set makes the common "same weight again" case a single tap.
                TextButton(onClick = { draft.sets += draft.sets.last().copy(done = false) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add set")
                }
                if (draft.sets.size > 1) {
                    TextButton(onClick = { draft.sets.removeAt(draft.sets.lastIndex) }) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove set")
                    }
                }
            }

            if (draft.showNote) {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft.note = it },
                    label = { Text("Note") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp)
                )
            }
        }
    }
}

/** Lays out one line of the set table, so the header and every set share the same columns. */
@Composable
private fun SetRow(
    number: @Composable () -> Unit,
    previous: @Composable () -> Unit,
    weight: @Composable () -> Unit,
    reps: @Composable () -> Unit,
    done: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { number() }
        Box(Modifier.weight(1.1f), contentAlignment = Alignment.Center) { previous() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { weight() }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { reps() }
        done()
    }
}

@Composable
private fun HeaderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A label-less number field; the column header names it, which keeps each set to one short row. */
@Composable
private fun SetField(value: String, onChange: (String) -> Unit, description: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("–", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
    )
}

private fun fmtPrevious(set: ExerciseSet, unit: WeightUnit): String =
    if (set.weightKg > 0) "${fmtWeight(set.weightKg.toUnit(unit))} × ${set.reps}" else "${set.reps} reps"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    recent: List<String>,
    catalog: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val recentNames = remember(recent) { recent.map { it.trim().lowercase() }.toSet() }
    val options = remember(query, recent, catalog) { exerciseOptions(query, recent, catalog) }
    // Anything typed that isn't already an option can still be logged as a new exercise.
    val custom = query.trim().takeIf { q -> q.isNotEmpty() && options.none { it.trim().equals(q, ignoreCase = true) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (custom != null) {
                    item {
                        ListItem(
                            headlineContent = { Text("Add \"$custom\"", fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.clickable { onPick(custom) }
                        )
                    }
                }
                items(options) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = {
                            Icon(
                                imageVector = if (name.trim().lowercase() in recentNames) Icons.Default.History
                                else Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { onPick(name) }
                    )
                }
            }
        }
    }
}
