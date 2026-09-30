package me.bankosegger.phribs

import org.json.JSONArray
import org.json.JSONObject

enum class BackupFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv")
}

object Backup {
    private const val VERSION = 1
    private val CSV_HEADER = listOf("workout_id", "date", "exercise", "sets", "reps", "weight_kg")

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
                ex.put(
                    JSONObject().put("name", e.name).put("sets", e.sets)
                        .put("reps", e.reps).put("weightKg", e.weightKg)
                )
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
                    Exercise(e.getString("name"), e.getInt("sets"), e.getInt("reps"), e.getDouble("weightKg"))
                }
            )
        }
    }

    fun toCsv(workouts: List<Workout>): String = buildString {
        appendLine(CSV_HEADER.joinToString(","))
        workouts.forEach { w ->
            w.exercises.forEach { e ->
                appendLine(
                    listOf(w.id.toString(), w.date.toString(), csvEscape(e.name), e.sets.toString(),
                        e.reps.toString(), e.weightKg.toString()).joinToString(",")
                )
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
                    exercises = group.map { Exercise(it[2], it[3].toInt(), it[4].toInt(), it[5].toDouble()) }
                )
            }
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
