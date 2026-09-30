package me.bankosegger.phribs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSet
import me.bankosegger.phribs.beatsRecord
import me.bankosegger.phribs.describe
import me.bankosegger.phribs.ui.theme.WeightUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    knownExercises: List<String>,
    previousBests: Map<String, ExerciseSet> = emptyMap(),
    weightUnit: WeightUnit = WeightUnit.KG,
    onSave: (List<Exercise>) -> Unit,
    onCancel: () -> Unit
) {
    val added = remember { mutableStateListOf<Exercise>() }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf("") }
    val sets = remember { mutableStateListOf(SetInput()) }

    fun clearInput() {
        editingIndex = null
        name = ""
        sets.clear()
        sets += SetInput()
    }

    fun startEditing(index: Int, e: Exercise) {
        editingIndex = index
        name = e.name
        sets.clear()
        sets += e.toSetInputs(weightUnit).ifEmpty { listOf(SetInput()) }
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

                        SetsEditor(sets, weightUnit)

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
                                    val newExercise = Exercise(name = name.trim(), sets = sets.toSets(weightUnit))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = e.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (beatsRecord(e.sets, previousBests[e.name.lowercase()])) PrBadge("New PR")
                                }
                                Text(
                                    text = describe(e, weightUnit),
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
