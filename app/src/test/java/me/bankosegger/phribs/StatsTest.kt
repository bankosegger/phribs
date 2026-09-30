package me.bankosegger.phribs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class StatsTest {
    private lateinit var savedLocale: Locale
    private lateinit var savedTimeZone: TimeZone

    // Week boundaries depend on locale (first day of week) and time zone, so pin both.
    @Before
    fun pinLocale() {
        savedLocale = Locale.getDefault()
        savedTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.GERMANY)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedTimeZone)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 18): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0)
        }.timeInMillis

    private fun workouts(vararg dates: Long) = dates.mapIndexed { i, d -> Workout(i.toLong(), d, emptyList()) }

    // Wednesday
    private val now = at(2026, 9, 30, 12)

    @Test
    fun `no workouts means no streak`() {
        assertEquals(Streaks(0, 0), weeklyStreaks(emptyList(), now))
    }

    @Test
    fun `consecutive weeks including the current one count`() {
        val w = workouts(at(2026, 9, 29), at(2026, 9, 22), at(2026, 9, 15))
        assertEquals(Streaks(3, 3), weeklyStreaks(w, now))
    }

    @Test
    fun `empty current week does not break the streak yet`() {
        val w = workouts(at(2026, 9, 22), at(2026, 9, 15))
        assertEquals(Streaks(2, 2), weeklyStreaks(w, now))
    }

    @Test
    fun `missing last week resets the current streak`() {
        val w = workouts(at(2026, 9, 15), at(2026, 9, 8))
        assertEquals(Streaks(0, 2), weeklyStreaks(w, now))
    }

    @Test
    fun `best streak survives a gap`() {
        val w = workouts(
            at(2026, 9, 30),
            // gap in week of Sep 21
            at(2026, 9, 14), at(2026, 9, 7), at(2026, 8, 31), at(2026, 8, 24)
        )
        assertEquals(Streaks(1, 4), weeklyStreaks(w, now))
    }

    @Test
    fun `several visits in one week count as one week`() {
        val w = workouts(at(2026, 9, 28), at(2026, 9, 29), at(2026, 9, 30, 8))
        assertEquals(Streaks(1, 1), weeklyStreaks(w, now))
    }

    @Test
    fun `sunday belongs to the week starting monday`() {
        val w = workouts(at(2026, 9, 27), at(2026, 9, 21))
        assertEquals(Streaks(1, 1), weeklyStreaks(w, now))
    }

    @Test
    fun `streak continues across new year`() {
        val w = workouts(at(2025, 12, 23), at(2025, 12, 31), at(2026, 1, 5))
        assertEquals(Streaks(3, 3), weeklyStreaks(w, at(2026, 1, 7, 12)))
    }

    @Test
    fun `streak continues across daylight saving change`() {
        val w = workouts(at(2026, 3, 23), at(2026, 3, 30, 0), at(2026, 4, 6))
        assertEquals(Streaks(3, 3), weeklyStreaks(w, at(2026, 4, 8, 12)))
    }

    @Test
    fun `visit days counts distinct days`() {
        val w = workouts(at(2026, 9, 30, 8), at(2026, 9, 30, 19), at(2026, 9, 29))
        assertEquals(2, visitDays(w))
    }

    @Test
    fun `visit days ignores workouts before since`() {
        val w = workouts(at(2026, 9, 30), at(2026, 9, 1), at(2026, 8, 31))
        assertEquals(2, visitDays(w, since = at(2026, 9, 1, 0)))
    }

    @Test
    fun `whole weights have no decimals`() {
        assertEquals("60", fmtWeight(60.0))
        assertEquals("62.5", fmtWeight(62.5))
    }

    @Test
    fun `describe includes weight only when set`() {
        assertEquals("3 × 10 @ 62.5 kg", describe(Exercise("Bench", 3, 10, 62.5)))
        assertEquals("3 × 8", describe(Exercise("Pull-ups", 3, 8, 0.0)))
    }
}
