package com.example.modulo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the hidden-modules in [AppViewModel].
 */
class ModuleVisibilityIntegrationTest : AppViewModelTestBase() {

    @Test
    fun `hiding a module adds its label to hiddenModules`() = runTest {
        viewModel.setModuleHidden("CS2030S", true)

        assertEquals(listOf("CS2030S"), viewModel.appData.value.hiddenModules)
    }

    @Test
    fun `hiding the same module twice does not duplicate it`() = runTest {
        viewModel.setModuleHidden("CS2030S", true)
        viewModel.setModuleHidden("CS2030S", true)

        assertEquals(listOf("CS2030S"), viewModel.appData.value.hiddenModules)
    }

    @Test
    fun `unhiding a module removes it`() = runTest {
        viewModel.setModuleHidden("CS2030S", true)
        viewModel.setModuleHidden("MA1521", true)

        viewModel.setModuleHidden("CS2030S", false)

        assertEquals(listOf("MA1521"), viewModel.appData.value.hiddenModules)
    }

    @Test
    fun `unhiding a module that was never hidden is a no-op`() = runTest {
        viewModel.setModuleHidden("CS2030S", false)

        assertTrue(viewModel.appData.value.hiddenModules.isEmpty())
    }

    @Test
    fun `hiding a module leaves its tasks untouched`() = runTest {
        val task = sampleTask(module = "CS2030S")
        viewModel.addTask(task)

        viewModel.setModuleHidden("CS2030S", true)

        val data = viewModel.appData.value
        assertEquals(listOf("CS2030S"), data.hiddenModules)
        assertEquals(1, data.tasks.size)
        assertEquals(task.title, data.tasks.first().title)
    }

    @Test
    fun `hidden modules are per-handbook - archived on switch and restored on swap`() = runTest {
        // ARRANGE: current = poly (hb-old) with a module hidden.
        viewModel.saveHandbook(sampleHandbook(id = "hb-old", educationLevel = "poly"))
        viewModel.setModuleHidden("CS2030S", true)

        // ACT: switch to a fresh handbook — the current one is archived with its hidden set.
        viewModel.saveHandbook(sampleHandbook(id = "hb-new", educationLevel = "university"))

        // The new term starts with nothing hidden...
        assertTrue(viewModel.appData.value.hiddenModules.isEmpty())
        // ...and the archived term kept its hidden module.
        val archived = viewModel.appData.value.otherHandbooks.first { it.id == "hb-old" }
        assertEquals(listOf("CS2030S"), archived.hiddenModules)

        // ACT: swap back to the poly term.
        viewModel.swapHandbook(archived)

        // ASSERT: its hidden set is restored as the active one.
        assertEquals(listOf("CS2030S"), viewModel.appData.value.hiddenModules)
    }

    @Test
    fun `toggling module visibility stamps updatedAt`() = runTest {
        assertNull(viewModel.appData.value.updatedAt)

        viewModel.setModuleHidden("CS2030S", true)

        assertTrue(viewModel.appData.value.updatedAt!!.isNotBlank())
    }
}