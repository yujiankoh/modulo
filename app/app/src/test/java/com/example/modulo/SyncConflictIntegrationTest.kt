package com.example.modulo

import com.example.modulo.helpers.AuthenticationHelper
import com.example.modulo.helpers.SyncingHelper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Integration tests for the opt-into-sync conflict flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncConflictIntegrationTest : AppViewModelTestBase() {

    private fun cloudData(
        educationLevel: String? = "poly",
        tasks: List<Task> = listOf(sampleTask(title = "Cloud task")),
        studySessions: List<StudySession> = listOf(
            StudySession(start = "2026-07-01T10:00:00Z", end = "2026-07-01T11:00:00Z", durationMins = 60)
        ),
        otherHandbooks: List<Handbook> = emptyList()
    ) = AppData(
        educationLevel = educationLevel,
        academicYear = "2025/2026",
        semester = 1,
        updatedAt = "2026-07-01T12:00:00Z",
        tasks = tasks,
        studySessions = studySessions,
        otherHandbooks = otherHandbooks
    )
    
    // Cancelling the opt-in forgets the Google account
    override fun onBeforeViewModelCreated() {
        mockkObject(AuthenticationHelper)
        coEvery { AuthenticationHelper.signOut(any()) } just Runs
    }

    private fun giveLocalData() {
        viewModel.saveHandbook(sampleHandbook())
        viewModel.addTask(sampleTask())
    }

    // Sign in through the interactive opt-in path with a specific Drive payload.
    private fun TestScope.signInWithCloud(cloud: AppData?) {
        mockkObject(SyncingHelper.Companion)
        every { SyncingHelper.getSyncService(any(), any()) } returns syncingHelper
        coEvery { syncingHelper.downloadAppData() } returns cloud
        viewModel.onAuthenticationSuccess(mockApplication, "user@test.com")
        advanceUntilIdle()
    }

    @Test
    fun `data on both sides raises a conflict instead of overwriting`() = runTest {
        giveLocalData() // local now has a handbook (meaningful)

        signInWithCloud(cloudData())

        val conflict = viewModel.syncConflict.value
        assertNotNull("both sides have data, so the user must choose", conflict)
        assertEquals(1, conflict!!.local.handbooks)
        assertEquals("university", conflict.local.currentHandbook.substringBefore("\n").lowercase())
        assertEquals(1, conflict.cloud.handbooks)
        assertEquals(1, conflict.cloud.tasks)
        assertEquals(60, conflict.cloud.studyMinutes)
    }

    @Test
    fun `keepDrive adopts the cloud copy and clears the conflict`() = runTest {
        giveLocalData()
        val cloud = cloudData()
        signInWithCloud(cloud)

        viewModel.keepDriveData()
        advanceUntilIdle()

        assertNull(viewModel.syncConflict.value)
        assertEquals("poly", viewModel.appData.value.educationLevel)
        assertEquals("Cloud task", viewModel.appData.value.tasks.first().title)
    }

    @Test
    fun `keepLocal uploads this device's copy and clears the conflict`() = runTest {
        giveLocalData() // university
        signInWithCloud(cloudData())

        viewModel.keepLocalData()
        advanceUntilIdle()

        assertNull(viewModel.syncConflict.value)
        // Local is untouched and pushed up to Drive.
        assertEquals("university", viewModel.appData.value.educationLevel)
        coVerify { syncingHelper.uploadAppData(viewModel.appData.value) }
    }

    @Test
    fun `cancel aborts the opt-in and reverts to local-only`() = runTest {
        giveLocalData()
        signInWithCloud(cloudData())

        viewModel.cancelSyncConflict()
        advanceUntilIdle()

        assertNull(viewModel.syncConflict.value)
        assertFalse("cancelling opt-in leaves Drive sync off", viewModel.isDriveSyncEnabled.value)
        // Local data is preserved, and the Google account is forgotten.
        assertEquals("university", viewModel.appData.value.educationLevel)
        coVerify { AuthenticationHelper.signOut(any()) }
    }

    @Test
    fun `an empty local profile just adopts the cloud with no prompt`() = runTest {
        // No local handbook/tasks → nothing worth preserving → standard reconcile takes over.
        signInWithCloud(cloudData())

        assertNull("no conflict when this device is empty", viewModel.syncConflict.value)
        assertEquals("poly", viewModel.appData.value.educationLevel)
    }

    @Test
    fun `no cloud data means no prompt`() = runTest {
        giveLocalData()

        signInWithCloud(null)

        assertNull("no conflict when Drive is empty", viewModel.syncConflict.value)
        assertEquals("university", viewModel.appData.value.educationLevel)
    }
}