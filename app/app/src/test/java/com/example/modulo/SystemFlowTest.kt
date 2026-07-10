package com.example.modulo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * System / end-to-end tests that drive [AppViewModel] through complete user journeys, touching
 * multiple features in sequence exactly as the UI would: onboarding, handbook creation, task
 * management, timetable capture, and multi-term switching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemFlowTest : AppViewModelTestBase() {
    @Test
    fun `onboarding journey - a sync-disabled first term lands on HANDBOOK then goes READY`() = runTest {
        advanceUntilIdle()
        // startUpChecks() ran in init: tutorial seen, sync disabled, no education level -> HANDBOOK.
        assertEquals(StartupState.HANDBOOK, viewModel.startupState.value)

        // User fills in their first handbook.
        viewModel.saveHandbook(sampleHandbook(id = "hb-uni", educationLevel = "university"))

        assertEquals(StartupState.READY, viewModel.startupState.value)
        assertEquals("university", viewModel.appData.value.educationLevel)
    }

    @Test
    fun `task management journey - add, complete and delete across several tasks`() = runTest {
        viewModel.saveHandbook(sampleHandbook())

        val t1 = sampleTask(id = 1, title = "Tutorial 1")
        val t2 = sampleTask(id = 2, title = "Lab 2", type = "lab")
        val t3 = sampleTask(id = 3, title = "Essay", type = "assignment")
        viewModel.addTask(t1)
        viewModel.addTask(t2)
        viewModel.addTask(t3)
        assertEquals(3, viewModel.appData.value.tasks.size)

        // Complete the middle one.
        viewModel.completeTask(t2)
        assertTrue(viewModel.appData.value.tasks.first { it.id == 2L }.done)
        assertFalse(viewModel.appData.value.tasks.first { it.id == 1L }.done)

        // Delete the first one.
        viewModel.deleteTask(t1)
        val remaining = viewModel.appData.value.tasks.map { it.id }
        assertEquals(listOf(2L, 3L), remaining)
    }

    @Test
    fun `timetable journey - saving a parsed timetable sets the education level and modules`() = runTest {
        viewModel.saveHandbook(sampleHandbook(educationLevel = "poly"))

        val timetable = Timetable(
            educationLevel = "poly",
            modules = listOf(
                Module(
                    code = "EG1311",
                    name = "Design & Make",
                    slots = listOf(Slot(day = "Wednesday", start = "09:00", end = "11:00", sessionType = "Lecture"))
                )
            )
        )
        viewModel.saveTimetable(timetable)

        val data = viewModel.appData.value
        assertEquals("poly", data.educationLevel)
        assertEquals(1, data.timetable?.modules?.size)
        assertEquals(TimetableState.Idle, viewModel.timetableState.value)
    }

    @Test
    fun `multi-term journey - switching handbooks preserves each term's tasks independently`() = runTest {
        // Term 1: university with two tasks.
        viewModel.saveHandbook(sampleHandbook(id = "uni", educationLevel = "university"))
        viewModel.addTask(sampleTask(id = 10, title = "CS tutorial"))
        viewModel.addTask(sampleTask(id = 11, title = "CS lab", type = "lab"))

        // Term 2: create a poly handbook (archives the uni term).
        viewModel.saveHandbook(sampleHandbook(id = "poly", educationLevel = "poly"))
        viewModel.addTask(sampleTask(id = 20, title = "Poly project", type = "project"))

        // Currently on poly with one task; uni archived with two.
        assertEquals("poly", viewModel.appData.value.educationLevel)
        assertEquals(1, viewModel.appData.value.tasks.size)

        // Switch back to the university term.
        val uni = viewModel.appData.value.otherHandbooks.first { it.id == "uni" }
        viewModel.swapHandbook(uni)

        val data = viewModel.appData.value
        assertEquals("university", data.educationLevel)
        assertEquals(setOf("CS tutorial", "CS lab"), data.tasks.map { it.title }.toSet())
        // Poly is now the archived term, still holding its single task.
        val polyArchived = data.otherHandbooks.first { it.id == "poly" }
        assertEquals(listOf("Poly project"), polyArchived.tasks.map { it.title })
    }

    @Test
    fun `persistence journey - a fully populated session survives a JSON round trip`() = runTest {
        viewModel.saveHandbook(sampleHandbook())
        viewModel.addTask(sampleTask(title = "Persist me"))
        viewModel.saveTimetable(
            Timetable(educationLevel = "university", modules = listOf(Module(code = "CS1101S", name = "Prog")))
        )

        val live = viewModel.appData.value
        val json = syncJsonParser.encodeToString(live)
        val restored = syncJsonParser.decodeFromString<AppData>(json)

        assertEquals(live, restored)
        assertEquals("Persist me", restored.tasks.first().title)
        assertEquals("CS1101S", restored.timetable?.modules?.first()?.code)
    }

    @Test
    fun `preference journey - choosing local-only sync parks a level-less profile at HANDBOOK`() = runTest {
        viewModel.saveSyncPreference(enabled = false)
        advanceUntilIdle()

        assertFalse(viewModel.isDriveSyncEnabled.value)
        assertEquals(SyncState.OFFLINE, viewModel.syncState.value)
        assertEquals(StartupState.HANDBOOK, viewModel.startupState.value)
    }

    @Test
    fun `tutorial journey - completing the tutorial advances to SIGN_IN`() = runTest {
        viewModel.completeTutorial()
        advanceUntilIdle()

        assertEquals(StartupState.SIGN_IN, viewModel.startupState.value)
    }
}
