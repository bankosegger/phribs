package me.bankosegger.phribs

import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Navigation resolves type-safe routes through kotlinx.serialization at runtime. Without the
 * serialization plugin the routes still compile and only blow up when the graph is built, so these
 * assert that each one really has a generated serializer.
 */
class RoutesTest {
    @Test
    fun `every destination has a generated serializer`() {
        val names = listOf(
            serializer<Home>().descriptor.serialName,
            serializer<Settings>().descriptor.serialName,
            serializer<LogWorkout>().descriptor.serialName,
            serializer<LogRoutine>().descriptor.serialName,
            serializer<ExerciseProgress>().descriptor.serialName
        )
        assertEquals(
            listOf(
                "me.bankosegger.phribs.Home",
                "me.bankosegger.phribs.Settings",
                "me.bankosegger.phribs.LogWorkout",
                "me.bankosegger.phribs.LogRoutine",
                "me.bankosegger.phribs.ExerciseProgress"
            ),
            names
        )
    }

    @Test
    fun `exercise progress carries the exercise name`() {
        val descriptor = serializer<ExerciseProgress>().descriptor
        assertEquals(1, descriptor.elementsCount)
        assertEquals("exerciseName", descriptor.getElementName(0))
    }

    @Test
    fun `log routine carries the routine id`() {
        val descriptor = serializer<LogRoutine>().descriptor
        assertEquals(1, descriptor.elementsCount)
        assertEquals("routineId", descriptor.getElementName(0))
    }

    // Both argument types have a built-in NavType; a nullable primitive would not.
    @Test
    fun `route arguments are not nullable`() {
        assertEquals(false, serializer<LogRoutine>().descriptor.getElementDescriptor(0).isNullable)
        assertEquals(false, serializer<ExerciseProgress>().descriptor.getElementDescriptor(0).isNullable)
    }
}
