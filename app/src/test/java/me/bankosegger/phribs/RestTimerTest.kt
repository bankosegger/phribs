package me.bankosegger.phribs

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `remaining seconds round up so a fresh timer shows its full length`() {
        assertEquals(90, remainingSeconds(now + 90_000, now))
        assertEquals(90, remainingSeconds(now + 89_001, now))
        assertEquals(89, remainingSeconds(now + 89_000, now))
    }

    @Test
    fun `remaining seconds floor at zero once the deadline passes`() {
        assertEquals(0, remainingSeconds(now, now))
        assertEquals(0, remainingSeconds(now - 60_000, now))
    }

    @Test
    fun `durations format as minutes and padded seconds`() {
        assertEquals("0:00", formatRestDuration(0))
        assertEquals("0:09", formatRestDuration(9))
        assertEquals("1:30", formatRestDuration(90))
        assertEquals("10:00", formatRestDuration(600))
    }

    @Test
    fun `negative durations format as zero rather than going backwards`() {
        assertEquals("0:00", formatRestDuration(-5))
    }

    @Test
    fun `state reports its own remaining time`() {
        val state = RestTimerState(endAt = now + 45_000, totalSeconds = 90)
        assertEquals(45, state.remainingSeconds(now))
    }
}
