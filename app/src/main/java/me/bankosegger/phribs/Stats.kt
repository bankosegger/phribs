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
