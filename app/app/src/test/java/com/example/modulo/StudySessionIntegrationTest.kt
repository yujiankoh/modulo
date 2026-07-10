package com.example.modulo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the study-session timer state machine in [AppViewModel].
 */
class StudySessionIntegrationTest : AppViewModelTestBase() {
    @Test
    fun `a fresh view model has an idle timer`() = runTest {
        assertFalse(viewModel.isTimerRunning)
        assertFalse(viewModel.hasSessionStarted)
        assertEquals(0L, viewModel.elapsedSeconds)
    }

    @Test
    fun `startOrResumeTimer marks the session started and running`() = runTest {
        viewModel.startOrResumeTimer()

        assertTrue(viewModel.isTimerRunning)
        assertTrue(viewModel.hasSessionStarted)

        viewModel.pauseTimer() // stop the infinite tick loop
    }

    @Test
    fun `pauseTimer stops running but keeps the session open`() = runTest {
        viewModel.startOrResumeTimer()

        viewModel.pauseTimer()

        assertFalse(viewModel.isTimerRunning)
        assertTrue(viewModel.hasSessionStarted) // paused, not ended
    }

    @Test
    fun `stopTimer ends the session and stops the clock`() = runTest {
        viewModel.startOrResumeTimer()

        viewModel.stopTimer()

        assertFalse(viewModel.isTimerRunning)
        assertFalse(viewModel.hasSessionStarted)
    }

    @Test
    fun `saveSession does not persist a session shorter than a minute`() = runTest {
        // elapsedSeconds is still 0, so duration rounds to 0 minutes.
        viewModel.startOrResumeTimer()
        viewModel.stopTimer()

        viewModel.saveSession(rating = 5)

        assertTrue(viewModel.appData.value.studySessions.isEmpty())
    }

    @Test
    fun `saveSession always clears the timer state afterwards`() = runTest {
        viewModel.startOrResumeTimer()
        viewModel.stopTimer()

        viewModel.saveSession(rating = 3)

        // discardSession() resets the counter regardless of whether a session was stored.
        assertEquals(0L, viewModel.elapsedSeconds)
        assertFalse(viewModel.isTimerRunning)
    }

    @Test
    fun `discardSession resets elapsed time without recording anything`() = runTest {
        viewModel.startOrResumeTimer()
        viewModel.pauseTimer()

        viewModel.discardSession()

        assertEquals(0L, viewModel.elapsedSeconds)
        assertTrue(viewModel.appData.value.studySessions.isEmpty())
    }
}