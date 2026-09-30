package me.bankosegger.phribs

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    private val workouts = listOf(
        Workout(1L, 1000L, listOf(Exercise("Bench Press", 4, 8, 62.5), Exercise("Pull-ups", 3, 8, 0.0))),
        Workout(2L, 2000L, listOf(Exercise("Curl, \"hammer\"", 3, 12, 12.25)))
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

    @Test(expected = IllegalArgumentException::class)
    fun csvRejectsUnknownHeader() {
        Backup.fromCsv("a,b,c\n1,2,3\n")
    }
}
