package me.bankosegger.phribs.data

import java.util.Calendar
import kotlin.random.Random

private data class Template(val name: String, val sets: Int, val reps: Int, val startKg: Double, val gainPerWeekKg: Double)

private val routines = listOf(
    listOf(
        Template("Bench Press", 4, 8, 60.0, 1.25),
        Template("Overhead Press", 3, 8, 35.0, 0.5),
        Template("Incline Dumbbell Press", 3, 10, 22.0, 0.5),
        Template("Triceps Pushdown", 3, 12, 25.0, 0.5)
    ),
    listOf(
        Template("Deadlift", 3, 5, 100.0, 2.5),
        Template("Pull-ups", 3, 8, 0.0, 0.0),
        Template("Barbell Row", 4, 8, 55.0, 1.0),
        Template("Biceps Curl", 3, 12, 12.0, 0.25)
    ),
    listOf(
        Template("Squat", 4, 6, 80.0, 2.0),
        Template("Leg Press", 3, 10, 140.0, 2.5),
        Template("Romanian Deadlift", 3, 10, 60.0, 1.0),
        Template("Calf Raise", 4, 15, 40.0, 0.5)
    )
)

// Spans several months with skipped weeks so streaks, the heatmap and progress all have something to show.
suspend fun WorkoutDao.seedTestData(weeks: Int = 20, random: Random = Random.Default) {
    val cal = Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, -weeks)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }
    val now = System.currentTimeMillis()
    var routineIndex = 0

    for (week in 0 until weeks) {
        val weekStart = cal.clone() as Calendar
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        if (random.nextInt(10) == 0) continue

        val days = (0 until 7).shuffled(random).take(random.nextInt(2, 5)).sorted()
        for (day in days) {
            val date = (weekStart.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, day)
                set(Calendar.HOUR_OF_DAY, random.nextInt(7, 21))
                set(Calendar.MINUTE, random.nextInt(60))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, random.nextInt(1000))
            }.timeInMillis
            if (date > now) continue

            val routine = routines[routineIndex++ % routines.size]
            val exercises = routine.filter { random.nextInt(6) != 0 }.ifEmpty { routine }.map { t ->
                val weight = t.startKg + t.gainPerWeekKg * week
                ExerciseEntity(
                    workoutId = date,
                    name = t.name,
                    sets = (t.sets + random.nextInt(-1, 2)).coerceAtLeast(1),
                    reps = (t.reps + random.nextInt(-2, 3)).coerceAtLeast(1),
                    weightKg = (weight / 1.25).toInt() * 1.25
                )
            }
            insertFullWorkout(WorkoutEntity(id = date, date = date), exercises)
        }
    }
}
