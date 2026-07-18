package com.example.modulo

import com.example.modulo.helpers.StudyStatsHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for the pure study-stats logic ([StudyStatsHelper])
 */
class StudyStatsHelperTest {

    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15) // a Wednesday; that week's Monday is 2026-07-13

    // A session on the given local date.
    private fun session(date: String, mins: Int = 60) = StudySession(start = "${date}T10:00:00Z", end = "${date}T11:00:00Z", durationMins = mins)

    @Test
    fun `no sessions means no streak`() {
        assertEquals(0, StudyStatsHelper.currentStreak(emptyList(), today, utc))
    }

    @Test
    fun `a session today is a one-day streak`() {
        val sessions = listOf(session("2026-07-15"))
        assertEquals(1, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `consecutive days ending today count up`() {
        val sessions = listOf(session("2026-07-13"), session("2026-07-14"), session("2026-07-15"))
        assertEquals(3, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `an empty today leans on yesterday`() {
        val sessions = listOf(session("2026-07-13"), session("2026-07-14"))
        assertEquals(2, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `a gap resets the streak`() {
        val sessions = listOf(session("2026-07-15"), session("2026-07-12"), session("2026-07-11"))
        assertEquals(1, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `neither today nor yesterday means the streak is broken`() {
        val sessions = listOf(session("2026-07-10"), session("2026-07-11"))
        assertEquals(0, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `multiple sessions on the same day count once`() {
        val sessions = listOf(session("2026-07-15", 30), session("2026-07-15", 45), session("2026-07-14"))
        assertEquals(2, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `unparseable session dates are ignored, not thrown`() {
        val sessions = listOf(session("2026-07-15"), StudySession(start = "not-a-date", end = "", durationMins = 10))
        assertEquals(1, StudyStatsHelper.currentStreak(sessions, today, utc))
    }

    @Test
    fun `minutesThisWeek sums from this week's Monday and excludes earlier`() {
        val sessions = listOf(
            session("2026-07-13", 30),  // Monday, in week
            session("2026-07-15", 60),  // Wednesday (today), in week
            session("2026-07-12", 100), // Sunday, previous week — excluded
        )
        assertEquals(90, StudyStatsHelper.minutesThisWeek(sessions, today, utc))
    }

    @Test
    fun `minutesThisWeek is zero with no sessions this week`() {
        val sessions = listOf(session("2026-07-06", 200))
        assertEquals(0, StudyStatsHelper.minutesThisWeek(sessions, today, utc))
    }

    @Test
    fun `minutesToday counts only today's sessions`() {
        val sessions = listOf(
            session("2026-07-15", 25),  // today
            session("2026-07-15", 35),  // today
            session("2026-07-14", 90),  // yesterday — excluded
        )
        assertEquals(60, StudyStatsHelper.minutesToday(sessions, today, utc))
    }

    @Test
    fun `totalMinutes sums every session regardless of date`() {
        val sessions = listOf(session("2026-07-15", 10), session("2026-01-01", 20), session("2025-12-31", 5))
        assertEquals(35, StudyStatsHelper.totalMinutes(sessions))
    }

    @Test
    fun `formatTimer renders zero-padded HH MM SS`() {
        assertEquals("00:00:00", StudyStatsHelper.formatTimer(0))
        assertEquals("00:00:09", StudyStatsHelper.formatTimer(9))
        assertEquals("00:01:05", StudyStatsHelper.formatTimer(65))
        assertEquals("01:01:01", StudyStatsHelper.formatTimer(3661))
        assertEquals("10:00:00", StudyStatsHelper.formatTimer(36000))
    }

    @Test
    fun `formatHours renders whole and fractional hours, dropping trailing zero`() {
        assertEquals("0h", StudyStatsHelper.formatHours(0))
        assertEquals("1.5h", StudyStatsHelper.formatHours(90))
        assertEquals("3h", StudyStatsHelper.formatHours(180))
        assertEquals("14.5h", StudyStatsHelper.formatHours(870))
    }
}