package com.example.modulo.helpers

import com.example.modulo.AppData
import com.example.modulo.Grade

object GpaHelper {
    data class Scheme(
        val id: String,
        val name: String,
        val maxPoints: Double,
        val points: Map<String, Double>,
        val excluded: List<String>, // valid grades that don't enter the average
        val suElection: Boolean
    )

    val SCHEMES = mapOf(
        "nus5" to Scheme(
            id = "nus5",
            name = "NUS 5.0 GPA",
            maxPoints = 5.0,
            points = linkedMapOf(
                "A+" to 5.0, "A" to 5.0, "A-" to 4.5,
                "B+" to 4.0, "B" to 3.5, "B-" to 3.0,
                "C+" to 2.5, "C" to 2.0,
                "D+" to 1.5, "D" to 1.0,
                "F" to 0.0
            ),
            excluded = listOf("S", "U", "CS", "CU"),
            suElection = true
        ),
        "poly4" to Scheme(
            id = "poly4",
            name = "Polytechnic 4.0 GPA",
            maxPoints = 4.0,
            points = linkedMapOf(
                "DIST" to 4.0, "A" to 4.0,
                "B+" to 3.5, "B" to 3.0,
                "C+" to 2.5, "C" to 2.0,
                "D+" to 1.5, "D" to 1.0,
                "F" to 0.0
            ),
            excluded = listOf("P"),
            suElection = false
        )
    )

    private val LEVEL_TO_SCHEME = mapOf(
        "university" to "nus5",
        "poly" to "poly4"
    )

    sealed interface SchemeResult {
        data class Supported(val scheme: Scheme) : SchemeResult
        data class Unsupported(val reason: String) : SchemeResult
    }

    fun schemeForLevel(level: String?): SchemeResult {
        val schemeId = LEVEL_TO_SCHEME[level]
        if (schemeId != null) return SchemeResult.Supported(SCHEMES.getValue(schemeId))
        return SchemeResult.Unsupported(
            "No grading scheme for this education level yet."
        )
    }

    data class GpaResult(
        val gpa: Double?,
        val gradedCredits: Double,
        val gradedCount: Int,
        val excludedCount: Int,
        val skippedCount: Int
    )

    fun computeGPA(entries: List<Grade>, scheme: Scheme): GpaResult {
        var weightedPoints = 0.0
        var gradedCredits = 0.0
        var gradedCount = 0
        var excludedCount = 0
        var skippedCount = 0

        for (entry in entries) {
            if (entry.su) {
                excludedCount += 1
                continue
            }

            val grade = entry.grade.trim().uppercase()

            if (grade.isNotEmpty() && scheme.excluded.contains(grade)) {
                excludedCount += 1
                continue
            }

            val credits = entry.credits
            val creditsValid = credits.isFinite() && credits > 0
            val points = scheme.points[grade]
            if (points == null || !creditsValid) {
                skippedCount += 1
                continue
            }

            weightedPoints += points * credits
            gradedCredits += credits
            gradedCount += 1
        }

        val gpa = if (gradedCredits > 0) weightedPoints / gradedCredits else null
        return GpaResult(gpa, gradedCredits, gradedCount, excludedCount, skippedCount)
    }

    fun cumulativeGPA(data: AppData): GpaResult? {
        val active = schemeForLevel(data.educationLevel)
        if (active !is SchemeResult.Supported) return null

        val pooled = ArrayList(data.grades)
        for (handbook in data.otherHandbooks) {
            val other = schemeForLevel(handbook.educationLevel)
            if (other is SchemeResult.Supported && other.scheme.id == active.scheme.id) {
                pooled.addAll(handbook.grades)
            }
        }
        return computeGPA(pooled, active.scheme)
    }
}
