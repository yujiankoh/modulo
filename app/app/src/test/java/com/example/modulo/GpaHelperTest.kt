package com.example.modulo

import com.example.modulo.helpers.GpaHelper
import com.example.modulo.helpers.GpaHelper.SchemeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the grade-calculator logic ([GpaHelper])
 */
class GpaHelperTest {

    private val nus5 = GpaHelper.SCHEMES.getValue("nus5")
    private val poly4 = GpaHelper.SCHEMES.getValue("poly4")
    private val EPS = 1e-9

    private fun grade(module: String, credits: Double, grade: String, su: Boolean = false) =
        Grade(module = module, credits = credits, grade = grade, su = su)

    private fun supported(level: String?) = GpaHelper.schemeForLevel(level) as SchemeResult.Supported

    @Test
    fun `schemeForLevel maps university to nus5 and polytechnic to poly4`() {
        val uni = supported("university")
        assertEquals("nus5", uni.scheme.id)
        assertEquals(5.0, uni.scheme.maxPoints, EPS)

        val poly = supported("poly")   // "poly" is the stored educationLevel value
        assertEquals("poly4", poly.scheme.id)
        assertEquals(4.0, poly.scheme.maxPoints, EPS)
    }

    @Test
    fun `schemeForLevel stubs jc secondary primary with a human-readable reason`() {
        for (level in listOf("jc", "secondary", "primary")) {
            val result = GpaHelper.schemeForLevel(level)
            assertTrue("$level must be unsupported", result is SchemeResult.Unsupported)
            assertTrue("$level needs a reason to show", (result as SchemeResult.Unsupported).reason.isNotEmpty())
        }
    }

    @Test
    fun `schemeForLevel treats a null or unknown level as unsupported, not an error`() {
        assertTrue(GpaHelper.schemeForLevel(null) is SchemeResult.Unsupported)
        assertTrue(GpaHelper.schemeForLevel("hogwarts") is SchemeResult.Unsupported)
    }

