package me.bankosegger.phribs.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Exercise
import me.bankosegger.phribs.ExerciseSession
import me.bankosegger.phribs.Workout
import me.bankosegger.phribs.describe
import me.bankosegger.phribs.exerciseSessions
import me.bankosegger.phribs.fmtWeight
import me.bankosegger.phribs.getDisplayFormat
import me.bankosegger.phribs.personalRecord
import me.bankosegger.phribs.ui.theme.WeightUnit
import me.bankosegger.phribs.ui.theme.toUnit
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProgressMetric(val label: String) {
    WEIGHT("Top weight"),
    VOLUME("Volume"),
    REPS("Total reps")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(
    name: String,
    workouts: List<Workout>,
    weightUnit: WeightUnit,
    onBack: () -> Unit
) {
    val sessions = remember(workouts, name) { exerciseSessions(workouts, name) }
    val record = remember(sessions) { personalRecord(sessions) }
    // Bodyweight exercises have nothing to chart by weight, so fall back to reps.
    val weighted = sessions.any { s -> s.sets.any { it.weightKg > 0 } }
    val metrics = if (weighted) listOf(ProgressMetric.WEIGHT, ProgressMetric.VOLUME) else listOf(ProgressMetric.REPS)
    var metric by remember(weighted) { mutableStateOf(metrics.first()) }
    var selected by remember(sessions) { mutableStateOf(sessions.lastIndex.takeIf { it >= 0 }) }

    val number = remember { NumberFormat.getIntegerInstance() }
    val shortDate = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val fullDate = remember { getDisplayFormat() }
    fun value(s: ExerciseSession): Double = when (metric) {
        ProgressMetric.WEIGHT -> s.topWeightKg.toUnit(weightUnit)
        ProgressMetric.VOLUME -> s.volumeKg.toUnit(weightUnit)
        ProgressMetric.REPS -> s.totalReps.toDouble()
    }
    fun format(v: Double): String = when (metric) {
        ProgressMetric.WEIGHT -> "${fmtWeight(v)} ${weightUnit.unitSymbol}"
        ProgressMetric.VOLUME -> "${number.format(v)} ${weightUnit.unitSymbol}"
        ProgressMetric.REPS -> number.format(v)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, fontWeight = FontWeight.SemiBold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "No sessions logged for this exercise yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@LazyColumn
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    record?.let { pr ->
                        val prText = if (pr.set.weightKg > 0)
                            "${fmtWeight(pr.set.weightKg.toUnit(weightUnit))} ${weightUnit.unitSymbol} × ${pr.set.reps}"
                        else "${pr.set.reps} reps"
                        RecordCard(
                            label = if (weighted) "Personal record" else "Most reps",
                            value = prText,
                            date = shortDate.format(Date(pr.date)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val best = if (weighted) sessions.maxBy { it.volumeKg } else sessions.maxBy { it.totalReps }
                    RecordCard(
                        label = if (weighted) "Best volume" else "Best session",
                        value = if (weighted) "${number.format(best.volumeKg.toUnit(weightUnit))} ${weightUnit.unitSymbol}"
                        else "${best.totalReps} reps",
                        date = shortDate.format(Date(best.date)),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (metrics.size > 1) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                metrics.forEachIndexed { i, m ->
                                    SegmentedButton(
                                        selected = m == metric,
                                        onClick = { metric = m },
                                        shape = SegmentedButtonDefaults.itemShape(i, metrics.size)
                                    ) { Text(m.label) }
                                }
                            }
                        } else {
                            Text(metric.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }

                        selected?.let { i ->
                            val s = sessions[i]
                            Column {
                                Text(
                                    format(value(s)),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    fullDate.format(Date(s.date)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        ProgressChart(
                            points = sessions.map { ChartPoint(it.date, value(it)) },
                            selected = selected,
                            onSelect = { selected = it },
                            formatY = { if (metric == ProgressMetric.WEIGHT) fmtWeight(it) else number.format(it) },
                            formatX = { shortDate.format(Date(it)) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(sessions.asReversed()) { s ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fullDate.format(Date(s.date)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            describe(Exercise(name, s.sets), weightUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (s.note.isNotBlank()) {
                        Text(
                            s.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RecordCard(label: String, value: String, date: String, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
