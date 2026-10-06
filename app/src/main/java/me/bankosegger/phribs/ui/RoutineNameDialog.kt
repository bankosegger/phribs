package me.bankosegger.phribs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.bankosegger.phribs.Routine
import me.bankosegger.phribs.findRoutineByName

/**
 * Names a routine. Reusing an existing name is allowed and turns the dialog into a replace, which
 * is how a routine gets edited: start from it, change the exercises, save it under the same name.
 *
 * @param existingRoutines routines the name could collide with; pass the list minus the one being
 *   renamed, so keeping its own name isn't reported as a clash.
 * @param allowReplace whether reusing a name is allowed. False where the caller can only create or
 *   rename, so a clash blocks the dialog instead of silently making a second routine by that name.
 */
@Composable
fun RoutineNameDialog(
    title: String,
    initialName: String = "",
    existingRoutines: List<Routine> = emptyList(),
    allowReplace: Boolean = true,
    confirmLabel: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val clash = remember(name, existingRoutines) { findRoutineByName(existingRoutines, name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name (e.g. Push day)") },
                    singleLine = true,
                    isError = clash != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (clash != null) {
                    Text(
                        text = if (allowReplace) "\"${clash.name}\" already exists. Saving replaces its exercises."
                        else "\"${clash.name}\" already exists. Pick a different name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && (allowReplace || clash == null)
            ) {
                Text(if (clash != null && allowReplace) "Replace" else confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
