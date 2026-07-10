package com.example.modulo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Pure unit tests for the data model in [AppData.kt].
 * These cover the value-type behaviour the rest of the app relies on: defaults, equality,
 * copy semantics, enum lookups and the small pieces of arithmetic used for study sessions.
 */
class ModelUnitTest {
    @Test
    fun `Task applies documented defaults for optional fields`() {
        val task = Task(id = 1L, title = "Read chapter 3", type = "reading")

        assertEquals("", task.module)
        assertEquals("", task.due)
        assertFalse(task.done)
        assertNull(task.createdAt)
        assertNull(task.updatedAt)
    }

    @Test
    fun `toggling done via copy leaves every other field untouched`() {
        val task = Task(id = 1L, module = "CS2030S", title = "Lab 2", due = "2026-06-10", type = "lab")

        val toggled = task.copy(done = !task.done)

        assertTrue(toggled.done)
        assertEquals(task.id, toggled.id)
        assertEquals(task.module, toggled.module)
        assertEquals(task.title, toggled.title)
        assertEquals(task.due, toggled.due)
        assertEquals(task.type, toggled.type)
    }

    @Test
    fun `data class equality is value based, so identical tasks are equal but a changed id is not`() {
        val a = Task(id = 1L, title = "Essay", type = "assignment")
        val b = Task(id = 1L, title = "Essay", type = "assignment")
        val c = a.copy(id = 2L)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    // ---- Slot / Module / Timetable ------------------------------------------

    @Test
    fun `Slot defaults optional metadata and defaults week to all`() {
        val slot = Slot(day = "Monday", start = "10:00", end = "12:00")

        assertEquals("", slot.location)
        assertEquals("", slot.sessionType)
        assertEquals("", slot.classNo)
        assertEquals("all", slot.week)
    }

    @Test
    fun `Module and Timetable default to empty collections`() {
        val module = Module(code = "CS1101S", name = "Programming Methodology")
        val timetable = Timetable(educationLevel = "university")

        assertTrue(module.slots.isEmpty())
        assertTrue(timetable.modules.isEmpty())
    }

    @Test
    fun `StudySession generates a unique id when none supplied`() {
        val first = StudySession(start = "s", end = "e", durationMins = 30)
        val second = StudySession(start = "s", end = "e", durationMins = 30)

        assertNotEquals(first.id, second.id)
        assertTrue(first.id.isNotBlank())
    }

    @Test
    fun `study session duration between two instants is exact in minutes`() {
        val start = Instant.parse("2026-06-01T10:00:00Z")
        val end = Instant.parse("2026-06-01T11:30:00Z")

        val minutes = ChronoUnit.MINUTES.between(start, end)

        assertEquals(90L, minutes)
    }

    @Test
    fun `elapsed seconds round to the nearest minute the same way saveSession does`() {
        // Mirrors (elapsedSeconds / 60.0).roundToInt() in AppViewModel.saveSession
        assertEquals(0, (29L / 60.0).roundToInt())
        assertEquals(1, (30L / 60.0).roundToInt())
        assertEquals(1, (89L / 60.0).roundToInt())
        assertEquals(2, (90L / 60.0).roundToInt())
    }

    @Test
    fun `fresh AppData carries the current schema version and empty collections`() {
        val data = AppData()

        assertEquals(2, data.schemaVersion)
        assertNull(data.educationLevel)
        assertEquals(0, data.cityLevel)
        assertTrue(data.tasks.isEmpty())
        assertTrue(data.studySessions.isEmpty())
        assertTrue(data.otherHandbooks.isEmpty())
        assertTrue(data.breaks.isEmpty())
    }

    @Test
    fun `EducationLevel fromJson resolves every known level`() {
        assertEquals(EducationLevel.PRIMARY, EducationLevel.fromJson("primary"))
        assertEquals(EducationLevel.SECONDARY, EducationLevel.fromJson("secondary"))
        assertEquals(EducationLevel.JC, EducationLevel.fromJson("jc"))
        assertEquals(EducationLevel.POLY, EducationLevel.fromJson("poly"))
        assertEquals(EducationLevel.UNIVERSITY, EducationLevel.fromJson("university"))
    }

    @Test
    fun `EducationLevel fromJson returns null for unknown or null input`() {
        assertNull(EducationLevel.fromJson("kindergarten"))
        assertNull(EducationLevel.fromJson(null))
    }

    @Test
    fun `EducationLevel getDisplay maps known levels and yields empty string otherwise`() {
        assertEquals("Polytechnic", EducationLevel.getDisplay("poly"))
        assertEquals("University", EducationLevel.getDisplay("university"))
        assertEquals("", EducationLevel.getDisplay("unknown"))
        assertEquals("", EducationLevel.getDisplay(null))
    }

    @Test
    fun `SortOption exposes human readable display names`() {
        assertEquals("Due Date", SortOption.DUE_DATE.displayName)
        assertEquals("Module Code", SortOption.MODULE_CODE.displayName)
        assertEquals("Task Type", SortOption.TYPE.displayName)
        assertEquals("Name", SortOption.TITLE.displayName)
        assertEquals(4, SortOption.entries.size)
    }
}