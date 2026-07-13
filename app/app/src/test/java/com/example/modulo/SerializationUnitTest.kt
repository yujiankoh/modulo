package com.example.modulo

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the single shared [syncJsonParser] used for local save, Drive sync and the
 * timetable proxy payloads.
 */
class SerializationUnitTest {
    private val task = Task(
        id = 1_717_059_306_606,
        module = "CS1101S",
        title = "Tutorial 5",
        due = "2026-06-10",
        type = "tutorial",
        done = false
    )

    @Test
    fun `AppData round trips through JSON without losing data`() {
        val original = AppData(
            handbookId = "hb-1",
            educationLevel = "university",
            academicYear = "2026/2027",
            semester = 1,
            termStart = "2026-08-10",
            termEnd = "2026-11-20",
            breaks = listOf(Break(start = "2026-09-21", end = "2026-09-27")),
            updatedAt = "2026-07-10T00:00:00Z",
            tasks = listOf(task),
            studySessions = listOf(StudySession(id = "s1", start = "a", end = "b", durationMins = 45, rating = 4)),
            hiddenModules = listOf("CS1231S"),
            city = City(buildings = listOf(Building(x = 0, y = 0, floors = 3), Building(x = -1, y = 1, floors = 1))),
            timetable = Timetable(
                educationLevel = "university",
                modules = listOf(
                    Module(
                        code = "CS1101S",
                        name = "Programming Methodology",
                        slots = listOf(Slot(day = "Monday", start = "10:00", end = "12:00", location = "COM1"))
                    )
                )
            ),
            otherHandbooks = listOf(Handbook(id = "hb-2", educationLevel = "poly"))
        )

        val json = syncJsonParser.encodeToString(original)
        val restored = syncJsonParser.decodeFromString<AppData>(json)

        assertEquals(original, restored)
    }

    @Test
    fun `encodeDefaults writes default valued fields into the JSON`() {
        val json = syncJsonParser.encodeToString(AppData())

        // With encodeDefaults = true these are present even though they hold default values.
        assertTrue(json.contains("\"schemaVersion\":2"))
        assertTrue(json.contains("\"city\":{\"buildings\":[]}"))
        assertTrue(json.contains("\"tasks\":[]"))
    }

    @Test
    fun `ignoreUnknownKeys lets us read data that has extra fields from a newer schema`() {
        val forwardCompatibleJson = """
            {
                "schemaVersion": 99,
                "educationLevel": "university",
                "aFieldFromTheFuture": {"nested": true},
                "tasks": [
                    {"id": 1, "title": "T", "type": "tutorial", "brandNewTaskFlag": "ignore-me"}
                ]
            }
        """.trimIndent()

        val parsed = syncJsonParser.decodeFromString<AppData>(forwardCompatibleJson)

        assertEquals(99, parsed.schemaVersion)
        assertEquals("university", parsed.educationLevel)
        assertEquals(1, parsed.tasks.size)
        assertEquals("T", parsed.tasks.first().title)
    }

    @Test
    fun `missing optional fields fall back to model defaults on decode`() {
        // Only the required fields are present; everything else must come from defaults.
        val minimalJson = """{ "tasks": [ {"id": 5, "title": "Quiz", "type": "quiz"} ] }"""

        val parsed = syncJsonParser.decodeFromString<AppData>(minimalJson)

        assertEquals(2, parsed.schemaVersion)
        assertNull(parsed.educationLevel)
        assertEquals("", parsed.tasks.first().module)
        assertFalse(parsed.tasks.first().done)
    }

    @Test
    fun `ParsingData serializes the exact keys the proxy expects`() {
        val payload = ParsingData(image = "base64==", mimeType = "image/png", educationLevel = "university")

        val json = syncJsonParser.encodeToString(payload)

        assertTrue(json.contains("\"image\":\"base64==\""))
        assertTrue(json.contains("\"mimeType\":\"image/png\""))
        assertTrue(json.contains("\"educationLevel\":\"university\""))
    }

    @Test
    fun `Timetable returned by the proxy decodes into modules and slots`() {
        val proxyResponse = """
            {
              "educationLevel": "university",
              "modules": [
                {
                  "code": "CS2040S",
                  "name": "Data Structures",
                  "slots": [
                    {"day": "Tuesday", "start": "14:00", "end": "16:00", "sessionType": "Lecture"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val timetable = syncJsonParser.decodeFromString<Timetable>(proxyResponse)

        assertEquals(1, timetable.modules.size)
        val slot = timetable.modules.first().slots.first()
        assertEquals("Tuesday", slot.day)
        assertEquals("Lecture", slot.sessionType)
        assertEquals("all", slot.week) // defaulted, not present in payload
    }
}