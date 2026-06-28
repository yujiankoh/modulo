package com.example.modulo

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import com.example.modulo.helpers.AuthenticationHelper
import com.google.android.gms.auth.api.identity.AuthorizationResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppViewModelTest {
    val module = Module(
        code = "CS1101S",
        name = "Programming Methodology"
    )
    val task = Task(
        id = 1717059306606,
        module = "CS1101S",
        title = "Tutorial 5",
        due = "2026-06-10",
        type = "tutorial",
        done = false
    )

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockApplication: Application
    private lateinit var mockSavedStateHandle: SavedStateHandle
    private lateinit var viewModel: AppViewModel

    fun createTestDatastore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { File.createTempFile("test_settings", ".preferences_pb") }
        )
    }

    @Before
    fun setup() {
        mockApplication = mockk<Application>(relaxed = true)
        mockSavedStateHandle = mockk<SavedStateHandle>(relaxed = true)

        // Setup shared mocks
        val mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val tempDir = File(System.getProperty("java.io.tmpdir"))

        every { mockApplication.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempDir

        // Mock the static DataStore
        mockkStatic("com.example.modulo.AppViewModelKt")
        val mockDataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { any<Context>().dataStore } returns mockDataStore

        val fakePrefs = preferencesOf(HAS_SEEN_TUTORIAL to true, IS_DRIVE_SYNC_ENABLED to true)
        every { mockDataStore.data } returns flowOf(fakePrefs)

        viewModel = AppViewModel(mockApplication, mockSavedStateHandle)
    }

    @After
    fun tearDown() {
        unmockkStatic("com.example.modulo.AppViewModelKt")
        unmockkObject(AuthenticationHelper)
    }

    @Test
    fun `when a new task is added, appData state updates correctly`() = runTest {
        // ACT
        viewModel.addTask(task)

        // ASSERT
        val currentData = viewModel.appData.value
        assertTrue(currentData.tasks.isNotEmpty())
        assertEquals("Tutorial 5", currentData.tasks.first().title)
    }

    @Test
    fun `when task completion is toggled, state updates correctly`() = runTest {
        // ARRANGE
        viewModel.addTask(task)

        // ACT
        viewModel.completeTask(task)

        // ASSERT
        val currentTasks = viewModel.appData.value.tasks
        assertTrue(currentTasks.first { it.id == 1717059306606 }.done)
    }

    @Test
    fun `when a task is deleted, it is completely removed from appData`() = runTest {
        // ARRANGE
        val task1 = task
        val task2 = Task(
            id = 200000000,
            module = "CS2040S",
            title = "Assignment 2",
            due = "2026-07-10",
            type = "Assignment",
            done = true
        )

        viewModel.addTask(task1)
        viewModel.addTask(task2)

        // ACT
        viewModel.deleteTask(task1)

        // ASSERT
        val currentTasks = viewModel.appData.value.tasks
        assertEquals(1, currentTasks.size)
        assertEquals("Assignment 2", currentTasks.first().title)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when silentSignIn fails, authState moves to SIGN_IN`() = runTest {
        // ARRANGE
        mockkObject(AuthenticationHelper)
        coEvery {
            AuthenticationHelper.silentSignIn(any(), any(), any(), captureLambda())
        } answers {
            lambda<( () -> Unit )>().captured.invoke()
        }

        // ACT
        viewModel.startUpChecks()
        advanceUntilIdle()

        // ASSERT
        assertEquals(StartupState.SIGN_IN, viewModel.startupState.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when silentSignIn succeeds, authState moves to READY`() = runTest {
        // ARRANGE
        mockkObject(AuthenticationHelper)
        coEvery {
            AuthenticationHelper.silentSignIn(any(), any(), captureLambda(), any())
        } answers {
            lambda<( (String) -> Unit )>().captured.invoke("test@email.com")
        }

        // ACT
        viewModel.startUpChecks()
        advanceUntilIdle()

        // ASSERT
        assertEquals(StartupState.READY, viewModel.startupState.value)
    }
}