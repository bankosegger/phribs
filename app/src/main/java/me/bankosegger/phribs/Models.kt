package me.bankosegger.phribs

import me.bankosegger.phribs.ui.theme.WeightUnit
import me.bankosegger.phribs.ui.theme.toUnit
import kotlin.math.roundToInt

data class Exercise(val name: String, val sets: Int, val reps: Int, val weightKg: Double)
data class Workout(val id: Long, val date: Long, val exercises: List<Exercise>)

internal fun fmtWeight(d: Double): String {
    val rounded = (d * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

internal fun describe(e: Exercise, unit: WeightUnit = WeightUnit.KG): String {
    val valInUnit = e.weightKg.toUnit(unit)
    val weight = if (e.weightKg > 0) " @ ${fmtWeight(valInUnit)} ${unit.unitSymbol}" else ""
    return "${e.sets} × ${e.reps}$weight"
}
