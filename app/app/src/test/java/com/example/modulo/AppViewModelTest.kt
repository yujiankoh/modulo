package com.example.modulo

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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

    @Before
    fun setup() {
        mockApplication = mockk<Application>(relaxed = true)
        mockSavedStateHandle = mockk<SavedStateHandle>(relaxed = true)
        val mockConnectivityManager = mockk<ConnectivityManager>(relaxed = true)

        every { mockApplication.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempDir

        // Initialize a fresh ViewModel for every test
        viewModel = AppViewModel(mockApplication, mockSavedStateHandle)
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
}