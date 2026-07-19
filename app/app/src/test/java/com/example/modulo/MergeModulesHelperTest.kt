package com.example.modulo

import com.example.modulo.helpers.MergeModulesHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure timetable merge ([MergeModulesHelper])
 */
class MergeModulesHelperTest {

    private fun slot(
        day: String,
        start: String,
        end: String,
        week: String = "all",
        location: String = "",
        sessionType: String = "",
        classNo: String = ""
    ) = Slot(day, start, end, location, sessionType, classNo, week)

    @Test
    fun `sameSlot is true only when every field matches including week`() {
        val a = slot("MON", "10:00", "12:00", week = "odd")
        assertTrue(MergeModulesHelper.sameSlot(a, a.copy()))
        assertFalse(MergeModulesHelper.sameSlot(a, a.copy(week = "even")))
        assertFalse(MergeModulesHelper.sameSlot(a, a.copy(start = "11:00")))
    }

    @Test
    fun `merging into an empty timetable just adds the incoming modules`() {
        val incoming = listOf(Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00"))))
        val result = MergeModulesHelper.mergeModules(emptyList(), incoming)
        assertEquals(incoming, result)
    }

    @Test
    fun `same module across odd and even images combines its slots`() {
        val existing = listOf(
            Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00", week = "odd")))
        )
        val incoming = listOf(
            Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00", week = "even")))
        )
        val result = MergeModulesHelper.mergeModules(existing, incoming)

        assertEquals(1, result.size)
        assertEquals(
            listOf("odd", "even"),
            result.first().slots.map { it.week }
        )
    }

    @Test
    fun `a new module in the second image is appended`() {
        val existing = listOf(Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00"))))
        val incoming = listOf(Module("MA1521", "Calculus", listOf(slot("TUE", "14:00", "16:00"))))
        val result = MergeModulesHelper.mergeModules(existing, incoming)

        assertEquals(listOf("CS2030S", "MA1521"), result.map { it.code })
    }

    @Test
    fun `re-uploading the same image adds no duplicate slots`() {
        val timetable = listOf(
            Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00", week = "all")))
        )
        val result = MergeModulesHelper.mergeModules(timetable, timetable)

        assertEquals(1, result.size)
        assertEquals(1, result.first().slots.size)
    }

    @Test
    fun `modules matching by code+name merge, differing names stay separate`() {
        val existing = listOf(Module("GEA1000", "Quantitative Reasoning", listOf(slot("WED", "09:00", "11:00"))))
        // Same code, different name → treated as a distinct module (matches web's code+name key).
        val incoming = listOf(Module("GEA1000", "Different Name", listOf(slot("THU", "09:00", "11:00"))))
        val result = MergeModulesHelper.mergeModules(existing, incoming)

        assertEquals(2, result.size)
    }

    @Test
    fun `merge does not mutate the input lists`() {
        val existing = listOf(Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00", week = "odd"))))
        val incoming = listOf(Module("CS2030S", "Programming", listOf(slot("MON", "10:00", "12:00", week = "even"))))

        MergeModulesHelper.mergeModules(existing, incoming)

        assertEquals(1, existing.first().slots.size)
        assertEquals(1, incoming.first().slots.size)
    }
}