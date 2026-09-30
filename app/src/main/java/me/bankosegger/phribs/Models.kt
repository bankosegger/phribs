package me.bankosegger.phribs

data class Exercise(val name: String, val sets: Int, val reps: Int, val weightKg: Double)
data class Workout(val id: Long, val date: Long, val exercises: List<Exercise>)

internal fun fmtWeight(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

internal fun describe(e: Exercise): String {
    val weight = if (e.weightKg > 0) " @ ${fmtWeight(e.weightKg)} kg" else ""
    return "${e.sets} × ${e.reps}$weight"
}
