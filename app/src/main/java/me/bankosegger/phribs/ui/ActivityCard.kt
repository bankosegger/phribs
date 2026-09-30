package me.bankosegger.phribs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import me.bankosegger.phribs.Workout
import me.bankosegger.phribs.dayKeyFormat
import me.bankosegger.phribs.weekStart
import me.bankosegger.phribs.weeklyStreaks
import java.util.Calendar
import java.util.Date

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