    @Test
    fun `NUS worked example - A+ B+ A- C at 4 MCs each is 3_875`() {
        val entries = listOf(
            grade("CS2030S", 4.0, "A+"),
            grade("MA1521", 4.0, "B+"),
            grade("CS2040S", 4.0, "A-"),
            grade("GEA1000", 4.0, "C"),
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals(3.875, result.gpa!!, EPS)
        assertEquals(16.0, result.gradedCredits, EPS)
        assertEquals(4, result.gradedCount)
        assertEquals(0, result.excludedCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `credit weighting - a bigger module pulls the average harder`() {
        val entries = listOf(
            grade("CP2106", 8.0, "A"),
            grade("CS1101S", 4.0, "F"),
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals(40.0 / 12.0, result.gpa!!, EPS)
        assertEquals(12.0, result.gradedCredits, EPS)
    }

    @Test
    fun `poly worked example - DIST B C+ with mixed credits`() {
        val entries = listOf(
            grade("IT1234", 5.0, "DIST"),
            grade("MS5678", 4.0, "B"),
            grade("LC9012", 3.0, "C+"),
        )
        val result = GpaHelper.computeGPA(entries, poly4)
        assertEquals(39.5 / 12.0, result.gpa!!, EPS)
        assertEquals(3, result.gradedCount)
    }

    @Test
    fun `S U entries are excluded - the GPA is identical with or without them`() {
        val graded = listOf(
            grade("CS2030S", 4.0, "A"),
            grade("MA1521", 4.0, "B"),
        )
        val withSU = graded + listOf(
            grade("GEC1015", 4.0, "S"),
            grade("ES1103", 4.0, "U"),
            grade("CP2201", 2.0, "CS"),
        )
        val base = GpaHelper.computeGPA(graded, nus5)
        val result = GpaHelper.computeGPA(withSU, nus5)
        assertEquals(base.gpa!!, result.gpa!!, EPS)
        assertEquals("excluded credits must not enter the denominator", base.gradedCredits, result.gradedCredits, EPS)
        assertEquals(3, result.excludedCount)
        assertEquals("excluded is not skipped — S/U are valid grades", 0, result.skippedCount)
    }

    @Test
    fun `poly P pass-fail is excluded the same way`() {
        val entries = listOf(
            grade("IT1234", 4.0, "A"),
            grade("LC0001", 2.0, "P"),
        )
        val result = GpaHelper.computeGPA(entries, poly4)
        assertEquals(4.0, result.gpa!!, EPS)
        assertEquals(1, result.excludedCount)
    }

    @Test
    fun `su election - row keeps its letter but is excluded`() {
        val entries = listOf(
            grade("CS2030S", 4.0, "A"),
            grade("ES1103", 4.0, "B", su = true),  // elected S/U
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals("the elected B must not count", 5.0, result.gpa!!, EPS)
        assertEquals(4.0, result.gradedCredits, EPS)
        assertEquals(1, result.excludedCount)
        assertEquals("elected is excluded, not skipped", 0, result.skippedCount)
    }

    @Test
    fun `su true excludes even an otherwise-junk row, su false counts normally`() {
        val entries = listOf(
            grade("CS2030S", 4.0, "A"),
            grade("X1", 0.0, "??", su = true),
            grade("MA1521", 4.0, "B", su = false),
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals(4.25, result.gpa!!, EPS)
        assertEquals(1, result.excludedCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `suElection is a scheme flag - nus5 supports the election, poly4 does not`() {
        assertTrue(nus5.suElection)
        assertTrue(!poly4.suElection)
    }

    @Test
    fun `all-excluded entries give gpa null, not 0`() {
        val entries = listOf(
            grade("GEC1015", 4.0, "S"),
            grade("ES1103", 4.0, "U"),
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertNull(result.gpa)
        assertEquals(2, result.excludedCount)
    }

    @Test
    fun `empty entries give gpa null, all counts 0`() {
        val result = GpaHelper.computeGPA(emptyList(), nus5)
        assertNull(result.gpa)
        assertEquals(0, result.gradedCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `junk rows are skipped and reported, never thrown, and do not taint the average`() {
        val entries = listOf(
            grade("CS2030S", 4.0, "A"),
            grade("X1", 4.0, "A*"),            // unknown grade
            grade("X2", 0.0, "B"),             // zero credits
            grade("X3", -4.0, "B"),            // negative credits
            grade("X4", Double.NaN, "B"),      // NaN credits
            grade("X5", 4.0, ""),              // no grade at all
        )
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals(5.0, result.gpa!!, EPS)
        assertEquals(1, result.gradedCount)
        assertEquals(5, result.skippedCount)
    }

    @Test
    fun `grades are normalised - lowercase and padded strings still count`() {
        val entries = listOf(
            grade("CS2030S", 4.0, "a+"),
            grade("MA1521", 4.0, " b "),
            grade("GEC1015", 4.0, "s"),
        )
        // (5.0×4 + 3.5×4) / 8 = 34/8 = 4.25; the lowercase "s" is excluded, not skipped.
        val result = GpaHelper.computeGPA(entries, nus5)
        assertEquals(4.25, result.gpa!!, EPS)
        assertEquals(1, result.excludedCount)
        assertEquals(0, result.skippedCount)
    }

    private fun multiSemesterState() = AppData(
        handbookId = "NOW",
        educationLevel = "university",
        grades = listOf(grade("CS2030S", 4.0, "A")),      // 5.0×4
        otherHandbooks = listOf(
            Handbook(id = "PAST-UNI", educationLevel = "university", grades = listOf(grade("MA1521", 4.0, "B"))), // 3.5×4
            Handbook(id = "PAST-JC", educationLevel = "jc", grades = listOf(grade("H2 Math", 4.0, "A"))),         // must NOT count
            Handbook(id = "PRE-16", educationLevel = "university"),                                               // no grades
        ),
    )

    @Test
    fun `cumulativeGPA pools the active handbook with same-scheme stored handbooks only`() {
        val result = GpaHelper.cumulativeGPA(multiSemesterState())!!
        assertEquals(4.25, result.gpa!!, EPS)
        assertEquals(8.0, result.gradedCredits, EPS)
        assertEquals(2, result.gradedCount)
    }

    @Test
    fun `cumulativeGPA - poly and university never combine even though both are numeric`() {
        val state = multiSemesterState().copy(
            otherHandbooks = listOf(
                Handbook(id = "PAST-POLY", educationLevel = "poly", grades = listOf(grade("IT1234", 20.0, "DIST"))),
            )
        )
        val result = GpaHelper.cumulativeGPA(state)!!
        assertEquals("only the active handbook's A must count", 5.0, result.gpa!!, EPS)
        assertEquals(4.0, result.gradedCredits, EPS)
    }

    @Test
    fun `cumulativeGPA returns null when the active level has no scheme`() {
        val state = multiSemesterState().copy(educationLevel = "jc")
        assertNull(GpaHelper.cumulativeGPA(state))
    }
}
