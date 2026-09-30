package me.bankosegger.phribs.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.Workout
import me.bankosegger.phribs.describe
import me.bankosegger.phribs.fmtWeight

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
