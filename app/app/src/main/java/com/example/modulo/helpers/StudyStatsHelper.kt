package com.example.modulo.helpers

import com.example.modulo.StudySession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object StudyStatsHelper {

    // Local calendar days that have at least one session.
    private fun studyDays(sessions: List<StudySession>, zone: ZoneId): Set<LocalDate> =
        sessions.mapNotNull { session ->
            runCatching { Instant.parse(session.start).atZone(zone).toLocalDate() }.getOrNull()
        }.toSet()
    
    fun currentStreak(
        sessions: List<StudySession>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val days = studyDays(sessions, zone)
        // Lean on yesterday if nothing's logged for today yet.
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
    
    fun minutesThisWeek(
        sessions: List<StudySession>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val weekStart = today.with(DayOfWeek.MONDAY)
        return sessions.filter { session ->
            val day = runCatching { Instant.parse(session.start).atZone(zone).toLocalDate() }.getOrNull()
            day != null && !day.isBefore(weekStart)
        }.sumOf { it.durationMins }
    }
    
    // Minutes studied today (sessions whose local day is today).
    fun minutesToday(
        sessions: List<StudySession>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int = sessions.sumOf { session ->
        val day = runCatching { Instant.parse(session.start).atZone(zone).toLocalDate() }.getOrNull()
        if (day != null && !day.isBefore(today)) session.durationMins else 0
    }

    // Total minutes ever studied (across every session, regardless of date).
    fun totalMinutes(sessions: List<StudySession>): Int = sessions.sumOf { it.durationMins }

    // Elapsed seconds as a "HH:MM:SS" timer string.
    fun formatTimer(elapsedSeconds: Long): String {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatHours(mins: Int): String {
        val rounded = Math.round(mins / 60.0 * 10) / 10.0
        val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
        return "${text}h"
    }
}