package me.bankosegger.phribs

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    private val workouts = listOf(
        Workout(1L, 1000L, listOf(
            Exercise("Bench Press", listOf(ExerciseSet(8, 60.0), ExerciseSet(8, 62.5), ExerciseSet(6, 65.0))),
            Exercise("Pull-ups", listOf(ExerciseSet(8, 0.0), ExerciseSet(7, 0.0)), "Felt easy, add weight next time"),
            Exercise("Bench Press", listOf(ExerciseSet(12, 40.0)))
        )),
        Workout(2L, 2000L, listOf(
            Exercise("Curl, \"hammer\"", listOf(ExerciseSet(12, 12.25)), "Note with a comma, and \"quotes\"")
        ))
    )

    @Test
    fun jsonRoundTrip() {
        assertEquals(workouts, Backup.decode(Backup.encode(workouts, BackupFormat.JSON)))
    }

    @Test
    fun csvRoundTripWithQuotedNames() {
        assertEquals(workouts, Backup.decode(Backup.encode(workouts, BackupFormat.CSV)))
    }

    @Test
    fun csvToleratesCrlfLineEndings() {
        val csv = Backup.toCsv(workouts).replace("\n", "\r\n")
        assertEquals(workouts, Backup.fromCsv(csv))
    }

    @Test
    fun csvImportsOlderFilesWithoutNotes() {
        val csv = """
            workout_id,date,exercise,set,reps,weight_kg
            1,1000,Bench Press,1,8,60.0
            1,1000,Bench Press,2,6,65.0
        """.trimIndent() + "\n"
        val expected = listOf(
            Workout(1L, 1000L, listOf(Exercise("Bench Press", listOf(ExerciseSet(8, 60.0), ExerciseSet(6, 65.0)))))
        )
        assertEquals(expected, Backup.fromCsv(csv))
    }

    @Test
    fun jsonImportsOlderFilesWithoutNotes() {
        val json = """{"version":1,"workouts":[{"id":1,"date":1000,"exercises":""" +
            """[{"name":"Bench Press","sets":[{"reps":8,"weightKg":60.0}]}]}]}"""
        val expected = listOf(Workout(1L, 1000L, listOf(Exercise("Bench Press", listOf(ExerciseSet(8, 60.0))))))
        assertEquals(expected, Backup.fromJson(json))
    }

    @Test(expected = IllegalArgumentException::class)
    fun csvRejectsUnknownHeader() {
        Backup.fromCsv("a,b,c\n1,2,3\n")
    }
}
