package me.bankosegger.phribs

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

// Legacy Storage helper for fallback and auto-migration to Room
object Storage {
    private const val PREFS = "gym_tracker"
    private const val KEY = "workouts"

    fun load(context: Context): List<Workout> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val w = arr.getJSONObject(i)
                val ex = w.getJSONArray("exercises")
                Workout(
                    id = w.getLong("id"),
                    date = w.getLong("date"),
                    exercises = List(ex.length()) { j ->
                        val e = ex.getJSONObject(j)
                        Exercise(e.getString("name"), e.getInt("sets"), e.getInt("reps"), e.getDouble("weight"))
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, workouts: List<Workout>) {
        val arr = JSONArray()
        workouts.forEach { w ->
            val ex = JSONArray()
            w.exercises.forEach { e ->
                ex.put(
                    JSONObject().put("name", e.name).put("sets", e.sets)
                        .put("reps", e.reps).put("weight", e.weightKg)
                )
            }
            arr.put(JSONObject().put("id", w.id).put("date", w.date).put("exercises", ex))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }
}
