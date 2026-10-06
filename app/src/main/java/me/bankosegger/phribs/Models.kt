package me.bankosegger.phribs

import me.bankosegger.phribs.ui.theme.WeightUnit
import me.bankosegger.phribs.ui.theme.toUnit
import kotlin.math.roundToInt

data class ExerciseSet(val reps: Int, val weightKg: Double)
data class Exercise(val name: String, val sets: List<ExerciseSet>, val note: String = "")
data class Workout(val id: Long, val date: Long, val exercises: List<Exercise>)

/**
 * A reusable workout template, e.g. "Push day". Only the exercise names are stored — the sets to
 * start from are read back out of the workout history, so a routine never holds stale weights.
 */
data class Routine(val id: Long, val name: String, val exerciseNames: List<String>)

internal fun fmtWeight(d: Double): String {
    val rounded = (d * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

internal fun describe(e: Exercise, unit: WeightUnit = WeightUnit.KG): String {
    val sets = e.sets
    if (sets.isEmpty()) return ""
    fun w(kg: Double) = fmtWeight(kg.toUnit(unit))
    val suffix = if (sets.any { it.weightKg > 0 }) " ${unit.unitSymbol}" else ""
    val weights = sets.map { it.weightKg }.distinct()
    return when {
        sets.distinct().size == 1 -> {
            val s = sets.first()
            "${sets.size} × ${s.reps}" + if (s.weightKg > 0) " @ ${w(s.weightKg)}$suffix" else ""
        }
        weights.size == 1 -> sets.joinToString(", ") { "${it.reps}" } +
            if (weights[0] > 0) " @ ${w(weights[0])}$suffix" else ""
        else -> sets.joinToString(", ") { if (it.weightKg > 0) "${it.reps} @ ${w(it.weightKg)}" else "${it.reps}" } + suffix
    }
}
