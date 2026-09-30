package me.bankosegger.phribs

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
internal fun getDisplayFormat() = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

internal fun visitDays(workouts: List<Workout>, since: Long = 0L): Int =
    workouts.filter { it.date >= since }
        .map { dayKeyFormat.format(Date(it.date)) }
        .distinct().size

internal fun startOfWeek(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun startOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun weekStart(time: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun weekKey(cal: Calendar): String = dayKeyFormat.format(cal.time)

internal data class Streaks(val current: Int, val best: Int)

// Weekly rather than daily: rest days are part of training and shouldn't break a streak.
internal fun weeklyStreaks(workouts: List<Workout>, now: Long = System.currentTimeMillis()): Streaks {
    val weeks = workouts.map { weekKey(weekStart(it.date)) }.toSet()
    if (weeks.isEmpty()) return Streaks(0, 0)

    val cursor = weekStart(now)
    // An empty current week doesn't break the streak until it's over.
    if (weekKey(cursor) !in weeks) cursor.add(Calendar.WEEK_OF_YEAR, -1)
    var current = 0
    while (weekKey(cursor) in weeks) {
        current++
        cursor.add(Calendar.WEEK_OF_YEAR, -1)
    }

    var best = 0
    var run = 0
    val walk = weekStart(workouts.minOf { it.date })
    val end = weekStart(now)
    while (!walk.after(end)) {
        run = if (weekKey(walk) in weeks) run + 1 else 0
        best = maxOf(best, run)
        walk.add(Calendar.WEEK_OF_YEAR, 1)
    }
    return Streaks(current, best)
}

internal data class ExerciseSession(val date: Long, val sets: List<ExerciseSet>) {
    val topWeightKg: Double get() = sets.maxOfOrNull { it.weightKg } ?: 0.0
    val volumeKg: Double get() = sets.sumOf { it.reps * it.weightKg }
    val totalReps: Int get() = sets.sumOf { it.reps }
}

// Oldest first. An exercise logged twice in one workout counts as one session.
internal fun exerciseSessions(workouts: List<Workout>, name: String): List<ExerciseSession> =
    workouts.mapNotNull { w ->
        val sets = w.exercises.filter { it.name.equals(name, ignoreCase = true) }.flatMap { it.sets }
        if (sets.isEmpty()) null else ExerciseSession(w.date, sets)
    }.sortedBy { it.date }

internal data class PersonalRecord(val set: ExerciseSet, val date: Long)

// Heaviest set, with reps breaking ties; the earliest date wins so the record shows when it was first hit.
internal fun personalRecord(sessions: List<ExerciseSession>): PersonalRecord? =
    sessions.flatMap { s -> s.sets.map { PersonalRecord(it, s.date) } }
        .maxWithOrNull(compareBy<PersonalRecord> { it.set.weightKg }.thenBy { it.set.reps }.thenByDescending { it.date })
