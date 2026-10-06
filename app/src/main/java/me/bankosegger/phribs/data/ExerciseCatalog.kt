package me.bankosegger.phribs.data

import android.content.Context
import org.json.JSONArray

fun loadExerciseNames(context: Context): List<String> = context.assets
    .open("exercise_names.json")
    .bufferedReader()
    .use { reader ->
        val names = JSONArray(reader.readText())
        List(names.length()) { index -> names.getString(index) }
    }
