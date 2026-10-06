package me.bankosegger.phribs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Routine

/**
 * Saved routines, each startable in one tap. Shown even when empty, because a routine can only be
 * created from a workout and that isn't discoverable otherwise.
 */
@Composable
fun RoutinesCard(
    routines: List<Routine>,
    onStart: (Routine) -> Unit,
    onRename: (Routine, String) -> Unit,
    onDelete: (Routine) -> Unit
) {
    var renaming by remember { mutableStateOf<Routine?>(null) }
    var deleting by remember { mutableStateOf<Routine?>(null) }

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
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Routines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (routines.isEmpty()) {
                Text(
                    text = "Expand a workout and tap \"Save as Routine\" to reuse it later, " +
                        "pre-filled with the sets you last logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                routines.forEach { routine ->
                    RoutineRow(
                        routine = routine,
                        onStart = { onStart(routine) },
                        onRename = { renaming = routine },
                        onDelete = { deleting = routine }
                    )
                }
            }
        }
    }

    renaming?.let { routine ->
        RoutineNameDialog(
            title = "Rename routine",
            initialName = routine.name,
            existingRoutines = routines.filterNot { it.id == routine.id },
            // Renaming only moves the label; it can't absorb another routine's exercises.
            allowReplace = false,
            confirmLabel = "Rename",
            onConfirm = { name ->
                renaming = null
                onRename(routine, name)
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete \"${routine.name}\"?") },
            text = { Text("The routine is removed. Workouts you logged from it are kept.") },
            confirmButton = {
                Button(
                    onClick = { deleting = null; onDelete(routine) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    onStart: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = routine.exerciseNames.joinToString(" · ").ifBlank { "No exercises" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(
            onClick = onStart,
            enabled = routine.exerciseNames.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start", fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options for ${routine.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}
