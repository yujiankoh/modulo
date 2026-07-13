package com.example.modulo

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AppLogicTest {
    val task = Task(
        id = 1717059306606,
        module = "CS1101S",
        title = "Tutorial 5",
        due = "2026-06-10",
        type = "tutorial",
        done = false
    )

    @Test
    fun `when task is toggled, isCompleted becomes true`() {
        val updatedTask = task.copy(done = !task.done)

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

    @Test
    fun `AppData successfully serializes into JSON string`() {
        // ARRANGE: A dummy data
        val appData = AppData(tasks = listOf(task))

        // ACT: Parse the data
        val jsonString = syncJsonParser.encodeToString(appData)

        // ASSERT
        assertTrue(jsonString.contains("Tutorial 5"))
        assertTrue(jsonString.contains("\"done\":false"))
    }

    @Test
    fun `JSON string successfully deserializes into AppData object`() {
        // ARRANGE: A raw JSON string simulating what Google Drive gives you
        val rawJson = """
            {
                "tasks": [
                    {"id": 1717059306606,
                     "module": "CS1101S",
                     "title": "Tutorial 5",
                     "due": "2026-06-10",
                     "type": "tutorial", 
                     "done": false}
                ]
            }
        """.trimIndent()

        // ACT
        val parsedData = syncJsonParser.decodeFromString<AppData>(rawJson)

        // ASSERT
        assertEquals(1, parsedData.tasks.size)
        assertEquals("Tutorial 5", parsedData.tasks[0].title)
        assertEquals("2026-06-10", parsedData.tasks[0].due)
        assertEquals("tutorial", parsedData.tasks[0].type)
        assertEquals(false, parsedData.tasks[0].done)
    }
}