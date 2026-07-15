package com.example.modulo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the handbook lifecycle in [AppViewModel]: saving, swapping, updating and
 * deleting handbooks.
 */
class HandbookIntegrationTest : AppViewModelTestBase() {
    @Test
    fun `saveHandbook adopts the new handbook fields as the current term`() = runTest {
        val handbook = sampleHandbook()

        viewModel.saveHandbook(handbook)

        val data = viewModel.appData.value
        assertEquals(handbook.id, data.handbookId)
        assertEquals("university", data.educationLevel)
        assertEquals("2026/2027", data.academicYear)
        assertEquals(1, data.semester)
        // A brand new handbook starts with a clean slate.
        assertTrue(data.tasks.isEmpty())
        assertNull(data.timetable)
        assertTrue(data.hiddenModules.isEmpty())
    }

    @Test
    fun `saveHandbook archives the previous current term into otherHandbooks`() = runTest {
        // ARRANGE: establish a current handbook with a task in it.
        viewModel.saveHandbook(sampleHandbook(id = "hb-old", educationLevel = "poly"))
        viewModel.addTask(sampleTask())

        // ACT: create/switch to a second handbook.
        viewModel.saveHandbook(sampleHandbook(id = "hb-new", educationLevel = "university"))

        // ASSERT: the old term is preserved with its task, the new term is current and empty.
        val data = viewModel.appData.value
        assertEquals("hb-new", data.handbookId)
        assertEquals(1, data.otherHandbooks.size)
        val archived = data.otherHandbooks.first()
        assertEquals("hb-old", archived.id)
        assertEquals("poly", archived.educationLevel)
        assertEquals(1, archived.tasks.size)
        assertEquals("Tutorial 5", archived.tasks.first().title)
    }

    @Test
    fun `saveHandbook from onboarding moves startupState to READY`() = runTest {
        setStartupToHandbook()

        viewModel.saveHandbook(sampleHandbook())

        assertEquals(StartupState.READY, viewModel.startupState.value)
    }

    @Test
    fun `swapHandbook restores an archived handbook and re-files the current one`() = runTest {
        // ARRANGE: current = university (hb-new), archived = poly (hb-old) holding a task.
        viewModel.saveHandbook(sampleHandbook(id = "hb-old", educationLevel = "poly"))
        viewModel.addTask(sampleTask(title = "Poly essay"))
        viewModel.saveHandbook(sampleHandbook(id = "hb-new", educationLevel = "university"))
        viewModel.addTask(sampleTask(title = "Uni lab"))

        val archived = viewModel.appData.value.otherHandbooks.first { it.id == "hb-old" }

        // ACT
        viewModel.swapHandbook(archived)

        // ASSERT: poly is now current with its original task restored.
        val data = viewModel.appData.value
        assertEquals("poly", data.educationLevel)
        assertEquals(1, data.tasks.size)
        assertEquals("Poly essay", data.tasks.first().title)

        // The previously current university term is now archived (still with its task).
        assertEquals(1, data.otherHandbooks.size)
        val nowArchived = data.otherHandbooks.first()
        assertEquals("university", nowArchived.educationLevel)
        assertEquals("Uni lab", nowArchived.tasks.first().title)
    }

    @Test
    fun `deleteHandbook removes only the targeted archived handbook`() = runTest {
        viewModel.saveHandbook(sampleHandbook(id = "hb-a", educationLevel = "poly"))
        viewModel.saveHandbook(sampleHandbook(id = "hb-b", educationLevel = "jc"))
        viewModel.saveHandbook(sampleHandbook(id = "hb-c", educationLevel = "university"))

        val toDelete = viewModel.appData.value.otherHandbooks.first { it.id == "hb-a" }
        viewModel.deleteHandbook(toDelete)

        val remaining = viewModel.appData.value.otherHandbooks.map { it.id }
        assertEquals(listOf("hb-b"), remaining)
    }

    @Test
    fun `updateHandbook edits current term fields without touching tasks or timetable`() = runTest {
        viewModel.saveHandbook(sampleHandbook(id = "hb-1", educationLevel = "university"))
        viewModel.addTask(sampleTask(title = "Keep me"))

        val edited = viewModel.appData.value.let {
            Handbook(
                id = it.handbookId,
                educationLevel = it.educationLevel,
                academicYear = "2027/2028",
                semester = 2,
                termStart = "2027-01-13",
                termEnd = "2027-05-01",
                breaks = listOf(Break(start = "2027-02-16", end = "2027-02-22"))
            )
        }

        viewModel.updateHandbook(edited)

        val data = viewModel.appData.value
        assertEquals("2027/2028", data.academicYear)
        assertEquals(2, data.semester)
        assertEquals(1, data.breaks.size)
        // Tasks are untouched by an update.
        assertEquals(1, data.tasks.size)
        assertEquals("Keep me", data.tasks.first().title)
    }

    @Test
    fun `every handbook mutation stamps updatedAt`() = runTest {
        assertNull(viewModel.appData.value.updatedAt)

        viewModel.saveHandbook(sampleHandbook())

        assertTrue(viewModel.appData.value.updatedAt!!.isNotBlank())
    }

    private fun setStartupToHandbook() {
        assertEquals(StartupState.HANDBOOK, viewModel.startupState.value)
    }
}