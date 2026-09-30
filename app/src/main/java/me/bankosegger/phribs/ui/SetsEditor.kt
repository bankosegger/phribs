package me.bankosegger.phribs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSet
import me.bankosegger.phribs.fmtWeight
import me.bankosegger.phribs.ui.theme.WeightUnit
import me.bankosegger.phribs.ui.theme.fromUnitToKg
import me.bankosegger.phribs.ui.theme.toUnit

data class SetInput(val reps: String = "10", val weight: String = "")

fun List<SetInput>.toSets(unit: WeightUnit): List<ExerciseSet> = map {
    ExerciseSet(
        reps = it.reps.toIntOrNull() ?: 0,
        weightKg = (it.weight.replace(',', '.').toDoubleOrNull() ?: 0.0).fromUnitToKg(unit)
    )
}

fun Exercise.toSetInputs(unit: WeightUnit): List<SetInput> = sets.map {
    SetInput(it.reps.toString(), if (it.weightKg > 0) fmtWeight(it.weightKg.toUnit(unit)) else "")
}

@Composable
fun SetsEditor(sets: SnapshotStateList<SetInput>, weightUnit: WeightUnit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sets.forEachIndexed { i, set ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${i + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp)
                )
                NumberField("Reps", set.reps, { sets[i] = set.copy(reps = it) }, Modifier.weight(1f))
                NumberField(
                    "Weight (${weightUnit.unitSymbol})", set.weight,
                    { sets[i] = set.copy(weight = it) }, Modifier.weight(1f)
                )
                IconButton(onClick = { sets.removeAt(i) }, enabled = sets.size > 1) {
                    Icon(Icons.Default.Close, contentDescription = "Remove set ${i + 1}")
                }
            }
        }
        // Copying the last set makes the common "same weight again" case a single tap.
        TextButton(onClick = { sets += sets.lastOrNull() ?: SetInput() }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }
    }
}
