package me.bankosegger.phribs

import org.json.JSONArray
import org.json.JSONObject

enum class BackupFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv")
}

object Backup {
    private const val VERSION = 1
    private val CSV_HEADER = listOf("workout_id", "date", "exercise", "set", "reps", "weight_kg")

    fun encode(workouts: List<Workout>, format: BackupFormat): String = when (format) {
        BackupFormat.JSON -> toJson(workouts)
        BackupFormat.CSV -> toCsv(workouts)
    }

    fun decode(text: String): List<Workout> =
        if (text.trimStart().startsWith("{")) fromJson(text) else fromCsv(text)

    fun toJson(workouts: List<Workout>): String {
        val arr = JSONArray()
        workouts.forEach { w ->
            val ex = JSONArray()
            w.exercises.forEach { e ->
                val sets = JSONArray()
                e.sets.forEach { sets.put(JSONObject().put("reps", it.reps).put("weightKg", it.weightKg)) }
                ex.put(JSONObject().put("name", e.name).put("sets", sets))
            }
            arr.put(JSONObject().put("id", w.id).put("date", w.date).put("exercises", ex))
        }
        return JSONObject().put("version", VERSION).put("workouts", arr).toString(2)
    }

    fun fromJson(text: String): List<Workout> {
        val arr = JSONObject(text).getJSONArray("workouts")
        return List(arr.length()) { i ->
            val w = arr.getJSONObject(i)
            val ex = w.getJSONArray("exercises")
            Workout(
                id = w.getLong("id"),
                date = w.getLong("date"),
                exercises = List(ex.length()) { j ->
                    val e = ex.getJSONObject(j)
                    val sets = e.getJSONArray("sets")
                    Exercise(e.getString("name"), List(sets.length()) { k ->
                        val s = sets.getJSONObject(k)
                        ExerciseSet(s.getInt("reps"), s.getDouble("weightKg"))
                    })
                }
            )
        }
    }

    fun toCsv(workouts: List<Workout>): String = buildString {
        appendLine(CSV_HEADER.joinToString(","))
        workouts.forEach { w ->
            w.exercises.forEach { e ->
                e.sets.forEachIndexed { i, s ->
                    appendLine(
                        listOf(w.id.toString(), w.date.toString(), csvEscape(e.name), (i + 1).toString(),
                            s.reps.toString(), s.weightKg.toString()).joinToString(",")
                    )
                }
            }
        }
    }

    fun fromCsv(text: String): List<Workout> {
        val rows = parseCsv(text).filter { row -> row.any { it.isNotBlank() } }
        require(rows.isNotEmpty() && rows.first() == CSV_HEADER) { "Unrecognized CSV header" }
        return rows.drop(1)
            .groupBy { it[0].toLong() }
            .map { (id, group) ->
                Workout(
                    id = id,
                    date = group.first()[1].toLong(),
                    exercises = groupExercises(group)
                )
            }
    }

    // Rows for one exercise are consecutive; set 1 starts a new one, so a workout can repeat an exercise name.
    private fun groupExercises(rows: List<List<String>>): List<Exercise> {
        val exercises = mutableListOf<Exercise>()
        rows.forEach { row ->
            val set = ExerciseSet(row[4].toInt(), row[5].toDouble())
            val last = exercises.lastOrNull()
            if (last != null && last.name == row[2] && row[3].toInt() != 1) {
                exercises[exercises.lastIndex] = last.copy(sets = last.sets + set)
            } else {
                exercises += Exercise(row[2], listOf(set))
            }
        }
        return exercises
    }

    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"' && text.getOrNull(i + 1) == '"') { field.append('"'); i++ }
                else if (c == '"') inQuotes = false
                else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
