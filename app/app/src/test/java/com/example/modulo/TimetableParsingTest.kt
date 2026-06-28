package com.example.modulo

import com.example.modulo.helpers.NetworkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParsingTest {
    @Test
    fun `NetworkResult Success properly holds data`() {
        // ARRANGE
        val mockTimetable = Timetable(educationLevel = "university", modules = emptyList())

        // ACT
        val result = NetworkResult.Success(mockTimetable)

        // ASSERT
        assertTrue(result is NetworkResult.Success)
        assertEquals(mockTimetable, (result as NetworkResult.Success).data)
    }

    @Test
    fun `NetworkResult Failure correctly records error codes`() {
        // ARRANGE
        val errorCode = 504
        val errorMessage = "Timeout"

        // ACT
        val result = NetworkResult.Failure(errorCode, errorMessage)

        // ASSERT
        assertTrue(result is NetworkResult.Failure)
        assertEquals(504, result.statusCode)
        assertEquals("Timeout", result.errorMessage)
    }
}