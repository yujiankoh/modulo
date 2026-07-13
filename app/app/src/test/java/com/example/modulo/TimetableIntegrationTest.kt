package com.example.modulo

import android.util.Base64
import com.example.modulo.helpers.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests for the timetable upload/parse flow in [AppViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableIntegrationTest : AppViewModelTestBase() {

    private val fakeImage = byteArrayOf(1, 2, 3, 4)

    override fun onBeforeViewModelCreated() {
        // Base64 is an Android stub in unit tests; give it a deterministic return.
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "ZmFrZQ=="
    }

    override fun onTearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `clearTimetableState returns to Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.clearTimetableState()

        assertEquals(TimetableState.Idle, viewModel.timetableState.value)
    }

    @Test
    fun `a successful parse ends in ReviewData carrying the timetable`() = runTest(mainDispatcherRule.testDispatcher) {
        val parsed = Timetable(
            educationLevel = "university",
            modules = listOf(Module(code = "CS2040S", name = "Data Structures"))
        )
        coEvery { parsingHelper.parseTimetable(any()) } returns NetworkResult.Success(parsed)

        viewModel.uploadTimetable(fakeImage, "image/png", "university")
        advanceUntilIdle()

        val state = viewModel.timetableState.value
        assertTrue(state is TimetableState.ReviewData)
        assertEquals(parsed, (state as TimetableState.ReviewData).timetable)
    }

    @Test
    fun `a 429 failure maps to the rate-limit message`() = runTest(mainDispatcherRule.testDispatcher) {
        assertErrorMessageFor(429, "Parsing limit reached for today, try again tomorrow.")
    }

    @Test
    fun `a 504 failure maps to the timeout message`() = runTest(mainDispatcherRule.testDispatcher) {
        assertErrorMessageFor(504, "Taking too long, please retry.")
    }

    @Test
    fun `a 400 failure maps to the bad-image message`() = runTest(mainDispatcherRule.testDispatcher) {
        assertErrorMessageFor(400, "Invalid image format sent to server. Please try another image.")
    }

    @Test
    fun `an unmapped failure code falls back to the generic message`() = runTest(mainDispatcherRule.testDispatcher) {
        assertErrorMessageFor(500, "Couldn't read the timetable, try another photo.")
    }

    @Test
    fun `an exception thrown while parsing surfaces the generic unexpected-error message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { parsingHelper.parseTimetable(any()) } throws RuntimeException("boom")

            viewModel.uploadTimetable(fakeImage, "image/png", "university")
            advanceUntilIdle()

            val state = viewModel.timetableState.value
            assertTrue(state is TimetableState.Error)
            assertEquals(
                "An unexpected error occurred. Please try again.",
                (state as TimetableState.Error).message
            )
        }

    @Test
    fun `saveTimetable adopts the education level and timetable then returns to Idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val timetable = Timetable(
                educationLevel = "poly",
                modules = listOf(Module(code = "EG1311", name = "Design & Make"))
            )

            viewModel.saveTimetable(timetable)

            val data = viewModel.appData.value
            assertEquals("poly", data.educationLevel)
            assertEquals(timetable, data.timetable)
            assertEquals(TimetableState.Idle, viewModel.timetableState.value)
        }

    // Extension on TestScope (not its own runTest) so callers invoke it inside their runTest block.
    private suspend fun TestScope.assertErrorMessageFor(statusCode: Int, expected: String) {
        coEvery { parsingHelper.parseTimetable(any()) } returns NetworkResult.Failure(statusCode, "server said no")

        viewModel.uploadTimetable(fakeImage, "image/png", "university")
        advanceUntilIdle()

        val state = viewModel.timetableState.value
        assertTrue(state is TimetableState.Error)
        assertEquals(expected, (state as TimetableState.Error).message)
    }
}
