package com.example.modulo

import com.example.modulo.helpers.AuthenticationHelper
import com.example.modulo.helpers.SyncingHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for [AppViewModel]'s task mutations and the silent-sign-in startup path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest : AppViewModelTestBase() {
    private val task = sampleTask()

    override fun buildPrefs() =
        androidx.datastore.preferences.core.preferencesOf(
            HAS_SEEN_TUTORIAL to true,
            IS_DRIVE_SYNC_ENABLED to true
        )

    override val networkConnected: Boolean = true

    override fun onBeforeViewModelCreated() {
        mockkObject(AuthenticationHelper)
        mockkObject(SyncingHelper.Companion)
        every { SyncingHelper.getSyncService(any(), any()) } returns syncingHelper
        coEvery { syncingHelper.downloadAppData() } returns null
        coEvery { AuthenticationHelper.silentSignIn(any(), any(), any(), captureLambda(), any()) } answers {
            lambda<() -> Unit>().captured.invoke()
        }
    }

    override fun onTearDown() {
        unmockkObject(SyncingHelper.Companion)
    }

    @Test
    fun `when a new task is added, appData state updates correctly`() = runTest {
        viewModel.addTask(task)

        val currentData = viewModel.appData.value
        assertTrue(currentData.tasks.isNotEmpty())
        assertEquals("Tutorial 5", currentData.tasks.first().title)
    }

    @Test
    fun `when task completion is toggled, state updates correctly`() = runTest {
        viewModel.addTask(task)

        viewModel.completeTask(task)

        val currentTasks = viewModel.appData.value.tasks
        assertTrue(currentTasks.first { it.id == task.id }.done)
    }

    @Test
    fun `when a task is deleted, it is completely removed from appData`() = runTest {
        val task2 = sampleTask(id = 200000000, module = "CS2040S", title = "Assignment 2", type = "Assignment", done = true)
        viewModel.addTask(task)
        viewModel.addTask(task2)

        viewModel.deleteTask(task)

        val currentTasks = viewModel.appData.value.tasks
        assertEquals(1, currentTasks.size)
        assertEquals("Assignment 2", currentTasks.first().title)
    }

    @Test
    fun `when silentSignIn fails, startup routes to AUTHENTICATE`() = runTest {
        coEvery {
            AuthenticationHelper.silentSignIn(any(), any(), any(), captureLambda(), any())
        } answers {
            lambda<() -> Unit>().captured.invoke() // onFailure
        }

        viewModel.startUpChecks()
        advanceUntilIdle()

        assertEquals(StartupState.AUTHENTICATE, viewModel.startupState.value)
    }

    @Test
    fun `when silentSignIn succeeds with no handbook, startup routes to HANDBOOK`() = runTest {
        coEvery {
            AuthenticationHelper.silentSignIn(any(), any(), captureLambda(), any(), any())
        } answers {
            lambda<(String) -> Unit>().captured.invoke("test@email.com") // onSuccess
        }

        viewModel.startUpChecks()
        advanceUntilIdle()

        // Fresh profile has no education level, so a successful sign-in lands on handbook creation.
        assertEquals(StartupState.HANDBOOK, viewModel.startupState.value)
    }
}
