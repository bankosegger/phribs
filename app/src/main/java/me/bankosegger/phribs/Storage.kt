package me.bankosegger.phribs

import android.content.Context
import org.json.JSONArray

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
                        val set = ExerciseSet(e.getInt("reps"), e.getDouble("weight"))
                        Exercise(e.getString("name"), List(e.getInt("sets")) { set })
                    }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
