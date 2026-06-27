package com.example.modulo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AppLogicTest {
    @Test
    fun `when task is toggled, isCompleted becomes true`() {
        val initialTask = Task(
            id = 1717059306606,
            module = "CS1101S",
            title = "Tutorial 5",
            due = "2026-06-10",
            type = "tutorial",
            done = false
        )

        val updatedTask = initialTask.copy(done = !initialTask.done)

        assertTrue(updatedTask.done)
        assertEquals("Tutorial 5", updatedTask.title) // Title shouldn't change!
    }

    @Test
    fun `calculating study session duration is perfectly accurate`() {
        val startTime = Instant.parse("2026-06-01T10:00:00Z")
        val endTime = Instant.parse("2026-06-01T10:45:00Z")

        val durationInMinutes = ChronoUnit.MINUTES.between(startTime, endTime)

        assertEquals(45, durationInMinutes)
    }
}